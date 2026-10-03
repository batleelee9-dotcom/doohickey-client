// The only module that talks to the Rust core. Types here mirror the serde
// structs in src-tauri/src; keep them in sync when either side changes.
import { Channel, invoke } from "@tauri-apps/api/core";

// ---- Accounts ----------------------------------------------------------------

export type AccountKind = "microsoft" | "offline";

export interface Account {
  id: string;
  username: string;
  kind: AccountKind;
  skinUrl: string | null;
  addedAt: number;
  lastUsedAt: number;
}

export interface AccountsSnapshot {
  activeId: string | null;
  /** Most recently used first. */
  accounts: Account[];
}

export type LoginStep = "xbox" | "minecraft" | "profile";

export type LoginEvent =
  | { event: "step"; data: { step: LoginStep } };

// ---- App & settings ------------------------------------------------------------

export interface AppInfo {
  version: string;
  platform: "windows" | "macos" | "linux";
  msConfigured: boolean;
  offlineRequiresMicrosoft: boolean;
  totalMemoryMb: number;
  recommendedMemoryMb: number;
  dataDir: string;
  discordConfigured: boolean;
  curseforgeConfigured: boolean;
  updaterConfigured: boolean;
  /** Doohickey Client builds embedded in this launcher. */
  quartzClient: { minecraft: string; loader: LoaderKind; version: string; summary: string }[];
}

export type Theme = "system" | "dark" | "light";
export type Material = "solid" | "mica" | "acrylic" | "vibrancy";
export type OnLaunch = "keep" | "minimize" | "close";
export type JvmPreset = "balanced" | "performance" | "lowLatency" | "jvmDefault";

export interface Settings {
  theme: Theme;
  accent: string;
  material: Material;
  opacity: number;
  onLaunch: OnLaunch;
  discordRpc: boolean;
  defaultMemoryMb: number;
  defaultJvmPreset: JvmPreset;
  selectedInstance: string | null;
  selectedBuild: string | null;
  selectedLoader: LoaderKind | null;
  /** Where the curated build list comes from; null = the one built into Doohickey. */
  manifestUrl: string | null;
  /** Azure app (client) ID pasted on the sign-in screen; null = the built-in one. */
  msClientId: string | null;
}

// ---- Curated builds -------------------------------------------------------------

export interface BuildLoaderView {
  kind: LoaderKind;
  /** Modrinth slugs of the bundled performance mods. */
  mods: string[];
  quartzClient: boolean;
  /** The hidden install this build + loader launches from. */
  instanceId: string;
  /** Offer "Add OptiFine" (the player downloads it; the launcher installs it). */
  optifine: boolean;
}

export interface BuildView {
  id: string;
  name: string;
  minecraft: string;
  tagline: string;
  loaders: BuildLoaderView[];
}

export interface ManifestView {
  source: "remote" | "cached" | "bundled";
  /** Why the remote manifest wasn't used, if it wasn't. */
  problem: string | null;
  updated: string | null;
  builds: BuildView[];
}

// ---- Versions & profiles --------------------------------------------------------

export type LoaderKind = "vanilla" | "fabric" | "quilt" | "forge" | "neoforge" | "legacyfabric";

export interface GameVersion {
  id: string;
  kind: "release" | "snapshot" | "old_beta" | "old_alpha" | string;
  releaseTime: string;
}

export interface LoaderVersion {
  id: string;
  name: string;
  stable: boolean;
  recommended: boolean;
}

export interface Instance {
  id: string;
  name: string;
  gameVersion: string;
  loader: LoaderKind;
  loaderVersion: string | null;
  versionId: string | null;
  color: string | null;
  createdAt: number;
  lastPlayedAt: number | null;
  playTimeSecs: number;
  memoryMb: number | null;
  jvmPreset: JvmPreset | null;
  jvmArgs: string;
  javaPath: string | null;
  width: number | null;
  height: number | null;
  server: string | null;
  quartzClient: boolean;
}

export interface Replay {
  fileName: string;
  name: string;
  size: number;
  /** Unix milliseconds. */
  date: number;
  durationMs: number | null;
  server: string | null;
  singleplayer: boolean;
  mcVersion: string | null;
  players: number;
}

export interface ReplayModStatus {
  installed: boolean;
  available: string | null;
}

export interface World {
  /** Folder name under saves/. */
  id: string;
  name: string;
  lastPlayed: number | null;
  mode: "survival" | "creative" | "adventure" | "spectator" | "hardcore" | null;
  version: string | null;
  cheats: boolean;
  icon: string | null;
}

export interface NewInstance {
  name: string;
  gameVersion: string;
  loader: LoaderKind;
  loaderVersion: string | null;
  server?: string | null;
  quartzClient?: boolean;
}

export interface Progress {
  doneBytes: number;
  totalBytes: number;
  doneFiles: number;
  totalFiles: number;
}

export type LaunchStage = "account" | "java" | "game" | "loader" | "files" | "natives" | "starting";

export interface LaunchProgress {
  stage: LaunchStage;
  message: string;
  progress: Progress | null;
}

export interface RunningInfo {
  instanceId: string;
  instanceName: string;
  pid: number;
  startedAt: number;
}

export type LogLevel = "trace" | "debug" | "info" | "warn" | "error" | "fatal";

export interface LogLine {
  level: LogLevel;
  thread: string | null;
  time: number | null;
  text: string;
}

export interface CrashAnalysis {
  category: string;
  title: string;
  summary: string;
  fixes: string[];
  culprits: string[];
  evidence: string[];
}

export interface CrashRecord {
  id: string;
  instanceId: string;
  instanceName: string;
  time: number;
  exitCode: number | null;
  analysis: CrashAnalysis;
  reportPath: string | null;
  seen: boolean;
}

export interface GameExited {
  instanceId: string;
  exitCode: number | null;
  killed: boolean;
  playedSecs: number;
  crash: CrashRecord | null;
}

export interface InstalledRuntime {
  component: string;
  version: string;
  path: string;
  sizeBytes: number;
}

// ---- Mods ------------------------------------------------------------------------

export type ModPlatform = "modrinth" | "curseforge";

export interface ModSource {
  platform: ModPlatform;
  projectId: string;
  versionId: string;
  title: string | null;
  iconUrl: string | null;
}

export interface InstalledMod {
  fileName: string;
  enabled: boolean;
  size: number;
  modId: string | null;
  name: string | null;
  version: string | null;
  description: string | null;
  authors: string[];
  hasIcon: boolean;
  source: ModSource | null;
}

export interface SearchHit {
  platform: ModPlatform;
  projectId: string;
  slug: string;
  title: string;
  description: string;
  iconUrl: string | null;
  downloads: number;
  author: string;
  categories: string[];
  url: string;
}

export interface InstallOutcome {
  installed: string[];
  external: { name: string; url: string }[];
  skipped: string[];
}

export interface ModUpdate {
  fileName: string;
  projectId: string;
  versionId: string;
  versionNumber: string;
}

// ---- Servers & skins ----------------------------------------------------------------

export interface Server {
  id: string;
  name: string;
  address: string;
  favorite: boolean;
  instanceId: string | null;
  addedAt: number;
}

export interface ServerStatus {
  latencyMs: number;
  version: string;
  protocol: number;
  playersOnline: number;
  playersMax: number;
  playerSample: string[];
  motd: unknown;
  favicon: string | null;
}

export type SkinVariant = "classic" | "slim";

export interface Skin {
  id: string;
  name: string;
  variant: SkinVariant;
  addedAt: number;
}

export interface Cape {
  id: string;
  alias: string;
  url: string;
  active: boolean;
}

export interface ProfileTextures {
  skinUrl: string | null;
  variant: SkinVariant;
  capes: Cape[];
}

export interface UpdateInfo {
  version: string;
  currentVersion: string;
  notes: string | null;
}

// ---- Errors ----------------------------------------------------------------------------

export interface AppError {
  kind: "auth" | "network" | "invalid" | "config" | "cancelled" | "io" | "keychain" | "internal";
  message: string;
}

/** Commands reject with a serialized `AppError`; anything else is a bug surfaced as-is. */
export function toAppError(e: unknown): AppError {
  if (e && typeof e === "object" && "kind" in e && "message" in e) return e as AppError;
  return { kind: "internal", message: e instanceof Error ? e.message : String(e) };
}

function channel<T>(onMessage: (m: T) => void) {
  const ch = new Channel<T>();
  ch.onmessage = onMessage;
  return ch;
}

export const api = {
  // App
  appInfo: () => invoke<AppInfo>("app_info"),
  getSettings: () => invoke<Settings>("get_settings"),
  updateSettings: (patch: Partial<Settings>) => invoke<Settings>("update_settings", { patch }),
  setCurseforgeKey: (key: string | null) => invoke<boolean>("set_curseforge_key", { key }),
  openFolder: (kind: "data" | "instance" | "mods" | "screenshots" | "logs", id?: string) =>
    invoke<void>("open_folder", { kind, id: id ?? null }),
  openPath: (path: string) => invoke<void>("open_path", { path }),
  openExternal: (url: string) => invoke<void>("open_external", { url }),

  // Accounts
  listAccounts: () => invoke<AccountsSnapshot>("list_accounts"),
  addOfflineAccount: (username: string) => invoke<AccountsSnapshot>("add_offline_account", { username }),
  switchAccount: (id: string) => invoke<AccountsSnapshot>("switch_account", { id }),
  removeAccount: (id: string) => invoke<AccountsSnapshot>("remove_account", { id }),
  /** Resolves when sign-in completes; progress arrives through `onEvent`. */
  msLogin: (onEvent: (e: LoginEvent) => void) =>
    invoke<AccountsSnapshot>("ms_login", { onEvent: channel(onEvent) }),
  msLoginCancel: () => invoke<void>("ms_login_cancel"),

  // Versions
  listGameVersions: () => invoke<GameVersion[]>("list_game_versions"),
  listLoaderVersions: (loader: LoaderKind, gameVersion: string) =>
    invoke<LoaderVersion[]>("list_loader_versions", { loader, gameVersion }),
  listJavaRuntimes: () => invoke<InstalledRuntime[]>("list_java_runtimes"),
  removeJavaRuntime: (component: string) => invoke<void>("remove_java_runtime", { component }),

  // Profiles
  listInstances: () => invoke<Instance[]>("list_instances"),
  createInstance: (request: NewInstance) => invoke<Instance>("create_instance", { request }),
  updateInstance: (id: string, patch: Partial<Instance>) => invoke<Instance>("update_instance", { id, patch }),
  deleteInstance: (id: string) => invoke<void>("delete_instance", { id }),
  duplicateInstance: (id: string) => invoke<Instance>("duplicate_instance", { id }),
  selectInstance: (id: string) => invoke<void>("select_instance", { id }),
  getManifest: () => invoke<ManifestView>("get_manifest"),
  installBuild: (buildId: string, loader: LoaderKind, onProgress: (p: LaunchProgress) => void) =>
    invoke<void>("install_build", { buildId, loader, onProgress: channel(onProgress) }),
  addOptifine: (buildId: string, loader: LoaderKind, path: string) => invoke<string>("add_optifine", { buildId, loader, path }),
  launchBuild: (buildId: string, loader: LoaderKind, quickJoin: string | null, onProgress: (p: LaunchProgress) => void) =>
    invoke<RunningInfo>("launch_build", { buildId, loader, quickJoin, onProgress: channel(onProgress) }),
  launchInstance: (id: string, quickJoin: string | null, onProgress: (p: LaunchProgress) => void, world: string | null = null) =>
    invoke<RunningInfo>("launch_instance", { id, quickJoin, world, onProgress: channel(onProgress) }),
  listWorlds: (id: string) => invoke<World[]>("list_worlds", { id }),
  deleteWorld: (id: string, world: string) => invoke<void>("delete_world", { id, world }),
  listReplays: (id: string) => invoke<Replay[]>("list_replays", { id }),
  deleteReplay: (id: string, fileName: string) => invoke<void>("delete_replay", { id, fileName }),
  renameReplay: (id: string, fileName: string, name: string) => invoke<string>("rename_replay", { id, fileName, name }),
  replayModStatus: (id: string) => invoke<ReplayModStatus>("replaymod_status", { id }),
  installReplayMod: (id: string) => invoke<InstallOutcome>("install_replaymod", { id }),
  cancelLaunch: (id: string) => invoke<void>("cancel_launch", { id }),
  killGame: (id: string) => invoke<boolean>("kill_game", { id }),
  runningGames: () => invoke<RunningInfo[]>("running_games"),
  gameLog: (id: string) => invoke<LogLine[]>("game_log", { id }),
  repairInstance: (id: string, onProgress: (p: LaunchProgress) => void) =>
    invoke<void>("repair_instance", { id, onProgress: channel(onProgress) }),

  // Crashes
  listCrashes: () => invoke<CrashRecord[]>("list_crashes"),
  markCrashSeen: (id: string) => invoke<void>("mark_crash_seen", { id }),
  clearCrashes: () => invoke<void>("clear_crashes"),
  analyzeLogFile: (path: string) => invoke<CrashAnalysis>("analyze_log_file", { path }),

  // Mods
  listMods: (instanceId: string) => invoke<InstalledMod[]>("list_mods", { instanceId }),
  modIcon: (instanceId: string, fileName: string) => invoke<string | null>("mod_icon", { instanceId, fileName }),
  setModEnabled: (instanceId: string, fileName: string, enabled: boolean) =>
    invoke<void>("set_mod_enabled", { instanceId, fileName, enabled }),
  removeMod: (instanceId: string, fileName: string) => invoke<void>("remove_mod", { instanceId, fileName }),
  importMods: (instanceId: string, paths: string[]) => invoke<string[]>("import_mods", { instanceId, paths }),
  searchMods: (instanceId: string, platform: ModPlatform, query: string, sort: string, offset: number) =>
    invoke<{ hits: SearchHit[]; total: number }>("search_mods", { instanceId, platform, query, sort, offset }),
  installMod: (instanceId: string, platform: ModPlatform, projectId: string) =>
    invoke<InstallOutcome>("install_mod", { instanceId, platform, projectId }),
  checkModUpdates: (instanceId: string) => invoke<ModUpdate[]>("check_mod_updates", { instanceId }),
  updateMod: (instanceId: string, fileName: string, versionId: string) =>
    invoke<string>("update_mod", { instanceId, fileName, versionId }),
  installPerformancePack: (instanceId: string) => invoke<InstallOutcome>("install_performance_pack", { instanceId }),

  // Servers
  listServers: () => invoke<Server[]>("list_servers"),
  saveServer: (server: Partial<Server> & { name: string; address: string }) => invoke<Server[]>("save_server", { server }),
  removeServer: (id: string) => invoke<Server[]>("remove_server", { id }),
  pingServer: (address: string) => invoke<ServerStatus>("ping_server", { address }),

  // Skins
  listSkins: () => invoke<Skin[]>("list_skins"),
  importSkin: (path: string, name: string, variant: SkinVariant) => invoke<Skin>("import_skin", { path, name, variant }),
  updateSkin: (id: string, name: string | null, variant: SkinVariant | null) =>
    invoke<Skin[]>("update_skin", { id, name, variant }),
  deleteSkin: (id: string) => invoke<Skin[]>("delete_skin", { id }),
  skinData: (id: string) => invoke<string>("skin_data", { id }),
  textureData: (url: string) => invoke<string>("texture_data", { url }),
  profileTextures: () => invoke<ProfileTextures>("profile_textures"),
  applySkin: (id: string) => invoke<ProfileTextures>("apply_skin", { id }),
  setCape: (capeId: string | null) => invoke<ProfileTextures>("set_cape", { capeId }),

  // Updates
  checkUpdate: () => invoke<UpdateInfo | null>("check_update"),
  installUpdate: (onProgress: (p: { downloaded: number; total: number | null }) => void) =>
    invoke<void>("install_update", { onProgress: channel(onProgress) }),
};
