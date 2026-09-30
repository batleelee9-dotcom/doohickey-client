use tauri::State;

use crate::{
    error::AppError,
    fsutil,
    servers::{self, Server, ServerStatus},
    state::AppState,
};

#[tauri::command]
pub fn list_servers(state: State<AppState>) -> Vec<Server> {
    state.servers.list()
}

#[derive(serde::Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct ServerInput {
    id: Option<String>,
    name: String,
    address: String,
    #[serde(default)]
    favorite: bool,
    #[serde(default)]
    instance_id: Option<String>,
}

#[tauri::command]
pub async fn save_server(state: State<'_, AppState>, server: ServerInput) -> Result<Vec<Server>, AppError> {
    let existing = server.id.as_deref().and_then(|id| state.servers.list().into_iter().find(|s| s.id == id));
    state.servers.upsert(Server {
        id: server.id.unwrap_or_else(|| uuid::Uuid::new_v4().simple().to_string()),
        name: server.name,
        address: server.address,
        favorite: server.favorite,
        instance_id: server.instance_id.filter(|i| !i.is_empty()),
        added_at: existing.map(|s| s.added_at).unwrap_or_else(fsutil::unix_now),
    })
}

#[tauri::command]
pub async fn remove_server(state: State<'_, AppState>, id: String) -> Result<Vec<Server>, AppError> {
    state.servers.remove(&id)
}

#[tauri::command]
pub async fn ping_server(address: String) -> Result<ServerStatus, AppError> {
    servers::ping(&address).await
}
