//! Launching and supervising games.

pub mod logparse;
pub mod presets;

use std::{
    collections::{HashMap, VecDeque},
    path::{Path, PathBuf},
    process::Stdio,
    sync::{
        atomic::{AtomicBool, Ordering},
        Arc, Mutex, PoisonError,
    },
    time::{Duration, SystemTime},
};

use serde::Serialize;
use tauri::{AppHandle, Emitter, Manager};
use tokio::{
    io::{AsyncBufReadExt, AsyncRead, BufReader},
    sync::oneshot,
};

use crate::{
    accounts::{session::{self, LaunchAuth}, Account, AccountKind},
    client_mod,
    crash::{self, CrashContext, CrashRecord},
    discord::Presence,
    download::Verify,
    error::AppError,
    fsutil,
    install::{self, LaunchProgress, Prepared, Stage},
    instances::Instance,
    meta::mojang::RuleEnv,
    settings::OnLaunch,
    state::AppState,
    system,
};
use logparse::{LogLine, LogParser};
use presets::JvmPreset;

const LOG_CAPACITY: usize = 5000;

pub struct RunningGame {
    pub instance_id: String,
    pub instance_name: String,
    pub pid: u32,
    pub started_at: u64,
    log: Mutex<VecDeque<LogLine>>,
    kill: Mutex<Option<oneshot::Sender<()>>>,
    killed: AtomicBool,
}

#[derive(Clone, Debug, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct RunningInfo {
    pub instance_id: String,
    pub instance_name: String,
    pub pid: u32,
    pub started_at: u64,
}

impl RunningGame {
    fn info(&self) -> RunningInfo {
        RunningInfo {
            instance_id: self.instance_id.clone(),
            instance_name: self.instance_name.clone(),
            pid: self.pid,
            started_at: self.started_at,
        }
    }
}

#[derive(Default)]
pub struct Games {
    running: Mutex<HashMap<String, Arc<RunningGame>>>,
    /// Launches still preparing (downloads etc.), cancellable.
    preparing: Mutex<HashMap<String, oneshot::Sender<()>>>,
}

impl Games {
    pub fn list(&self) -> Vec<RunningInfo> {
        self.running.lock().unwrap_or_else(PoisonError::into_inner).values().map(|g| g.info()).collect()
    }

    pub fn any_running(&self) -> bool {
        !self.running.lock().unwrap_or_else(PoisonError::into_inner).is_empty()
    }

    pub fn log(&self, instance_id: &str) -> Vec<LogLine> {
        self.running
            .lock()
            .unwrap_or_else(PoisonError::into_inner)
            .get(instance_id)
            .map(|g| g.log.lock().unwrap_or_else(PoisonError::into_inner).iter().cloned().collect())
            .unwrap_or_default()
    }

    pub fn kill(&self, instance_id: &str) -> bool {
        let game = self.running.lock().unwrap_or_else(PoisonError::into_inner).get(instance_id).cloned();
        let Some(game) = game else { return false };
        game.killed.store(true, Ordering::SeqCst);
        if let Some(tx) = game.kill.lock().unwrap_or_else(PoisonError::into_inner).take() {
            let _ = tx.send(());
        }
        true
    }

    pub fn cancel_prepare(&self, instance_id: &str) {
        if let Some(tx) = self.preparing.lock().unwrap_or_else(PoisonError::into_inner).remove(instance_id) {
            let _ = tx.send(());
        }
    }
}

#[derive(Clone, Serialize)]
#[serde(rename_all = "camelCase")]
struct LogBatch {
    instance_id: String,
    lines: Vec<LogLine>,
}

#[derive(Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct GameExited {
    pub instance_id: String,
    pub exit_code: Option<i32>,
    pub killed: bool,
    pub played_secs: u64,
    pub crash: Option<CrashRecord>,
}

/// Where the game goes straight after loading ("quick play").
#[derive(Clone, Debug, PartialEq)]
pub enum QuickPlay {
    Server(String),
    /// A world's folder name under saves/.
    World(String),
}

/// Prepares and starts a profile. Resolves once the game process is running.
pub async fn launch(
    app: &AppHandle,
    state: &AppState,
    instance: Instance,
    account: Account,
    quick_play: Option<QuickPlay>,
    reporter: &(dyn Fn(LaunchProgress) + Send + Sync),
) -> Result<RunningInfo, AppError> {
    if state.games.running.lock().unwrap_or_else(PoisonError::into_inner).contains_key(&instance.id) {
        return Err(AppError::Invalid(format!("{} is already running.", instance.name)));
    }
    // Offline accounts are for singleplayer and LAN: never auto-join a public server with one.
    if account.kind == AccountKind::Offline {
        let server = match &quick_play {
            Some(QuickPlay::Server(address)) => Some(address),
            Some(QuickPlay::World(_)) => None,
            None => instance.server.as_ref(),
        };
        if let Some(address) = server.filter(|a| !crate::servers::is_lan_address(a)) {
            return Err(AppError::Invalid(format!(
                "Offline accounts are for singleplayer and LAN only, so {address} is off limits. Switch to a Microsoft account to play on servers."
            )));
        }
    }
    let (cancel_tx, cancel_rx) = oneshot::channel();
    state.games.preparing.lock().unwrap_or_else(PoisonError::into_inner).insert(instance.id.clone(), cancel_tx);

    let prepare = async {
        reporter(LaunchProgress { stage: Stage::Account, message: format!("Signing in as {}", account.username), progress: None });
        let auth = session::launch_auth(state, &account).await?;
        let prepared = install::prepare(&state.http, &state.paths, &instance, Verify::Size, reporter).await?;
        let mut system_props = Vec::new();
        if client_mod::active(&instance) {
            reporter(LaunchProgress { stage: Stage::Loader, message: "Adding Doohickey Client".into(), progress: None });
            system_props.push(client_mod::prepare(state, &instance).await?);
        }
        Ok::<_, AppError>((auth, prepared, system_props))
    };
    let result = tokio::select! {
        r = prepare => r,
        Ok(()) = cancel_rx => Err(AppError::Cancelled),
    };
    state.games.preparing.lock().unwrap_or_else(PoisonError::into_inner).remove(&instance.id);
    let (auth, prepared, system_props) = result?;

    // Remember the installed loader version so later launches skip installing.
    let mut instance = instance;
    if instance.version_id.as_deref() != Some(prepared.version_id.as_str()) {
        instance.version_id = Some(prepared.version_id.clone());
        state.instances.save(&instance)?;
    }

    reporter(LaunchProgress { stage: Stage::Starting, message: "Starting Minecraft".into(), progress: None });
    let settings = state.settings.get();
    let game_dir = state.paths.instance(&instance.id);
    let memory_mb = instance.memory_mb.unwrap_or(settings.default_memory_mb);
    let preset = instance.jvm_preset.unwrap_or(settings.default_jvm_preset);
    let quick_play = quick_play.or_else(|| instance.server.clone().map(QuickPlay::Server));
    let args = build_args(&ArgInputs {
        prepared: &prepared,
        libraries_dir: &state.paths.libraries(),
        game_dir: &game_dir,
        auth: &auth,
        memory_mb,
        preset,
        extra_jvm_args: &instance.jvm_args,
        system_props: &system_props,
        resolution: instance.width.zip(instance.height),
        quick_play: quick_play.as_ref(),
    });
    let args = maybe_argfile(args, prepared.java_major, &game_dir)?;
    if client_mod::active(&instance) {
        if let Some(bridge) = state.bridge.get() {
            bridge.write_handshake(&game_dir)?;
        }
    }

    #[cfg(windows)]
    if settings.high_performance_gpu {
        prefer_dedicated_gpu(&prepared.java).await;
    }

    let mut cmd = tokio::process::Command::new(&prepared.java);
    cmd.args(&args)
        .current_dir(&game_dir)
        .stdin(Stdio::null())
        .stdout(Stdio::piped())
        .stderr(Stdio::piped());
    // No console window, and above-normal priority: the game gets the CPU
    // first when other programs are busy (not "high", which can starve audio and input).
    #[cfg(windows)]
    cmd.creation_flags(0x0800_0000 | 0x0000_8000); // CREATE_NO_WINDOW | ABOVE_NORMAL_PRIORITY_CLASS
    let mut child = cmd
        .spawn()
        .map_err(|e| AppError::Invalid(format!("Couldn't start Java ({}): {e}", prepared.java.display())))?;

    let (kill_tx, kill_rx) = oneshot::channel();
    let game = Arc::new(RunningGame {
        instance_id: instance.id.clone(),
        instance_name: instance.name.clone(),
        pid: child.id().unwrap_or(0),
        started_at: fsutil::unix_now(),
        log: Mutex::new(VecDeque::new()),
        kill: Mutex::new(Some(kill_tx)),
        killed: AtomicBool::new(false),
    });
    state.games.running.lock().unwrap_or_else(PoisonError::into_inner).insert(instance.id.clone(), game.clone());
    let info = game.info();
    let _ = app.emit("game://started", &info);

    let detail = match &quick_play {
        Some(QuickPlay::Server(server)) => format!("On {server}"),
        Some(QuickPlay::World(_)) => "In singleplayer".into(),
        None => format!("{} · {}", instance.game_version, instance.loader.label()),
    };
    state.discord.set(Presence::Playing {
        profile: instance.name.clone(),
        detail,
        started_ms: fsutil::unix_now_ms() as i64,
    });

    let stdout = child.stdout.take().expect("piped");
    let stderr = child.stderr.take().expect("piped");
    let app = app.clone();
    let ctx = ExitContext {
        instance,
        game_dir,
        started: SystemTime::now(),
        java_major: prepared.java_major,
        memory_mb,
        on_launch: settings.on_launch,
    };
    tauri::async_runtime::spawn(async move {
        supervise(app, game, child, stdout, stderr, kill_rx, ctx).await;
    });
    Ok(info)
}

struct ExitContext {
    instance: Instance,
    game_dir: PathBuf,
    started: SystemTime,
    java_major: u32,
    memory_mb: u32,
    on_launch: OnLaunch,
}

async fn supervise(
    app: AppHandle,
    game: Arc<RunningGame>,
    mut child: tokio::process::Child,
    stdout: impl AsyncRead + Unpin + Send + 'static,
    stderr: impl AsyncRead + Unpin + Send + 'static,
    kill_rx: oneshot::Receiver<()>,
    ctx: ExitContext,
) {
    let pending: Arc<Mutex<Vec<LogLine>>> = Arc::default();
    let window_seen = Arc::new(AtomicBool::new(false));

    let out_task = tokio::spawn(pump(stdout, true, game.clone(), pending.clone(), window_seen.clone()));
    let err_task = tokio::spawn(pump(stderr, false, game.clone(), pending.clone(), window_seen.clone()));

    // Log lines go to the UI in batches, and the launcher steps aside once the
    // game's window exists (or after 20 s, whichever comes first).
    let flusher = {
        let app = app.clone();
        let pending = pending.clone();
        let id = game.instance_id.clone();
        let on_launch = ctx.on_launch;
        tokio::spawn(async move {
            let mut ticker = tokio::time::interval(Duration::from_millis(100));
            let started = std::time::Instant::now();
            let mut stepped_aside = false;
            loop {
                ticker.tick().await;
                let lines = std::mem::take(&mut *pending.lock().unwrap_or_else(PoisonError::into_inner));
                if !lines.is_empty() {
                    let _ = app.emit("game://log", LogBatch { instance_id: id.clone(), lines });
                }
                if !stepped_aside && (window_seen.load(Ordering::Relaxed) || started.elapsed() > Duration::from_secs(20)) {
                    stepped_aside = true;
                    crate::window::step_aside(&app, on_launch);
                }
            }
        })
    };

    let kill_requested = tokio::select! {
        status = child.wait() => Err(status),
        Ok(()) = kill_rx => Ok(()),
    };
    let status = match kill_requested {
        Err(status) => status,
        Ok(()) => {
            let _ = child.start_kill();
            child.wait().await
        }
    };
    let _ = tokio::join!(out_task, err_task);
    flusher.abort();
    let remaining = std::mem::take(&mut *pending.lock().unwrap_or_else(PoisonError::into_inner));
    if !remaining.is_empty() {
        let _ = app.emit("game://log", LogBatch { instance_id: game.instance_id.clone(), lines: remaining });
    }

    let exit_code = status.ok().and_then(|s| s.code());
    let killed = game.killed.load(Ordering::SeqCst);
    let state = app.state::<AppState>();
    state.games.running.lock().unwrap_or_else(PoisonError::into_inner).remove(&game.instance_id);

    let played_secs = ctx.started.elapsed().map(|d| d.as_secs()).unwrap_or(0);
    if let Ok(mut instance) = state.instances.get(&ctx.instance.id) {
        instance.last_played_at = Some(fsutil::unix_now());
        instance.play_time_secs += played_secs;
        let _ = state.instances.save(&instance);
    }

    let crash = if killed {
        None
    } else {
        let session_log = game
            .log
            .lock()
            .unwrap_or_else(PoisonError::into_inner)
            .iter()
            .map(|l| l.text.as_str())
            .collect::<Vec<_>>()
            .join("\n");
        let evidence = {
            let (dir, since) = (ctx.game_dir.clone(), ctx.started);
            tokio::task::spawn_blocking(move || crash::collect(&dir, since, session_log)).await.ok()
        };
        evidence.and_then(|ev| {
            // Minecraft sometimes exits with 0 after writing a crash report.
            if exit_code == Some(0) && ev.crash_report.is_none() {
                return None;
            }
            let analysis = crash::analyze(
                &ev.log,
                ev.crash_report.as_ref().map(|(_, t)| t.as_str()),
                ev.hs_err.as_deref(),
                &CrashContext {
                    java_major: ctx.java_major,
                    memory_mb: ctx.memory_mb,
                    loader: match ctx.instance.loader {
                        crate::meta::loaders::LoaderKind::Vanilla => "vanilla",
                        _ => "modded",
                    },
                    exit_code,
                },
            );
            let record = CrashRecord {
                id: uuid::Uuid::new_v4().simple().to_string(),
                instance_id: ctx.instance.id.clone(),
                instance_name: ctx.instance.name.clone(),
                time: fsutil::unix_now(),
                exit_code,
                analysis,
                report_path: ev.crash_report.map(|(p, _)| p.to_string_lossy().into_owned()),
                seen: false,
            };
            let _ = state.crashes.add(record.clone());
            Some(record)
        })
    };

    let _ = app.emit(
        "game://exited",
        GameExited { instance_id: game.instance_id.clone(), exit_code, killed, played_secs, crash },
    );
    if !state.games.any_running() {
        state.discord.set(Presence::Launcher);
        crate::window::come_back(&app, ctx.on_launch);
    }
}

async fn pump(
    stream: impl AsyncRead + Unpin,
    parse_xml: bool,
    game: Arc<RunningGame>,
    pending: Arc<Mutex<Vec<LogLine>>>,
    window_seen: Arc<AtomicBool>,
) {
    let mut reader = BufReader::new(stream);
    let mut parser = LogParser::default();
    let mut buf = Vec::new();
    loop {
        buf.clear();
        match reader.read_until(b'\n', &mut buf).await {
            Ok(0) | Err(_) => break,
            Ok(_) => {}
        }
        // Game output isn't guaranteed to be UTF-8 (mods print in the system
        // code page on Windows); decode lossily rather than dropping lines.
        let line = String::from_utf8_lossy(&buf);
        let line = line.trim_end_matches(['\r', '\n']);
        let lines = if parse_xml {
            parser.push(line)
        } else if line.trim().is_empty() {
            vec![]
        } else {
            vec![LogLine { level: logparse::Level::Error, thread: None, time: None, text: line.to_owned() }]
        };
        if lines.is_empty() {
            continue;
        }
        if lines.iter().any(|l| l.text.contains("LWJGL")) {
            window_seen.store(true, Ordering::Relaxed);
        }
        {
            let mut log = game.log.lock().unwrap_or_else(PoisonError::into_inner);
            for l in &lines {
                if log.len() == LOG_CAPACITY {
                    log.pop_front();
                }
                log.push_back(l.clone());
            }
        }
        pending.lock().unwrap_or_else(PoisonError::into_inner).extend(lines);
    }
}

// ---- Command line ------------------------------------------------------------

pub struct ArgInputs<'a> {
    pub prepared: &'a Prepared,
    pub libraries_dir: &'a Path,
    pub game_dir: &'a Path,
    pub auth: &'a LaunchAuth,
    pub memory_mb: u32,
    pub preset: JvmPreset,
    pub extra_jvm_args: &'a str,
    /// Launcher-supplied `-D` properties, kept whole (paths may hold spaces).
    pub system_props: &'a [String],
    pub resolution: Option<(u32, u32)>,
    pub quick_play: Option<&'a QuickPlay>,
}

pub fn build_args(i: &ArgInputs) -> Vec<String> {
    let p = i.prepared;
    let version = &p.version;
    let separator = if cfg!(windows) { ";" } else { ":" };
    let mut seen = std::collections::HashSet::new();
    let classpath: Vec<String> = p
        .classpath
        .iter()
        .filter(|path| seen.insert(path.to_path_buf()))
        .map(|path| path.to_string_lossy().into_owned())
        .collect();

    let supports_quick_play = version.supports_quick_play();
    let mut features: Vec<&'static str> = Vec::new();
    if i.resolution.is_some() {
        features.push("has_custom_resolution");
    }
    let (server, world) = match i.quick_play {
        Some(QuickPlay::Server(s)) => (Some(s.as_str()), None),
        Some(QuickPlay::World(w)) => (None, Some(w.as_str())),
        None => (None, None),
    };
    if supports_quick_play {
        if server.is_some() {
            features.push("is_quick_play_multiplayer");
        }
        if world.is_some() {
            features.push("is_quick_play_singleplayer");
        }
    }
    let env = RuleEnv::with_features(&features);

    let (width, height) = i.resolution.unwrap_or((854, 480));
    let vars: HashMap<&str, String> = HashMap::from([
        ("auth_player_name", i.auth.username.clone()),
        ("version_name", p.version_id.clone()),
        ("game_directory", i.game_dir.to_string_lossy().into_owned()),
        ("assets_root", p.assets_root.to_string_lossy().into_owned()),
        ("game_assets", p.game_assets.to_string_lossy().into_owned()),
        ("assets_index_name", p.assets_index.clone()),
        ("auth_uuid", i.auth.uuid.clone()),
        ("auth_access_token", i.auth.access_token.clone()),
        ("auth_session", format!("token:{}:{}", i.auth.access_token, i.auth.uuid)),
        ("auth_xuid", i.auth.xuid.clone()),
        ("clientid", String::new()),
        ("user_type", i.auth.user_type.to_owned()),
        ("user_properties", "{}".into()),
        ("version_type", version.kind.clone().unwrap_or_else(|| "release".into())),
        ("natives_directory", p.natives_dir.to_string_lossy().into_owned()),
        ("launcher_name", "quartz".into()),
        ("launcher_version", env!("CARGO_PKG_VERSION").into()),
        ("classpath", classpath.join(separator)),
        ("classpath_separator", separator.into()),
        ("library_directory", i.libraries_dir.to_string_lossy().into_owned()),
        ("resolution_width", width.to_string()),
        ("resolution_height", height.to_string()),
        ("quickPlayMultiplayer", server.unwrap_or("").to_owned()),
        ("quickPlaySingleplayer", world.unwrap_or("").to_owned()),
    ]);
    let substitute = |arg: &str| -> String {
        let mut out = arg.to_owned();
        // Replace until stable; values never contain "${".
        while let Some(start) = out.find("${") {
            let Some(len) = out[start..].find('}') else { break };
            let key = &out[start + 2..start + len];
            let value = vars.get(key).cloned().unwrap_or_default();
            out.replace_range(start..start + len + 1, &value);
        }
        out
    };

    let mut args = presets::memory_flags(i.memory_mb, i.preset);
    args.extend(presets::gc_flags(i.preset, p.java_major));
    args.extend(presets::split_args(i.extra_jvm_args));
    args.extend(i.system_props.iter().cloned());
    // Belt and braces for Log4Shell on 1.12–1.16; harmless elsewhere.
    args.push("-Dlog4j2.formatMsgNoLookups=true".into());

    match version.arguments.as_ref().filter(|a| !a.jvm.is_empty()) {
        Some(a) => args.extend(a.jvm.iter().flat_map(|arg| arg.resolve(&env)).map(|s| substitute(&s))),
        None => {
            // Pre-1.13 versions don't list JVM arguments; these are the
            // vanilla launcher's built-in defaults.
            match system::mojang_os() {
                "windows" => args.push("-XX:HeapDumpPath=MojangTricksIntelDriversForPerformance_javaw.exe_minecraft.exe.heapdump".into()),
                "osx" => args.push("-XstartOnFirstThread".into()),
                _ => {}
            }
            if system::is_32bit() {
                args.push("-Xss1M".into());
            }
            for a in [
                "-Djava.library.path=${natives_directory}",
                "-Dminecraft.launcher.brand=${launcher_name}",
                "-Dminecraft.launcher.version=${launcher_version}",
                "-cp",
                "${classpath}",
            ] {
                args.push(substitute(a));
            }
        }
    }
    if let Some((template, path)) = &p.log_config {
        args.push(template.replace("${path}", &path.to_string_lossy()));
    }

    args.push(version.main_class.clone().unwrap_or_else(|| "net.minecraft.client.main.Main".into()));

    match (&version.arguments, &version.minecraft_arguments) {
        (Some(a), _) if !a.game.is_empty() => {
            args.extend(a.game.iter().flat_map(|arg| arg.resolve(&env)).map(|s| substitute(&s)));
        }
        (_, Some(legacy)) => {
            args.extend(legacy.split_whitespace().map(substitute));
            if let Some((w, h)) = i.resolution {
                args.extend(["--width".into(), w.to_string(), "--height".into(), h.to_string()]);
            }
        }
        _ => {}
    }
    // Before quick play (1.20), joining on launch used --server/--port.
    if let (Some(address), false) = (server, supports_quick_play) {
        if let Ok((host, port)) = crate::servers::parse_address(address) {
            args.extend(["--server".into(), host, "--port".into(), port.unwrap_or(25565).to_string()]);
        }
    }
    args
}

/// Windows limits a command line to 32 767 characters, which heavily modded
/// classpaths can approach. Java 9+ reads arguments from an @argfile instead.
fn maybe_argfile(args: Vec<String>, java_major: u32, game_dir: &Path) -> Result<Vec<String>, AppError> {
    let total: usize = args.iter().map(|a| a.len() + 3).sum();
    if total < 30_000 || java_major < 9 {
        return Ok(args);
    }
    // Inside @argfiles, backslashes and quotes are escapes within quoted tokens.
    let body: Vec<String> =
        args.iter().map(|a| format!("\"{}\"", a.replace('\\', "\\\\").replace('"', "\\\""))).collect();
    let path = game_dir.join(".quartz").join("launch-args.txt");
    std::fs::create_dir_all(path.parent().expect("has parent"))?;
    std::fs::write(&path, body.join("\n"))?;
    Ok(vec![format!("@{}", path.to_string_lossy())])
}

/// Asks Windows to run the game's Java on the high-performance GPU: laptops
/// with integrated and dedicated graphics otherwise often pick the slow one.
/// It's the same per-app choice as Settings › System › Display › Graphics,
/// per user, and only made when there's no choice for this Java yet.
#[cfg(windows)]
async fn prefer_dedicated_gpu(java: &Path) {
    const KEY: &str = r"HKCU\Software\Microsoft\DirectX\UserGpuPreferences";
    let exe = java.to_string_lossy().into_owned();
    let reg = |args: Vec<&str>| {
        let mut c = tokio::process::Command::new("reg");
        c.args(args).stdin(Stdio::null()).stdout(Stdio::null()).stderr(Stdio::null());
        c.creation_flags(0x0800_0000);
        c
    };
    let chosen = reg(vec!["query", KEY, "/v", &exe]).status().await.map(|s| s.success()).unwrap_or(true);
    if !chosen {
        let _ = reg(vec!["add", KEY, "/v", &exe, "/t", "REG_SZ", "/d", "GpuPreference=2;", "/f"]).status().await;
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::meta::mojang::VersionJson;

    fn prepared(version: VersionJson) -> Prepared {
        Prepared {
            version_id: version.id.clone(),
            version,
            java: PathBuf::from("java"),
            java_major: 21,
            classpath: vec![PathBuf::from("/lib/a.jar"), PathBuf::from("/lib/a.jar"), PathBuf::from("/v/26.3.jar")],
            natives_dir: PathBuf::from("/natives"),
            assets_root: PathBuf::from("/assets"),
            assets_index: "34".into(),
            game_assets: PathBuf::from("/assets"),
            log_config: Some(("-Dlog4j.configurationFile=${path}".into(), PathBuf::from("/cfg.xml"))),
        }
    }

    fn auth() -> LaunchAuth {
        LaunchAuth { username: "Steve".into(), uuid: "abc".into(), access_token: "0".into(), user_type: "legacy", xuid: "0".into() }
    }

    fn inputs<'a>(p: &'a Prepared, a: &'a LaunchAuth, quick_play: Option<&'a QuickPlay>) -> ArgInputs<'a> {
        ArgInputs {
            prepared: p,
            libraries_dir: Path::new("/lib"),
            game_dir: Path::new("/game"),
            auth: a,
            memory_mb: 4096,
            preset: JvmPreset::Balanced,
            extra_jvm_args: "-Dx=1",
            system_props: &[],
            resolution: None,
            quick_play,
        }
    }

    fn modern_version() -> VersionJson {
        serde_json::from_value(serde_json::json!({
            "id": "26.3", "type": "release", "mainClass": "net.minecraft.client.main.Main",
            "arguments": {
                "jvm": ["-Djava.library.path=${natives_directory}/java", "-cp", "${classpath}"],
                "game": ["--username", "${auth_player_name}", "--version", "${version_name}",
                    {"rules": [{"action": "allow", "features": {"is_quick_play_singleplayer": true}}], "value": ["--quickPlaySingleplayer", "${quickPlaySingleplayer}"]},
                    {"rules": [{"action": "allow", "features": {"is_quick_play_multiplayer": true}}], "value": ["--quickPlayMultiplayer", "${quickPlayMultiplayer}"]},
                    {"rules": [{"action": "allow", "features": {"has_custom_resolution": true}}], "value": ["--width", "${resolution_width}"]}]
            }
        }))
        .unwrap()
    }

    #[test]
    fn modern_arguments_with_quick_play() {
        let p = prepared(modern_version());
        let a = auth();
        let server = QuickPlay::Server("mc.hypixel.net".into());
        let args = build_args(&inputs(&p, &a, Some(&server)));
        let sep = if cfg!(windows) { ";" } else { ":" };
        let cp = args.iter().position(|x| x == "-cp").unwrap();
        // Duplicate classpath entries are collapsed.
        assert_eq!(args[cp + 1], ["/lib/a.jar", "/v/26.3.jar"].join(sep));
        assert!(args.contains(&"-Xmx4096m".to_owned()));
        assert!(args.contains(&"-Dx=1".to_owned()));
        assert!(args.contains(&"-Dlog4j.configurationFile=/cfg.xml".to_owned()));
        let qp = args.iter().position(|x| x == "--quickPlayMultiplayer").unwrap();
        assert_eq!(args[qp + 1], "mc.hypixel.net");
        assert!(!args.contains(&"--quickPlaySingleplayer".to_owned()));
        assert!(!args.contains(&"--width".to_owned()), "resolution feature is off");
        assert!(!args.contains(&"--server".to_owned()));
        let main = args.iter().position(|x| x == "net.minecraft.client.main.Main").unwrap();
        assert!(main > cp, "main class follows JVM args");
    }

    #[test]
    fn quick_play_into_a_world() {
        let p = prepared(modern_version());
        let a = auth();
        let world = QuickPlay::World("My World (1)".into());
        let args = build_args(&inputs(&p, &a, Some(&world)));
        let qp = args.iter().position(|x| x == "--quickPlaySingleplayer").unwrap();
        assert_eq!(args[qp + 1], "My World (1)");
        assert!(!args.contains(&"--quickPlayMultiplayer".to_owned()));
    }

    #[test]
    fn legacy_arguments_use_server_flag() {
        let version: VersionJson = serde_json::from_value(serde_json::json!({
            "id": "1.8.9", "mainClass": "net.minecraft.client.main.Main",
            "minecraftArguments": "--username ${auth_player_name} --session ${auth_session} --assetsDir ${assets_root}"
        }))
        .unwrap();
        let p = prepared(version);
        let a = auth();
        let server = QuickPlay::Server("play.example.com:25566".into());
        let args = build_args(&inputs(&p, &a, Some(&server)));
        assert!(args.iter().any(|x| x.starts_with("-Djava.library.path=")));
        assert!(args.contains(&"token:0:abc".to_owned()));
        let s = args.iter().position(|x| x == "--server").unwrap();
        assert_eq!(args[s + 1], "play.example.com");
        assert_eq!(args[s + 3], "25566");
    }
}
