//! Reads a mod's name/version/icon from inside its jar, whichever loader it
//! targets: fabric.mod.json, quilt.mod.json, META-INF/(neoforge.)mods.toml or
//! the legacy mcmod.info (1.12 and older).

use std::{io::Read, path::Path};

use serde::Serialize;
use serde_json::Value;

#[derive(Clone, Debug, Default, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct ModMeta {
    pub mod_id: Option<String>,
    pub name: Option<String>,
    pub version: Option<String>,
    pub description: Option<String>,
    pub authors: Vec<String>,
    #[serde(skip)]
    pub icon_path: Option<String>,
}

type Zip = zip::ZipArchive<std::fs::File>;

fn read_entry(zip: &mut Zip, name: &str) -> Option<String> {
    let mut entry = zip.by_name(name).ok()?;
    let mut s = String::new();
    entry.read_to_string(&mut s).ok()?;
    Some(s)
}

pub fn read(path: &Path) -> ModMeta {
    let Ok(file) = std::fs::File::open(path) else { return ModMeta::default() };
    let Ok(mut zip) = zip::ZipArchive::new(file) else { return ModMeta::default() };
    if let Some(json) = read_entry(&mut zip, "fabric.mod.json") {
        return fabric(&json);
    }
    if let Some(json) = read_entry(&mut zip, "quilt.mod.json") {
        return quilt(&json);
    }
    for name in ["META-INF/neoforge.mods.toml", "META-INF/mods.toml"] {
        if let Some(toml) = read_entry(&mut zip, name) {
            let manifest_version = read_entry(&mut zip, "META-INF/MANIFEST.MF").and_then(|m| {
                m.lines().find_map(|l| l.strip_prefix("Implementation-Version:").map(|v| v.trim().to_owned()))
            });
            return forge(&toml, manifest_version);
        }
    }
    if let Some(json) = read_entry(&mut zip, "mcmod.info") {
        return mcmod(&json);
    }
    ModMeta::default()
}

pub fn read_icon(path: &Path, icon: &str) -> Option<Vec<u8>> {
    let mut zip = zip::ZipArchive::new(std::fs::File::open(path).ok()?).ok()?;
    let mut entry = zip.by_name(icon.trim_start_matches('/')).ok()?;
    if entry.size() > 512 * 1024 {
        return None;
    }
    let mut bytes = Vec::new();
    entry.read_to_end(&mut bytes).ok()?;
    Some(bytes)
}

/// Many fabric.mod.json files in the wild have tabs/control characters in
/// strings, which strict JSON rejects; clean those up before giving up.
fn lenient_json(text: &str) -> Option<Value> {
    serde_json::from_str(text).ok().or_else(|| {
        let cleaned: String = text.chars().map(|c| if c.is_control() && c != '\n' { ' ' } else { c }).collect();
        serde_json::from_str(&cleaned).ok()
    })
}

fn string(v: &Value) -> Option<String> {
    v.as_str().map(str::to_owned).filter(|s| !s.is_empty())
}

fn people(v: &Value) -> Vec<String> {
    v.as_array()
        .map(|a| a.iter().filter_map(|p| string(p).or_else(|| string(&p["name"]))).take(4).collect())
        .unwrap_or_default()
}

/// Icons may be a path or an object of size → path; take the largest.
fn icon(v: &Value) -> Option<String> {
    string(v).or_else(|| {
        v.as_object()?
            .iter()
            .max_by_key(|(size, _)| size.parse::<u32>().unwrap_or(0))
            .and_then(|(_, p)| string(p))
    })
}

fn fabric(text: &str) -> ModMeta {
    let Some(v) = lenient_json(text) else { return ModMeta::default() };
    ModMeta {
        mod_id: string(&v["id"]),
        name: string(&v["name"]),
        version: string(&v["version"]),
        description: string(&v["description"]),
        authors: people(&v["authors"]),
        icon_path: icon(&v["icon"]),
    }
}

fn quilt(text: &str) -> ModMeta {
    let Some(v) = lenient_json(text) else { return ModMeta::default() };
    let q = &v["quilt_loader"];
    let m = &q["metadata"];
    let authors = m["contributors"].as_object().map(|o| o.keys().take(4).cloned().collect()).unwrap_or_default();
    ModMeta {
        mod_id: string(&q["id"]),
        name: string(&m["name"]),
        version: string(&q["version"]),
        description: string(&m["description"]),
        authors,
        icon_path: icon(&m["icon"]),
    }
}

fn forge(text: &str, manifest_version: Option<String>) -> ModMeta {
    let Ok(doc) = text.parse::<toml::Table>() else { return ModMeta::default() };
    let Some(first) = doc.get("mods").and_then(|m| m.as_array()).and_then(|a| a.first()).and_then(|m| m.as_table()) else {
        return ModMeta::default();
    };
    let get = |k: &str| first.get(k).and_then(|v| v.as_str()).map(str::to_owned).filter(|s| !s.is_empty());
    // "${file.jarVersion}" is filled in from the jar manifest at runtime.
    let version = get("version").and_then(|v| if v.contains("${") { manifest_version.clone() } else { Some(v) });
    let logo = get("logoFile").or_else(|| doc.get("logoFile").and_then(|v| v.as_str()).map(str::to_owned));
    let authors = get("authors")
        .or_else(|| doc.get("authors").and_then(|v| v.as_str()).map(str::to_owned))
        .map(|a| a.split(',').map(|s| s.trim().to_owned()).filter(|s| !s.is_empty()).take(4).collect())
        .unwrap_or_default();
    ModMeta {
        mod_id: get("modId"),
        name: get("displayName"),
        version,
        description: get("description").map(|d| d.trim().to_owned()),
        authors,
        icon_path: logo,
    }
}

fn mcmod(text: &str) -> ModMeta {
    let Some(v) = lenient_json(text) else { return ModMeta::default() };
    // Either a bare array or { "modList": [...] }.
    let first = v.as_array().and_then(|a| a.first()).or_else(|| v["modList"].as_array().and_then(|a| a.first()));
    let Some(m) = first else { return ModMeta::default() };
    ModMeta {
        mod_id: string(&m["modid"]),
        name: string(&m["name"]),
        version: string(&m["version"]),
        description: string(&m["description"]),
        authors: people(&m["authorList"]),
        icon_path: string(&m["logoFile"]),
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn parses_fabric_with_icon_map() {
        let m = fabric(r#"{"id":"sodium","name":"Sodium","version":"0.6.0","authors":["jellysquid3",{"name":"IMS"}],"icon":{"64":"a.png","256":"b.png"}}"#);
        assert_eq!(m.name.as_deref(), Some("Sodium"));
        assert_eq!(m.authors, ["jellysquid3", "IMS"]);
        assert_eq!(m.icon_path.as_deref(), Some("b.png"));
    }

    #[test]
    fn parses_forge_toml_with_manifest_version() {
        let toml = "modLoader=\"javafml\"\nloaderVersion=\"[47,)\"\nlicense=\"MIT\"\n[[mods]]\nmodId=\"jei\"\nversion=\"${file.jarVersion}\"\ndisplayName=\"Just Enough Items\"\nlogoFile=\"jei.png\"\nauthors=\"mezz, others\"\n";
        let m = forge(toml, Some("15.2.0".into()));
        assert_eq!(m.mod_id.as_deref(), Some("jei"));
        assert_eq!(m.version.as_deref(), Some("15.2.0"));
        assert_eq!(m.authors, ["mezz", "others"]);
    }

    #[test]
    fn parses_legacy_mcmod_info() {
        let m = mcmod(r#"[{"modid":"patcher","name":"Patcher","version":"1.8.9","authorList":["Sk1er"]}]"#);
        assert_eq!(m.name.as_deref(), Some("Patcher"));
        let m = mcmod(r#"{"modListVersion":2,"modList":[{"modid":"x","name":"X"}]}"#);
        assert_eq!(m.mod_id.as_deref(), Some("x"));
    }
}
