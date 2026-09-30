//! Discord Rich Presence. Discord's IPC is a blocking pipe/socket, so it runs
//! on its own thread fed by a channel; the rest of the app never waits on it.
//! If Discord isn't running, updates are dropped and retried on the next one.

use std::sync::mpsc;

use discord_rich_presence::{
    activity::{Activity, Assets, Timestamps},
    DiscordIpc, DiscordIpcClient,
};

pub enum Presence {
    Launcher,
    Playing { profile: String, detail: String, started_ms: i64 },
}

enum Msg {
    Set(Presence),
    Enabled(bool),
}

/// Discord application id ("Doohickey" in the Developer Portal). Runtime env
/// wins over the build-time value, like the Microsoft client id.
pub fn app_id() -> Option<String> {
    std::env::var("QUARTZ_DISCORD_APP_ID")
        .ok()
        .or_else(|| option_env!("QUARTZ_DISCORD_APP_ID").map(str::to_owned))
        .filter(|s| !s.trim().is_empty())
}

pub struct Discord {
    tx: Option<mpsc::Sender<Msg>>,
}

impl Discord {
    pub fn start(enabled: bool) -> Self {
        let Some(app_id) = app_id() else { return Self { tx: None } };
        let (tx, rx) = mpsc::channel::<Msg>();
        std::thread::Builder::new()
            .name("discord-rpc".into())
            .spawn(move || worker(app_id, enabled, rx))
            .ok();
        let discord = Self { tx: Some(tx) };
        discord.set(Presence::Launcher);
        discord
    }

    pub fn set(&self, presence: Presence) {
        if let Some(tx) = &self.tx {
            let _ = tx.send(Msg::Set(presence));
        }
    }

    pub fn set_enabled(&self, enabled: bool) {
        if let Some(tx) = &self.tx {
            let _ = tx.send(Msg::Enabled(enabled));
        }
    }
}

fn worker(app_id: String, mut enabled: bool, rx: mpsc::Receiver<Msg>) {
    let mut client: Option<DiscordIpcClient> = None;
    let mut last = Presence::Launcher;
    for msg in rx {
        match msg {
            Msg::Set(p) => last = p,
            Msg::Enabled(e) => enabled = e,
        }
        if !enabled {
            if let Some(mut c) = client.take() {
                let _ = c.clear_activity();
                let _ = c.close();
            }
            continue;
        }
        if client.is_none() {
            let mut c = DiscordIpcClient::new(&app_id);
            if c.connect().is_err() {
                continue; // Discord not running; try again on the next update
            }
            client = Some(c);
        }
        let c = client.as_mut().expect("connected above");
        let assets = Assets::new().large_image("quartz").large_text("Doohickey Client");
        let result = match &last {
            Presence::Launcher => c.set_activity(Activity::new().details("In the launcher").assets(assets)),
            Presence::Playing { profile, detail, started_ms } => c.set_activity(
                Activity::new()
                    .details(format!("Playing {profile}"))
                    .state(detail.clone())
                    .timestamps(Timestamps::new().start(*started_ms))
                    .assets(assets),
            ),
        };
        if result.is_err() {
            // Discord restarted or closed; reconnect on the next update.
            client = None;
        }
    }
}
