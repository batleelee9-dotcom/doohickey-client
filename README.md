# Doohickey Client

A fast, minimal launcher for Minecraft: Java Edition, with its own in-game client. Built with Tauri 2 (Rust) and Svelte 5; the in-game part is a Fabric mod.

> Not an official Minecraft product. Not approved by or associated with Mojang or Microsoft.

**Install:** run `Doohickey Client_0.2.0_x64-setup.exe` from the [Releases](../../releases) page. It installs per user, with no admin prompt.

## What it does

**Launcher.** One screen, no profiles: pick a version, pick a loader, press Play.
- **Curated versions:** 26.3, 1.21.11, 1.20.1, 1.12.2 and 1.8.9, each an "Optimized" build. Loaders are only the maintained ones for that version: Fabric and NeoForge on 1.20.2+, Fabric and Forge on 1.20.1, Forge on 1.12.2, Legacy Fabric on 1.8.9 (with Doohickey Client built in, the way Lunar runs 1.8.9).
- **One-click launch:** Play installs the game, the loader, the right Java (8 to 25), optimization mods from Modrinth (Sodium, Lithium, FerriteCore, Krypton, Starlight, ModernFix, EntityCulling, ImmediatelyFast…, whichever exist for that version and loader) and nothing else: no menus or config libraries like OneConfig and Doohickey Client where there's a build for it. If Modrinth is unreachable, the game still launches with what's already installed.
- **Remote manifest:** the version list is a JSON file (see [Build manifest](#build-manifest)), so builds and mod sets change without a launcher update. A copy ships inside the app and the last good download is cached, so it works offline.
- **Accounts:** Microsoft sign-in in a window inside the launcher (Microsoft's own page, with PKCE) when you have an approved Azure app ID, and offline accounts. Offline accounts are for **singleplayer and LAN only**: joining a public server with one is refused before the game starts.
- **Servers:** live status and MOTD, favorites, one-click join with the selected build.
- **Skins:** 3D preview; change skin and cape (Microsoft accounts).
- **Also:** crash analyzer in plain English, game console, Discord Rich Presence, themes (dark, light, accent colours, Mica/Acrylic), Ctrl+K command palette, tray icon and signed auto-updates.

**Doohickey Client (in game).** One codebase, one jar per Minecraft version, added automatically when the build supports it. Press **Right Shift** in game to set it up. Fabric **26.3** has everything below; **1.8.9** (Legacy Fabric) has the whole HUD, zoom, hitboxes, the custom crosshair, the World controls, the performance options, toggle sprint/sneak and the account switcher. Per-version details: [docs/COMPATIBILITY.md](docs/COMPATIBILITY.md).
- **HUD (20 elements on both versions):** FPS, CPS, ping, coordinates, reach, keystrokes, potion effects, armor, clock, session time, memory, server address, direction, speed, day counter, saturation, arrow counter, combo counter, block info and biome, plus toggle status on 26.3. Drag to move, scroll to resize. The newer ones start switched off: turn them on in the HUD tab.
- **PvP:** zoom (hold C, 2x–8x, smooth or instant), toggle sprint/sneak, hitboxes, a custom crosshair, plus on 26.3 hit colour and damage tint.
- **Effects and sounds:** hit effects (critical, magic, hearts, flames, blood, smoke, notes, sparkle, lava, with an amount setting), particle trails, kill effects, hit and kill sounds (with a preview as you pick), a low-health heartbeat and no hurt camera. All client-side: only you see and hear them, and nothing is sent to servers.
- **Performance:** particle limiter, entity render distance (1.8.9) and dynamic render distance, which steps the view distance down while FPS stays under your target and back up when it recovers. The client itself is built to stay out of the way: in the headless benchmark the whole 20-element HUD costs about 1 µs and 32 bytes per frame, and the open menu about 4 µs and 280 bytes.
- **World:** sky colour, locked time of day, fog distance and colour, weather override, fullbright (all client-side), and void-fog removal on 1.8.9.
- **Map (26.3):** minimap and waypoints (death waypoints, labels in the world). It respects servers that disable minimaps.
- **Cosmetics (26.3):** capes, hats, bandanas and wings, all original designs. Only you see them.
- **Account switcher:** an **Account** button on the title, multiplayer and pause screens switches between the accounts added to Doohickey without restarting the game (from the title screen, or after leaving a world). Offline accounts are labelled *Singleplayer/LAN only*, and while one is active the multiplayer screen says so and public servers can't be joined.

## Prerequisites (to build)

- **Rust** stable 1.80+, **Node.js** 20+
- A **JDK 25** to build Doohickey Client (it compiles Java 8 bytecode for 1.8.9 too). The launcher's own Java download works: `%APPDATA%\dev.quartz.launcher\java\java-runtime-epsilon`.
- **Windows:** Visual Studio Build Tools ("Desktop development with C++"). **macOS:** Xcode Command Line Tools. **Linux:** `libwebkit2gtk-4.1-dev build-essential libxdo-dev libssl-dev libayatana-appindicator3-dev librsvg2-dev libdbus-1-dev`.

> **Windows path-length gotcha:** MSVC's linker fails with `LNK1104` once paths pass 260 characters. Set `CARGO_TARGET_DIR` to a short folder.

## Build

```bash
cd client-mod && ./gradlew build && cd ..   # Doohickey Client for every Minecraft version; the launcher embeds the jars
npm install
npm run tauri dev                            # develop
npm run tauri build                          # installer → target/release/bundle/nsis
```

The launcher build stops with a clear message if the client jar hasn't been built yet. After changing cosmetic designs, regenerate the textures with `node client-mod/tools/make-textures.mjs`.

## Configuration

| What | How |
|---|---|
| Microsoft sign-in | Paste your Azure app's client ID in the **Azure client ID** box on the sign-in screen, or set `QUARTZ_MS_CLIENT_ID` during `tauri build` to build it in. |
| Build manifest | `QUARTZ_MANIFEST_URL` at build time sets the default; **Settings → Builds** overrides it per install. Leave both empty to use the bundled list. |
| Discord Rich Presence | `QUARTZ_DISCORD_APP_ID`: an application from the Discord developer portal. |
| Auto-updates | `QUARTZ_UPDATE_ENDPOINT`: URL of your `latest.json`. Sign releases with the private key matching the `pubkey` in `tauri.conf.json`. |
| CurseForge | Paste an API key from console.curseforge.com in **Settings → Integrations**. It's stored in the OS keychain. |
| Portable data folder | `QUARTZ_DATA_DIR` |

### Microsoft sign-in setup (one-time)

1. In the Azure portal, open **Microsoft Entra ID → App registrations → New registration**. Choose **Personal Microsoft accounts only**.
2. Under **Authentication**, choose **Add a platform → Mobile and desktop applications** and tick `https://login.microsoftonline.com/common/oauth2/nativeclient`. Copy the **Application (client) ID** from Overview.
3. Apply for Minecraft API access at <https://aka.ms/mce-reviewappid>. Until Mojang allow-lists your ID, sign-in stops at "Log in to Minecraft", and Doohickey shows a "Couldn't sign in" screen with the reason and a Retry button.

Never borrow another launcher's client ID (including the official launcher's): Mojang approves each ID for one app, and using someone else's breaks their terms. Refresh tokens live in the OS keychain.

## Offline mode

Release builds allow offline accounts only after a Microsoft account that owns the game has been added (Prism Launcher's policy, to respect the EULA). Debug builds skip this for development. Whatever the build, offline accounts only reach singleplayer and LAN: the launcher refuses a server join unless the address is `localhost`, a `.local`/`.lan` name, or a loopback, private or link-local IP, and Doohickey Client applies the same rule in game.

## Build manifest

```json
{
  "schema": 1,
  "updated": "2026-09-27",
  "builds": [
    {
      "id": "1.8.9",
      "name": "1.8.9 Optimized",
      "minecraft": "1.8.9",
      "tagline": "Classic PvP.",
      "loaders": [
        { "kind": "legacyfabric", "version": "stable", "mods": [] }
      ]
    }
  ]
}
```

- `kind`: `fabric`, `quilt`, `forge`, `neoforge` or `legacyfabric`. `version` is a loader version, or `stable` for the loader's recommended build.
- `mods`: Modrinth project slugs. Dependencies are added automatically; mods without a build for that version and loader are skipped (the game folder's `.quartz/build.json` records which).
- Each build + loader gets its own hidden game folder, so worlds and settings survive switching. The bundled copy is [`src-tauri/manifests/builds.json`](src-tauri/manifests/builds.json). A manifest that fails to download or validate falls back to the cached copy, then the bundled one, and the Play screen says which it's using.

## Tests

```bash
cd src-tauri && cargo test   # 57 tests: args, rules, installers, manifest, bridge, LAN rule, crash rules, accounts…
npm run check                # svelte-check / TypeScript
# Doohickey Client core, headless (no Minecraft): config migration, hot reload, matrix, HUD editor, world logic
java -cp "client-mod/versions/26.3/build/libs/quartz-client-1.0.0+26.3.jar;<gson jar>" client-mod/core/src/test/java/dev/quartz/core/CoreTest.java
```

## Benchmarks

Windows 11, release build, `scripts/bench.ps1` (3 runs, 10 s idle). The numbers cover the **whole process tree**: `quartz.exe` plus its WebView2 helpers.

| Metric | Target | Measured |
|---|---|---|
| Launch → window visible (warm) | < 2 s | **264–430 ms** |
| Idle RAM, private working set | < 150 MB | **98–114 MB** |
| Idle CPU | ~0 | **0.00–0.27 %** |
| **While playing** (webview closed to tray) | — | **56 MB, 2 processes** (down from 421 MB, 8) |
| Installer / `quartz.exe` | — | 3.7 MB / 13.4 MB |
| UI JS (gzip) | — | 64 KB, plus 129 KB for the 3D skin viewer, loaded only on the Skins page |

Lunar Client hasn't been measured on the same machine. To compare, run `.\scripts\bench.ps1 -Exe "<Lunar Client.exe>" -WindowClass Chrome_WidgetWin_1`.

## Limitations

- Doohickey Client ships for **Fabric 26.3** (full) and **Legacy Fabric 1.8.9** (HUD, World, performance, account switcher). The other builds get the performance mods only; see [docs/CLIENT.md](docs/CLIENT.md) for the plan.
- **Microsoft sign-in needs your own Azure app ID approved by Mojang.** Without one, use offline accounts (singleplayer and LAN).
- Not built yet: custom sky gradients/cubemaps, 1.7 animations, and the crosshair and hit colour on 1.8.9. Entity culling comes from the EntityCulling mod on the builds where it exists, not from Doohickey Client.
- Cosmetics are visible only to you: there is no cosmetics server.
- **OptiFine** isn't offered: its licence forbids launchers from bundling or downloading it, and on 1.8.9 it needs Forge. Sodium + Iris cover the same ground on newer versions.

- **Menu:** Right Shift opens the same smooth menu on 1.8.9 and 26.3: HUD, PvP, Visual, Sound, Performance (and Cosmetics on 26.3) tabs, type-to-search, settings pages behind each module's gear icon, and right-click to step a value back.

Design notes: [ARCHITECTURE.md](ARCHITECTURE.md) (launcher) and [docs/CLIENT.md](docs/CLIENT.md) (in-game client).
