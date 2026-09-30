use std::sync::PoisonError;

use serde_json::json;
use tauri::{ipc::Channel, AppHandle, State};

use crate::{
    builds,
    download::Verify,
    error::AppError,
    install::{self, LaunchProgress},
    instances::Instance,
    launch::{self, QuickPlay, RunningInfo},
    manifest::{self, ManifestView},
    meta::loaders::LoaderKind,
    state::AppState,
};

/// The curated builds for the Play screen (remote manifest, else cached, else bundled).
#[tauri::command]
pub async fn get_manifest(state: State<'_, AppState>) -> Result<ManifestView, AppError> {
    let url = state.settings.get().manifest_url;
    let (manifest, source, problem) = manifest::load(&state.http, &state.paths, url.as_deref()).await;
    let view = manifest::view(&manifest, source, problem);
    *state.manifest.lock().unwrap_or_else(PoisonError::into_inner) = Some(manifest);
    Ok(view)
}

/// Downloads everything a build needs without starting the game.
#[tauri::command]
pub async fn install_build(
    state: State<'_, AppState>,
    build_id: String,
    loader: LoaderKind,
    on_progress: Channel<LaunchProgress>,
) -> Result<(), AppError> {
    let manifest = state.manifest.lock().unwrap_or_else(PoisonError::into_inner).clone().unwrap_or_else(manifest::bundled);
    let (build, build_loader) = manifest
        .find(&build_id, loader)
        .ok_or_else(|| AppError::Invalid("That build isn't available any more. Pick another one.".into()))?;
    let reporter = move |p: LaunchProgress| {
        let _ = on_progress.send(p);
    };
    let instance = builds::prepare(&state, build, build_loader, &reporter).await?;
    let prepared = install::prepare(&state.http, &state.paths, &instance, Verify::Size, &reporter).await?;
    if instance.version_id.as_deref() != Some(prepared.version_id.as_str()) {
        state.instances.save(&Instance { version_id: Some(prepared.version_id), ..instance })?;
    }
    Ok(())
}

/// One click: installs the build (loader, performance mods, Doohickey Client)
/// if needed and starts the game. Resolves once the game process is running.
#[tauri::command]
pub async fn launch_build(
    app: AppHandle,
    state: State<'_, AppState>,
    build_id: String,
    loader: LoaderKind,
    quick_join: Option<String>,
    on_progress: Channel<LaunchProgress>,
) -> Result<RunningInfo, AppError> {
    let manifest = state.manifest.lock().unwrap_or_else(PoisonError::into_inner).clone().unwrap_or_else(manifest::bundled);
    let (build, build_loader) = manifest
        .find(&build_id, loader)
        .ok_or_else(|| AppError::Invalid("That build isn't available any more. Pick another one.".into()))?;
    let account = state.accounts.active().ok_or_else(|| AppError::Invalid("Sign in first.".into()))?;
    let reporter = move |p: LaunchProgress| {
        let _ = on_progress.send(p);
    };
    let instance = builds::prepare(&state, build, build_loader, &reporter).await?;
    state.settings.update(json!({ "selectedBuild": build_id, "selectedLoader": loader, "selectedInstance": instance.id }))?;
    launch::launch(&app, &state, instance, account, quick_join.map(QuickPlay::Server), &reporter).await
}
