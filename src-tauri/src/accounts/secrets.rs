//! Microsoft refresh tokens live in the OS keychain (Windows Credential
//! Manager, macOS Keychain, Secret Service on Linux), never in accounts.json.

use crate::error::AppError;

const SERVICE: &str = "dev.quartz.launcher";

fn entry(account_id: &str) -> Result<keyring::Entry, AppError> {
    Ok(keyring::Entry::new(SERVICE, account_id)?)
}

pub fn store_refresh_token(account_id: &str, token: &str) -> Result<(), AppError> {
    // `set_secret` stores raw UTF-8. `set_password` would store UTF-16 on
    // Windows, halving Credential Manager's 2560-byte limit — close enough to
    // the size of a Microsoft refresh token to fail for some accounts.
    entry(account_id)?.set_secret(token.as_bytes())?;
    Ok(())
}

pub fn get_refresh_token(account_id: &str) -> Result<Option<String>, AppError> {
    match entry(account_id)?.get_secret() {
        Ok(bytes) => Ok(Some(String::from_utf8_lossy(&bytes).into_owned())),
        Err(keyring::Error::NoEntry) => Ok(None),
        Err(err) => Err(err.into()),
    }
}

/// Generic secret slot for launcher-level keys (e.g. the CurseForge API key).
pub fn set_named(name: &str, value: Option<&str>) -> Result<(), AppError> {
    match value.filter(|v| !v.trim().is_empty()) {
        Some(v) => Ok(entry(&format!("key:{name}"))?.set_secret(v.trim().as_bytes())?),
        None => delete_refresh_token(&format!("key:{name}")),
    }
}

pub fn get_named(name: &str) -> Option<String> {
    get_refresh_token(&format!("key:{name}")).ok().flatten()
}

pub fn delete_refresh_token(account_id: &str) -> Result<(), AppError> {
    match entry(account_id)?.delete_credential() {
        Ok(()) | Err(keyring::Error::NoEntry) => Ok(()),
        Err(err) => Err(err.into()),
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    /// Touches the real OS keychain, so it's opt-in: `cargo test -- --ignored`.
    /// Guards against keyring silently falling back to its in-memory mock store
    /// (which it does when no platform backend feature is enabled): the mock
    /// can't read back a secret through a second, independent Entry.
    #[test]
    #[ignore]
    fn round_trips_through_os_keychain() {
        let id = "quartz-selftest";
        let token = "M.C5".to_owned() + &"x".repeat(1800); // realistic refresh-token length
        store_refresh_token(id, &token).unwrap();
        let read = keyring::Entry::new(SERVICE, id).unwrap().get_secret().unwrap();
        assert_eq!(read, token.as_bytes());

        delete_refresh_token(id).unwrap();
        let gone = keyring::Entry::new(SERVICE, id).unwrap().get_secret();
        assert!(matches!(gone, Err(keyring::Error::NoEntry)));
        delete_refresh_token(id).unwrap(); // deleting twice is fine
    }
}
