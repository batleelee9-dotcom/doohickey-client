//! Skin library (local PNGs the player has collected) and the Minecraft
//! services calls that change the skin/cape everyone else sees.

use std::{
    path::PathBuf,
    sync::{Mutex, PoisonError},
};

use base64::Engine;
use reqwest::Client;
use serde::{Deserialize, Serialize};

use crate::{error::AppError, fsutil};

const SKINS_URL: &str = "https://api.minecraftservices.com/minecraft/profile/skins";
const CAPE_URL: &str = "https://api.minecraftservices.com/minecraft/profile/capes/active";

#[derive(Clone, Copy, Debug, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "lowercase")]
pub enum Variant {
    Classic,
    Slim,
}

#[derive(Clone, Debug, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct Skin {
    pub id: String,
    pub name: String,
    pub variant: Variant,
    pub added_at: u64,
}

pub struct SkinStore {
    dir: PathBuf,
    data: Mutex<Vec<Skin>>,
}

impl SkinStore {
    pub fn load(dir: PathBuf) -> Result<Self, AppError> {
        std::fs::create_dir_all(&dir)?;
        let data = fsutil::read_json(&dir.join("skins.json"))?.unwrap_or_default();
        Ok(Self { dir, data: Mutex::new(data) })
    }

    fn save(&self, data: &[Skin]) -> Result<(), AppError> {
        fsutil::write_json_atomic(&self.dir.join("skins.json"), &data)
    }

    pub fn list(&self) -> Vec<Skin> {
        self.data.lock().unwrap_or_else(PoisonError::into_inner).clone()
    }

    pub fn import(&self, bytes: &[u8], name: &str, variant: Variant) -> Result<Skin, AppError> {
        validate_skin(bytes)?;
        let skin = Skin {
            id: uuid::Uuid::new_v4().simple().to_string(),
            name: if name.trim().is_empty() { "Untitled skin".into() } else { name.trim().chars().take(40).collect() },
            variant,
            added_at: fsutil::unix_now(),
        };
        std::fs::write(self.file(&skin.id)?, bytes)?;
        let mut data = self.data.lock().unwrap_or_else(PoisonError::into_inner);
        data.insert(0, skin.clone());
        self.save(&data)?;
        Ok(skin)
    }

    pub fn update(&self, id: &str, name: Option<String>, variant: Option<Variant>) -> Result<Vec<Skin>, AppError> {
        let mut data = self.data.lock().unwrap_or_else(PoisonError::into_inner);
        let skin = data.iter_mut().find(|s| s.id == id).ok_or_else(|| AppError::Invalid("Skin not found.".into()))?;
        if let Some(name) = name.filter(|n| !n.trim().is_empty()) {
            skin.name = name.trim().chars().take(40).collect();
        }
        if let Some(variant) = variant {
            skin.variant = variant;
        }
        self.save(&data)?;
        Ok(data.clone())
    }

    pub fn delete(&self, id: &str) -> Result<Vec<Skin>, AppError> {
        let path = self.file(id)?;
        let mut data = self.data.lock().unwrap_or_else(PoisonError::into_inner);
        data.retain(|s| s.id != id);
        self.save(&data)?;
        let _ = std::fs::remove_file(path);
        Ok(data.clone())
    }

    pub fn get(&self, id: &str) -> Result<(Skin, Vec<u8>), AppError> {
        let skin = self
            .list()
            .into_iter()
            .find(|s| s.id == id)
            .ok_or_else(|| AppError::Invalid("Skin not found.".into()))?;
        Ok((skin, std::fs::read(self.file(id)?)?))
    }

    fn file(&self, id: &str) -> Result<PathBuf, AppError> {
        if !id.chars().all(|c| c.is_ascii_hexdigit()) {
            return Err(AppError::Invalid("Invalid skin id.".into()));
        }
        Ok(self.dir.join(format!("{id}.png")))
    }
}

/// A skin is a PNG of 64×64 (or legacy 64×32). Reads the IHDR chunk directly
/// rather than pulling in an image decoder.
pub fn validate_skin(bytes: &[u8]) -> Result<(), AppError> {
    const SIGNATURE: &[u8] = &[0x89, b'P', b'N', b'G', 0x0d, 0x0a, 0x1a, 0x0a];
    if bytes.len() < 24 || &bytes[..8] != SIGNATURE || &bytes[12..16] != b"IHDR" {
        return Err(AppError::Invalid("That file isn't a PNG image.".into()));
    }
    let width = u32::from_be_bytes(bytes[16..20].try_into().expect("4 bytes"));
    let height = u32::from_be_bytes(bytes[20..24].try_into().expect("4 bytes"));
    if width != 64 || !(height == 64 || height == 32) {
        return Err(AppError::Invalid(format!("Skins must be 64×64 or 64×32 pixels; this one is {width}×{height}.")));
    }
    if bytes.len() > 256 * 1024 {
        return Err(AppError::Invalid("That skin file is too large.".into()));
    }
    Ok(())
}

pub fn data_url(bytes: &[u8]) -> String {
    format!("data:image/png;base64,{}", base64::engine::general_purpose::STANDARD.encode(bytes))
}

pub async fn upload(http: &Client, token: &str, bytes: Vec<u8>, variant: Variant) -> Result<serde_json::Value, AppError> {
    let file = reqwest::multipart::Part::bytes(bytes)
        .file_name("skin.png")
        .mime_str("image/png")
        .map_err(|e| AppError::Invalid(e.to_string()))?;
    let form = reqwest::multipart::Form::new()
        .text("variant", if variant == Variant::Slim { "slim" } else { "classic" })
        .part("file", file);
    let res = http.post(SKINS_URL).bearer_auth(token).multipart(form).send().await?;
    match res.status().as_u16() {
        200..=299 => Ok(res.json().await?),
        429 => Err(AppError::Auth("Minecraft limits how often skins can change. Wait a minute and try again.".into())),
        s => Err(AppError::Auth(format!("Minecraft services rejected the skin ({s})."))),
    }
}

pub async fn set_cape(http: &Client, token: &str, cape_id: Option<&str>) -> Result<serde_json::Value, AppError> {
    let res = match cape_id {
        Some(id) => http.put(CAPE_URL).bearer_auth(token).json(&serde_json::json!({ "capeId": id })).send().await?,
        None => http.delete(CAPE_URL).bearer_auth(token).send().await?,
    };
    match res.status().as_u16() {
        200..=299 => Ok(res.json().await?),
        s => Err(AppError::Auth(format!("Minecraft services rejected the cape change ({s})."))),
    }
}

/// Fetches a texture from Mojang's CDN as a data URL — the CDN sends no CORS
/// headers, so the 3D preview (WebGL) can't load it directly.
pub async fn fetch_texture(http: &Client, url: &str) -> Result<String, AppError> {
    let parsed = reqwest::Url::parse(url).map_err(|_| AppError::Invalid("Invalid texture URL.".into()))?;
    if parsed.host_str() != Some("textures.minecraft.net") {
        return Err(AppError::Invalid("Only Minecraft texture URLs can be loaded.".into()));
    }
    let bytes = http.get(parsed).send().await?.error_for_status()?.bytes().await?;
    if bytes.len() > 512 * 1024 {
        return Err(AppError::Invalid("Texture too large.".into()));
    }
    Ok(data_url(&bytes))
}

#[cfg(test)]
mod tests {
    use super::*;

    fn png_header(w: u32, h: u32) -> Vec<u8> {
        let mut b = vec![0x89, b'P', b'N', b'G', 0x0d, 0x0a, 0x1a, 0x0a, 0, 0, 0, 13];
        b.extend_from_slice(b"IHDR");
        b.extend_from_slice(&w.to_be_bytes());
        b.extend_from_slice(&h.to_be_bytes());
        b.extend_from_slice(&[8, 6, 0, 0, 0]);
        b
    }

    #[test]
    fn validates_dimensions() {
        assert!(validate_skin(&png_header(64, 64)).is_ok());
        assert!(validate_skin(&png_header(64, 32)).is_ok());
        assert!(validate_skin(&png_header(128, 128)).is_err());
        assert!(validate_skin(b"GIF89a....................").is_err());
    }
}
