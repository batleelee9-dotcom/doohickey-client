// Dev-only: lets `npm run dev` render the launcher in a normal browser by
// faking the Rust backend. main.ts loads this only when there's no Tauri
// runtime, and `import.meta.env.DEV` keeps it out of release builds.
import { mockIPC, mockWindows } from "@tauri-apps/api/mocks";
import bundled from "../../src-tauri/manifests/builds.json";
import type { AccountsSnapshot, AppInfo, ManifestView, Server, Settings } from "../lib/ipc";

const slug = (s: string) => s.toLowerCase().replace(/[^a-z0-9]+/g, "-");
const hasClient = (minecraft: string, kind: string) =>
  (minecraft === "26.3" && kind === "fabric") || (minecraft === "1.8.9" && kind === "legacyfabric");

const manifest: ManifestView = {
  source: "bundled",
  problem: null,
  updated: bundled.updated,
  builds: bundled.builds.map((b) => ({
    ...b,
    loaders: b.loaders.map((l) => ({
      kind: l.kind as never,
      mods: l.mods,
      quartzClient: hasClient(b.minecraft, l.kind),
      instanceId: `build-${slug(b.id)}-${l.kind}`,
    })),
  })),
};

const info: AppInfo = {
  version: "0.2.0",
  platform: "windows",
  msConfigured: true,
  offlineRequiresMicrosoft: false,
  totalMemoryMb: 32768,
  recommendedMemoryMb: 6144,
  dataDir: "C:\\Users\\you\\AppData\\Roaming\\dev.quartz.launcher",
  discordConfigured: true,
  curseforgeConfigured: false,
  updaterConfigured: false,
  quartzClient: [],
};

let settings: Settings = {
  theme: "dark",
  accent: "#8b7cf6",
  material: "solid",
  opacity: 85,
  onLaunch: "close",
  discordRpc: true,
  defaultMemoryMb: 6144,
  defaultJvmPreset: "balanced",
  selectedInstance: null,
  selectedBuild: "1.8.9",
  selectedLoader: "legacyfabric",
  manifestUrl: null,
  msClientId: null,
};

// `?signedout` previews the sign-in screen.
const signedOut = new URLSearchParams(location.search).has("signedout");
const accounts: AccountsSnapshot = {
  activeId: signedOut ? null : "a1",
  accounts: [
    { id: "a1", username: "Doohickey", kind: "microsoft", skinUrl: null, addedAt: 0, lastUsedAt: 2 },
    { id: "a2", username: "LanPlayer", kind: "offline", skinUrl: null, addedAt: 0, lastUsedAt: 1 },
  ],
};

const servers: Server[] = [
  { id: "s1", name: "Hypixel", address: "mc.hypixel.net", favorite: true, instanceId: null, addedAt: 0 },
  { id: "s2", name: "Minemen Club", address: "minemen.club", favorite: true, instanceId: null, addedAt: 0 },
  { id: "s3", name: "Home LAN", address: "192.168.1.20", favorite: true, instanceId: null, addedAt: 0 },
];

const handlers: Record<string, (args: Record<string, unknown>) => unknown> = {
  app_info: () => info,
  get_settings: () => settings,
  update_settings: (a) => (settings = { ...settings, ...(a.patch as Partial<Settings>) }),
  get_manifest: () => manifest,
  running_games: () => [],
  list_crashes: () => [],
  list_servers: () => servers,
  list_accounts: () => accounts,
  switch_account: (a) => ({ ...accounts, activeId: a.id }),
  list_skins: () => [],
  list_mods: () => [],
  list_java_runtimes: () => [],
  ping_server: (a) => ({
    latencyMs: 40,
    version: "1.8-1.21",
    protocol: 47,
    playersOnline: a.address === "mc.hypixel.net" ? 38211 : 1204,
    playersMax: 100000,
    playerSample: [],
    motd: a.address === "mc.hypixel.net" ? "§aHypixel Network §c[1.8-1.21]" : "§bA Minecraft server",
    favicon: null,
  }),
};

mockWindows("main");
mockIPC(
  (cmd, args) => {
    const handler = handlers[cmd];
    if (!handler) {
      console.info(`[preview] ${cmd} isn't mocked`, args);
      return null;
    }
    return handler((args ?? {}) as Record<string, unknown>);
  },
  { shouldMockEvents: true },
);
