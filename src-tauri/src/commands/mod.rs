//! Everything the UI can call. Each submodule groups one feature area; the
//! TypeScript side calls these only through the typed wrappers in `src/lib/ipc.ts`.

pub mod accounts;
pub mod builds;
pub mod instances;
pub mod mods;
pub mod servers;
pub mod skins;
pub mod updates;

use serde::Serialize;
use tauri::{AppHandle, Manager, State};
use tauri_plugin_opener::OpenerExt;

use crate::{
    accounts::{microsoft, secrets},
    client_mod,
    crash::{self, CrashContext, CrashRecord},
    discord,
    error::AppError,
    java::{self, InstalledRuntime},
    meta::{self, loaders::{self, LoaderKind, LoaderVersion}},
    settings::Settings,
    state::AppState,
    system,
};

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub struct AppInfo {
    version: String,
    platform: &'static str,
    ms_configured: bool,
    /// Release builds only allow offline accounts once a Microsoft account that
    /// owns the game has been added (see `add_offline_account`).
    offline_requires_microsoft: bool,
    total_memory_mb: u64,
    recommended_memory_mb: u32,
    data_dir: String,
    discord_configured: bool,
    curseforge_configured: bool,
    updater_configured: bool,
    quartz_client: Vec<client_mod::TargetInfo>,
}

#[tauri::command]
pub fn app_info(app: AppHandle, state: State<AppState>) -> AppInfo {
    AppInfo {
        version: app.package_info().version.to_string(),
        platform: if cfg!(windows) { "windows" } else if cfg!(target_os = "macos") { "macos" } else { "linux" },
        ms_configured: microsoft::client_id().is_some(),
        offline_requires_microsoft: !cfg!(debug_assertions),
        total_memory_mb: system::total_memory_mb(),
        recommended_memory_mb: system::recommended_memory_mb(),
        data_dir: state.paths.root.to_string_lossy().into_owned(),
        discord_configured: discord::app_id().is_some(),
        curseforge_configured: secrets::get_named("curseforge").is_some(),
        updater_configured: updates::endpoint().is_some(),
        quartz_client: client_mod::targets(),
    }
}

#[tauri::command]
pub fn get_settings(state: State<AppState>) -> Settings {
    state.settings.get()
}

#[tauri::command]
pub async fn update_settings(
    app: AppHandle,
    state: State<'_, AppState>,
    patch: serde_json::Value,
) -> Result<Settings, AppError> {
    let before = state.settings.get();
    let after = state.settings.update(patch)?;
    if before.material != after.material {
        if let Some(window) = app.get_webview_window(crate::window::MAIN) {
            crate::window::apply_material(&window, after.material);
        }
    }
    if before.discord_rpc != after.discord_rpc {
        state.discord.set_enabled(after.discord_rpc);
    }
    Ok(after)
}

#[tauri::command]
pub async fn set_curseforge_key(key: Option<String>) -> Result<bool, AppError> {
    secrets::set_named("curseforge", key.as_deref())?;
    Ok(secrets::get_named("curseforge").is_some())
}

/// Opens a launcher folder in the system file manager.
#[tauri::command]
pub async fn open_folder(app: AppHandle, state: State<'_, AppState>, kind: String, id: Option<String>) -> Result<(), AppError> {
    let path = match (kind.as_str(), id) {
        ("data", _) => state.paths.root.clone(),
        ("instance", Some(id)) => {
            state.instances.get(&id)?;
            state.paths.instance(&id)
        }
        ("mods", Some(id)) => {
            state.instances.get(&id)?;
            state.paths.instance(&id).join("mods")
        }
        ("screenshots", Some(id)) => {
            state.instances.get(&id)?;
            state.paths.instance(&id).join("screenshots")
        }
        ("logs", Some(id)) => {
            state.instances.get(&id)?;
            state.paths.instance(&id).join("logs")
        }
        _ => return Err(AppError::Invalid("Unknown folder.".into())),
    };
    std::fs::create_dir_all(&path)?;
    app.opener()
        .open_path(path.to_string_lossy(), None::<&str>)
        .map_err(|e| AppError::Invalid(format!("Couldn't open the folder: {e}")))
}

/// Opens a file (e.g. a crash report) with its default app. Only files inside
/// the launcher's data folder, so the UI can't be used to open arbitrary paths.
#[tauri::command]
pub async fn open_path(app: AppHandle, state: State<'_, AppState>, path: String) -> Result<(), AppError> {
    let target = std::fs::canonicalize(&path)?;
    let root = std::fs::canonicalize(&state.paths.root)?;
    if !target.starts_with(&root) {
        return Err(AppError::Invalid("That file is outside Doohickey's data folder.".into()));
    }
    app.opener()
        .open_path(path, None::<&str>)
        .map_err(|e| AppError::Invalid(format!("Couldn't open the file: {e}")))
}

/// Opens an https link (mod pages, CurseForge downloads) in the browser.
#[tauri::command]
pub async fn open_external(app: AppHandle, url: String) -> Result<(), AppError> {
    if !url.starts_with("https://") {
        return Err(AppError::Invalid("Only https links can be opened.".into()));
    }
    app.opener().open_url(url, None::<&str>).map_err(|e| AppError::Invalid(format!("Couldn't open the link: {e}")))
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub struct GameVersion {
    id: String,
    kind: String,
    release_time: String,
}

#[tauri::command]
pub async fn list_game_versions(state: State<'_, AppState>) -> Result<Vec<GameVersion>, AppError> {
    let manifest = meta::manifest(&state.http, &state.paths).await?;
    Ok(manifest
        .versions
        .into_iter()
        .map(|v| GameVersion { id: v.id, kind: v.kind, release_time: v.release_time })
        .collect())
}

#[tauri::command]
pub async fn list_loader_versions(
    state: State<'_, AppState>,
    loader: LoaderKind,
    game_version: String,
) -> Result<Vec<LoaderVersion>, AppError> {
    loaders::list_versions(&state.http, &state.paths, loader, &game_version).await
}

#[tauri::command]
pub async fn list_java_runtimes(state: State<'_, AppState>) -> Result<Vec<InstalledRuntime>, AppError> {
    let paths = state.paths.clone();
    tokio::task::spawn_blocking(move || java::list_installed(&paths)).await.map_err(|e| AppError::Invalid(e.to_string()))
}

#[tauri::command]
pub async fn remove_java_runtime(state: State<'_, AppState>, component: String) -> Result<(), AppError> {
    if state.games.any_running() {
        return Err(AppError::Invalid("Close running games before removing a Java runtime.".into()));
    }
    java::remove_runtime(&state.paths, &component)
}

#[tauri::command]
pub fn list_crashes(state: State<AppState>) -> Vec<CrashRecord> {
    state.crashes.list()
}

#[tauri::command]
pub async fn mark_crash_seen(state: State<'_, AppState>, id: String) -> Result<(), AppError> {
    state.crashes.mark_seen(&id)
}

#[tauri::command]
pub async fn clear_crashes(state: State<'_, AppState>) -> Result<(), AppError> {
    state.crashes.clear()
}

/// Analyses any log or crash report the player picks (e.g. from another launcher).
#[tauri::command]
pub async fn analyze_log_file(path: String) -> Result<crash::CrashAnalysis, AppError> {
    let bytes = tokio::fs::read(&path).await?;
    let text = String::from_utf8_lossy(&bytes);
    let is_report = text.contains("---- Minecraft Crash Report ----");
    let is_hs_err = text.contains("A fatal error has been detected by the Java Runtime Environment");
    let ctx = CrashContext { java_major: 0, memory_mb: 0, loader: "modded", exit_code: None };
    Ok(crash::analyze(
        if is_report || is_hs_err { "" } else { &text },
        is_report.then_some(&*text),
        is_hs_err.then_some(&*text),
        &ctx,
    ))
}
