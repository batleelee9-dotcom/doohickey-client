//! Account model and its on-disk store (`<app data>/accounts.json`).
//!
//! Only non-secret profile data lives in the JSON file. Microsoft refresh
//! tokens go to the OS keychain (see [`secrets`]).

pub mod microsoft;
pub mod offline;
pub mod secrets;
pub mod session;

use std::{
    fs, io,
    path::PathBuf,
    sync::{Mutex, MutexGuard, PoisonError},
    time::{SystemTime, UNIX_EPOCH},
};

use serde::{Deserialize, Serialize};

use crate::error::AppError;

#[derive(Clone, Copy, Debug, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "lowercase")]
pub enum AccountKind {
    Microsoft,
    Offline,
}

#[derive(Clone, Debug, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct Account {
    /// The player's Minecraft UUID (hyphenated). Stable across renames for
    /// Microsoft accounts; derived from the name for offline ones.
    pub id: String,
    pub username: String,
    pub kind: AccountKind,
    pub skin_url: Option<String>,
    pub added_at: u64,
    pub last_used_at: u64,
}

#[derive(Clone, Default, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
struct AccountsFile {
    active_id: Option<String>,
    accounts: Vec<Account>,
}

/// Guest (demo) accounts existed briefly and were removed; builds that
/// created one leave it behind. Drop those rather than failing the whole file.
fn parse_accounts(bytes: &[u8]) -> Result<AccountsFile, serde_json::Error> {
    let mut value: serde_json::Value = serde_json::from_slice(bytes)?;
    if let Some(list) = value.get_mut("accounts").and_then(serde_json::Value::as_array_mut) {
        list.retain(|a| a.get("kind").and_then(serde_json::Value::as_str) != Some("guest"));
    }
    serde_json::from_value(value)
}

/// What the UI renders: every account (most recently used first) and which one is active.
#[derive(Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct AccountsSnapshot {
    pub active_id: Option<String>,
    pub accounts: Vec<Account>,
}

pub struct AccountStore {
    path: PathBuf,
    data: Mutex<AccountsFile>,
}

impl AccountStore {
    pub fn load(path: PathBuf) -> Result<Self, AppError> {
        let data = match fs::read(&path) {
            Ok(bytes) => parse_accounts(&bytes).unwrap_or_else(|err| {
                // Keep the unreadable file for inspection rather than silently
                // overwriting it on the next save, then start fresh.
                let backup = path.with_extension(format!("json.corrupt-{}", unix_now()));
                let _ = fs::rename(&path, &backup);
                eprintln!("accounts.json was unreadable ({err}); moved to {}", backup.display());
                AccountsFile::default()
            }),
            Err(err) if err.kind() == io::ErrorKind::NotFound => AccountsFile::default(),
            // Anything else (permissions, file locked by antivirus…) must not be
            // treated as "no accounts", or the next save would wipe the real file.
            Err(err) => return Err(err.into()),
        };
        Ok(Self { path, data: Mutex::new(data) })
    }

    pub fn snapshot(&self) -> AccountsSnapshot {
        snapshot_of(&self.lock())
    }

    pub fn has_microsoft(&self) -> bool {
        self.lock().accounts.iter().any(|a| a.kind == AccountKind::Microsoft)
    }

    pub fn get(&self, id: &str) -> Option<Account> {
        self.lock().accounts.iter().find(|a| a.id == id).cloned()
    }

    pub fn active(&self) -> Option<Account> {
        let data = self.lock();
        let id = data.active_id.as_deref()?;
        data.accounts.iter().find(|a| a.id == id).cloned()
    }

    /// Refreshes the name/skin after a token refresh or a skin change
    /// (players can rename on minecraft.net at any time).
    pub fn update_profile(&self, id: &str, username: &str, skin_url: Option<String>) -> Result<(), AppError> {
        self.update(|data| {
            if let Some(a) = data.accounts.iter_mut().find(|a| a.id == id) {
                a.username = username.to_owned();
                a.skin_url = skin_url;
            }
            Ok(())
        })
        .map(|_| ())
    }

    /// Inserts `account` (or refreshes the stored copy with the same id) and makes it active.
    pub fn upsert_and_activate(&self, account: Account) -> Result<AccountsSnapshot, AppError> {
        self.update(|data| {
            match data.accounts.iter_mut().find(|a| a.id == account.id) {
                Some(existing) => {
                    existing.username = account.username;
                    existing.kind = account.kind;
                    existing.skin_url = account.skin_url;
                    existing.last_used_at = unix_now();
                }
                None => data.accounts.push(account.clone()),
            }
            data.active_id = Some(account.id);
            Ok(())
        })
    }

    pub fn activate(&self, id: &str) -> Result<AccountsSnapshot, AppError> {
        self.update(|data| {
            let account = data
                .accounts
                .iter_mut()
                .find(|a| a.id == id)
                .ok_or_else(|| AppError::Invalid("That account no longer exists.".into()))?;
            account.last_used_at = unix_now();
            data.active_id = Some(id.to_owned());
            Ok(())
        })
    }

    /// Removes the account and returns it. If it was active, the most recently
    /// used remaining account becomes active.
    pub fn remove(&self, id: &str) -> Result<(Account, AccountsSnapshot), AppError> {
        let mut removed = None;
        let snapshot = self.update(|data| {
            let index = data
                .accounts
                .iter()
                .position(|a| a.id == id)
                .ok_or_else(|| AppError::Invalid("That account no longer exists.".into()))?;
            removed = Some(data.accounts.remove(index));
            if data.active_id.as_deref() == Some(id) {
                data.active_id = data
                    .accounts
                    .iter()
                    .max_by_key(|a| a.last_used_at)
                    .map(|a| a.id.clone());
            }
            Ok(())
        })?;
        Ok((removed.expect("set by the successful update above"), snapshot))
    }

    /// Applies `mutate` to a copy, persists the copy, and only then commits it
    /// to memory — so a failed disk write never leaves memory and disk disagreeing.
    fn update(
        &self,
        mutate: impl FnOnce(&mut AccountsFile) -> Result<(), AppError>,
    ) -> Result<AccountsSnapshot, AppError> {
        let mut guard = self.lock();
        let mut next = guard.clone();
        mutate(&mut next)?;
        self.persist(&next)?;
        *guard = next;
        Ok(snapshot_of(&guard))
    }

    fn persist(&self, data: &AccountsFile) -> Result<(), AppError> {
        // Write-then-rename is atomic on every supported OS, so a crash or power
        // loss mid-save can't leave a half-written accounts.json behind.
        let tmp = self.path.with_extension("json.tmp");
        fs::write(&tmp, serde_json::to_vec_pretty(data)?)?;
        fs::rename(&tmp, &self.path)?;
        Ok(())
    }

    fn lock(&self) -> MutexGuard<'_, AccountsFile> {
        // The data is always left consistent (see `update`), so a panic in
        // another thread while holding the lock doesn't make it unusable.
        self.data.lock().unwrap_or_else(PoisonError::into_inner)
    }
}

fn snapshot_of(data: &AccountsFile) -> AccountsSnapshot {
    let mut accounts = data.accounts.clone();
    accounts.sort_by(|a, b| b.last_used_at.cmp(&a.last_used_at));
    AccountsSnapshot { active_id: data.active_id.clone(), accounts }
}

pub fn unix_now() -> u64 {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map(|d| d.as_secs())
        .unwrap_or(0)
}

#[cfg(test)]
mod tests {
    use super::*;

    fn account(id: &str, last_used_at: u64) -> Account {
        Account {
            id: id.into(),
            username: id.into(),
            kind: AccountKind::Offline,
            skin_url: None,
            added_at: 0,
            last_used_at,
        }
    }

    fn temp_store(name: &str) -> AccountStore {
        let dir = std::env::temp_dir().join(format!("quartz-test-{name}-{}", std::process::id()));
        let _ = fs::remove_dir_all(&dir);
        fs::create_dir_all(&dir).unwrap();
        AccountStore::load(dir.join("accounts.json")).unwrap()
    }

    #[test]
    fn persists_and_reloads() {
        let store = temp_store("reload");
        store.upsert_and_activate(account("a", 1)).unwrap();
        store.upsert_and_activate(account("b", 2)).unwrap();

        let reloaded = AccountStore::load(store.path.clone()).unwrap().snapshot();
        assert_eq!(reloaded.active_id.as_deref(), Some("b"));
        assert_eq!(reloaded.accounts.len(), 2);
    }

    #[test]
    fn removing_active_falls_back_to_most_recent() {
        let store = temp_store("remove");
        store.upsert_and_activate(account("old", 0)).unwrap();
        store.upsert_and_activate(account("newer", 0)).unwrap();
        store.upsert_and_activate(account("active", 0)).unwrap();
        store.activate("newer").unwrap(); // bumps last_used_at
        store.activate("active").unwrap();

        let (removed, snapshot) = store.remove("active").unwrap();
        assert_eq!(removed.id, "active");
        assert_eq!(snapshot.active_id.as_deref(), Some("newer"));
    }

    #[test]
    fn retired_guest_accounts_are_dropped_not_fatal() {
        let store = temp_store("guest");
        fs::write(
            &store.path,
            br#"{"activeId":"g","accounts":[
                {"id":"a","username":"Alex","kind":"offline","skinUrl":null,"addedAt":0,"lastUsedAt":1},
                {"id":"g","username":"Guest","kind":"guest","skinUrl":null,"addedAt":0,"lastUsedAt":2}]}"#,
        )
        .unwrap();
        let snapshot = AccountStore::load(store.path.clone()).unwrap().snapshot();
        assert_eq!(snapshot.accounts.len(), 1);
        assert_eq!(snapshot.accounts[0].username, "Alex");
    }

    #[test]
    fn corrupt_file_is_backed_up_not_lost() {
        let store = temp_store("corrupt");
        fs::write(&store.path, b"{ not json").unwrap();

        let reloaded = AccountStore::load(store.path.clone()).unwrap();
        assert!(reloaded.snapshot().accounts.is_empty());
        let backups = fs::read_dir(store.path.parent().unwrap())
            .unwrap()
            .filter_map(Result::ok)
            .filter(|e| e.file_name().to_string_lossy().contains(".corrupt-"))
            .count();
        assert_eq!(backups, 1);
    }
}
