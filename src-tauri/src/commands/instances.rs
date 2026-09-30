use serde::Serialize;
use tauri::{ipc::Channel, AppHandle, State};

use crate::{
    download::Verify,
    error::AppError,
    install::{self, LaunchProgress},
    instances::{Instance, NewInstance},
    launch::{self, logparse::LogLine, QuickPlay, RunningInfo},
    meta::loaders::LoaderKind,
    mods::{self, modrinth, InstallOutcome, ModCtx},
    replays::{self, Replay},
    state::AppState,
    worlds::{self, World},
};

#[tauri::command]
pub async fn list_instances(state: State<'_, AppState>) -> Result<Vec<Instance>, AppError> {
    Ok(state.instances.list())
}

#[tauri::command]
pub async fn create_instance(state: State<'_, AppState>, request: NewInstance) -> Result<Instance, AppError> {
    let instance = state.instances.create(request)?;
    state.settings.update(serde_json::json!({ "selectedInstance": instance.id }))?;
    Ok(instance)
}

#[tauri::command]
pub async fn update_instance(state: State<'_, AppState>, id: String, patch: serde_json::Value) -> Result<Instance, AppError> {
    state.instances.update(&id, patch)
}

#[tauri::command]
pub async fn delete_instance(state: State<'_, AppState>, id: String) -> Result<(), AppError> {
    if state.games.list().iter().any(|g| g.instance_id == id) {
        return Err(AppError::Invalid("Close the game before deleting its profile.".into()));
    }
    state.instances.delete(&id)?;
    if state.settings.get().selected_instance.as_deref() == Some(id.as_str()) {
        state.settings.update(serde_json::json!({ "selectedInstance": null }))?;
    }
    Ok(())
}

#[tauri::command]
pub async fn duplicate_instance(state: State<'_, AppState>, id: String) -> Result<Instance, AppError> {
    state.instances.duplicate(&id)
}

#[tauri::command]
pub async fn select_instance(state: State<'_, AppState>, id: String) -> Result<(), AppError> {
    state.instances.get(&id)?;
    state.settings.update(serde_json::json!({ "selectedInstance": id }))?;
    Ok(())
}

/// Installs whatever is missing and starts the game. Resolves once the game
/// process is running; its logs and exit arrive as `game://*` events.
#[tauri::command]
pub async fn launch_instance(
    app: AppHandle,
    state: State<'_, AppState>,
    id: String,
    quick_join: Option<String>,
    world: Option<String>,
    on_progress: Channel<LaunchProgress>,
) -> Result<RunningInfo, AppError> {
    let instance = state.instances.get(&id)?;
    let account = state.accounts.active().ok_or_else(|| AppError::Invalid("Sign in first.".into()))?;
    state.settings.update(serde_json::json!({ "selectedInstance": id }))?;
    let reporter = move |p: LaunchProgress| {
        let _ = on_progress.send(p);
    };
    let quick_play = match (world, quick_join) {
        (Some(world), _) => {
            worlds::validate_id(&world)?;
            Some(QuickPlay::World(world))
        }
        (None, Some(server)) => Some(QuickPlay::Server(server)),
        (None, None) => None,
    };
    launch::launch(&app, &state, instance, account, quick_play, &reporter).await
}

#[tauri::command]
pub async fn list_worlds(state: State<'_, AppState>, id: String) -> Result<Vec<World>, AppError> {
    state.instances.get(&id)?;
    let dir = state.paths.instance(&id);
    tokio::task::spawn_blocking(move || worlds::list(&dir)).await.map_err(|e| AppError::Invalid(e.to_string()))
}

#[tauri::command]
pub async fn delete_world(state: State<'_, AppState>, id: String, world: String) -> Result<(), AppError> {
    state.instances.get(&id)?;
    if state.games.list().iter().any(|g| g.instance_id == id) {
        return Err(AppError::Invalid("Close the game before deleting one of its worlds.".into()));
    }
    worlds::delete(&state.paths.instance(&id), &world)
}

#[tauri::command]
pub fn cancel_launch(state: State<AppState>, id: String) {
    state.games.cancel_prepare(&id);
}

#[tauri::command]
pub fn kill_game(state: State<AppState>, id: String) -> bool {
    state.games.kill(&id)
}

#[tauri::command]
pub fn running_games(state: State<AppState>) -> Vec<RunningInfo> {
    state.games.list()
}

#[tauri::command]
pub fn game_log(state: State<AppState>, id: String) -> Vec<LogLine> {
    state.games.log(&id)
}

/// Re-verifies every file by checksum and reinstalls the loader.
#[tauri::command]
pub async fn repair_instance(
    state: State<'_, AppState>,
    id: String,
    on_progress: Channel<LaunchProgress>,
) -> Result<(), AppError> {
    if state.games.list().iter().any(|g| g.instance_id == id) {
        return Err(AppError::Invalid("Close the game before repairing it.".into()));
    }
    let mut instance = state.instances.get(&id)?;
    let reporter = move |p: LaunchProgress| {
        let _ = on_progress.send(p);
    };
    let prepared = install::prepare(&state.http, &state.paths, &instance, Verify::Hash, &reporter).await?;
    instance.version_id = Some(prepared.version_id);
    state.instances.save(&instance)
}

#[tauri::command]
pub async fn list_replays(state: State<'_, AppState>, id: String) -> Result<Vec<Replay>, AppError> {
    state.instances.get(&id)?;
    let dir = state.paths.instance(&id);
    tokio::task::spawn_blocking(move || replays::list(&dir)).await.map_err(|e| AppError::Invalid(e.to_string()))
}

#[tauri::command]
pub async fn delete_replay(state: State<'_, AppState>, id: String, file_name: String) -> Result<(), AppError> {
    state.instances.get(&id)?;
    replays::delete(&state.paths.instance(&id), &file_name)
}

#[tauri::command]
pub async fn rename_replay(state: State<'_, AppState>, id: String, file_name: String, name: String) -> Result<String, AppError> {
    state.instances.get(&id)?;
    replays::rename(&state.paths.instance(&id), &file_name, &name)
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub struct ReplayModStatus {
    installed: bool,
    /// The ReplayMod version Modrinth has for this profile, if any.
    available: Option<String>,
}

#[tauri::command]
pub async fn replaymod_status(state: State<'_, AppState>, id: String) -> Result<ReplayModStatus, AppError> {
    let instance = state.instances.get(&id)?;
    if instance.loader == LoaderKind::Vanilla {
        return Ok(ReplayModStatus { installed: false, available: None });
    }
    let ctx = ModCtx::new(&state.http, state.paths.instance(&id), &instance, &state.mod_cache)?;
    let installed = tokio::task::block_in_place(|| mods::list(&ctx))?
        .iter()
        .any(|m| m.enabled && m.meta.mod_id.as_deref() == Some(replays::MOD));
    let available = modrinth::best_version(&state.http, replays::MOD, &instance.game_version, instance.loader)
        .await?
        .map(|v| v.version_number);
    Ok(ReplayModStatus { installed, available })
}

#[tauri::command]
pub async fn install_replaymod(state: State<'_, AppState>, id: String) -> Result<InstallOutcome, AppError> {
    let instance = state.instances.get(&id)?;
    let ctx = ModCtx::new(&state.http, state.paths.instance(&id), &instance, &state.mod_cache)?;
    mods::install_modrinth(&ctx, replays::MOD, None).await
}
