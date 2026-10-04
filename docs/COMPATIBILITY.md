# Doohickey Client compatibility matrix

The table is the output of [`CompatRegistry.java`](../client-mod/core/src/main/java/dev/quartz/core/CompatRegistry.java), so it can't drift from what the client actually does. Print it with:

```bash
java -cp client-mod/versions/26.3/build/libs/quartz-client-1.0.0+26.3.jar dev.quartz.core.CompatRegistry
```

✅ possible · ⚠ possible with limits · ❌ not possible · N/A doesn't apply · **●** built and shipping today.
Only features marked ● appear in the in-game menu; everything else is hidden rather than shown broken.

| Feature | 1.8.9 | 1.12.2 | 1.16.5 | 1.18.2 | 1.20.1 | 1.21.x | 26.3 | Notes |
|---|---|---|---|---|---|---|---|---|
| **WORLD** | | | | | | | | |
| Sky colour | ✅ ● | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ ● |  |
| Atmosphere: custom skies and ambient weather | ✅ ● | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ ● | Shader packs draw their own sky and take precedence. |
| Client-side time | ✅ ● | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ ● | Visual only: the server's time, mob spawning and crops are unaffected. |
| Fog distance | ✅ ● | ✅ | ✅ | ⚠ | ✅ | ✅ | ✅ ● | 1.18.2 sets shader fog through RenderSystem, which shader packs override. |
| Fog colour | ✅ ● | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ ● |  |
| Weather override | ✅ ● | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ ● | Visual only: the server still decides real weather (crops, lightning). |
| Fullbright | ✅ ● | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ ● | 1.19+ clamps gamma, so newer versions brighten through the lightmap. |
| Cloud control | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |  |
| Sun, moon and stars | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |  |
| Remove void fog | ✅ ● | ✅ | ⚠ | ❌ | ❌ | ❌ | ❌ | 1.16.5 only darkens the sky near bedrock; 1.18 removed void fog entirely. |
| Particle control | ✅ ● | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ ● |  |
| View and hand bobbing | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |  |
| Hurt camera shake | ✅ ● | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ ● |  |
| Damage tint | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ ● |  |
| Custom crosshair | ✅ ● | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ ● |  |
| Dynamic crosshair | ⚠ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | 1.8.9 has no attack cooldown; only bow charge can be shown. |
| Block outline | ✅ | ✅ | ✅ | ⚠ | ⚠ | ⚠ | ⚠ | Core-profile OpenGL (1.17+) can't draw lines thicker than 1 px on every GPU. |
| Item physics / 2D items | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |  |
| Chunk borders | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |  |
| Light level overlay | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |  |
| TNT countdown | ✅ ● | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ ● | A timer over lit TNT within 24 blocks, from its own fuse. |
| Low fire and no pumpkin blur | ✅ ● | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ ● |  |
| **HUD** | | | | | | | | |
| Draggable HUD editor | ✅ ● | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ ● |  |
| FPS | ✅ ● | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ ● |  |
| CPS | ✅ ● | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ ● |  |
| Ping | ✅ ● | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ ● |  |
| TPS | ⚠ | ⚠ | ⚠ | ⚠ | ⚠ | ⚠ | ⚠ | Servers don't report TPS; it's estimated from world-time packets. |
| Coordinates and facing | ✅ ● | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ ● |  |
| Biome and dimension | ✅ ● | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ ● |  |
| Keystrokes | ✅ ● | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ ● |  |
| Armor status | ✅ ● | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ ● |  |
| Potion effects | ✅ ● | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ ● |  |
| Reach display | ✅ ● | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ ● | Measured from your own hits; purely informational. |
| Combo counter | ✅ ● | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ ● |  |
| Scoreboard | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |  |
| Chat customization | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |  |
| Item and arrow counter | ✅ ● | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ ● |  |
| Ping and FPS graphs | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |  |
| Clock and session timer | ✅ ● | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ ● |  |
| Server address | ✅ ● | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ ● |  |
| Memory usage | ✅ ● | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ ● |  |
| Direction / compass | ✅ ● | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ ● |  |
| Speedometer | ✅ ● | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ ● |  |
| Day counter | ✅ ● | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ ● |  |
| Saturation | ✅ ● | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ ● |  |
| Block info | ✅ ● | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ ● |  |
| Ping numbers in the tab list | ✅ ● | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ ● |  |
| Smooth hotbar | ✅ ● | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ ● |  |
| **PVP** | | | | | | | | |
| Toggle sprint and sneak | ✅ ● | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ ● |  |
| Sprint reset on hit [OPT-IN / RISK] | ✅ | N/A | N/A | N/A | N/A | N/A | N/A | Automates a combat technique; many servers treat that as a macro. |
| 1.7/1.8 animations | N/A | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | 1.8.9 already has the old animations. |
| 1.7 blockhit animation | ✅ | ⚠ | ⚠ | ⚠ | ⚠ | ⚠ | ⚠ | Sword blocking was removed in 1.9; newer versions can only restyle the swing. |
| Hit colour | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ ● |  |
| No hit delay [OPT-IN / RISK] | ✅ | N/A | N/A | N/A | N/A | N/A | N/A | Removes 1.8.9's click delay after a miss; a gameplay change some servers ban. |
| Hitboxes | ✅ ● | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ ● |  |
| Custom name tags (health, armour) | ✅ ● | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ ● | Replaces vanilla player tags only where vanilla would show one (teams, sneaking and invisibility still apply). |
| Auto tool / weapon [OPT-IN / RISK] | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | Switches hotbar slots for you; forbidden on many PvP servers. |
| Raw mouse input | ⚠ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | 1.8.9 (LWJGL 2) needs a separate raw-input library. |
| Zoom | ✅ ● | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ ● |  |
| Aspect ratio (stretched) | ✅ ● | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ ● | Stretches the 3D view only; the HUD and menus keep their shape. |
| Freelook [OPT-IN / RISK] | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | Some servers (e.g. Hypixel) ask clients to disable it. |
| Ping-based reach display | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | Display only; never changes reach. |
| No speed FOV | ✅ ● | ✅ | ✅ | ✅ | ✅ | ✅ | N/A | 26.3 has this built in (FOV Effects slider). |
| Mouse delay fix | ✅ ● | N/A | N/A | N/A | N/A | N/A | N/A | A 1.8 bug (aim lagging a tick behind the crosshair); fixed in later versions. |
| **PERFORMANCE** | | | | | | | | |
| Sodium / Iris integration | N/A | N/A | ✅ | ✅ | ✅ | ✅ | ✅ | Sodium exists from 1.16. |
| Entity render distance | ✅ ● | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |  |
| Entity culling | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |  |
| Block entity culling | ✅ ● | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |  |
| Particle culling | ✅ ● | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |  |
| Chunk update throttling | ✅ | ✅ | ✅ | ⚠ | ⚠ | ⚠ | ⚠ | 1.18+ schedules chunk builds itself; only the budget can be tuned. |
| Max FPS preset (no vsync or cap, fast graphics) | ✅ ● | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ ● |  |
| Dynamic render distance | ✅ ● | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ ● |  |
| Smart animations | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |  |
| Borderless fullscreen | ⚠ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | LWJGL 2 has no borderless mode; 1.8.9 needs a window-style workaround. |
| Multithreaded chunk building | ⚠ | N/A | N/A | N/A | N/A | N/A | N/A | 1.8.9 already builds chunks on worker threads; Doohickey can only tune the count. |
| **AUDIO** | | | | | | | | |
| Per-category volume | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |  |
| Custom sound packs | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |  |
| Sound positioning | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |  |
| Mute specific sounds | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |  |
| Hit and kill sounds | ✅ ● | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ ● | Played on your client only. |
| Low health alert | ✅ ● | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ ● |  |
| **COSMETICS** | | | | | | | | |
| Capes | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ ● | Client-side: only you see them without a cosmetics server. |
| Wings, hats, bandanas, halos | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ ● | Client-side: only you see them without a cosmetics server. |
| Rice hat | ✅ ● | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ ● | Client-side: only you see it. |
| Emotes | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |  |
| Kill effects | ✅ ● | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ ● |  |
| Block-break particles | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |  |
| Totem pop effect | N/A | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | Totems arrived in 1.11. |
| Hit effects | ✅ ● | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ ● | Client-side particles: only you see them. |
| Particle trails | ✅ ● | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ ● | Client-side particles: only you see them. |
| **UTILITY** | | | | | | | | |
| Minimap and waypoints | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ ● | Honours servers' minimap/fair-play codes; no entity radar. |
| Screenshot manager | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |  |
| Replay recording | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | Recording is client-side; some servers forbid it. |
| Macros / scripted keybinds [OPT-IN / RISK] | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | Automating input breaks the rules of most servers. |
| Per-server config profiles | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |  |
| HUD profiles | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |  |
| In-game account switcher | ✅ ● | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ ● | Switches between accounts added to the Doohickey launcher. |
