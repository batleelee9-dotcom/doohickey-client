use serde::Serialize;
use tauri::State;

use crate::{
    accounts::secrets,
    error::AppError,
    mods::{self, curseforge, modrinth, InstallOutcome, InstalledMod, ModCtx, ModUpdate, SearchHit},
    state::AppState,
};

fn ctx<'a>(state: &'a AppState, instance_id: &str) -> Result<ModCtx<'a>, AppError> {
    let instance = state.instances.get(instance_id)?;
    ModCtx::new(&state.http, state.paths.instance(instance_id), &instance, &state.mod_cache)
}

fn curseforge_key() -> Result<String, AppError> {
    secrets::get_named("curseforge").ok_or_else(|| {
        AppError::Config(
            "CurseForge needs an API key. Get one at console.curseforge.com and add it in Settings → Integrations.".into(),
        )
    })
}

#[tauri::command]
pub async fn list_mods(state: State<'_, AppState>, instance_id: String) -> Result<Vec<InstalledMod>, AppError> {
    let ctx = ctx(&state, &instance_id)?;
    // Parsing jars is file I/O; keep it off the async workers.
    tokio::task::block_in_place(|| mods::list(&ctx))
}

#[tauri::command]
pub async fn mod_icon(state: State<'_, AppState>, instance_id: String, file_name: String) -> Result<Option<String>, AppError> {
    let ctx = ctx(&state, &instance_id)?;
    tokio::task::block_in_place(|| mods::icon(&ctx, &file_name))
}

#[tauri::command]
pub async fn set_mod_enabled(state: State<'_, AppState>, instance_id: String, file_name: String, enabled: bool) -> Result<(), AppError> {
    mods::set_enabled(&ctx(&state, &instance_id)?, &file_name, enabled)
}

#[tauri::command]
pub async fn remove_mod(state: State<'_, AppState>, instance_id: String, file_name: String) -> Result<(), AppError> {
    mods::remove(&ctx(&state, &instance_id)?, &file_name)
}

#[tauri::command]
pub async fn import_mods(state: State<'_, AppState>, instance_id: String, paths: Vec<String>) -> Result<Vec<String>, AppError> {
    let ctx = ctx(&state, &instance_id)?;
    paths.iter().map(|p| mods::import_file(&ctx, std::path::Path::new(p))).collect()
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub struct SearchResults {
    hits: Vec<SearchHit>,
    total: u32,
}

#[tauri::command]
pub async fn search_mods(
    state: State<'_, AppState>,
    instance_id: String,
    platform: String,
    query: String,
    sort: String,
    offset: u32,
) -> Result<SearchResults, AppError> {
    let ctx = ctx(&state, &instance_id)?;
    let (hits, total) = match platform.as_str() {
        "curseforge" => {
            curseforge::search(&state.http, &curseforge_key()?, &query, &ctx.game_version, ctx.loader, &sort, offset).await?
        }
        _ => modrinth::search(&state.http, &query, &ctx.game_version, ctx.loader, &sort, offset).await?,
    };
    Ok(SearchResults { hits, total })
}

#[tauri::command]
pub async fn install_mod(
    state: State<'_, AppState>,
    instance_id: String,
    platform: String,
    project_id: String,
) -> Result<InstallOutcome, AppError> {
    let ctx = ctx(&state, &instance_id)?;
    match platform.as_str() {
        "curseforge" => mods::install_curseforge(&ctx, &curseforge_key()?, &project_id).await,
        _ => mods::install_modrinth(&ctx, &project_id, None).await,
    }
}

#[tauri::command]
pub async fn check_mod_updates(state: State<'_, AppState>, instance_id: String) -> Result<Vec<ModUpdate>, AppError> {
    let ctx = ctx(&state, &instance_id)?;
    mods::check_updates(&ctx).await
}

#[tauri::command]
pub async fn update_mod(state: State<'_, AppState>, instance_id: String, file_name: String, version_id: String) -> Result<String, AppError> {
    let ctx = ctx(&state, &instance_id)?;
    mods::apply_update(&ctx, &file_name, &version_id).await
}

#[tauri::command]
pub async fn install_performance_pack(state: State<'_, AppState>, instance_id: String) -> Result<InstallOutcome, AppError> {
    let ctx = ctx(&state, &instance_id)?;
    mods::install_performance_pack(&ctx).await
}
