use serde::Serialize;
use tauri::State;

use crate::{
    accounts::{microsoft, session, AccountKind},
    error::AppError,
    skins::{self, Skin, Variant},
    state::AppState,
};

#[tauri::command]
pub fn list_skins(state: State<AppState>) -> Vec<Skin> {
    state.skins.list()
}

#[tauri::command]
pub async fn import_skin(state: State<'_, AppState>, path: String, name: String, variant: Variant) -> Result<Skin, AppError> {
    let bytes = tokio::fs::read(&path).await?;
    state.skins.import(&bytes, &name, variant)
}

#[tauri::command]
pub async fn update_skin(
    state: State<'_, AppState>,
    id: String,
    name: Option<String>,
    variant: Option<Variant>,
) -> Result<Vec<Skin>, AppError> {
    state.skins.update(&id, name, variant)
}

#[tauri::command]
pub async fn delete_skin(state: State<'_, AppState>, id: String) -> Result<Vec<Skin>, AppError> {
    state.skins.delete(&id)
}

#[tauri::command]
pub async fn skin_data(state: State<'_, AppState>, id: String) -> Result<String, AppError> {
    let (_, bytes) = state.skins.get(&id)?;
    Ok(skins::data_url(&bytes))
}

#[tauri::command]
pub async fn texture_data(state: State<'_, AppState>, url: String) -> Result<String, AppError> {
    skins::fetch_texture(&state.http, &url).await
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub struct Cape {
    id: String,
    alias: String,
    url: String,
    active: bool,
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub struct ProfileTextures {
    skin_url: Option<String>,
    variant: Variant,
    capes: Vec<Cape>,
}

fn textures_from(profile: &serde_json::Value) -> ProfileTextures {
    let active_skin = profile["skins"].as_array().and_then(|s| s.iter().find(|s| s["state"] == "ACTIVE"));
    ProfileTextures {
        skin_url: active_skin.and_then(|s| s["url"].as_str()).map(|u| u.replacen("http://", "https://", 1)),
        variant: match active_skin.and_then(|s| s["variant"].as_str()) {
            Some("SLIM") | Some("slim") => Variant::Slim,
            _ => Variant::Classic,
        },
        capes: profile["capes"]
            .as_array()
            .map(|capes| {
                capes
                    .iter()
                    .map(|c| Cape {
                        id: c["id"].as_str().unwrap_or("").to_owned(),
                        alias: c["alias"].as_str().unwrap_or("Cape").to_owned(),
                        url: c["url"].as_str().unwrap_or("").replacen("http://", "https://", 1),
                        active: c["state"] == "ACTIVE",
                    })
                    .collect()
            })
            .unwrap_or_default(),
    }
}

async fn active_ms_token(state: &AppState) -> Result<(crate::accounts::Account, String), AppError> {
    let account = state.accounts.active().ok_or_else(|| AppError::Invalid("Sign in first.".into()))?;
    if account.kind != AccountKind::Microsoft {
        return Err(AppError::Invalid(
            "Skins and capes can only be changed on Microsoft accounts — offline players have no Minecraft profile.".into(),
        ));
    }
    let token = session::minecraft_token(state, &account).await?;
    Ok((account, token))
}

#[tauri::command]
pub async fn profile_textures(state: State<'_, AppState>) -> Result<ProfileTextures, AppError> {
    let (_, token) = active_ms_token(&state).await?;
    let profile = microsoft::fetch_profile_raw(&state.http, &token).await?;
    Ok(textures_from(&profile))
}

/// Uploads a library skin as the active account's skin.
#[tauri::command]
pub async fn apply_skin(state: State<'_, AppState>, id: String) -> Result<ProfileTextures, AppError> {
    let (account, token) = active_ms_token(&state).await?;
    let (skin, bytes) = state.skins.get(&id)?;
    let profile = skins::upload(&state.http, &token, bytes, skin.variant).await?;
    let textures = textures_from(&profile);
    state.accounts.update_profile(&account.id, &account.username, textures.skin_url.clone())?;
    Ok(textures)
}

#[tauri::command]
pub async fn set_cape(state: State<'_, AppState>, cape_id: Option<String>) -> Result<ProfileTextures, AppError> {
    let (_, token) = active_ms_token(&state).await?;
    let profile = skins::set_cape(&state.http, &token, cape_id.as_deref()).await?;
    Ok(textures_from(&profile))
}
