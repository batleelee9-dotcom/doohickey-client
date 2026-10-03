# Doohickey Client — Architecture

## 1. Stack

**Tauri 2 (Rust core) + Svelte 5 + TypeScript** for the launcher, plus **Doohickey Client**, a Fabric mod (Java + Mixin) for everything drawn inside the game.

- **No bundled Chromium.** Tauri renders through the OS webview (WebView2 on Windows, WKWebView on macOS, WebKitGTK on Linux). Electron-based launchers ship roughly 100 MB of Chromium and Node.
- **Rust core.** Downloads, hashing, installers, JVM process management and file I/O run on tokio. The UI thread never blocks, and idle CPU is effectively zero.
- **Svelte 5** compiles components to direct DOM updates with no virtual-DOM runtime. The whole UI is 62 KB of gzipped JS; the 3D skin viewer (131 KB) loads only when you open the Skins page.
- **Rejected:** Electron (bundles Chromium and Node, heavy idle footprint), C++/Qt (development speed, licensing), and native Rust toolkits (egui/Slint) — they would save some RAM, but most of that saving comes back anyway by **destroying the webview while the game runs** (§6).

## 2. Two programs

| Launcher (`src/`, `src-tauri/`) | In game (`client-mod/`, Doohickey Client) |
|---|---|
| Accounts, versions, loaders (incl. Legacy Fabric), Java runtimes | HUD: FPS, CPS, ping, coordinates, reach, keystrokes, potions, armor (+ memory on 26.3) |
| Curated builds from a remote manifest, one-click install with performance mods | PvP: toggle sprint/sneak (+ crosshair, hit colour, damage tint on 26.3) |
| Offline accounts gated to singleplayer/LAN | World: sky, time, fog, weather, fullbright; performance: particles, dynamic render distance |
| Servers, skins, Discord RPC, crash analyzer, console, themes, updater | Account switcher; minimap and cosmetics on 26.3 |

A CPS counter or a cape has to be drawn inside the Minecraft JVM; no launcher technology changes that.

**No profiles.** The player chooses a *build* (a curated Minecraft version) and a loader; that's the whole model. Each build + loader maps to one hidden game folder, `instances/build-<id>-<loader>/`, created on first launch, so worlds, options and servers persist while the player never manages folders. `builds.rs` resolves the loader (`stable` → the loader's recommended version), then syncs the build's Modrinth mods and records the result in `.quartz/build.json`; a network failure launches with what's already there and finishes next time.

**The manifest.** `manifest.rs` loads the build list from the URL in Settings (or `QUARTZ_MANIFEST_URL`), validates it (schema version, unique ids, at least one loader, sane mod slugs), caches it to `meta/builds-manifest.json`, and falls back to the cache and then to the copy compiled into the binary. The UI gets a view with, for each loader, its mods, the instance id and whether Doohickey Client exists for it.

**How the mod gets into the game.** The launcher embeds one jar per supported Minecraft version (`build.rs` generates the table), writes the matching one to `<data>/client/` and passes `-Dfabric.addMods=<jar>` when the build and loader have one. The jar never touches the game folder's `mods/`, so it can't go stale or conflict with other mods, and it always matches the launcher version. Before launching, the launcher installs whatever that jar `requires` (Fabric API for 26.3) from Modrinth, or re-enables a disabled copy. The in-game architecture (version adapters, render backends, compatibility matrix, config schema) is in [docs/CLIENT.md](docs/CLIENT.md).

**Offline accounts.** `launch/mod.rs` refuses an offline account's quick-join to anything but a LAN address (`servers::is_lan_address`: `localhost`, `.local`/`.lan`, loopback, private and link-local IPs), and Doohickey Client's `OfflineGuard` applies the same rule to the in-game Direct Connect and server list. There is no demo mode and no online-mode bypass.

```
┌──────────────────── Doohickey launcher (1 process + OS webview) ───────────────────┐
│   UI: Svelte 5 in WebView2/WKWebView   ⇄ IPC ⇄   Core: Rust + tokio              │
└───────────────────────────────────────────────────────┬─────────────────────────┘
                                                        │ spawns (+ -Dfabric.addMods), pipes logs, watches exit
                                                        ▼
                               ┌──────────── Minecraft JVM ────────────┐
                               │ Fabric loader · Doohickey Client ·        │
                               │ Fabric API · Sodium · Lithium · …      │
                               └────────────────────────────────────────┘
```

## 3. Folder structure

```
cool/
├── README.md · ARCHITECTURE.md · package.json · vite.config.ts
├── src/                               Svelte UI
│   ├── App.svelte · app.css           shell, routing, shortcuts, design tokens (dark/light/accents/materials)
│   ├── lib/
│   │   ├── ipc.ts                     the ONLY place that calls invoke(); typed wrappers + types
│   │   ├── store.svelte.ts            app state: settings, manifest, selected build/loader, running games, crashes, toasts
│   │   ├── router.svelte.ts · theme.ts · format.ts · motd.ts · pings.svelte.ts · presets.ts · accounts.svelte.ts
│   │   └── components/                TitleBar, Sidebar, CommandPalette, PlayButton, Console(Modal), CrashDialog…
│   └── screens/                       Play, Servers, Skins, Settings, Login
├── src-tauri/                         Rust core
│   ├── build.rs                       embeds client-mod's jars (fails with instructions if they aren't built)
│   ├── manifests/builds.json          the bundled build manifest (fallback for the remote one)
│   └── src/
│       ├── lib.rs · state.rs · error.rs · paths.rs · fsutil.rs · settings.rs · system.rs · window.rs
│       ├── accounts/                  Microsoft (login window + PKCE → XBL → XSTS → MC), offline, keychain, session cache
│       ├── meta/                      Mojang manifests + rules, Fabric/Quilt/Legacy Fabric profiles, Forge/NeoForge installers
│       ├── download.rs · java.rs      bounded-concurrency verified downloads; Mojang Java runtimes
│       ├── install.rs · launch/       prepare (game, loader, libraries, assets, natives) → args → supervise
│       ├── manifest.rs · builds.rs    remote manifest (validate, cache, fallback); build → hidden game folder + mod sync
│       ├── instances.rs · client_mod.rs · bridge.rs (in-game account switching API)
│       ├── mods/                      installed-mod index, Modrinth (+ dependencies), CurseForge
│       ├── servers.rs · skins.rs · crash.rs · discord.rs
│       └── commands/                  IPC surface, one file per area
└── client-mod/                        Doohickey Client: one codebase, one jar per Minecraft version (docs/CLIENT.md)
    ├── core/                          version-independent: adapters, render backends, compat matrix, config, features
    ├── versions/1.8.9/                Legacy Fabric, Java 8 bytecode
    ├── versions/26.3/                 Fabric, Java 25: HUD, PvP, map, cosmetics, world
    └── tools/make-textures.mjs        generates the original cape/hat/bandana/wing textures
```

## 4. IPC design

| Mechanism | Used for | Example |
|---|---|---|
| **Command** (`invoke`) | request/response | `get_manifest() → ManifestView` |
| **Channel** | ordered progress for one call | `launch_build(buildId, loader, quickJoin, onProgress)` streams stage + bytes |
| **Event** (`emit`) | app-wide broadcasts | `game://started`, `game://log`, `game://exited` |

- **One door.** Rust commands live in `src-tauri/src/commands/<area>.rs`; only `src/lib/ipc.ts` calls `invoke`.
- **Errors are data.** Every command returns `Result<T, AppError>`, serialized as `{ kind, message }`, with a message written for the player.
- **Cancellation.** Long operations park a `oneshot::Sender`; cancelling drops the in-flight future inside `tokio::select!`.
- **Least privilege.** The main webview can't open URLs itself; the Microsoft login window is a separate window with no IPC access; `open_path` refuses anything outside the data folder. The CSP forbids remote scripts; images only from Mojang, Modrinth and CurseForge CDNs.

## 5. Data on disk

```
<data>/            Windows %APPDATA%\dev.quartz.launcher · macOS ~/Library/Application Support/… · Linux ~/.local/share/…
                   (QUARTZ_DATA_DIR overrides; the path is canonicalized — see Paths::resolve)
  accounts.json · settings.json · servers.json · crashes.json
  meta/builds-manifest.json        last good copy of the remote build manifest
  versions/ libraries/ assets/     shared .minecraft-style layout, so Forge/NeoForge installers work unmodified
  java/                            Mojang Java runtimes (Java 8 … 25), downloaded on demand
  client/                          the embedded Doohickey Client jars
  instances/build-<id>-<loader>/   one hidden game folder per build + loader: mods/, config/, saves/,
                                   .quartz/build.json (synced mods), .quartz/bridge.json (deleted by the mod on read)
```

Microsoft refresh tokens and the CurseForge key live in the OS keychain; Minecraft access tokens only in memory.

## 6. Performance

- The window is created hidden and shown after the first render; no white flash.
- **While the game runs, the webview is destroyed** and a tray icon remains (default "On launch: close to tray"). Measured: 421 MB / 8 processes → 56 MB / 2 processes while playing. The window returns ~1.6 s after the game exits.
- Virtualized game console, lazy images, lazy-loaded 3D skin viewer.
- Downloads: 24 concurrent, streaming SHA-1 verification, mirrors for legacy Forge libraries.
- JVM presets: Balanced (G1, Aikar-style), Performance, Low latency (generational ZGC on Java 21+), or JVM defaults.

## 7. Compliance

| Area | The issue | What Doohickey does |
|---|---|---|
| Microsoft sign-in | Launchers need their own Azure app, allow-listed by Mojang. | Your own ID via `QUARTZ_MS_CLIENT_ID`; never another launcher's, including the official launcher's. A failed sign-in shows the reason and a Retry button. |
| Offline mode | The EULA requires owning the game; online servers require a real session. | Release builds unlock offline accounts only after a Microsoft account that owns the game is added. Offline accounts can reach singleplayer and LAN only (launcher and in game). No demo mode. |
| Account switcher | A local API hands sessions to the game. | Localhost only, random secret per run passed via a file the mod deletes on read; only accounts already added to Doohickey. |
| OptiFine | Licence forbids redistribution; no download API. | Not auto-installed. Sodium + Iris is the default; OptiFine can be added by hand. |
| CurseForge | API key; authors can disable third-party distribution. | Key stored in the keychain; `allowModDistribution: false` opens the mod page instead. |
| Mojang assets | Can't be redistributed. | Always fetched from Mojang at install time. |
| Cosmetics | Mustn't imitate official capes or give an advantage. | Original designs, rendered only on your own client (no cosmetics server), no hitbox change. Hidden under helmets and elytras. |
| Minimap | Many servers ban radar/cave maps. | Honours the `§n§o§m§i§n§i§m§a§p` / fair-play chat codes; no entity radar. |
| Anti-cheat | Some client features look like cheats to server anti-cheat. | Nothing changes packets, reach, hitboxes or timing. Features some servers ban are marked **[OPT-IN / RISK]** in the compatibility matrix and none of them ship today. |

## 8. Status

Working and tested with real launches (before the profile system was removed): vanilla, Fabric, NeoForge, Forge 1.8.9; Doohickey Client loading with Fabric API auto-install; HUD, minimap and the Doohickey menu in game.

Verified without starting Minecraft: every curated build + loader installs through `install_build` (26.3 Fabric/NeoForge, 1.21.11 Fabric/NeoForge, 1.20.1 Fabric/Forge, 1.12.2 Forge, 1.8.9 Legacy Fabric/Forge) with dependencies; the launcher↔client account bridge (real client code against the running launcher, including 401s for bad secrets); the shared core (headless CoreTest: config migration, hot reload, matrix, HUD editor and elements, world logic, performance, offline rule, crash guard); 57 Rust tests; 0 svelte-check errors.

Not yet run in game: Doohickey Client on 1.8.9, the World hooks, fullbright and account switching on 26.3, and the hat/bandana/wing models. Not built: a cosmetics server (so others could see your cosmetics), custom sky gradients/cubemaps, 1.7 animations, crosshair/hit colour/map/cosmetics on 1.8.9, and client builds for 1.12.2–1.21.x (see docs/CLIENT.md).
