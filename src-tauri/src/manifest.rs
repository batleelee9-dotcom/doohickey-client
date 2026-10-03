//! Curated builds ("26.3 Optimized", "1.8.9 Optimized", …): which Minecraft
//! versions Doohickey offers, with which loaders and performance mods.
//!
//! The list is a JSON manifest fetched from a URL (Settings → Advanced, or
//! `QUARTZ_MANIFEST_URL` at build time), so builds can change without a
//! launcher release. The last good copy is cached, and a copy ships inside
//! the launcher, so the Play screen always works offline.

use std::{collections::HashSet, time::Duration};

use reqwest::Client;
use serde::{Deserialize, Serialize};

use crate::{client_mod, fsutil, meta::loaders::LoaderKind, paths::Paths};

/// The newest manifest format this launcher understands.
pub const SCHEMA: u32 = 1;
const BUNDLED: &str = include_str!("../manifests/builds.json");
const CACHE: &str = "builds-manifest.json";

#[derive(Clone, Debug, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct Manifest {
    pub schema: u32,
    #[serde(default)]
    pub updated: Option<String>,
    pub builds: Vec<Build>,
}

#[derive(Clone, Debug, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct Build {
    /// Stable id; profiles on disk are keyed by it, so never reuse one.
    pub id: String,
    pub name: String,
    pub minecraft: String,
    #[serde(default)]
    pub tagline: String,
    pub loaders: Vec<BuildLoader>,
}

#[derive(Clone, Debug, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct BuildLoader {
    pub kind: LoaderKind,
    /// "stable" (the loader's recommended build at install time) or an exact version.
    #[serde(default = "stable")]
    pub version: String,
    /// Modrinth slugs, installed with their required dependencies.
    #[serde(default)]
    pub mods: Vec<String>,
}

fn stable() -> String {
    "stable".into()
}

#[derive(Clone, Copy, Debug, PartialEq, Serialize)]
#[serde(rename_all = "lowercase")]
pub enum Source {
    Remote,
    Cached,
    Bundled,
}

impl Manifest {
    pub fn find(&self, build: &str, loader: LoaderKind) -> Option<(&Build, &BuildLoader)> {
        let b = self.builds.iter().find(|b| b.id == build)?;
        Some((b, b.loaders.iter().find(|l| l.kind == loader)?))
    }

    /// Rejects manifests the Play screen couldn't use safely.
    fn validate(self) -> Result<Self, String> {
        if self.schema > SCHEMA {
            return Err(format!("it needs a newer Doohickey (manifest schema {})", self.schema));
        }
        if self.builds.is_empty() {
            return Err("it has no builds".into());
        }
        let mut ids = HashSet::new();
        for b in &self.builds {
            if b.id.is_empty() || !b.id.chars().all(|c| c.is_ascii_alphanumeric() || matches!(c, '.' | '-' | '_')) {
                return Err(format!("build id \"{}\" isn't valid", b.id));
            }
            if !ids.insert(&b.id) {
                return Err(format!("build id \"{}\" appears twice", b.id));
            }
            if b.loaders.is_empty() {
                return Err(format!("{} has no loaders", b.name));
            }
            if b.loaders.iter().any(|l| l.mods.iter().any(|m| m.is_empty() || m.contains('/'))) {
                return Err(format!("{} lists an invalid mod", b.name));
            }
        }
        Ok(self)
    }
}

fn parse(bytes: &[u8]) -> Result<Manifest, String> {
    serde_json::from_slice::<Manifest>(bytes).map_err(|e| e.to_string())?.validate()
}

pub fn bundled() -> Manifest {
    parse(BUNDLED.as_bytes()).expect("the bundled manifest is valid (tested)")
}

/// The manifest to use, where it came from, and why the remote one wasn't used (if it wasn't).
pub async fn load(http: &Client, paths: &Paths, url: Option<&str>) -> (Manifest, Source, Option<String>) {
    let cache = paths.meta().join(CACHE);
    let mut problem = None;
    if let Some(url) = url.filter(|u| !u.trim().is_empty()) {
        let fetched = async {
            let res = http.get(url).timeout(Duration::from_secs(8)).send().await.map_err(|e| e.to_string())?;
            let bytes = res.error_for_status().map_err(|e| e.to_string())?.bytes().await.map_err(|e| e.to_string())?;
            let manifest = parse(&bytes)?;
            Ok::<_, String>((manifest, bytes))
        };
        match fetched.await {
            Ok((manifest, bytes)) => {
                if std::fs::create_dir_all(paths.meta()).and_then(|()| std::fs::write(&cache, &bytes)).is_err() {
                    eprintln!("couldn't cache the build manifest");
                }
                return (manifest, Source::Remote, None);
            }
            Err(e) => problem = Some(format!("Couldn't use the build manifest from {url}: {e}.")),
        }
        if let Ok(Some(manifest)) = fsutil::read_json::<Manifest>(&cache) {
            if let Ok(manifest) = manifest.validate() {
                return (manifest, Source::Cached, problem);
            }
        }
    }
    (bundled(), Source::Bundled, problem)
}

/// The hidden profile folder a build + loader installs into.
pub fn instance_id(build: &str, loader: LoaderKind) -> String {
    let loader = serde_json::to_value(loader).ok().and_then(|v| v.as_str().map(str::to_owned)).unwrap_or_default();
    format!("build-{}-{loader}", fsutil::slugify(build))
}

/// What the Play screen shows.
#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub struct ManifestView {
    pub source: Source,
    pub problem: Option<String>,
    pub updated: Option<String>,
    pub builds: Vec<BuildView>,
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub struct BuildView {
    pub id: String,
    pub name: String,
    pub minecraft: String,
    pub tagline: String,
    pub loaders: Vec<LoaderView>,
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub struct LoaderView {
    pub kind: LoaderKind,
    pub mods: Vec<String>,
    /// Whether this combination ships Doohickey Client.
    pub quartz_client: bool,
    pub instance_id: String,
}

pub fn view(manifest: &Manifest, source: Source, problem: Option<String>) -> ManifestView {
    ManifestView {
        source,
        problem,
        updated: manifest.updated.clone(),
        builds: manifest
            .builds
            .iter()
            .map(|b| BuildView {
                id: b.id.clone(),
                name: b.name.clone(),
                minecraft: b.minecraft.clone(),
                tagline: b.tagline.clone(),
                loaders: b
                    .loaders
                    .iter()
                    .map(|l| LoaderView {
                        kind: l.kind,
                        mods: l.mods.clone(),
                        quartz_client: client_mod::supports_version(l.kind, &b.minecraft),
                        instance_id: instance_id(&b.id, l.kind),
                    })
                    .collect(),
            })
            .collect(),
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn bundled_manifest_is_valid() {
        let m = bundled();
        assert!(m.builds.iter().any(|b| b.minecraft == "1.8.9"));
        assert!(m.find("26.3", LoaderKind::Fabric).is_some());
        assert!(m.find("26.3", LoaderKind::Forge).is_none());
    }

    #[test]
    fn rejects_bad_manifests() {
        let bad = |json: &str| parse(json.as_bytes()).is_err();
        assert!(bad(r#"{"schema": 99, "builds": [{"id": "a", "name": "A", "minecraft": "1.8.9", "loaders": [{"kind": "forge"}]}]}"#));
        assert!(bad(r#"{"schema": 1, "builds": []}"#));
        assert!(bad(r#"{"schema": 1, "builds": [{"id": "../x", "name": "A", "minecraft": "1.8.9", "loaders": [{"kind": "forge"}]}]}"#));
        assert!(bad(r#"{"schema": 1, "builds": [{"id": "a", "name": "A", "minecraft": "1.8.9", "loaders": []}]}"#));
        assert!(!bad(r#"{"schema": 1, "builds": [{"id": "a", "name": "A", "minecraft": "1.8.9", "loaders": [{"kind": "forge"}]}]}"#));
    }

    #[test]
    fn instance_ids_are_stable_and_safe() {
        assert_eq!(instance_id("1.8.9", LoaderKind::LegacyFabric), "build-1-8-9-legacyfabric");
        assert_eq!(instance_id("26.3", LoaderKind::NeoForge), "build-26-3-neoforge");
    }
}
