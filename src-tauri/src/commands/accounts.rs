use std::sync::{Arc, Mutex, PoisonError};

use serde::Serialize;
use tauri::{ipc::Channel, AppHandle, Manager, State, WebviewUrl, WebviewWindow, WebviewWindowBuilder, WindowEvent};
use tokio::sync::oneshot;
use uuid::Uuid;

use crate::{
    accounts::{
        microsoft::{self, LoginStep},
        offline, secrets, unix_now, Account, AccountKind, AccountsSnapshot,
    },
    error::AppError,
    state::AppState,
    window,
};

#[tauri::command]
pub fn list_accounts(state: State<AppState>) -> AccountsSnapshot {
    state.accounts.snapshot()
}

#[tauri::command]
pub fn add_offline_account(state: State<AppState>, username: String) -> Result<AccountsSnapshot, AppError> {
    let username = username.trim();
    offline::validate_username(username)?;
    // Prism Launcher's policy: offline play only once a Microsoft account that
    // owns the game is added. Debug builds skip it for development.
    if !cfg!(debug_assertions) && !state.accounts.has_microsoft() {
        return Err(AppError::Invalid(
            "Add a Microsoft account that owns Minecraft before playing offline.".into(),
        ));
    }

    let now = unix_now();
    state.accounts.upsert_and_activate(Account {
        id: offline::offline_uuid(username).hyphenated().to_string(),
        username: username.to_owned(),
        kind: AccountKind::Offline,
        skin_url: None,
        added_at: now,
        last_used_at: now,
    })
}

#[tauri::command]
pub fn switch_account(state: State<AppState>, id: String) -> Result<AccountsSnapshot, AppError> {
    state.accounts.activate(&id)
}

#[tauri::command]
pub fn remove_account(state: State<AppState>, id: String) -> Result<AccountsSnapshot, AppError> {
    let (removed, snapshot) = state.accounts.remove(&id)?;
    state.sessions.forget(&removed.id);
    if removed.kind == AccountKind::Microsoft {
        // The account is already gone from the list; a locked or unavailable
        // keychain shouldn't make "sign out" fail from the player's point of view.
        if let Err(err) = secrets::delete_refresh_token(&removed.id) {
            eprintln!("couldn't delete refresh token for {}: {err}", removed.id);
        }
    }
    Ok(snapshot)
}

/// Streamed to the UI over a Channel while `ms_login` runs.
#[derive(Clone, Serialize)]
#[serde(tag = "event", content = "data", rename_all = "camelCase", rename_all_fields = "camelCase")]
pub enum LoginEvent {
    Step { step: LoginStep },
}

/// Full Microsoft sign-in in a login window inside the launcher. Resolves once
/// the account is stored and active, or with `AppError::Cancelled` if the
/// window is closed or `ms_login_cancel` is called first.
#[tauri::command]
pub async fn ms_login(
    app: AppHandle,
    state: State<'_, AppState>,
    on_event: Channel<LoginEvent>,
) -> Result<AccountsSnapshot, AppError> {
    let client_id = microsoft::client_id(state.settings.get().ms_client_id).ok_or_else(|| AppError::Config(microsoft::NO_CLIENT_ID.into()))?;

    let (cancel_tx, cancel_rx) = oneshot::channel();
    // Replacing the sender drops the previous one, which also cancels a login
    // that is still waiting (e.g. "Continue with Microsoft" clicked twice).
    *state.login_cancel.lock().unwrap_or_else(PoisonError::into_inner) = Some(cancel_tx);

    let pkce = microsoft::Pkce::new();
    let (redirect_tx, redirect_rx) = oneshot::channel();
    let window = open_login_window(&app, &pkce.authorize_url(&client_id), redirect_tx)?;

    let login = async {
        // Closing the window drops the sender, which reads as a cancel.
        let redirect = redirect_rx.await.map_err(|_| AppError::Cancelled)?;
        let _ = window.destroy();
        let code = pkce.code_from_redirect(&redirect)?;
        let tokens = microsoft::exchange_code(&state.http, &client_id, &code, &pkce).await?;
        let (profile, session) = microsoft::login_minecraft(&state.http, &tokens.access_token, |step| {
            let _ = on_event.send(LoginEvent::Step { step });
        })
        .await?;
        Ok::<_, AppError>((tokens, profile, session))
    };

    // Dropping `login` when cancel wins aborts whatever request is in flight.
    let result = tokio::select! {
        result = login => result,
        _ = cancel_rx => Err(AppError::Cancelled),
    };
    let _ = window.destroy();
    let (tokens, profile, session) = result?;

    let id = Uuid::parse_str(&profile.id)
        .map_err(|_| AppError::Auth("Minecraft returned an invalid profile ID.".into()))?
        .hyphenated()
        .to_string();
    secrets::store_refresh_token(&id, &tokens.refresh_token)?;
    // The first launch right after signing in shouldn't need another refresh.
    state.sessions.put(&id, session.access_token, session.expires_in);

    let now = unix_now();
    state.accounts.upsert_and_activate(Account {
        id,
        skin_url: profile.active_skin_url(),
        username: profile.name,
        kind: AccountKind::Microsoft,
        added_at: now,
        last_used_at: now,
    })
}

/// Opens Microsoft's sign-in page in its own window. When Microsoft redirects
/// to `REDIRECT_URI` the navigation is stopped and the URL (carrying the code)
/// is sent on `redirect`. The page is remote, so the window gets no IPC access
/// (the capability file only covers the main window).
fn open_login_window(app: &AppHandle, url: &str, redirect: oneshot::Sender<String>) -> Result<WebviewWindow, AppError> {
    let failed = |e: tauri::Error| AppError::Auth(format!("Couldn't open the Microsoft sign-in window: {e}"));
    let url = url.parse().map_err(|_| AppError::Auth("Couldn't build the Microsoft sign-in address.".into()))?;
    let slot = Arc::new(Mutex::new(Some(redirect)));
    let on_redirect = slot.clone();
    let label = format!("ms-login-{}", Uuid::new_v4().simple());
    let mut builder = WebviewWindowBuilder::new(app, label, WebviewUrl::External(url))
        .title("Sign in with Microsoft")
        .inner_size(480.0, 640.0)
        .center()
        .on_navigation(move |url| {
            if !url.as_str().starts_with(microsoft::REDIRECT_URI) {
                return true;
            }
            if let Some(tx) = on_redirect.lock().unwrap_or_else(PoisonError::into_inner).take() {
                let _ = tx.send(url.to_string());
            }
            false
        });
    if let Some(main) = app.get_webview_window(window::MAIN) {
        builder = builder.parent(&main).map_err(failed)?;
    }
    let login = builder.build().map_err(failed)?;
    login.on_window_event(move |event| {
        if let WindowEvent::Destroyed = event {
            slot.lock().unwrap_or_else(PoisonError::into_inner).take();
        }
    });
    Ok(login)
}

#[tauri::command]
pub fn ms_login_cancel(state: State<AppState>) {
    if let Some(cancel) = state.login_cancel.lock().unwrap_or_else(PoisonError::into_inner).take() {
        let _ = cancel.send(());
    }
}
