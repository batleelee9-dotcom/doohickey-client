# Doohickey Client

The in-game half of Doohickey: one codebase, one jar per Minecraft version.

| Version | Loader | What it has |
|---|---|---|
| 26.3 | Fabric | HUD (10 modules), PvP tweaks, minimap and waypoints, cosmetics, World controls incl. fullbright, dynamic render distance, account switcher |
| 1.8.9 | Legacy Fabric | HUD (FPS, CPS, ping, coordinates, reach, keystrokes, potions, armor) with the drag editor, World controls incl. fullbright and void-fog removal, toggle sprint/sneak, particle limiter, entity and dynamic render distance, account switcher |

The launcher embeds every jar and loads the right one into each build that supports it, so you never install it by hand.

Build with any JDK 25 (the launcher's own `java-runtime-epsilon` works). It also produces the Java 8 bytecode for 1.8.9.

```bash
./gradlew build          # every version → versions/<mc>/build/libs/quartz-client-<version>+<mc>.jar
./gradlew :1.8.9:build   # just one
```

Test the shared core without Minecraft, using a fake version adapter:

```bash
java -cp "versions/26.3/build/libs/quartz-client-1.0.0+26.3.jar;<gson jar>" core/src/test/java/dev/quartz/core/CoreTest.java
```

In game:

- **Right Shift** opens the Doohickey menu.
- The **Account** button on the title, multiplayer and pause screens switches accounts (outside a world). Offline accounts are singleplayer/LAN only.
- Settings live in `config/quartz/client.json`, and edits made outside the game apply within a second.

Architecture, the version abstraction and the plan are in [../docs/CLIENT.md](../docs/CLIENT.md). The feature × version matrix is in [../docs/COMPATIBILITY.md](../docs/COMPATIBILITY.md).
