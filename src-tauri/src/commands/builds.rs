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

/// Installs an OptiFine jar the player downloaded from optifine.net (its licence
/// forbids launchers fetching or bundling it) into the build's mods folder,
/// replacing any older copy, since two copies crash Forge.
#[tauri::command]
pub async fn add_optifine(
    state: State<'_, AppState>,
    build_id: String,
    loader: LoaderKind,
    path: String,
) -> Result<String, AppError> {
    let manifest = state.manifest.lock().unwrap_or_else(PoisonError::into_inner).clone().unwrap_or_else(manifest::bundled);
    let (build, build_loader) = manifest
        .find(&build_id, loader)
        .filter(|(_, l)| l.optifine)
        .ok_or_else(|| AppError::Invalid("OptiFine isn't offered for this build.".into()))?;
    let source = std::path::Path::new(&path);
    let name = source.file_name().map(|n| n.to_string_lossy().into_owned()).unwrap_or_default();
    check_optifine_name(&name, &build.minecraft)?;

    let instance = builds::prepare(&state, build, build_loader, &|_| {}).await?;
    let mods = state.paths.instance(&instance.id).join("mods");
    std::fs::create_dir_all(&mods)?;
    for entry in std::fs::read_dir(&mods)?.flatten() {
        let old = entry.file_name().to_string_lossy().to_lowercase();
        if old.contains("optifine") && (old.ends_with(".jar") || old.ends_with(".jar.disabled")) {
            std::fs::remove_file(entry.path())?;
        }
    }
    std::fs::copy(source, mods.join(&name))?;
    Ok(name)
}

/// optifine.net names its files `OptiFine_<minecraft>_HD_U_<edition>.jar`.
fn check_optifine_name(name: &str, minecraft: &str) -> Result<(), AppError> {
    let lower = name.to_lowercase();
    if !lower.contains("optifine") || !lower.ends_with(".jar") {
        return Err(AppError::Invalid("That isn't an OptiFine download. Pick the OptiFine_….jar file from optifine.net.".into()));
    }
    if !lower.contains(&format!("_{minecraft}_")) {
        return Err(AppError::Invalid(format!("That OptiFine is for another Minecraft version. Download the one for {minecraft}.")));
    }
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::check_optifine_name;

    #[test]
    fn optifine_file_names() {
        assert!(check_optifine_name("OptiFine_1.8.9_HD_U_M5.jar", "1.8.9").is_ok());
        assert!(check_optifine_name("preview_OptiFine_1.8.9_HD_U_M6_pre2 (1).jar", "1.8.9").is_ok());
        assert!(check_optifine_name("OptiFine_1.12.2_HD_U_G5.jar", "1.8.9").is_err());
        assert!(check_optifine_name("OptiFine_1.8.9_HD_U_M5.zip", "1.8.9").is_err());
        assert!(check_optifine_name("Patcher-1.8.9.jar", "1.8.9").is_err());
    }
}
