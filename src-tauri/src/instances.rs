//! Profiles ("instances"): a game version + loader + its own mods, configs,
//! worlds and JVM settings, each in `instances/<id>/`. The folder *is* the
//! game directory, so mods/, saves/, config/ sit right next to instance.json.

use std::sync::{Mutex, PoisonError};

use serde::{Deserialize, Serialize};

use crate::{client_mod, error::AppError, fsutil, launch::presets::JvmPreset, meta::loaders::LoaderKind, paths::Paths};

#[derive(Clone, Debug, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct Instance {
    pub id: String,
    pub name: String,
    pub game_version: String,
    pub loader: LoaderKind,
    #[serde(default)]
    pub loader_version: Option<String>,
    /// The launchable version id once the loader is installed
    /// (e.g. `fabric-loader-0.19.5-26.3`). Cleared when version/loader change.
    #[serde(default)]
    pub version_id: Option<String>,
    #[serde(default)]
    pub color: Option<String>,
    pub created_at: u64,
    #[serde(default)]
    pub last_played_at: Option<u64>,
    #[serde(default)]
    pub play_time_secs: u64,
    /// None = use the launcher-wide default.
    #[serde(default)]
    pub memory_mb: Option<u32>,
    #[serde(default)]
    pub jvm_preset: Option<JvmPreset>,
    #[serde(default)]
    pub jvm_args: String,
    #[serde(default)]
    pub java_path: Option<String>,
    #[serde(default)]
    pub width: Option<u32>,
    #[serde(default)]
    pub height: Option<u32>,
    /// Server to join automatically on launch — makes this a per-server profile.
    #[serde(default)]
    pub server: Option<String>,
    /// Load Doohickey Client (HUD, PvP tweaks, minimap, cosmetics). Only takes
    /// effect on profiles the mod supports; see `client_mod::supports`.
    #[serde(default)]
    pub quartz_client: bool,
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct NewInstance {
    pub name: String,
    pub game_version: String,
    pub loader: LoaderKind,
    pub loader_version: Option<String>,
    #[serde(default)]
    pub server: Option<String>,
    /// Defaults to on wherever Doohickey Client is supported.
    #[serde(default)]
    pub quartz_client: Option<bool>,
}

const COLORS: [&str; 8] = ["#8b7cf6", "#4f8ef7", "#2bb8a6", "#3dd68c", "#f5a524", "#f2555a", "#ec5fb7", "#a3a3ad"];

pub struct InstanceStore {
    paths: Paths,
    /// Serializes read-modify-write cycles on instance.json files.
    lock: Mutex<()>,
}

impl InstanceStore {
    pub fn new(paths: Paths) -> Result<Self, AppError> {
        std::fs::create_dir_all(paths.instances())?;
        Ok(Self { paths, lock: Mutex::new(()) })
    }

    fn file(&self, id: &str) -> std::path::PathBuf {
        self.paths.instance(id).join("instance.json")
    }

    /// Most recently played first, then newest.
    pub fn list(&self) -> Vec<Instance> {
        let Ok(entries) = std::fs::read_dir(self.paths.instances()) else { return vec![] };
        let mut list: Vec<Instance> = entries
            .filter_map(Result::ok)
            .filter_map(|e| fsutil::read_json::<Instance>(&e.path().join("instance.json")).ok().flatten())
            .collect();
        list.sort_by(|a, b| {
            b.last_played_at.unwrap_or(0).cmp(&a.last_played_at.unwrap_or(0)).then(b.created_at.cmp(&a.created_at))
        });
        list
    }

    pub fn get(&self, id: &str) -> Result<Instance, AppError> {
        validate_id(id)?;
        fsutil::read_json(&self.file(id))?.ok_or_else(|| AppError::Invalid("That profile no longer exists.".into()))
    }

    pub fn save(&self, instance: &Instance) -> Result<(), AppError> {
        validate_id(&instance.id)?;
        fsutil::write_json_atomic(&self.file(&instance.id), instance)
    }

    pub fn create(&self, req: NewInstance) -> Result<Instance, AppError> {
        let _guard = self.lock.lock().unwrap_or_else(PoisonError::into_inner);
        let id = self.unique_id(&fsutil::slugify(req.name.trim()));
        self.insert(id, req)
    }

    /// Creates a profile with a fixed id (curated builds), failing if it exists.
    pub fn create_with_id(&self, id: &str, req: NewInstance) -> Result<Instance, AppError> {
        validate_id(id)?;
        let _guard = self.lock.lock().unwrap_or_else(PoisonError::into_inner);
        if self.paths.instance(id).join("instance.json").exists() {
            return Err(AppError::Invalid(format!("Profile {id} already exists.")));
        }
        self.insert(id.to_owned(), req)
    }

    fn insert(&self, id: String, req: NewInstance) -> Result<Instance, AppError> {
        let name = req.name.trim();
        if name.is_empty() {
            return Err(AppError::Invalid("Give the profile a name.".into()));
        }
        if req.loader != LoaderKind::Vanilla && req.loader_version.is_none() {
            return Err(AppError::Invalid(format!("Pick a {} version.", req.loader.label())));
        }
        let quartz_client =
            req.quartz_client.unwrap_or(true) && client_mod::supports_version(req.loader, &req.game_version);
        let hash = id.bytes().fold(0usize, |h, b| h.wrapping_mul(31).wrapping_add(b as usize));
        let instance = Instance {
            id,
            name: name.to_owned(),
            game_version: req.game_version,
            loader: req.loader,
            loader_version: if req.loader == LoaderKind::Vanilla { None } else { req.loader_version },
            version_id: None,
            color: Some(COLORS[hash % COLORS.len()].into()),
            created_at: fsutil::unix_now(),
            last_played_at: None,
            play_time_secs: 0,
            memory_mb: None,
            jvm_preset: None,
            jvm_args: String::new(),
            java_path: None,
            width: None,
            height: None,
            server: req.server.filter(|s| !s.trim().is_empty()),
            quartz_client,
        };
        std::fs::create_dir_all(self.paths.instance(&instance.id).join("mods"))?;
        self.save(&instance)?;
        Ok(instance)
    }

    /// Partial update (camelCase fields). Changing the game version or loader
    /// invalidates the installed version so the next launch reinstalls.
    pub fn update(&self, id: &str, patch: serde_json::Value) -> Result<Instance, AppError> {
        let _guard = self.lock.lock().unwrap_or_else(PoisonError::into_inner);
        let current = self.get(id)?;
        let mut value = serde_json::to_value(&current)?;
        let (Some(target), Some(patch)) = (value.as_object_mut(), patch.as_object()) else {
            return Err(AppError::Invalid("Profile update must be an object.".into()));
        };
        for (key, v) in patch {
            if matches!(key.as_str(), "id" | "createdAt" | "versionId" | "playTimeSecs") {
                continue;
            }
            target.insert(key.clone(), v.clone());
        }
        let mut next: Instance =
            serde_json::from_value(value).map_err(|e| AppError::Invalid(format!("Invalid profile setting: {e}")))?;
        if next.name.trim().is_empty() {
            return Err(AppError::Invalid("Give the profile a name.".into()));
        }
        if next.loader == LoaderKind::Vanilla {
            next.loader_version = None;
        }
        if next.game_version != current.game_version
            || next.loader != current.loader
            || next.loader_version != current.loader_version
        {
            next.version_id = None;
        }
        if let Some(mem) = next.memory_mb {
            if !(512..=65536).contains(&mem) {
                return Err(AppError::Invalid("Memory must be between 512 MB and 64 GB.".into()));
            }
        }
        next.server = next.server.filter(|s| !s.trim().is_empty());
        next.java_path = next.java_path.filter(|s| !s.trim().is_empty());
        self.save(&next)?;
        Ok(next)
    }

    /// Moves the profile folder to the OS recycle bin (recoverable).
    pub fn delete(&self, id: &str) -> Result<(), AppError> {
        validate_id(id)?;
        let dir = self.paths.instance(id);
        if dir.exists() {
            trash::delete(&dir).map_err(|e| AppError::Invalid(format!("Couldn't move the profile to the recycle bin: {e}")))?;
        }
        Ok(())
    }

    pub fn duplicate(&self, id: &str) -> Result<Instance, AppError> {
        let source = self.get(id)?;
        let _guard = self.lock.lock().unwrap_or_else(PoisonError::into_inner);
        let new_id = self.unique_id(&format!("{}-copy", source.id));
        fsutil::copy_dir(&self.paths.instance(id), &self.paths.instance(&new_id))?;
        let copy = Instance {
            id: new_id,
            name: format!("{} (copy)", source.name),
            created_at: fsutil::unix_now(),
            last_played_at: None,
            play_time_secs: 0,
            ..source
        };
        self.save(&copy)?;
        Ok(copy)
    }

    fn unique_id(&self, base: &str) -> String {
        let mut id = base.to_owned();
        let mut n = 2;
        while self.paths.instance(&id).exists() {
            id = format!("{base}-{n}");
            n += 1;
        }
        id
    }
}

/// Ids become folder names; never let one escape the instances directory.
fn validate_id(id: &str) -> Result<(), AppError> {
    if id.is_empty() || !id.chars().all(|c| c.is_ascii_alphanumeric() || c == '-' || c == '_') {
        return Err(AppError::Invalid("Invalid profile id.".into()));
    }
    Ok(())
}
