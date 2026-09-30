//! Launcher self-update via Tauri's signed updater. Updates are verified
//! against the public key in tauri.conf.json; the endpoint (a JSON manifest,
//! e.g. a GitHub release's latest.json) is set when building.

use serde::Serialize;
use tauri::{ipc::Channel, AppHandle};
use tauri_plugin_updater::UpdaterExt;

use crate::error::AppError;

pub fn endpoint() -> Option<String> {
    std::env::var("QUARTZ_UPDATE_ENDPOINT")
        .ok()
        .or_else(|| option_env!("QUARTZ_UPDATE_ENDPOINT").map(str::to_owned))
        .filter(|s| s.starts_with("https://"))
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub struct UpdateInfo {
    version: String,
    current_version: String,
    notes: Option<String>,
}

#[derive(Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct UpdateProgress {
    downloaded: u64,
    total: Option<u64>,
}

fn updater(app: &AppHandle) -> Result<tauri_plugin_updater::Updater, AppError> {
    let url = endpoint().ok_or_else(|| {
        AppError::Config("Auto-update isn't configured for this build (set QUARTZ_UPDATE_ENDPOINT when building).".into())
    })?;
    let url = url.parse().map_err(|_| AppError::Config("The update endpoint is not a valid URL.".into()))?;
    app.updater_builder()
        .endpoints(vec![url])
        .and_then(|b| b.build())
        .map_err(|e| AppError::Config(format!("Updater unavailable: {e}")))
}

#[tauri::command]
pub async fn check_update(app: AppHandle) -> Result<Option<UpdateInfo>, AppError> {
    let update = updater(&app)?.check().await.map_err(|e| AppError::Network(format!("Couldn't check for updates: {e}")))?;
    Ok(update.map(|u| UpdateInfo { version: u.version, current_version: u.current_version, notes: u.body }))
}

#[tauri::command]
pub async fn install_update(app: AppHandle, on_progress: Channel<UpdateProgress>) -> Result<(), AppError> {
    let update = updater(&app)?
        .check()
        .await
        .map_err(|e| AppError::Network(format!("Couldn't check for updates: {e}")))?
        .ok_or_else(|| AppError::Invalid("Doohickey is already up to date.".into()))?;
    let mut downloaded = 0u64;
    update
        .download_and_install(
            |chunk, total| {
                downloaded += chunk as u64;
                let _ = on_progress.send(UpdateProgress { downloaded, total });
            },
            || {},
        )
        .await
        .map_err(|e| AppError::Network(format!("Update failed: {e}")))?;
    app.restart();
}
