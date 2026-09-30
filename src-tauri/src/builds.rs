//! Turns a curated build (manifest entry + loader) into something
//! launchable: a hidden profile in `instances/build-<id>-<loader>/` with the
//! loader and the build's performance mods installed. Doohickey Client is added
//! at launch wherever it supports the version (see `client_mod`).

use serde::{Deserialize, Serialize};
use serde_json::json;

use crate::{
    error::AppError,
    fsutil,
    install::{LaunchProgress, Reporter, Stage},
    instances::{Instance, NewInstance},
    manifest::{self, Build, BuildLoader},
    meta::loaders::{self, LoaderKind},
    mods::{self, ModCtx},
    state::AppState,
};

/// Which of the build's mods are installed, so later launches skip Modrinth.
#[derive(Default, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
struct Synced {
    /// The manifest's mod list at the last complete sync.
    wanted: Vec<String>,
    installed: Vec<String>,
    /// No version exists for this Minecraft version and loader.
    skipped: Vec<String>,
}

fn report(r: Reporter, message: String) {
    r(LaunchProgress { stage: Stage::Loader, message, progress: None });
}

pub async fn prepare(state: &AppState, build: &Build, loader: &BuildLoader, reporter: Reporter<'_>) -> Result<Instance, AppError> {
    let id = manifest::instance_id(&build.id, loader.kind);
    let instance = match state.instances.get(&id) {
        Ok(existing) => follow_manifest(state, existing, build, loader).await?,
        Err(_) => {
            report(reporter, format!("Setting up {}", build.name));
            let loader_version = resolve_loader(state, loader.kind, &build.minecraft, &loader.version).await?;
            state.instances.create_with_id(
                &id,
                NewInstance {
                    name: format!("{} ({})", build.name, loader.kind.label()),
                    game_version: build.minecraft.clone(),
                    loader: loader.kind,
                    loader_version,
                    server: None,
                    quartz_client: Some(true),
                },
            )?
        }
    };
    sync_mods(state, &instance, loader, reporter).await?;
    Ok(instance)
}

/// A build pinned to an exact loader version follows the manifest when it changes.
async fn follow_manifest(state: &AppState, instance: Instance, build: &Build, loader: &BuildLoader) -> Result<Instance, AppError> {
    let pinned = (loader.version != "stable").then(|| loader.version.clone());
    let wrong_loader = pinned.is_some() && instance.loader_version != pinned;
    if instance.game_version == build.minecraft && !wrong_loader && (instance.loader_version.is_some() || loader.kind == LoaderKind::Vanilla) {
        return Ok(instance);
    }
    let loader_version = match pinned {
        Some(v) => Some(v),
        None => resolve_loader(state, loader.kind, &build.minecraft, &loader.version).await?,
    };
    state.instances.update(&instance.id, json!({ "gameVersion": build.minecraft, "loaderVersion": loader_version }))
}

async fn resolve_loader(state: &AppState, kind: LoaderKind, game: &str, wanted: &str) -> Result<Option<String>, AppError> {
    if kind == LoaderKind::Vanilla {
        return Ok(None);
    }
    if wanted != "stable" {
        return Ok(Some(wanted.to_owned()));
    }
    let versions = loaders::list_versions(&state.http, &state.paths, kind, game).await?;
    versions
        .iter()
        .find(|v| v.recommended)
        .or_else(|| versions.iter().find(|v| v.stable))
        .or_else(|| versions.first())
        .map(|v| Some(v.id.clone()))
        .ok_or_else(|| AppError::Invalid(format!("{} isn't available for Minecraft {game} right now.", kind.label())))
}

async fn sync_mods(state: &AppState, instance: &Instance, loader: &BuildLoader, reporter: Reporter<'_>) -> Result<(), AppError> {
    if loader.kind == LoaderKind::Vanilla {
        return Ok(());
    }
    let path = state.paths.instance(&instance.id).join(".quartz").join("build.json");
    let mut synced: Synced = fsutil::read_json(&path)?.unwrap_or_default();
    if synced.wanted == loader.mods {
        return Ok(());
    }
    let ctx = ModCtx::new(&state.http, state.paths.instance(&instance.id), instance, &state.mod_cache)?;
    let missing: Vec<&String> =
        loader.mods.iter().filter(|m| !synced.installed.contains(m) && !synced.skipped.contains(m)).collect();
    let mut complete = true;
    for (i, slug) in missing.iter().enumerate() {
        report(reporter, format!("Installing {} ({}/{})", mod_name(slug), i + 1, missing.len()));
        match mods::install_modrinth(&ctx, slug, None).await {
            Ok(_) => synced.installed.push((*slug).clone()),
            // Offline or Modrinth down: launch with what's there and finish next time.
            Err(AppError::Network(e)) => {
                eprintln!("couldn't install {slug}: {e}");
                complete = false;
                break;
            }
            // No build of this mod for this version and loader.
            Err(_) => synced.skipped.push((*slug).clone()),
        }
    }
    if complete {
        synced.wanted = loader.mods.clone();
    }
    fsutil::write_json_atomic(&path, &synced)
}

/// "ferrite-core" → "FerriteCore" for the few that don't title-case well.
fn mod_name(slug: &str) -> String {
    match slug {
        "ferrite-core" => "FerriteCore".into(),
        "entityculling" => "EntityCulling".into(),
        "immediatelyfast" => "ImmediatelyFast".into(),
        "moreculling" => "MoreCulling".into(),
        "modernfix" => "ModernFix".into(),
        "vintagefix" => "VintageFix".into(),
        "dynamic-fps" => "Dynamic FPS".into(),
        other => {
            let mut c = other.chars();
            c.next().map(|f| f.to_uppercase().collect::<String>() + c.as_str()).unwrap_or_default().replace('-', " ")
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn mod_names() {
        assert_eq!(mod_name("sodium"), "Sodium");
        assert_eq!(mod_name("ferrite-core"), "FerriteCore");
        assert_eq!(mod_name("legacy-fabric-api"), "Legacy fabric api");
    }
}
