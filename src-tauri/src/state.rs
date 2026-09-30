use std::{
    sync::{Mutex, OnceLock},
    time::Duration,
};

use tokio::sync::oneshot;

use crate::{
    accounts::{session::Sessions, AccountStore},
    bridge::Bridge,
    crash::CrashStore,
    discord::Discord,
    error::AppError,
    instances::InstanceStore,
    launch::Games,
    manifest::Manifest,
    mods::MetaCache,
    paths::Paths,
    servers::ServerStore,
    settings::SettingsStore,
    skins::SkinStore,
};

/// Process-wide state, managed by Tauri and injected into commands.
pub struct AppState {
    /// One pooled client for the whole app so TLS sessions and HTTP/2
    /// connections to Mojang/Modrinth hosts are reused.
    pub http: reqwest::Client,
    pub paths: Paths,
    pub accounts: AccountStore,
    pub settings: SettingsStore,
    pub instances: InstanceStore,
    pub sessions: Sessions,
    pub games: Games,
    pub crashes: CrashStore,
    pub servers: ServerStore,
    pub skins: SkinStore,
    pub mod_cache: MetaCache,
    pub discord: Discord,
    /// Cancel signal for the Microsoft sign-in currently in progress, if any.
    pub login_cancel: Mutex<Option<oneshot::Sender<()>>>,
    /// Local API for Doohickey Client's in-game account switcher; set once at startup.
    pub bridge: OnceLock<Bridge>,
    /// The build manifest the Play screen last loaded (launches use the same one).
    pub manifest: Mutex<Option<Manifest>>,
}

impl AppState {
    pub fn new(paths: Paths) -> Result<Self, AppError> {
        std::fs::create_dir_all(&paths.root)?;
        let http = reqwest::Client::builder()
            // Modrinth asks API clients to identify themselves.
            .user_agent(concat!("Doohickey-Client/", env!("CARGO_PKG_VERSION"), " (desktop launcher)"))
            .connect_timeout(Duration::from_secs(10))
            .read_timeout(Duration::from_secs(30))
            .build()?;
        let settings = SettingsStore::load(paths.file("settings.json"))?;
        let discord = Discord::start(settings.get().discord_rpc);
        Ok(Self {
            http,
            accounts: AccountStore::load(paths.file("accounts.json"))?,
            instances: InstanceStore::new(paths.clone())?,
            crashes: CrashStore::load(paths.file("crashes.json"))?,
            servers: ServerStore::load(paths.file("servers.json"))?,
            skins: SkinStore::load(paths.skins())?,
            settings,
            sessions: Sessions::default(),
            games: Games::default(),
            mod_cache: MetaCache::default(),
            discord,
            login_cancel: Mutex::new(None),
            bridge: OnceLock::new(),
            manifest: Mutex::new(None),
            paths,
        })
    }
}
