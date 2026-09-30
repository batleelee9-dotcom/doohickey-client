//! Forge and NeoForge installation.
//!
//! Modern installers (1.13+) patch the game with "processors" — Java programs
//! shipped inside the installer. Re-implementing those is fragile, so we run
//! the official installer headlessly against our shared game folder, which
//! mirrors the `.minecraft` layout it expects.
//!
//! Legacy installers (1.12.2 and older, including the popular 1.8.9) have no
//! processors and no headless mode: their `install_profile.json` carries the
//! version JSON, and the universal jar just needs unpacking into libraries/.

use std::{
    io::Read,
    path::{Path, PathBuf},
    process::Stdio,
    time::Duration,
};

use reqwest::Client;
use serde_json::Value;
use tokio::io::AsyncReadExt;

use super::loaders::LoaderKind;
use crate::{
    download::{self, Download},
    error::AppError,
    fsutil,
    paths::{maven_path, Paths},
};

enum InstallerKind {
    Modern { id: String },
    Legacy { id: String, version_info: Value, universal: String, library: String },
}

fn installer_url(kind: LoaderKind, version: &str) -> (String, String) {
    match kind {
        LoaderKind::NeoForge => (
            format!("https://maven.neoforged.net/releases/net/neoforged/neoforge/{version}/neoforge-{version}-installer.jar"),
            format!("neoforge-{version}-installer.jar"),
        ),
        _ => (
            format!("https://maven.minecraftforge.net/net/minecraftforge/forge/{version}/forge-{version}-installer.jar"),
            format!("forge-{version}-installer.jar"),
        ),
    }
}

/// Installs the loader (if needed) and returns the version id to launch.
pub async fn install(
    http: &Client,
    paths: &Paths,
    kind: LoaderKind,
    version: &str,
    java: &Path,
) -> Result<String, AppError> {
    let (url, file_name) = installer_url(kind, version);
    let installer = paths.installers().join(&file_name);
    // Maven publishes a .sha1 beside every artifact; use it when available.
    let sha1 = match http.get(format!("{url}.sha1")).send().await {
        Ok(res) if res.status().is_success() => res.text().await.ok().map(|t| t.trim().to_owned()),
        _ => None,
    }
    .filter(|s| s.len() == 40);
    download::fetch_one(http, Download::new(&url, installer.clone(), sha1, None)).await.map_err(|e| {
        AppError::Network(format!("Couldn't download the {} {version} installer: {e}", kind.label()))
    })?;

    let inspected = {
        let installer = installer.clone();
        tokio::task::spawn_blocking(move || inspect(&installer))
            .await
            .map_err(|e| AppError::Invalid(e.to_string()))??
    };

    match inspected {
        InstallerKind::Legacy { id, version_info, universal, library } => {
            let target = paths.libraries().join(&library);
            if !target.exists() {
                let installer = installer.clone();
                let target = target.clone();
                tokio::task::spawn_blocking(move || extract_entry(&installer, &universal, &target))
                    .await
                    .map_err(|e| AppError::Invalid(e.to_string()))??;
            }
            fsutil::write_json_atomic(&paths.version_json(&id), &version_info)?;
            Ok(id)
        }
        InstallerKind::Modern { id } => {
            if paths.version_json(&id).exists() && generated_files_present(paths, &id) {
                return Ok(id);
            }
            run_installer(paths, kind, &installer, java).await?;
            if !paths.version_json(&id).exists() {
                return Err(AppError::Invalid(format!(
                    "The {} installer finished without creating {id}. Its log is in {}.",
                    kind.label(),
                    paths.logs().display()
                )));
            }
            Ok(id)
        }
    }
}

fn inspect(installer: &Path) -> Result<InstallerKind, AppError> {
    let file = std::fs::File::open(installer)?;
    let mut zip = zip::ZipArchive::new(file).map_err(corrupt)?;

    let mut profile = String::new();
    zip.by_name("install_profile.json").map_err(corrupt)?.read_to_string(&mut profile)?;
    let profile: Value = serde_json::from_str(&profile)?;

    if let (Some(info), Some(install)) = (profile.get("versionInfo"), profile.get("install")) {
        let id = info["id"].as_str().ok_or_else(|| corrupt("missing versionInfo.id"))?.to_owned();
        let universal = install["filePath"].as_str().ok_or_else(|| corrupt("missing install.filePath"))?.to_owned();
        let library = install["path"].as_str().and_then(maven_path).ok_or_else(|| corrupt("missing install.path"))?;
        return Ok(InstallerKind::Legacy { id, version_info: info.clone(), universal, library });
    }

    let mut version = String::new();
    zip.by_name("version.json").map_err(corrupt)?.read_to_string(&mut version)?;
    let version: Value = serde_json::from_str(&version)?;
    let id = version["id"].as_str().ok_or_else(|| corrupt("missing version.json id"))?.to_owned();
    Ok(InstallerKind::Modern { id })
}

fn extract_entry(zip_path: &Path, entry: &str, target: &Path) -> Result<(), AppError> {
    let mut zip = zip::ZipArchive::new(std::fs::File::open(zip_path)?).map_err(corrupt)?;
    let mut src = zip.by_name(entry).map_err(corrupt)?;
    if let Some(parent) = target.parent() {
        std::fs::create_dir_all(parent)?;
    }
    let mut out = std::fs::File::create(target)?;
    std::io::copy(&mut src, &mut out)?;
    Ok(())
}

/// The installer's processors produce libraries with an empty download URL
/// (e.g. the patched client jar). If any is missing, the install is incomplete.
fn generated_files_present(paths: &Paths, id: &str) -> bool {
    let Ok(Some(version)) = fsutil::read_json::<Value>(&paths.version_json(id)) else { return false };
    let Some(libs) = version["libraries"].as_array() else { return false };
    libs.iter()
        .filter(|l| l["downloads"]["artifact"]["url"].as_str() == Some(""))
        .filter_map(|l| l["downloads"]["artifact"]["path"].as_str())
        .all(|p| crate::paths::join_rel(&paths.libraries(), p).exists())
}

async fn run_installer(paths: &Paths, kind: LoaderKind, installer: &Path, java: &Path) -> Result<(), AppError> {
    // The installer refuses to run unless the target looks like a launcher folder.
    let profiles = paths.file("launcher_profiles.json");
    if !profiles.exists() {
        std::fs::write(&profiles, r#"{ "profiles": {} }"#)?;
    }
    std::fs::create_dir_all(paths.logs())?;
    let log_path: PathBuf =
        paths.logs().join(format!("{}-installer-{}.log", kind.label().to_lowercase(), fsutil::unix_now()));

    let flag = if kind == LoaderKind::NeoForge { "--install-client" } else { "--installClient" };
    let mut cmd = tokio::process::Command::new(java);
    cmd.arg("-jar")
        .arg(installer)
        .arg(flag)
        .arg(&paths.root)
        .current_dir(paths.installers())
        .stdin(Stdio::null())
        .stdout(Stdio::piped())
        .stderr(Stdio::piped())
        .kill_on_drop(true);
    #[cfg(windows)]
    cmd.creation_flags(0x0800_0000); // CREATE_NO_WINDOW

    let mut child = cmd.spawn().map_err(|e| AppError::Invalid(format!("Couldn't start Java for the installer: {e}")))?;
    let mut stdout = child.stdout.take().expect("piped");
    let mut stderr = child.stderr.take().expect("piped");
    let collect = async {
        let (mut out, mut err) = (Vec::new(), Vec::new());
        let _ = tokio::join!(stdout.read_to_end(&mut out), stderr.read_to_end(&mut err));
        let status = child.wait().await;
        (out, err, status)
    };
    // Processors can take a few minutes on slow disks; anything past 15 is stuck.
    let (out, err, status) = tokio::time::timeout(Duration::from_secs(15 * 60), collect)
        .await
        .map_err(|_| AppError::Invalid(format!("The {} installer timed out.", kind.label())))?;

    let mut log = out;
    log.extend_from_slice(b"\n--- stderr ---\n");
    log.extend_from_slice(&err);
    std::fs::write(&log_path, &log)?;

    if !status?.success() {
        let text = String::from_utf8_lossy(&log);
        let last = text.lines().rev().find(|l| !l.trim().is_empty() && !l.starts_with("---")).unwrap_or("");
        return Err(AppError::Invalid(format!(
            "The {} installer failed: {last} (full log: {})",
            kind.label(),
            log_path.display()
        )));
    }
    Ok(())
}

fn corrupt(e: impl std::fmt::Display) -> AppError {
    AppError::Invalid(format!("The loader installer is corrupt or unsupported ({e}). Try again to re-download it."))
}
