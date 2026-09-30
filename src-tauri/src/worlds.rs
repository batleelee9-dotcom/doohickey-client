//! Singleplayer worlds in a profile's saves/ folder, for the Worlds tab and
//! quick play ("Play" launches straight into the world).

use std::{collections::HashMap, io::Read, path::Path};

use base64::Engine;
use serde::Serialize;

use crate::error::AppError;

#[derive(Clone, Debug, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct World {
    /// The folder name — what quick play and the game use to find it.
    pub id: String,
    pub name: String,
    /// Unix milliseconds.
    pub last_played: Option<u64>,
    /// survival / creative / adventure / spectator / hardcore
    pub mode: Option<String>,
    pub version: Option<String>,
    pub cheats: bool,
    pub icon: Option<String>,
}

pub fn list(game_dir: &Path) -> Vec<World> {
    let Ok(entries) = std::fs::read_dir(game_dir.join("saves")) else { return vec![] };
    let mut worlds: Vec<World> = entries
        .filter_map(Result::ok)
        .filter(|e| e.path().join("level.dat").is_file())
        .map(|e| read(&e.path(), e.file_name().to_string_lossy().into_owned()))
        .collect();
    worlds.sort_by(|a, b| b.last_played.cmp(&a.last_played).then_with(|| a.name.cmp(&b.name)));
    worlds
}

fn read(dir: &Path, id: String) -> World {
    let data = std::fs::read(dir.join("level.dat"))
        .ok()
        .and_then(|bytes| {
            let mut raw = Vec::new();
            flate2::read::GzDecoder::new(&bytes[..]).read_to_end(&mut raw).ok()?;
            nbt::parse(&raw)
        })
        .and_then(|root| root.get("Data").cloned());
    let field = |key: &str| data.as_ref().and_then(|d| d.get(key));
    let hardcore = field("hardcore").and_then(nbt::Tag::as_i64) == Some(1);
    let mode = field("GameType").and_then(nbt::Tag::as_i64).map(|m| {
        match (hardcore, m) {
            (true, _) => "hardcore",
            (_, 1) => "creative",
            (_, 2) => "adventure",
            (_, 3) => "spectator",
            _ => "survival",
        }
        .to_owned()
    });
    let icon = std::fs::read(dir.join("icon.png"))
        .ok()
        .filter(|b| b.len() < 256 * 1024)
        .map(|b| format!("data:image/png;base64,{}", base64::engine::general_purpose::STANDARD.encode(b)));
    World {
        name: field("LevelName").and_then(nbt::Tag::as_str).filter(|n| !n.trim().is_empty()).unwrap_or(&id).to_owned(),
        last_played: field("LastPlayed").and_then(nbt::Tag::as_i64).and_then(|t| u64::try_from(t).ok()),
        mode,
        version: field("Version").and_then(|v| v.get("Name")).and_then(nbt::Tag::as_str).map(str::to_owned),
        cheats: field("allowCommands").and_then(nbt::Tag::as_i64) == Some(1),
        icon,
        id,
    }
}

/// Folder names must stay inside saves/.
pub fn validate_id(id: &str) -> Result<(), AppError> {
    if id.is_empty() || id.contains(['/', '\\']) || id == "." || id == ".." {
        return Err(AppError::Invalid("Invalid world name.".into()));
    }
    Ok(())
}

pub fn delete(game_dir: &Path, id: &str) -> Result<(), AppError> {
    validate_id(id)?;
    let dir = game_dir.join("saves").join(id);
    if dir.exists() {
        trash::delete(&dir).map_err(|e| AppError::Invalid(format!("Couldn't move the world to the recycle bin: {e}")))?;
    }
    Ok(())
}

/// Just enough of Minecraft's NBT format to read level.dat.
mod nbt {
    use super::HashMap;

    #[derive(Clone, Debug)]
    pub enum Tag {
        Int(i64),
        String(String),
        Compound(HashMap<String, Tag>),
        /// Numbers and arrays level.dat readers here never need.
        Other,
    }

    impl Tag {
        pub fn get(&self, key: &str) -> Option<&Tag> {
            match self {
                Tag::Compound(map) => map.get(key),
                _ => None,
            }
        }
        pub fn as_i64(&self) -> Option<i64> {
            match self {
                Tag::Int(v) => Some(*v),
                _ => None,
            }
        }
        pub fn as_str(&self) -> Option<&str> {
            match self {
                Tag::String(s) => Some(s),
                _ => None,
            }
        }
    }

    struct Reader<'a> {
        buf: &'a [u8],
        pos: usize,
    }

    impl<'a> Reader<'a> {
        fn take(&mut self, n: usize) -> Option<&'a [u8]> {
            let end = self.pos.checked_add(n).filter(|&e| e <= self.buf.len())?;
            let out = &self.buf[self.pos..end];
            self.pos = end;
            Some(out)
        }
        fn be<const N: usize>(&mut self) -> Option<[u8; N]> {
            self.take(N)?.try_into().ok()
        }
        fn len(&mut self) -> Option<usize> {
            usize::try_from(i32::from_be_bytes(self.be()?)).ok()
        }
        fn string(&mut self) -> Option<String> {
            let n = u16::from_be_bytes(self.be()?) as usize;
            // Java's modified UTF-8; lossy decoding is fine for display.
            Some(String::from_utf8_lossy(self.take(n)?).into_owned())
        }
        fn payload(&mut self, kind: u8, depth: usize) -> Option<Tag> {
            if depth > 64 {
                return None;
            }
            Some(match kind {
                1 => Tag::Int(i8::from_be_bytes(self.be()?) as i64),
                2 => Tag::Int(i16::from_be_bytes(self.be()?) as i64),
                3 => Tag::Int(i32::from_be_bytes(self.be()?) as i64),
                4 => Tag::Int(i64::from_be_bytes(self.be()?)),
                5 => {
                    self.take(4)?;
                    Tag::Other
                }
                6 => {
                    self.take(8)?;
                    Tag::Other
                }
                7 => {
                    let n = self.len()?;
                    self.take(n)?;
                    Tag::Other
                }
                8 => Tag::String(self.string()?),
                9 => {
                    let item = self.be::<1>()?[0];
                    let n = self.len()?;
                    for _ in 0..n {
                        self.payload(item, depth + 1)?;
                    }
                    Tag::Other
                }
                10 => {
                    let mut map = HashMap::new();
                    loop {
                        let kind = self.be::<1>()?[0];
                        if kind == 0 {
                            break;
                        }
                        let name = self.string()?;
                        map.insert(name, self.payload(kind, depth + 1)?);
                    }
                    Tag::Compound(map)
                }
                11 => {
                    let n = self.len()?;
                    self.take(n.checked_mul(4)?)?;
                    Tag::Other
                }
                12 => {
                    let n = self.len()?;
                    self.take(n.checked_mul(8)?)?;
                    Tag::Other
                }
                _ => return None,
            })
        }
    }

    /// The root compound of an uncompressed NBT document.
    pub fn parse(buf: &[u8]) -> Option<Tag> {
        let mut r = Reader { buf, pos: 0 };
        if r.be::<1>()?[0] != 10 {
            return None;
        }
        r.string()?;
        r.payload(10, 0)
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::io::Write;

    fn string(out: &mut Vec<u8>, s: &str) {
        out.extend((s.len() as u16).to_be_bytes());
        out.extend(s.as_bytes());
    }

    #[test]
    fn reads_level_dat() {
        let mut nbt = vec![10];
        string(&mut nbt, "");
        nbt.push(10);
        string(&mut nbt, "Data");
        nbt.push(8);
        string(&mut nbt, "LevelName");
        string(&mut nbt, "Skyblock");
        nbt.push(4);
        string(&mut nbt, "LastPlayed");
        nbt.extend(1_790_000_000_000i64.to_be_bytes());
        nbt.push(3);
        string(&mut nbt, "GameType");
        nbt.extend(1i32.to_be_bytes());
        nbt.push(11);
        string(&mut nbt, "Skipped");
        nbt.extend(2i32.to_be_bytes());
        nbt.extend([0u8; 8]);
        nbt.push(10);
        string(&mut nbt, "Version");
        nbt.push(8);
        string(&mut nbt, "Name");
        string(&mut nbt, "26.3");
        nbt.extend([0, 0, 0]); // end Version, Data, root

        let dir = std::env::temp_dir().join(format!("quartz-world-test-{}", std::process::id()));
        let world = dir.join("saves").join("sky");
        std::fs::create_dir_all(&world).unwrap();
        let mut gz = flate2::write::GzEncoder::new(Vec::new(), flate2::Compression::fast());
        gz.write_all(&nbt).unwrap();
        std::fs::write(world.join("level.dat"), gz.finish().unwrap()).unwrap();

        let worlds = list(&dir);
        std::fs::remove_dir_all(&dir).ok();
        assert_eq!(worlds.len(), 1);
        let w = &worlds[0];
        assert_eq!((w.id.as_str(), w.name.as_str()), ("sky", "Skyblock"));
        assert_eq!(w.last_played, Some(1_790_000_000_000));
        assert_eq!(w.mode.as_deref(), Some("creative"));
        assert_eq!(w.version.as_deref(), Some("26.3"));
    }

    #[test]
    fn rejects_escaping_ids() {
        assert!(validate_id("../x").is_err());
        assert!(validate_id("..").is_err());
        assert!(validate_id("New World (1)").is_ok());
    }
}
