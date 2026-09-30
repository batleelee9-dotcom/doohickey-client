//! ReplayMod recordings (`replay_recordings/*.mcpr`) for the Replays tab.
//!
//! Recording and playback happen in game through ReplayMod; the launcher
//! lists, describes and tidies the files, and installs ReplayMod from
//! Modrinth where a build exists for the profile's version.

use std::{io::Read, path::Path, time::SystemTime};

use serde::{Deserialize, Serialize};

use crate::error::AppError;

pub const FOLDER: &str = "replay_recordings";
/// ReplayMod's Modrinth slug and Fabric mod id.
pub const MOD: &str = "replaymod";

#[derive(Clone, Debug, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct Replay {
    pub file_name: String,
    pub name: String,
    pub size: u64,
    /// Unix milliseconds: when recording started (falls back to the file time).
    pub date: u64,
    pub duration_ms: Option<u64>,
    pub server: Option<String>,
    pub singleplayer: bool,
    pub mc_version: Option<String>,
    pub players: usize,
}

/// The `metaData.json` entry ReplayMod writes into every .mcpr.
#[derive(Default, Deserialize)]
#[serde(rename_all = "camelCase", default)]
struct MetaData {
    singleplayer: bool,
    server_name: Option<String>,
    custom_server_name: Option<String>,
    duration: Option<u64>,
    date: Option<u64>,
    mcversion: Option<String>,
    players: Vec<String>,
}

pub fn list(game_dir: &Path) -> Vec<Replay> {
    let Ok(entries) = std::fs::read_dir(game_dir.join(FOLDER)) else { return vec![] };
    let mut replays: Vec<Replay> = entries
        .filter_map(Result::ok)
        .filter(|e| e.file_name().to_string_lossy().ends_with(".mcpr"))
        .filter_map(|e| {
            let meta = e.metadata().ok()?;
            meta.is_file().then(|| read(&e.path(), meta))
        })
        .collect();
    replays.sort_by(|a, b| b.date.cmp(&a.date));
    replays
}

fn read(path: &Path, file: std::fs::Metadata) -> Replay {
    let meta = std::fs::File::open(path)
        .ok()
        .and_then(|f| zip::ZipArchive::new(f).ok())
        .and_then(|mut zip| {
            let mut json = String::new();
            zip.by_name("metaData.json").ok()?.read_to_string(&mut json).ok()?;
            serde_json::from_str::<MetaData>(&json).ok()
        })
        .unwrap_or_default();
    let modified = file
        .modified()
        .ok()
        .and_then(|t| t.duration_since(SystemTime::UNIX_EPOCH).ok())
        .map_or(0, |d| d.as_millis() as u64);
    let file_name = path.file_name().map(|n| n.to_string_lossy().into_owned()).unwrap_or_default();
    Replay {
        name: file_name.strip_suffix(".mcpr").unwrap_or(&file_name).to_owned(),
        size: file.len(),
        date: meta.date.unwrap_or(modified),
        duration_ms: meta.duration,
        server: meta.custom_server_name.or(meta.server_name).filter(|s| !s.is_empty()),
        singleplayer: meta.singleplayer,
        mc_version: meta.mcversion,
        players: meta.players.len(),
        file_name,
    }
}

/// Recording file names must stay inside replay_recordings/.
fn validate(file_name: &str) -> Result<(), AppError> {
    if !file_name.ends_with(".mcpr") || file_name.contains(['/', '\\']) || file_name.starts_with('.') {
        return Err(AppError::Invalid("Invalid replay file name.".into()));
    }
    Ok(())
}

pub fn delete(game_dir: &Path, file_name: &str) -> Result<(), AppError> {
    validate(file_name)?;
    let path = game_dir.join(FOLDER).join(file_name);
    if path.exists() {
        trash::delete(&path).map_err(|e| AppError::Invalid(format!("Couldn't move the replay to the recycle bin: {e}")))?;
    }
    Ok(())
}

pub fn rename(game_dir: &Path, file_name: &str, new_name: &str) -> Result<String, AppError> {
    validate(file_name)?;
    let stem = new_name.trim();
    if stem.is_empty() || stem.chars().any(|c| matches!(c, '<' | '>' | ':' | '"' | '|' | '?' | '*') || c.is_control()) {
        return Err(AppError::Invalid("Replay names can't be empty or contain < > : \" | ? *".into()));
    }
    let target = format!("{stem}.mcpr");
    validate(&target)?;
    let dir = game_dir.join(FOLDER);
    if target != file_name && dir.join(&target).exists() {
        return Err(AppError::Invalid(format!("There's already a replay called {stem}.")));
    }
    std::fs::rename(dir.join(file_name), dir.join(&target))?;
    Ok(target)
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::io::Write;

    #[test]
    fn reads_replay_metadata() {
        let dir = std::env::temp_dir().join(format!("quartz-replay-test-{}", std::process::id()));
        let folder = dir.join(FOLDER);
        std::fs::create_dir_all(&folder).unwrap();
        let mut zip = zip::ZipWriter::new(std::fs::File::create(folder.join("2026_09_26_20_00_00.mcpr")).unwrap());
        zip.start_file("metaData.json", zip::write::SimpleFileOptions::default()).unwrap();
        zip.write_all(br#"{"singleplayer":false,"serverName":"mc.hypixel.net","duration":90500,"date":1790000000000,"mcversion":"26.2","players":["a","b"]}"#).unwrap();
        zip.finish().unwrap();
        std::fs::write(folder.join("notes.txt"), "ignored").unwrap();

        let replays = list(&dir);
        let renamed = rename(&dir, "2026_09_26_20_00_00.mcpr", "Bedwars win");
        let after = list(&dir);
        std::fs::remove_dir_all(&dir).ok();

        assert_eq!(replays.len(), 1);
        let r = &replays[0];
        assert_eq!(r.server.as_deref(), Some("mc.hypixel.net"));
        assert_eq!((r.duration_ms, r.date, r.players), (Some(90500), 1_790_000_000_000, 2));
        assert_eq!(renamed.unwrap(), "Bedwars win.mcpr");
        assert_eq!(after[0].name, "Bedwars win");
    }

    #[test]
    fn rejects_bad_names() {
        let dir = std::env::temp_dir();
        assert!(delete(&dir, "../x.mcpr").is_err());
        assert!(delete(&dir, "x.jar").is_err());
        assert!(rename(&dir, "a.mcpr", "a/b").is_err());
        assert!(rename(&dir, "a.mcpr", "  ").is_err());
    }
}
