use std::{
    fs,
    io::{self, Read},
    path::Path,
    time::{SystemTime, UNIX_EPOCH},
};

use serde::{de::DeserializeOwned, Serialize};
use sha1::{Digest, Sha1};

use crate::error::AppError;

/// Write-then-rename, so a crash mid-save never leaves a truncated file behind.
pub fn write_json_atomic<T: Serialize>(path: &Path, value: &T) -> Result<(), AppError> {
    if let Some(parent) = path.parent() {
        fs::create_dir_all(parent)?;
    }
    let mut tmp = path.as_os_str().to_owned();
    tmp.push(".tmp");
    fs::write(&tmp, serde_json::to_vec_pretty(value)?)?;
    fs::rename(&tmp, path)?;
    Ok(())
}

/// `Ok(None)` when the file doesn't exist. A file that exists but doesn't parse
/// is moved aside (`*.corrupt-<time>`) and treated as missing, so one bad write
/// can't brick the launcher.
pub fn read_json<T: DeserializeOwned>(path: &Path) -> Result<Option<T>, AppError> {
    let bytes = match fs::read(path) {
        Ok(b) => b,
        Err(e) if e.kind() == io::ErrorKind::NotFound => return Ok(None),
        Err(e) => return Err(e.into()),
    };
    match serde_json::from_slice(&bytes) {
        Ok(v) => Ok(Some(v)),
        Err(err) => {
            let mut backup = path.as_os_str().to_owned();
            backup.push(format!(".corrupt-{}", unix_now()));
            let _ = fs::rename(path, &backup);
            eprintln!("{} was unreadable ({err}); moved aside", path.display());
            Ok(None)
        }
    }
}

pub fn sha1_file(path: &Path) -> io::Result<String> {
    let mut file = fs::File::open(path)?;
    let mut hasher = Sha1::new();
    let mut buf = vec![0u8; 64 * 1024];
    loop {
        let n = file.read(&mut buf)?;
        if n == 0 {
            break;
        }
        hasher.update(&buf[..n]);
    }
    Ok(hex(&hasher.finalize()))
}

pub fn hex(bytes: &[u8]) -> String {
    bytes.iter().map(|b| format!("{b:02x}")).collect()
}

pub fn unix_now() -> u64 {
    SystemTime::now().duration_since(UNIX_EPOCH).map(|d| d.as_secs()).unwrap_or(0)
}

pub fn unix_now_ms() -> u64 {
    SystemTime::now().duration_since(UNIX_EPOCH).map(|d| d.as_millis() as u64).unwrap_or(0)
}

/// Recursive copy used for duplicating profiles.
pub fn copy_dir(from: &Path, to: &Path) -> io::Result<()> {
    fs::create_dir_all(to)?;
    for entry in fs::read_dir(from)? {
        let entry = entry?;
        let target = to.join(entry.file_name());
        if entry.file_type()?.is_dir() {
            copy_dir(&entry.path(), &target)?;
        } else {
            fs::copy(entry.path(), target)?;
        }
    }
    Ok(())
}

/// Human-friendly, filesystem-safe id derived from a display name.
pub fn slugify(name: &str) -> String {
    let mut out = String::new();
    for c in name.trim().chars() {
        if c.is_ascii_alphanumeric() {
            out.push(c.to_ascii_lowercase());
        } else if !out.ends_with('-') && !out.is_empty() {
            out.push('-');
        }
    }
    let out = out.trim_end_matches('-').to_owned();
    if out.is_empty() {
        "profile".into()
    } else {
        out.chars().take(40).collect()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn slugs() {
        assert_eq!(slugify("  Hypixel PvP 1.8.9! "), "hypixel-pvp-1-8-9");
        assert_eq!(slugify("???"), "profile");
    }
}
