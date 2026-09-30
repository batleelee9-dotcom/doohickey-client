# Doohickey Client — in-game architecture

Doohickey Client is the part of Doohickey that runs inside Minecraft. One source tree targets many Minecraft versions; today it ships builds for **1.8.9** and **26.3**.

## 1. Injection strategy

**Chosen: Option A, with a twist. Mixin on Fabric everywhere, using Legacy Fabric for pre-1.14 versions. There is one shared core, and each version has a thin adapter module.**

- **Mixin already solves the hard problems.** It gives precise bytecode injection, conflict handling between mods, and remapping between obfuscated and named code. Legacy Fabric brings the same Fabric Loader and Mixin to 1.8.9–1.13.2, so one injection mechanism covers 1.8.9 through the latest snapshot.
- **Option B (a custom agent on vanilla, Lunar-style)** would mean maintaining our own mappings and remapper for every obfuscated version. It would also mean re-implementing Mixin's conflict handling, and it's invisible to Sodium, Iris or ReplayMod. Lunar can afford that with a large team; we would pay for it forever.
- **Option C (hybrid)** adds a second injection system just for vanilla 1.8.9. Legacy Fabric makes that unnecessary, and the launcher already installs loaders automatically, so players never have to pick one.
- **The cost of A** is one jar per Minecraft version. A composite Gradle build makes that a single command, and the launcher embeds every jar and picks the right one for the selected build and loader.
- **Loading.** The launcher passes `-Dfabric.addMods=<jar>` instead of copying into `mods/`. The client can never go stale or clash with the player's own mods.

## 2. Layout

```
client-mod/
├── settings.gradle, build.gradle   composite build: `./gradlew build` builds every version
├── core/                           version-independent, Java 8 source, no Minecraft imports
│   └── dev/quartz/core/
│       ├── McVersion               registry of known versions (loader, Java release, render era)
│       ├── VersionAdapter          everything feature code needs from the game
│       ├── RenderBackend           2D drawing, one implementation per rendering era
│       ├── Feature, CompatRegistry the compatibility matrix as code (docs/COMPATIBILITY.md)
│       ├── Safe, Log               crash-proof hook wrapper, version-neutral logging
│       ├── config/                 ClientConfig (client.json schema v2) + Migrations
│       ├── env/                    Sky / time / fog / weather logic
│       ├── hud/                    shared HUD: elements (FPS, CPS, ping, coords, reach, keystrokes, potions, armor), placement, drag editor, CPS/reach tracking
│       ├── accounts/               LauncherBridge, AccountSwitcher, OfflineGuard (singleplayer/LAN rule)
│       ├── perf/                   particle limiter, entity distance, dynamic render distance
│       ├── pvp/                    toggle sprint/sneak state
│       └── ui/                     Option descriptors (HUD, World, Game); each version's menu renders them
├── core/src/test/                  CoreTest: headless checks with a fake adapter (no Minecraft)
└── versions/
    ├── 1.8.9/   Legacy Fabric: LegacyAdapter, LegacyGlBackend (GL11), mixins, menu (HUD/World/Game), HUD editor, accounts screen
    └── 26.3/    Fabric: ModernAdapter, PipelineBackend (GuiGraphicsExtractor), mixins, extra HUD modules, PvP, map, cosmetics, accounts screen
```

Each version build compiles `core/` into its own jar (`sourceSets.main.java.srcDir '../../core/src/main/java'`), with that version's Java release: Java 8 bytecode for 1.8.9, Java 25 for 26.3.

## 3. Version abstraction

Rules, enforced by structure:

- **Core never imports Minecraft.** Game → Doohickey goes through Mixin hooks in the version module. They hand plain values (an RGB int, a float, a tick count) to core logic and apply the result. Doohickey → game goes through `VersionAdapter`.
- **No version checks in feature code.** A feature asks `Quartz.available(Feature.X)`, which consults `CompatRegistry` for the running version. Menus build from `ui.Option` descriptors and drop unavailable ones, so a toggle is hidden, never broken.
- **Never crash the game.** Every hook body runs through `Safe.run` or `Safe.call`. On an exception, vanilla behaviour is used, the error is logged once, and after 3 failures the hook switches off for the session.
- **Render eras.** `RenderBackend.Kind` has four eras: `LEGACY_OPENGL` (1.8.9–1.12), `TESSELLATOR` (1.13–1.16), `RENDER_SYSTEM` (1.17–1.19) and `PIPELINE` (1.20+). Two are implemented: `LegacyGlBackend` and `PipelineBackend`. The middle two arrive with the first 1.13–1.19 builds.

### Proof: Sky / Fog / Weather on 1.8.9 and 26.3

One implementation of the logic (`core/env/EnvironmentModule`) and one menu (`core/ui/WorldOptions`), hooked differently per version:

| | 1.8.9 (Legacy Fabric, yarn names) | 26.3 (Fabric, official names) |
|---|---|---|
| Sky colour | `World#method_3631` (getSkyColor) return value | `EnvironmentAttributeProbe#getValue` for `SKY_COLOR` (the camera's probe; client-only) |
| Time lock | `World#getSkyAngle`: recomputed from the locked time | `AttributeTrackSampler#applyTimeBased`: the Overworld clock read by the client's timelines |
| Weather | `World#getRainGradient` / `getThunderGradient` (client world only) | `Level#getRainLevel` / `getThunderLevel` (client level only) |
| Fog distance | `GameRenderer#renderFog` tail, via GL fog start/end | `FogRenderer#setupFog` return value (`FogData`) |
| Fog colour | `GameRenderer#updateFog` tail, then `clearColor` | `FogData.color` |
| Void fog | `Dimension#method_3994` (horizon ratio) → 1.0, as superflat | N/A: removed in 1.18 |

Everything is visual only: the integrated server's time, weather, mob spawning and crops are untouched. Water, lava, blindness and darkness keep their fog, because those are gameplay information.

## 4. Config: `config/quartz/client.json`

One file per game folder (each build + loader has its own), shared by every version. Fields a version doesn't use are kept untouched.

```json
{
  "schemaVersion": 2,
  "modules": { "fps": { "enabled": true, "x": 0.0, "y": 0.0, "scale": 1.0 } },
  "textShadow": true, "moduleBackground": true, "textColor": -1,
  "customCrosshair": false, "crosshairStyle": 0, "crosshairColor": -1, "crosshairSize": 5, "crosshairGap": 2,
  "crosshairOutline": true, "hitColorEnabled": false, "hitColor": -1304395521, "damageTint": true,
  "particlePercent": 100, "sprintToggled": false,
  "minimapZoom": 1, "waypointsInWorld": true, "deathWaypoints": true,
  "cape": "none", "hat": "none", "bandana": "none", "wings": "none",
  "environment": {
    "customSky": false, "skyColor": 8103167,
    "lockTime": false, "timeOfDay": 6000,
    "fog": "VANILLA", "customFogColor": false, "fogColor": 14477045,
    "weather": "VANILLA", "removeVoidFog": false, "fullbright": false
  },
  "performance": {
    "dynamicRenderDistance": false, "targetFps": 60, "minRenderDistance": 4, "entityDistance": 0
  }
}
```

- **Migrations** (`config/Migrations.java`) upgrade one schema step at a time. Schema 1 (`config/quartz-client.json`, from Doohickey Client 1.0) moves to the new path, and its separate `minimap` switch folds into the minimap module. The old file is kept as `quartz-client.json.migrated`.
- **Unknown values from a newer Doohickey** (an enum name this build doesn't know) fall back to defaults. A higher `schemaVersion` is preserved rather than downgraded.
- **Hot reload.** The file's modification time is checked once a second. Changes made outside the game (by hand, or by the launcher) are loaded into the live config object, so open menus see them too.
- **Corrupt files** are moved aside as `*.corrupt`, and the client starts from defaults.
- **Launcher sync.** The launcher owns the game folder, so it can read and write `client.json`. Hot reload makes those edits apply live.
- **Risky features** (`Feature.risk`) are shown as "[OPT-IN / RISK]" and default to off.

## 4b. Account switcher

The title, multiplayer and pause screens get an **Account: name** button (on 1.8.9 too). It lists the accounts added to the Doohickey launcher and switches the game's session without restarting.

- **Launcher side (`src-tauri/src/bridge.rs`).** A tiny HTTP API on `127.0.0.1` only, on a random port, with a random 256-bit secret per launcher run. `GET /v1/accounts` lists the accounts; `POST /v1/accounts/<id>/session` returns a fresh session, refreshing Microsoft tokens if needed.
- **Handshake.** The port and secret go to the game in `<game folder>/.quartz/bridge.json`, written just before launch. The mod reads the file and deletes it at startup, so the secret never appears on a command line. Requests without the secret get `401`. Browsers can't use the API either: they can't send the secret header cross-origin, and responses carry no CORS headers.
- **Game side.** `core/accounts` does the network calls off the game thread. The adapter swaps the session on the game thread.
  - **1.8.9:** replaces `MinecraftClient.session`.
  - **26.3:** rebuilds everything Minecraft derives from the account: user, profile, services API, chat-signing keys, reporting, friends and telemetry. It builds them all first, then swaps, so a failure leaves the old session whole.
  - **Outside a world only**, since the session is in use while connected.
- **What it switches between:** only accounts already added to Doohickey.
- **Offline accounts** are listed as *Singleplayer/LAN only*. While one is active, the multiplayer screen shows a notice and `OfflineGuard` stops connections to anything but LAN addresses (the same rule as the launcher: `localhost`, `.local`/`.lan`, loopback, private and link-local IPs), with a message instead of a crash. Singleplayer and "Open to LAN" work as usual.

## 5. Build

```bash
cd client-mod
./gradlew build          # every version → versions/<mc>/build/libs/quartz-client-<ver>+<mc>.jar
./gradlew :1.8.9:build   # one version
```

The root build is a Gradle composite: each version is its own included build, because each pins its own Loom flavour (legacy-looming 1.16.1 or fabric-loom 1.18), mappings and Java release. The launcher's `build.rs` finds every `versions/*/gradle.properties` and embeds each jar. It records which loader each jar belongs to (`loader=`), the mods it needs (`requires=`, installed from Modrinth at launch) and a one-line `summary=`.

**Adding a version:** copy a `versions/<mc>` folder, adjust `gradle.properties`, and write its adapter, backend and hooks. Then mark the version `built` in `McVersion` and list its features in `CompatRegistry.implemented(...)`.

## 6. Feature implementation plan

Estimates are new lines of code: core logic (shared) + hooks per version.

| Module | Features | Core | Per version | Status |
|---|---|---|---|---|
| World | sky colour, time, fog, fog colour, weather, void fog | 250 | 120–180 | **Done: 1.8.9, 26.3** |
| World+ | sky texture/cubemap, clouds, sun/moon/stars, fullbright, block outline, chunk borders, light overlay | 600 | 400 | Fullbright **done** (1.8.9 via gamma, 26.3 via the lightmap); the rest planned |
| HUD framework | draggable/snap/scale editor, profiles, fonts | 700 | 150 (backend + render hook) | **Done in core** (editor, snapping, scaling); profiles and fonts planned |
| HUD elements | FPS, CPS, ping, TPS, coords+biome, keystrokes, armor, potions, reach, combo, item/arrow count, graphs, clock, server IP, memory, compass, scoreboard, chat | 1,500 | 300 | Shared (1.8.9 + 26.3): FPS, CPS, ping, coords, reach, keystrokes, potions, armor. 26.3 also has memory and toggle status |
| PvP | toggle sprint/sneak, old animations, blockhit, hit/hurt colour, hitboxes, nametags, zoom, raw input, freelook [risk], reach display | 900 | 500 | Toggle sprint/sneak on both; crosshair, hit colour and damage tint on 26.3 |
| Performance | entity distance/culling, block entity culling, particle culling, FPS cap, dynamic render distance, smart animations, borderless | 800 | 400 | Particle limiter + dynamic render distance on both, entity distance on 1.8.9. Culling comes from the EntityCulling mod in the builds that have it |
| Audio | per-category volume, mute list, sound packs | 300 | 150 | Planned |
| Cosmetics | capes, wings/hats/bandanas/halos, emotes, kill/break/totem effects | 900 | 600 (models differ per version) | 26.3 has capes + wearables |
| Utility | waypoints/minimap, screenshot browser, replay recording, per-server profiles, macros [risk], account switcher | 2,000 | 700 | Account switcher: **done** (1.8.9, 26.3). 26.3 has minimap + waypoints |

**Replay recording** across 1.8.9 to latest is the largest single item: packet capture per protocol version plus a playback server. It isn't built; ReplayMod can be added to a build's manifest entry where Modrinth has it.

## 7. Rules we hold to

- **No cheats.** No X-ray, reach, aim assist or anything that changes what the server lets you do. Features are visual, UI or performance only.
- **No packet modification.** Nothing is sent to servers that vanilla wouldn't send.
- **Honour servers.** Minimap fair-play codes are respected. Features some servers ban (freelook, auto-tool, sprint reset, no hit delay, macros) are marked `[OPT-IN / RISK]` and default to off.
- **Shader packs.** Hooks modify values, never draw calls. With Iris or OptiFine active, the shader pack's own sky and fog take precedence, and the matrix notes where that happens.
