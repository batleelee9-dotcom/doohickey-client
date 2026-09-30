//! Mojang's version metadata: the manifest of every release/snapshot, and the
//! per-version JSON describing libraries, arguments, assets and Java needs.
//! Loader profiles (Fabric, Quilt, Forge, NeoForge) use the same format with
//! `inheritsFrom`, which `merge` resolves.

use std::collections::{HashMap, HashSet};

use serde::{Deserialize, Serialize};

use crate::system;

pub const MANIFEST_URL: &str = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";
pub const LIBRARIES_URL: &str = "https://libraries.minecraft.net/";
pub const RESOURCES_URL: &str = "https://resources.download.minecraft.net/";

#[derive(Clone, Debug, Serialize, Deserialize)]
pub struct VersionManifest {
    pub latest: Latest,
    pub versions: Vec<ManifestEntry>,
}

#[derive(Clone, Debug, Serialize, Deserialize)]
pub struct Latest {
    pub release: String,
    pub snapshot: String,
}

#[derive(Clone, Debug, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct ManifestEntry {
    pub id: String,
    #[serde(rename = "type")]
    pub kind: String,
    pub url: String,
    pub release_time: String,
    #[serde(default)]
    pub sha1: Option<String>,
}

#[derive(Clone, Debug, Default, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct VersionJson {
    pub id: String,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub inherits_from: Option<String>,
    #[serde(default, rename = "type", skip_serializing_if = "Option::is_none")]
    pub kind: Option<String>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub main_class: Option<String>,
    /// Pre-1.13 versions: one whitespace-separated string of game arguments.
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub minecraft_arguments: Option<String>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub arguments: Option<Arguments>,
    #[serde(default)]
    pub libraries: Vec<Library>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub asset_index: Option<AssetIndexRef>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub assets: Option<String>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub downloads: Option<VersionDownloads>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub java_version: Option<JavaVersion>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub logging: Option<Logging>,
}

#[derive(Clone, Debug, Default, Serialize, Deserialize)]
pub struct Arguments {
    #[serde(default)]
    pub game: Vec<Argument>,
    #[serde(default)]
    pub jvm: Vec<Argument>,
}

#[derive(Clone, Debug, Serialize, Deserialize)]
#[serde(untagged)]
pub enum Argument {
    Plain(String),
    Conditional { rules: Vec<Rule>, value: ArgValue },
}

#[derive(Clone, Debug, Serialize, Deserialize)]
#[serde(untagged)]
pub enum ArgValue {
    One(String),
    Many(Vec<String>),
}

#[derive(Clone, Debug, Serialize, Deserialize)]
pub struct Rule {
    pub action: RuleAction,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub os: Option<OsRule>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub features: Option<HashMap<String, bool>>,
}

#[derive(Clone, Copy, Debug, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "lowercase")]
pub enum RuleAction {
    Allow,
    Disallow,
}

#[derive(Clone, Debug, Serialize, Deserialize)]
pub struct OsRule {
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub name: Option<String>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub arch: Option<String>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub version: Option<String>,
}

#[derive(Clone, Debug, Serialize, Deserialize)]
pub struct Library {
    pub name: String,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub downloads: Option<LibraryDownloads>,
    /// Maven repository base (Fabric/Quilt/legacy Forge style).
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub url: Option<String>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub rules: Option<Vec<Rule>>,
    /// Pre-1.19: OS → classifier of a jar whose native libraries get extracted.
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub natives: Option<HashMap<String, String>>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub extract: Option<Extract>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub sha1: Option<String>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub size: Option<u64>,
    /// Legacy Forge marks server-only libraries with `clientreq: false`.
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub clientreq: Option<bool>,
}

#[derive(Clone, Debug, Serialize, Deserialize)]
pub struct LibraryDownloads {
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub artifact: Option<Artifact>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub classifiers: Option<HashMap<String, Artifact>>,
}

#[derive(Clone, Debug, Serialize, Deserialize)]
pub struct Artifact {
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub path: Option<String>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub sha1: Option<String>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub size: Option<u64>,
    /// Empty for files a Forge installer generates locally.
    #[serde(default)]
    pub url: String,
}

#[derive(Clone, Debug, Default, Serialize, Deserialize)]
pub struct Extract {
    #[serde(default)]
    pub exclude: Vec<String>,
}

#[derive(Clone, Debug, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct AssetIndexRef {
    pub id: String,
    pub sha1: String,
    pub size: u64,
    pub url: String,
}

#[derive(Clone, Debug, Serialize, Deserialize)]
pub struct VersionDownloads {
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub client: Option<Artifact>,
}

#[derive(Clone, Debug, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct JavaVersion {
    pub component: String,
    pub major_version: u32,
}

#[derive(Clone, Debug, Serialize, Deserialize)]
pub struct Logging {
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub client: Option<LoggingClient>,
}

#[derive(Clone, Debug, Serialize, Deserialize)]
pub struct LoggingClient {
    pub argument: String,
    pub file: LogFile,
}

#[derive(Clone, Debug, Serialize, Deserialize)]
pub struct LogFile {
    pub id: String,
    pub sha1: String,
    pub size: u64,
    pub url: String,
}

#[derive(Clone, Debug, Deserialize)]
pub struct AssetIndex {
    pub objects: HashMap<String, AssetObject>,
    /// 1.6–1.7.2: assets are laid out by name under assets/virtual/<id>.
    #[serde(default, rename = "virtual")]
    pub is_virtual: bool,
    /// Pre-1.6: assets are copied into <game dir>/resources.
    #[serde(default)]
    pub map_to_resources: bool,
}

#[derive(Clone, Debug, Deserialize)]
pub struct AssetObject {
    pub hash: String,
    pub size: u64,
}

/// What rules are evaluated against.
pub struct RuleEnv {
    pub features: HashSet<&'static str>,
}

impl RuleEnv {
    pub fn with_features(features: &[&'static str]) -> Self {
        Self { features: features.iter().copied().collect() }
    }
}

impl Rule {
    fn matches(&self, env: &RuleEnv) -> bool {
        if let Some(os) = &self.os {
            if os.name.as_deref().is_some_and(|n| n != system::mojang_os()) {
                return false;
            }
            if os.arch.as_deref().is_some_and(|a| a == "x86") && !system::is_32bit() {
                return false;
            }
            // Version regexes only target ancient OS releases (e.g. OS X 10.5);
            // treating them as non-matching is the safe choice.
            if os.version.is_some() {
                return false;
            }
        }
        if let Some(features) = &self.features {
            for (name, wanted) in features {
                if env.features.contains(name.as_str()) != *wanted {
                    return false;
                }
            }
        }
        true
    }
}

/// Mojang semantics: no rules = allowed; otherwise the last matching rule wins
/// and nothing matching means disallowed.
pub fn rules_allow(rules: Option<&[Rule]>, env: &RuleEnv) -> bool {
    let Some(rules) = rules.filter(|r| !r.is_empty()) else { return true };
    let mut allowed = false;
    for rule in rules {
        if rule.matches(env) {
            allowed = rule.action == RuleAction::Allow;
        }
    }
    allowed
}

impl Argument {
    pub fn resolve(&self, env: &RuleEnv) -> Vec<String> {
        match self {
            Self::Plain(s) => vec![s.clone()],
            Self::Conditional { rules, value } => {
                if !rules_allow(Some(rules), env) {
                    return vec![];
                }
                match value {
                    ArgValue::One(s) => vec![s.clone()],
                    ArgValue::Many(v) => v.clone(),
                }
            }
        }
    }

    fn mentions(&self, needle: &str) -> bool {
        match self {
            Self::Plain(s) => s.contains(needle),
            Self::Conditional { value: ArgValue::One(s), .. } => s.contains(needle),
            Self::Conditional { value: ArgValue::Many(v), .. } => v.iter().any(|s| s.contains(needle)),
        }
    }
}

impl VersionJson {
    pub fn supports_quick_play(&self) -> bool {
        self.arguments.as_ref().is_some_and(|a| a.game.iter().any(|g| g.mentions("--quickPlayMultiplayer")))
    }
}

impl Library {
    /// `group:artifact[:classifier]` — two entries with the same key are the
    /// same library at different versions.
    pub fn dedup_key(&self) -> String {
        let parts: Vec<&str> = self.name.split('@').next().unwrap_or("").split(':').collect();
        match parts.as_slice() {
            [g, a, _, c, ..] => format!("{g}:{a}:{c}"),
            [g, a, ..] => format!("{g}:{a}"),
            _ => self.name.clone(),
        }
    }
}

/// Resolves `child` on top of its parent: child libraries first (so a loader's
/// newer copy of a library wins), argument lists concatenated, everything else
/// inherited unless the child overrides it.
pub fn merge(child: VersionJson, parent: VersionJson) -> VersionJson {
    let mut seen = HashSet::new();
    let libraries = child
        .libraries
        .into_iter()
        .chain(parent.libraries)
        .filter(|lib| seen.insert(lib.dedup_key()))
        .collect();

    let arguments = match (parent.arguments, child.arguments) {
        (Some(mut p), Some(c)) => {
            p.game.extend(c.game);
            p.jvm.extend(c.jvm);
            Some(p)
        }
        (p, c) => c.or(p),
    };

    VersionJson {
        id: child.id,
        inherits_from: parent.inherits_from,
        kind: child.kind.or(parent.kind),
        main_class: child.main_class.or(parent.main_class),
        minecraft_arguments: child.minecraft_arguments.or(parent.minecraft_arguments),
        arguments,
        libraries,
        asset_index: child.asset_index.or(parent.asset_index),
        assets: child.assets.or(parent.assets),
        downloads: child.downloads.or(parent.downloads),
        java_version: child.java_version.or(parent.java_version),
        logging: child.logging.or(parent.logging),
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn lib(name: &str) -> Library {
        Library {
            name: name.into(),
            downloads: None,
            url: None,
            rules: None,
            natives: None,
            extract: None,
            sha1: None,
            size: None,
            clientreq: None,
        }
    }

    #[test]
    fn merge_prefers_child_libraries_and_concats_args() {
        let parent = VersionJson {
            id: "26.3".into(),
            main_class: Some("net.minecraft.client.main.Main".into()),
            libraries: vec![lib("org.ow2.asm:asm:9.0"), lib("com.mojang:brigadier:1.0")],
            arguments: Some(Arguments { game: vec![Argument::Plain("--a".into())], jvm: vec![] }),
            ..Default::default()
        };
        let child = VersionJson {
            id: "fabric-loader-0.19.5-26.3".into(),
            inherits_from: Some("26.3".into()),
            main_class: Some("net.fabricmc.loader.impl.launch.knot.KnotClient".into()),
            libraries: vec![lib("org.ow2.asm:asm:9.10.1")],
            arguments: Some(Arguments { game: vec![], jvm: vec![Argument::Plain("-DFabricMcEmu".into())] }),
            ..Default::default()
        };
        let merged = merge(child, parent);
        assert_eq!(merged.id, "fabric-loader-0.19.5-26.3");
        assert_eq!(merged.main_class.as_deref(), Some("net.fabricmc.loader.impl.launch.knot.KnotClient"));
        let names: Vec<_> = merged.libraries.iter().map(|l| l.name.as_str()).collect();
        assert_eq!(names, ["org.ow2.asm:asm:9.10.1", "com.mojang:brigadier:1.0"]);
        let args = merged.arguments.unwrap();
        assert_eq!(args.game.len(), 1);
        assert_eq!(args.jvm.len(), 1);
    }

    #[test]
    fn rules_follow_last_match() {
        let env = RuleEnv::with_features(&["has_custom_resolution"]);
        let other_os = if system::mojang_os() == "windows" { "osx" } else { "windows" };
        let rules: Vec<Rule> = serde_json::from_value(serde_json::json!([
            { "action": "allow" },
            { "action": "disallow", "os": { "name": other_os } }
        ]))
        .unwrap();
        assert!(rules_allow(Some(&rules), &env));

        let only_other: Vec<Rule> =
            serde_json::from_value(serde_json::json!([{ "action": "allow", "os": { "name": other_os } }])).unwrap();
        assert!(!rules_allow(Some(&only_other), &env));

        let feature: Vec<Rule> = serde_json::from_value(serde_json::json!([
            { "action": "allow", "features": { "has_custom_resolution": true } }
        ]))
        .unwrap();
        assert!(rules_allow(Some(&feature), &env));
        assert!(!rules_allow(Some(&feature), &RuleEnv::with_features(&[])));
    }
}
