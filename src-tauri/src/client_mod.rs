//! Doohickey Client — the in-game half of Doohickey, built from `client-mod/` —
//! ships inside the launcher binary, one jar per supported Minecraft
//! version (see build.rs).
//!
//! It's loaded with Fabric's `fabric.addMods` property instead of being
//! copied into the profile's mods/ folder, so it never mixes with the
//! player's own mods, can't be left behind in an old version, and updates
//! together with the launcher.

use std::path::PathBuf;

use serde::Serialize;

use crate::{
    error::AppError,
    instances::Instance,
    meta::loaders::LoaderKind,
    mods::{self, ModCtx},
    paths::Paths,
    state::AppState,
};

/// One embedded build of Doohickey Client.
pub struct Target {
    /// The Minecraft version it's built for (patch releases included).
    pub minecraft: &'static str,
    pub loader: LoaderKind,
    pub version: &'static str,
    /// What it does, for the Mods tab.
    pub summary: &'static str,
    /// Mods it needs next to it, by Modrinth slug (= mod id).
    pub requires: &'static [&'static str],
    jar: &'static [u8],
}

include!(concat!(env!("OUT_DIR"), "/clients.rs"));

#[derive(Clone, Debug, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct TargetInfo {
    pub minecraft: &'static str,
    pub loader: LoaderKind,
    pub version: &'static str,
    pub summary: &'static str,
}

pub fn targets() -> Vec<TargetInfo> {
    TARGETS.iter().map(|t| TargetInfo { minecraft: t.minecraft, loader: t.loader, version: t.version, summary: t.summary }).collect()
}

pub fn target(loader: LoaderKind, game_version: &str) -> Option<&'static Target> {
    TARGETS.iter().find(|t| {
        t.loader == loader
            && (game_version == t.minecraft || game_version.strip_prefix(t.minecraft).is_some_and(|rest| rest.starts_with('.')))
    })
}

pub fn supports_version(loader: LoaderKind, game_version: &str) -> bool {
    target(loader, game_version).is_some()
}

/// Whether this launch should load Doohickey Client.
pub fn active(instance: &Instance) -> bool {
    instance.quartz_client && supports_version(instance.loader, &instance.game_version)
}

/// The embedded jar, written to `<data>/client/` and rewritten only when the
/// launcher ships a different build.
fn jar_path(paths: &Paths, target: &Target) -> Result<PathBuf, AppError> {
    let path = paths.root.join("client").join(format!("quartz-client-{}+{}.jar", target.version, target.minecraft));
    if std::fs::read(&path).ok().as_deref() != Some(target.jar) {
        std::fs::create_dir_all(path.parent().expect("client/"))?;
        let tmp = path.with_extension("jar.tmp");
        std::fs::write(&tmp, target.jar)?;
        std::fs::rename(&tmp, &path)?;
    }
    Ok(path)
}

/// Makes sure the profile can load the mod — its required mods present and
/// enabled — and returns the JVM property that adds it.
pub async fn prepare(state: &AppState, instance: &Instance) -> Result<String, AppError> {
    let target = target(instance.loader, &instance.game_version)
        .ok_or_else(|| AppError::Invalid("Doohickey Client isn't available for this version.".into()))?;
    if !target.requires.is_empty() {
        let ctx = ModCtx::new(&state.http, state.paths.instance(&instance.id), instance, &state.mod_cache)?;
        for slug in target.requires {
            ensure_mod(&ctx, slug).await?;
        }
    }
    let jar = jar_path(&state.paths, target)?;
    Ok(format!("-Dfabric.addMods={}", jar.display()))
}

async fn ensure_mod(ctx: &ModCtx<'_>, slug: &str) -> Result<(), AppError> {
    let installed = mods::list(ctx)?;
    let found: Vec<_> = installed.iter().filter(|m| m.meta.mod_id.as_deref() == Some(slug)).collect();
    if found.iter().any(|m| m.enabled) {
        return Ok(());
    }
    // A disabled copy is re-enabled rather than downloading a second one.
    if let Some(m) = found.first() {
        return mods::set_enabled(ctx, &m.file_name, true);
    }
    mods::install_modrinth(ctx, slug, None).await.map_err(|e| match e {
        AppError::Network(_) => AppError::Network(format!(
            "Doohickey Client needs {slug}, which couldn't be downloaded. Connect to the internet and try again."
        )),
        other => AppError::Invalid(format!("Doohickey Client needs {slug}: {other}")),
    })?;
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn version_support() {
        assert!(supports_version(LoaderKind::Fabric, "26.3"));
        assert!(supports_version(LoaderKind::Fabric, "26.3.1"));
        assert!(!supports_version(LoaderKind::Fabric, "26.31"));
        assert!(!supports_version(LoaderKind::Forge, "26.3"));
        assert!(supports_version(LoaderKind::LegacyFabric, "1.8.9"));
        assert!(!supports_version(LoaderKind::Forge, "1.8.9"));
        assert!(!supports_version(LoaderKind::Fabric, "1.8.9"));
    }

    #[test]
    fn embedded_jars_are_quartz_builds() {
        assert!(!TARGETS.is_empty());
        for t in TARGETS {
            let mut zip = zip::ZipArchive::new(std::io::Cursor::new(t.jar)).expect("jar is a zip");
            let mut json = String::new();
            std::io::Read::read_to_string(&mut zip.by_name("fabric.mod.json").expect("fabric.mod.json"), &mut json).unwrap();
            let v: serde_json::Value = serde_json::from_str(&json).unwrap();
            assert_eq!(v["id"], "quartz");
            assert_eq!(v["version"], format!("{}+{}", t.version, t.minecraft));
        }
    }
}
