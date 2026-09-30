import { listen } from "@tauri-apps/api/event";
import { accounts } from "./accounts.svelte";
import {
  api,
  toAppError,
  type AppInfo,
  type CrashRecord,
  type GameExited,
  type BuildLoaderView,
  type BuildView,
  type LaunchProgress,
  type LoaderKind,
  type LogLine,
  type ManifestView,
  type RunningInfo,
  type Server,
  type Settings,
} from "./ipc";
import { applyTheme } from "./theme";

export interface Toast {
  id: number;
  kind: "error" | "success" | "info";
  message: string;
  action?: { label: string; run: () => void };
}

const MAX_LOG_LINES = 5000;

/** App-wide reactive state. The Rust side is the source of truth; this
 *  mirrors it and stays current through `game://*` events. */
class Store {
  info = $state<AppInfo | null>(null);
  settings = $state<Settings | null>(null);
  manifest = $state<ManifestView | null>(null);
  /** The install whose game output is open in the console window, if any. */
  consoleFor = $state<string | null>(null);
  running = $state<RunningInfo[]>([]);
  /** Profiles currently installing/starting, with their latest progress. */
  launches = $state<Record<string, LaunchProgress>>({});
  crashes = $state<CrashRecord[]>([]);
  servers = $state<Server[]>([]);
  toasts = $state<Toast[]>([]);
  /** Bumped whenever a profile's log grows; consoles re-read on change. Logs
   *  themselves live outside $state — thousands of lines don't need proxies. */
  logVersion = $state<Record<string, number>>({});
  private logs = new Map<string, LogLine[]>();
  private toastSeq = 0;

  /** The build and loader picked on the Play screen (first ones until the player chooses). */
  build = $derived(this.manifest?.builds.find((b) => b.id === this.settings?.selectedBuild) ?? this.manifest?.builds[0] ?? null);
  loader = $derived(this.build ? (this.build.loaders.find((l) => l.kind === this.settings?.selectedLoader) ?? this.build.loaders[0]) : null);
  unseenCrash = $derived(this.crashes.find((c) => !c.seen) ?? null);

  isRunning(id: string) {
    return this.running.some((g) => g.instanceId === id);
  }

  async init() {
    const [info, settings, manifest, running, crashes, servers] = await Promise.all([
      api.appInfo(),
      api.getSettings(),
      api.getManifest(),
      api.runningGames(),
      api.listCrashes(),
      api.listServers(),
      accounts.load(),
    ]);
    Object.assign(this, { info, settings, manifest, running, crashes, servers });
    applyTheme(settings);

    await listen<RunningInfo>("game://started", (e) => {
      if (!this.isRunning(e.payload.instanceId)) this.running = [...this.running, e.payload];
      this.logs.set(e.payload.instanceId, []);
      this.bumpLog(e.payload.instanceId);
    });
    await listen<{ instanceId: string; lines: LogLine[] }>("game://log", (e) => {
      this.appendLog(e.payload.instanceId, e.payload.lines);
    });
    await listen<GameExited>("game://exited", (e) => {
      const { instanceId, crash } = e.payload;
      this.running = this.running.filter((g) => g.instanceId !== instanceId);
      if (crash) this.crashes = [crash, ...this.crashes];
    });

    // The window may have been recreated while games were running; catch up.
    for (const game of running) {
      this.logs.set(game.instanceId, await api.gameLog(game.instanceId));
      this.bumpLog(game.instanceId);
    }
  }

  // ---- Logs ----

  log(id: string): LogLine[] {
    return this.logs.get(id) ?? [];
  }

  private appendLog(id: string, lines: LogLine[]) {
    const log = this.logs.get(id) ?? [];
    log.push(...lines);
    if (log.length > MAX_LOG_LINES) log.splice(0, log.length - MAX_LOG_LINES);
    this.logs.set(id, log);
    this.bumpLog(id);
  }

  clearLog(id: string) {
    this.logs.set(id, []);
    this.bumpLog(id);
  }

  private bumpLog(id: string) {
    this.logVersion[id] = (this.logVersion[id] ?? 0) + 1;
  }

  // ---- Builds ----

  async loadManifest() {
    try {
      this.manifest = await api.getManifest();
    } catch (e) {
      this.error(e);
    }
  }

  /** The build whose hidden install is `instanceId`. */
  buildFor(instanceId: string) {
    return this.manifest?.builds.find((b) => b.loaders.some((l) => l.instanceId === instanceId)) ?? null;
  }

  selectBuild(id: string) {
    void this.updateSettings({ selectedBuild: id });
  }

  selectLoader(kind: LoaderKind) {
    void this.updateSettings({ selectedLoader: kind });
  }

  /** One click: installs whatever the build needs, then starts the game. */
  async launchBuild(build: BuildView, loader: BuildLoaderView, quickJoin: string | null = null) {
    const id = loader.instanceId;
    if (this.launches[id] || this.isRunning(id)) return;
    this.launches[id] = { stage: "account", message: "Preparing", progress: null };
    try {
      const info = await api.launchBuild(build.id, loader.kind, quickJoin, (p) => {
        if (this.launches[id]) this.launches[id] = p;
      });
      if (!this.isRunning(id)) this.running = [...this.running, info];
    } catch (e) {
      this.error(e);
    } finally {
      delete this.launches[id];
    }
  }

  /** Downloads a build without playing; progress shows on its Play button. */
  async installBuild(build: BuildView, loader: BuildLoaderView) {
    const id = loader.instanceId;
    if (this.launches[id] || this.isRunning(id)) return;
    this.launches[id] = { stage: "game", message: "Preparing", progress: null };
    try {
      await api.installBuild(build.id, loader.kind, (p) => {
        if (this.launches[id]) this.launches[id] = p;
      });
      this.toast("success", `${build.name} is ready to play.`);
    } catch (e) {
      this.error(e);
    } finally {
      delete this.launches[id];
    }
  }

  /** Plays the selected build (Ctrl+Enter, server joins, the palette). */
  playSelected(quickJoin: string | null = null) {
    if (this.build && this.loader) return this.launchBuild(this.build, this.loader, quickJoin);
    this.toast("info", "The build list is still loading.");
  }

  async stop(id: string) {
    if (this.launches[id]) await api.cancelLaunch(id);
    else await api.killGame(id);
  }

  // ---- Settings ----

  async updateSettings(patch: Partial<Settings>) {
    try {
      this.settings = await api.updateSettings(patch);
      applyTheme(this.settings);
    } catch (e) {
      this.error(e);
    }
  }

  // ---- Crashes ----

  async markCrashSeen(id: string) {
    this.crashes = this.crashes.map((c) => (c.id === id ? { ...c, seen: true } : c));
    await api.markCrashSeen(id);
  }

  // ---- Toasts ----

  toast(kind: Toast["kind"], message: string, action?: Toast["action"]) {
    const id = ++this.toastSeq;
    this.toasts = [...this.toasts.slice(-3), { id, kind, message, action }];
    setTimeout(() => this.dismiss(id), kind === "error" ? 9000 : 5000);
  }

  dismiss(id: number) {
    this.toasts = this.toasts.filter((t) => t.id !== id);
  }

  error(e: unknown) {
    const err = toAppError(e);
    if (err.kind !== "cancelled") this.toast("error", err.message);
  }
}

export const store = new Store();
