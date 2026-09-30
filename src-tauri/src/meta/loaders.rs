//! Mod loader version lists and Fabric/Quilt installation. (Forge and
//! NeoForge need their installers run; see `forge.rs`.)

use std::{collections::HashMap, time::Duration};

use reqwest::Client;
use serde::{Deserialize, Serialize};

use super::{cached_json, mojang::VersionJson};
use crate::{error::AppError, fsutil, paths::Paths};

#[derive(Clone, Copy, Debug, PartialEq, Eq, Hash, Serialize, Deserialize)]
#[serde(rename_all = "lowercase")]
pub enum LoaderKind {
    Vanilla,
    Fabric,
    Quilt,
    Forge,
    NeoForge,
    /// Legacy Fabric (legacyfabric.net): Fabric Loader for 1.3 to 1.13.2.
    LegacyFabric,
}

impl LoaderKind {
    pub fn label(self) -> &'static str {
        match self {
            Self::Vanilla => "Vanilla",
            Self::Fabric => "Fabric",
            Self::Quilt => "Quilt",
            Self::Forge => "Forge",
            Self::NeoForge => "NeoForge",
            Self::LegacyFabric => "Legacy Fabric",
        }
    }

    /// Loader names as Modrinth spells them. Quilt runs Fabric mods too.
    pub fn modrinth_loaders(self) -> &'static [&'static str] {
        match self {
            Self::Vanilla => &[],
            Self::Fabric => &["fabric"],
            Self::Quilt => &["quilt", "fabric"],
            Self::Forge => &["forge"],
            Self::NeoForge => &["neoforge"],
            Self::LegacyFabric => &["legacy-fabric"],
        }
    }
}

#[derive(Clone, Debug, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct LoaderVersion {
    /// What gets stored on the profile and used to install.
    pub id: String,
    /// What the UI shows.
    pub name: String,
    pub stable: bool,
    pub recommended: bool,
}

const HOUR: Duration = Duration::from_secs(3600);

pub async fn list_versions(
    http: &Client,
    paths: &Paths,
    kind: LoaderKind,
    game: &str,
) -> Result<Vec<LoaderVersion>, AppError> {
    match kind {
        LoaderKind::Vanilla => Ok(vec![]),
        LoaderKind::Fabric | LoaderKind::Quilt | LoaderKind::LegacyFabric => {
            #[derive(Deserialize)]
            struct Entry {
                loader: Loader,
            }
            #[derive(Deserialize)]
            struct Loader {
                version: String,
                #[serde(default)]
                stable: Option<bool>,
            }
            let (key, url) = fabric_like_endpoint(kind, game);
            let entries: Vec<Entry> = cached_json(http, paths, &key, &url, HOUR).await?;
            let mut first_stable = true;
            Ok(entries
                .into_iter()
                .map(|e| {
                    // Quilt's meta has no `stable` flag; its betas say so in the version.
                    let stable = e.loader.stable.unwrap_or(!e.loader.version.contains("beta"));
                    let recommended = stable && std::mem::take(&mut first_stable);
                    LoaderVersion { id: e.loader.version.clone(), name: e.loader.version, stable, recommended }
                })
                .collect())
        }
        LoaderKind::Forge => {
            let all: HashMap<String, Vec<String>> = cached_json(
                http,
                paths,
                "forge-maven-metadata.json",
                "https://files.minecraftforge.net/net/minecraftforge/forge/maven-metadata.json",
                HOUR,
            )
            .await?;
            #[derive(Deserialize)]
            struct Promotions {
                promos: HashMap<String, String>,
            }
            let promos: Promotions = cached_json(
                http,
                paths,
                "forge-promotions.json",
                "https://files.minecraftforge.net/net/minecraftforge/forge/promotions_slim.json",
                HOUR,
            )
            .await?;
            let recommended = promos.promos.get(&format!("{game}-recommended"));
            let mut versions = all.get(game).cloned().unwrap_or_default();
            versions.reverse(); // newest first
            Ok(versions
                .into_iter()
                .map(|full| {
                    let name = forge_display(&full, game);
                    let is_rec = recommended.is_some_and(|r| *r == name);
                    LoaderVersion { id: full, name, stable: true, recommended: is_rec }
                })
                .collect())
        }
        LoaderKind::NeoForge => {
            #[derive(Deserialize)]
            struct Versions {
                versions: Vec<String>,
            }
            let all: Versions = cached_json(
                http,
                paths,
                "neoforge-versions.json",
                "https://maven.neoforged.net/api/maven/versions/releases/net/neoforged/neoforge",
                HOUR,
            )
            .await?;
            let mut matching: Vec<LoaderVersion> = all
                .versions
                .into_iter()
                .filter(|v| neoforge_game_version(v).as_deref() == Some(game))
                .map(|v| LoaderVersion {
                    stable: !v.contains("beta") && !v.contains("alpha"),
                    recommended: false,
                    name: v.clone(),
                    id: v,
                })
                .collect();
            matching.reverse();
            if let Some(first) = matching.iter_mut().find(|v| v.stable) {
                first.recommended = true;
            } else if let Some(first) = matching.first_mut() {
                first.recommended = true;
            }
            Ok(matching)
        }
    }
}

fn fabric_like_endpoint(kind: LoaderKind, game: &str) -> (String, String) {
    match kind {
        LoaderKind::Quilt => (
            format!("quilt-loaders-{game}.json"),
            format!("https://meta.quiltmc.org/v3/versions/loader/{game}"),
        ),
        LoaderKind::LegacyFabric => (
            format!("legacyfabric-loaders-{game}.json"),
            format!("https://meta.legacyfabric.net/v2/versions/loader/{game}"),
        ),
        _ => (
            format!("fabric-loaders-{game}.json"),
            format!("https://meta.fabricmc.net/v2/versions/loader/{game}"),
        ),
    }
}

/// "1.8.9-11.15.1.2318-1.8.9" → "11.15.1.2318"; "26.3-66.0.4" → "66.0.4".
fn forge_display(full: &str, game: &str) -> String {
    let without_prefix = full.strip_prefix(&format!("{game}-")).unwrap_or(full);
    without_prefix.strip_suffix(&format!("-{game}")).unwrap_or(without_prefix).to_owned()
}

/// NeoForge encodes the Minecraft version in its own: "21.1.77" → 1.21.1,
/// "21.0.5" → 1.21, and since the year-based releases "26.3.0.23-beta" → 26.3.
pub fn neoforge_game_version(v: &str) -> Option<String> {
    let base = v.split('-').next()?;
    let parts: Vec<u32> = base.split('.').map(|p| p.parse().ok()).collect::<Option<_>>()?;
    match parts.as_slice() {
        [major, minor, patch, _build] if *major >= 26 => Some(if *patch == 0 {
            format!("{major}.{minor}")
        } else {
            format!("{major}.{minor}.{patch}")
        }),
        [major, minor, _] if *major < 26 => Some(if *minor == 0 {
            format!("1.{major}")
        } else {
            format!("1.{major}.{minor}")
        }),
        _ => None,
    }
}

/// Writes the Fabric/Quilt launcher profile to versions/ and returns its id.
pub async fn install_fabric_like(
    http: &Client,
    paths: &Paths,
    kind: LoaderKind,
    game: &str,
    loader_version: &str,
) -> Result<String, AppError> {
    let url = match kind {
        LoaderKind::Quilt => {
            format!("https://meta.quiltmc.org/v3/versions/loader/{game}/{loader_version}/profile/json")
        }
        LoaderKind::LegacyFabric => {
            format!("https://meta.legacyfabric.net/v2/versions/loader/{game}/{loader_version}/profile/json")
        }
        _ => format!("https://meta.fabricmc.net/v2/versions/loader/{game}/{loader_version}/profile/json"),
    };
    let res = http.get(&url).send().await?;
    if res.status() == reqwest::StatusCode::NOT_FOUND || res.status() == reqwest::StatusCode::BAD_REQUEST {
        return Err(AppError::Invalid(format!(
            "{} {loader_version} isn't available for Minecraft {game}.",
            kind.label()
        )));
    }
    let profile: VersionJson = res.error_for_status()?.json().await?;
    fsutil::write_json_atomic(&paths.version_json(&profile.id), &profile)?;
    Ok(profile.id)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn neoforge_versions_map_to_minecraft() {
        assert_eq!(neoforge_game_version("21.1.77").as_deref(), Some("1.21.1"));
        assert_eq!(neoforge_game_version("21.0.5-beta").as_deref(), Some("1.21"));
        assert_eq!(neoforge_game_version("20.4.237").as_deref(), Some("1.20.4"));
        assert_eq!(neoforge_game_version("26.3.0.23-beta").as_deref(), Some("26.3"));
        assert_eq!(neoforge_game_version("26.1.1.4").as_deref(), Some("26.1.1"));
        assert_eq!(neoforge_game_version("garbage"), None);
    }

    #[test]
    fn forge_names() {
        assert_eq!(forge_display("1.8.9-11.15.1.2318-1.8.9", "1.8.9"), "11.15.1.2318");
        assert_eq!(forge_display("26.3-66.0.4", "26.3"), "66.0.4");
    }
}
