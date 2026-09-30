//! Java runtimes. Each Minecraft version declares the runtime it needs
//! (`javaVersion.component`, e.g. `java-runtime-epsilon` = Java 25,
//! `jre-legacy` = Java 8) and Mojang hosts matching builds for every desktop
//! platform. Using those means players never have to install Java themselves.

use std::{
    collections::HashMap,
    path::{Path, PathBuf},
    time::Duration,
};

use reqwest::Client;
use serde::{Deserialize, Serialize};
use sha1::{Digest, Sha1};

use crate::{
    download::{self, Download, Progress, Verify},
    error::AppError,
    fsutil,
    meta::cached_json,
    paths::{join_rel, Paths},
};

const RUNTIME_INDEX: &str =
    "https://launchermeta.mojang.com/v1/products/java-runtime/2ec0cc96c44e5a76b9c8b7c39df7210883d12871/all.json";

#[derive(Deserialize)]
struct RuntimeEntry {
    manifest: ManifestRef,
    version: RuntimeVersion,
}

#[derive(Deserialize)]
struct ManifestRef {
    sha1: String,
    url: String,
}

#[derive(Deserialize)]
struct RuntimeVersion {
    name: String,
}

#[derive(Deserialize)]
struct RuntimeManifest {
    files: HashMap<String, RuntimeFile>,
}

#[derive(Deserialize)]
struct RuntimeFile {
    #[serde(rename = "type")]
    kind: String,
    #[serde(default)]
    executable: bool,
    #[serde(default)]
    downloads: Option<RuntimeDownloads>,
    #[serde(default)]
    #[cfg_attr(windows, allow(dead_code))]
    target: Option<String>,
}

#[derive(Deserialize)]
struct RuntimeDownloads {
    raw: RawDownload,
}

#[derive(Deserialize)]
struct RawDownload {
    sha1: String,
    size: u64,
    url: String,
}

/// An installed runtime, as shown in Settings.
#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub struct InstalledRuntime {
    pub component: String,
    pub version: String,
    pub path: String,
    pub size_bytes: u64,
}

fn platform_keys() -> &'static [&'static str] {
    if cfg!(all(windows, target_arch = "aarch64")) {
        &["windows-arm64", "windows-x64"]
    } else if cfg!(all(windows, target_pointer_width = "32")) {
        &["windows-x86"]
    } else if cfg!(windows) {
        &["windows-x64"]
    } else if cfg!(all(target_os = "macos", target_arch = "aarch64")) {
        // Java 8 has no Apple Silicon build; Rosetta runs the x64 one.
        &["mac-os-arm64", "mac-os"]
    } else if cfg!(target_os = "macos") {
        &["mac-os"]
    } else if cfg!(target_pointer_width = "32") {
        &["linux-i386"]
    } else {
        &["linux"]
    }
}

/// The java binary inside a Mojang runtime folder. `javaw` on Windows so the
/// game doesn't get a console window.
pub fn java_binary(runtime_dir: &Path) -> PathBuf {
    if cfg!(windows) {
        runtime_dir.join("bin").join("javaw.exe")
    } else if cfg!(target_os = "macos") {
        runtime_dir.join("jre.bundle/Contents/Home/bin/java")
    } else {
        runtime_dir.join("bin").join("java")
    }
}

fn marker(dir: &Path) -> PathBuf {
    dir.join(".quartz-runtime")
}

/// Makes sure `component` is installed and returns the java executable.
pub async fn ensure_runtime(
    http: &Client,
    paths: &Paths,
    component: &str,
    on_progress: impl Fn(Progress) + Send + Sync,
) -> Result<PathBuf, AppError> {
    let dir = paths.java().join(component);
    let exe = java_binary(&dir);
    if marker(&dir).exists() && exe.exists() {
        return Ok(exe);
    }

    let index: HashMap<String, HashMap<String, Vec<RuntimeEntry>>> =
        cached_json(http, paths, "java-runtimes.json", RUNTIME_INDEX, Duration::from_secs(24 * 3600)).await?;
    let entry = platform_keys()
        .iter()
        .find_map(|key| index.get(*key)?.get(component)?.first())
        .ok_or_else(|| {
            AppError::Config(format!(
                "Mojang doesn't provide the {component} Java runtime for this platform. \
                 Set a custom Java path in the profile's settings."
            ))
        })?;

    let bytes = http.get(&entry.manifest.url).send().await?.error_for_status()?.bytes().await?;
    if !fsutil::hex(&Sha1::digest(&bytes)).eq_ignore_ascii_case(&entry.manifest.sha1) {
        return Err(AppError::Network("The Java runtime manifest failed its checksum. Try again.".into()));
    }
    let manifest: RuntimeManifest = serde_json::from_slice(&bytes)?;

    let mut downloads = Vec::new();
    let mut executables = Vec::new();
    #[cfg_attr(windows, allow(unused_mut))]
    let mut links = Vec::new();
    for (rel, file) in &manifest.files {
        let path = join_rel(&dir, rel);
        match file.kind.as_str() {
            "directory" => std::fs::create_dir_all(&path)?,
            "file" => {
                if let Some(d) = &file.downloads {
                    downloads.push(Download::new(&d.raw.url, path.clone(), Some(d.raw.sha1.clone()), Some(d.raw.size)));
                    if file.executable {
                        executables.push(path);
                    }
                }
            }
            "link" => links.push((path, file.target.clone())),
            _ => {}
        }
    }
    download::fetch_all(http, downloads, Verify::Size, on_progress).await?;

    #[cfg(unix)]
    {
        use std::os::unix::fs::PermissionsExt;
        for path in &executables {
            std::fs::set_permissions(path, std::fs::Permissions::from_mode(0o755))?;
        }
        for (path, target) in &links {
            if let Some(target) = target {
                let _ = std::fs::remove_file(path);
                std::os::unix::fs::symlink(target, path)?;
            }
        }
    }
    #[cfg(not(unix))]
    let _ = (&executables, &links);

    if !exe.exists() {
        return Err(AppError::Invalid(format!("The {component} runtime downloaded but has no java executable.")));
    }
    std::fs::write(marker(&dir), &entry.version.name)?;
    Ok(exe)
}

/// Java major version of an arbitrary installation, read from its `release`
/// file (present in every JDK/JRE since Java 9, and in most Java 8 builds).
pub fn detect_major(java_exe: &Path) -> Option<u32> {
    let home = java_exe.parent()?.parent()?;
    let release = std::fs::read_to_string(home.join("release")).ok()?;
    let line = release.lines().find(|l| l.starts_with("JAVA_VERSION="))?;
    let version = line.trim_start_matches("JAVA_VERSION=").trim_matches('"');
    parse_major(version)
}

/// "1.8.0_51" → 8, "21.0.7" → 21, "25" → 25.
pub fn parse_major(version: &str) -> Option<u32> {
    let mut parts = version.split(['.', '_', '+', '-']);
    let first: u32 = parts.next()?.parse().ok()?;
    if first == 1 {
        parts.next()?.parse().ok()
    } else {
        Some(first)
    }
}

pub fn list_installed(paths: &Paths) -> Vec<InstalledRuntime> {
    let Ok(entries) = std::fs::read_dir(paths.java()) else { return vec![] };
    entries
        .filter_map(Result::ok)
        .filter_map(|e| {
            let dir = e.path();
            let version = std::fs::read_to_string(marker(&dir)).ok()?;
            Some(InstalledRuntime {
                component: e.file_name().to_string_lossy().into_owned(),
                version,
                path: java_binary(&dir).to_string_lossy().into_owned(),
                size_bytes: dir_size(&dir),
            })
        })
        .collect()
}

fn dir_size(dir: &Path) -> u64 {
    std::fs::read_dir(dir)
        .map(|entries| {
            entries
                .filter_map(Result::ok)
                .map(|e| match e.file_type() {
                    Ok(t) if t.is_dir() => dir_size(&e.path()),
                    _ => e.metadata().map(|m| m.len()).unwrap_or(0),
                })
                .sum()
        })
        .unwrap_or(0)
}

pub fn remove_runtime(paths: &Paths, component: &str) -> Result<(), AppError> {
    if component.contains(['/', '\\', '.']) {
        return Err(AppError::Invalid("Invalid runtime name.".into()));
    }
    let dir = paths.java().join(component);
    if dir.exists() {
        std::fs::remove_dir_all(dir)?;
    }
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn majors() {
        assert_eq!(parse_major("1.8.0_51"), Some(8));
        assert_eq!(parse_major("21.0.7"), Some(21));
        assert_eq!(parse_major("25"), Some(25));
        assert_eq!(parse_major("17.0.15+6"), Some(17));
    }
}
