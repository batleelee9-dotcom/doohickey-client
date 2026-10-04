use std::{
    path::PathBuf,
    sync::{Mutex, PoisonError},
};

use serde::{Deserialize, Serialize};

use crate::{error::AppError, fsutil, launch::presets::JvmPreset, meta::loaders::LoaderKind, system};

#[derive(Clone, Copy, Debug, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub enum Theme {
    System,
    Dark,
    Light,
}

/// Window backdrop. Non-solid materials make the window translucent and let
/// the OS draw a blurred backdrop behind the UI.
#[derive(Clone, Copy, Debug, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub enum Material {
    Solid,
    Mica,
    Acrylic,
    Vibrancy,
}

/// What the launcher does with its own window once a game starts.
#[derive(Clone, Copy, Debug, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub enum OnLaunch {
    Keep,
    Minimize,
    /// Destroys the webview (freeing its memory) and keeps a tray icon; the
    /// window comes back when the last game exits.
    Close,
}

#[derive(Clone, Debug, Serialize, Deserialize)]
#[serde(rename_all = "camelCase", default)]
pub struct Settings {
    pub theme: Theme,
    pub accent: String,
    pub material: Material,
    /// 0–100: how opaque panels are when a translucent material is active.
    pub opacity: u8,
    pub on_launch: OnLaunch,
    pub discord_rpc: bool,
    pub default_memory_mb: u32,
    pub default_jvm_preset: JvmPreset,
    pub selected_instance: Option<String>,
    /// The curated build (manifest id) and loader picked on the Play screen.
    pub selected_build: Option<String>,
    pub selected_loader: Option<LoaderKind>,
    /// Where to fetch the build manifest from; None uses the one built into the launcher.
    pub manifest_url: Option<String>,
    /// Azure app (client) ID pasted on the sign-in screen; None uses the built-in one.
    pub ms_client_id: Option<String>,
    /// Ask Windows to run the game on the dedicated GPU (laptops with two GPUs).
    pub high_performance_gpu: bool,
    /// Which defaults this file has been brought up to (0 = before they were tracked).
    #[serde(default)]
    pub defaults_version: u32,
}

/// Bumped when a default changes in a way existing settings should follow.
const DEFAULTS_VERSION: u32 = 2;

impl Default for Settings {
    fn default() -> Self {
        Self {
            theme: Theme::System,
            accent: "#8b7cf6".into(),
            material: Material::Solid,
            opacity: 85,
            on_launch: OnLaunch::Close,
            discord_rpc: true,
            default_memory_mb: system::recommended_memory_mb(),
            default_jvm_preset: JvmPreset::Performance,
            selected_instance: None,
            selected_build: None,
            selected_loader: None,
            manifest_url: option_env!("QUARTZ_MANIFEST_URL").map(str::to_owned),
            ms_client_id: None,
            high_performance_gpu: true,
            defaults_version: DEFAULTS_VERSION,
        }
    }
}

pub struct SettingsStore {
    path: PathBuf,
    data: Mutex<Settings>,
}

impl SettingsStore {
    pub fn load(path: PathBuf) -> Result<Self, AppError> {
        let mut data: Settings = fsutil::read_json(&path)?.unwrap_or_default();
        if data.defaults_version < 2 {
            // 0.2: the tuned Performance preset became the default. Follow it unless
            // a different preset was picked on purpose.
            if data.default_jvm_preset == JvmPreset::Balanced {
                data.default_jvm_preset = JvmPreset::Performance;
            }
            data.defaults_version = DEFAULTS_VERSION;
        }
        Ok(Self { path, data: Mutex::new(data) })
    }

    pub fn get(&self) -> Settings {
        self.data.lock().unwrap_or_else(PoisonError::into_inner).clone()
    }

    /// Applies a partial update (any subset of fields, camelCase). Going through
    /// serde validates every value, so a bad patch is rejected as a whole.
    pub fn update(&self, patch: serde_json::Value) -> Result<Settings, AppError> {
        let mut guard = self.data.lock().unwrap_or_else(PoisonError::into_inner);
        let mut merged = serde_json::to_value(&*guard)?;
        let (Some(target), Some(patch)) = (merged.as_object_mut(), patch.as_object()) else {
            return Err(AppError::Invalid("Settings update must be an object.".into()));
        };
        for (key, value) in patch {
            target.insert(key.clone(), value.clone());
        }
        let mut next: Settings = serde_json::from_value(merged)
            .map_err(|e| AppError::Invalid(format!("Invalid setting: {e}")))?;
        next.ms_client_id = normalize_client_id(next.ms_client_id.as_deref())?;
        if !next.accent.starts_with('#') || !(next.accent.len() == 7 || next.accent.len() == 4) {
            return Err(AppError::Invalid("Accent must be a hex colour like #8b7cf6.".into()));
        }
        fsutil::write_json_atomic(&self.path, &next)?;
        *guard = next.clone();
        Ok(next)
    }
}

/// Azure app IDs are GUIDs; blank means "use the built-in one".
fn normalize_client_id(raw: Option<&str>) -> Result<Option<String>, AppError> {
    match raw.map(str::trim).filter(|s| !s.is_empty()) {
        None => Ok(None),
        Some(id) => uuid::Uuid::parse_str(id).map(|u| Some(u.hyphenated().to_string())).map_err(|_| {
            AppError::Invalid(
                "That isn't a client ID. Copy the Application (client) ID from your Azure app's Overview page.".into(),
            )
        }),
    }
}

#[cfg(test)]
mod client_id_tests {
    use super::normalize_client_id;

    #[test]
    fn client_ids() {
        assert_eq!(normalize_client_id(None).unwrap(), None);
        assert_eq!(normalize_client_id(Some("  ")).unwrap(), None);
        assert_eq!(
            normalize_client_id(Some(" 507B785F-1CE8-4A33-B5CF-2850E76A326D ")).unwrap().as_deref(),
            Some("507b785f-1ce8-4a33-b5cf-2850e76a326d")
        );
        assert!(normalize_client_id(Some("00000000402b5328")).is_err());
        assert!(normalize_client_id(Some("tickleme")).is_err());
    }
}
