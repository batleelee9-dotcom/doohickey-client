//! Minecraft access tokens for launching and for the skin/cape APIs.
//!
//! Tokens live only in memory (they're valid ~24h). When one is missing or
//! about to expire, the refresh token from the OS keychain is exchanged for a
//! new chain of tokens — no browser needed.

use std::{
    collections::HashMap,
    sync::{Mutex, PoisonError},
    time::{Duration, Instant},
};

use super::{microsoft, secrets, Account, AccountKind};
use crate::{error::AppError, state::AppState};

#[derive(Default)]
pub struct Sessions {
    tokens: Mutex<HashMap<String, (String, Instant)>>,
}

impl Sessions {
    fn get(&self, account_id: &str) -> Option<String> {
        let map = self.tokens.lock().unwrap_or_else(PoisonError::into_inner);
        let (token, expires) = map.get(account_id)?;
        // Leave headroom so a token doesn't expire mid-launch.
        (*expires > Instant::now() + Duration::from_secs(600)).then(|| token.clone())
    }

    pub fn put(&self, account_id: &str, token: String, expires_in: u64) {
        let expires = Instant::now() + Duration::from_secs(expires_in);
        self.tokens.lock().unwrap_or_else(PoisonError::into_inner).insert(account_id.to_owned(), (token, expires));
    }

    pub fn forget(&self, account_id: &str) {
        self.tokens.lock().unwrap_or_else(PoisonError::into_inner).remove(account_id);
    }
}

/// Everything the game needs to know about the player.
pub struct LaunchAuth {
    pub username: String,
    /// UUID without dashes, as the game expects it.
    pub uuid: String,
    pub access_token: String,
    pub user_type: &'static str,
    pub xuid: String,
}

pub async fn launch_auth(state: &AppState, account: &Account) -> Result<LaunchAuth, AppError> {
    let uuid = account.id.replace('-', "");
    match account.kind {
        AccountKind::Offline => Ok(LaunchAuth {
            username: account.username.clone(),
            uuid,
            // Use a valid-looking token and "msa" user_type so the game treats it identically to Microsoft login
            access_token: "offline-token".into(),
            user_type: "msa",
            xuid: "0".into(),
        }),
        AccountKind::Microsoft => {
            let token = minecraft_token(state, account).await?;
            let account = state.accounts.get(&account.id).unwrap_or_else(|| account.clone());
            Ok(LaunchAuth {
                username: account.username.clone(),
                uuid: account.id.replace('-', ""),
                access_token: token,
                user_type: "msa",
                xuid: "0".into(),
                })
        }
    }
}

/// A valid Minecraft services token for a Microsoft account, refreshing it
/// (and the stored profile name/skin) when needed.
pub async fn minecraft_token(state: &AppState, account: &Account) -> Result<String, AppError> {
    if account.kind != AccountKind::Microsoft {
        return Err(AppError::Invalid("Offline accounts can't use Minecraft services.".into()));
    }
    if let Some(token) = state.sessions.get(&account.id) {
        return Ok(token);
    }
    let client_id = microsoft::client_id().ok_or_else(|| AppError::Config(microsoft::NO_CLIENT_ID.into()))?;
    let refresh = secrets::get_refresh_token(&account.id)?.ok_or_else(|| {
        AppError::Auth(format!("{} needs to sign in again (no saved sign-in found).", account.username))
    })?;
    let tokens = microsoft::refresh_tokens(&state.http, &client_id, &refresh).await?;
    secrets::store_refresh_token(&account.id, &tokens.refresh_token)?;
    let (profile, session) = microsoft::login_minecraft(&state.http, &tokens.access_token, |_| {}).await?;
    state.accounts.update_profile(&account.id, &profile.name, profile.active_skin_url())?;
    state.sessions.put(&account.id, session.access_token.clone(), session.expires_in);
    Ok(session.access_token)
}
