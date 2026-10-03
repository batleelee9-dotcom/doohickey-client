package dev.quartz.core;

import dev.quartz.core.accounts.LauncherBridge;
import dev.quartz.core.accounts.OfflineGuard;
import dev.quartz.core.config.ClientConfig;
import dev.quartz.core.env.EnvironmentModule;
import dev.quartz.core.env.EnvironmentSettings;
import dev.quartz.core.hud.Hud;
import dev.quartz.core.hud.HudData;
import dev.quartz.core.hud.HudEditor;
import dev.quartz.core.hud.HudElement;
import dev.quartz.core.hud.Input;
import dev.quartz.core.perf.Performance;
import dev.quartz.core.ui.HudOptions;
import dev.quartz.core.ui.WorldOptions;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;

/**
 * Headless checks of the version-independent core, with a fake version
 * adapter: no Minecraft needed.
 *
 * Run: java -cp versions/26.3/build/libs/quartz-client-*.jar;<gson.jar> core/src/test/java/dev/quartz/core/CoreTest.java
 */
public final class CoreTest {
	private static int failures;

	/** What a text element shows: draw it and read back the last string. */
	private static String textOf(FakeAdapter adapter, String id) {
		HudElement e = Hud.ELEMENTS.stream().filter(x -> x.id.equals(id)).findFirst().get();
		e.render(adapter.backend, false);
		return adapter.backend.lastText;
	}

	private static void check(boolean ok, String what) {
		System.out.println((ok ? "  ok   " : "  FAIL ") + what);
		if (!ok) {
			failures++;
		}
	}

	/** A 400x240 screen with 6-pixel-wide characters. */
	static final class FakeBackend implements RenderBackend {
		public Kind kind() { return Kind.LEGACY_OPENGL; }
		public int screenWidth() { return 400; }
		public int screenHeight() { return 240; }
		public void fill(int x0, int y0, int x1, int y1, int argb) { }
		String lastText = "";
		public int text(String text, int x, int y, int argb, boolean shadow) { lastText = text; return x + textWidth(text); }
		public int textWidth(String text) { return text.length() * 6; }
		public int fontHeight() { return 9; }
		public void push() { }
		public void pop() { }
		public void translate(float x, float y) { }
		public void scale(float factor) { }
		public void item(Object stack, int x, int y) { }
	}

	static final class FakeAdapter implements VersionAdapter {
		McVersion version = McVersion.V1_8_9;
		final Path config;
		final FakeBackend backend = new FakeBackend();

		FakeAdapter(Path config) { this.config = config; }
		public McVersion version() { return version; }
		public Path configDir() { return config; }
		public RenderBackend render() { return backend; }
		public boolean inWorld() { return true; }
		public String dimension() { return "minecraft:overworld"; }
		public String worldKey() { return "world:test"; }
		int fps = 144;
		int renderDistance = 12;
		public int fps() { return fps; }
		public int renderDistance() { return renderDistance; }
		public void setRenderDistance(int chunks) { renderDistance = chunks; }
		public boolean inputDown(Input input) { return input == Input.FORWARD; }
		public String inputLabel(Input input) { return input.name().substring(0, 1); }
		public int[] blockPosition() { return new int[] {10, 64, -20}; }
		public String facing() { return "N"; }
		public int ping() { return 42; }
		public java.util.List<HudData.Effect> effects() { return java.util.Collections.singletonList(new HudData.Effect("Speed II", "1:30", false)); }
		public java.util.List<HudData.Item> armor() { return java.util.Collections.emptyList(); }
		double[] position = {0, 64, 0};
		public double[] position() { return position; }
		public float yaw() { return 225f; }
		public long worldTime() { return 24000L * 12 + 6000; }
		public float saturation() { return 5f; }
		public int arrows() { return 0; }
		public String targetBlock() { return "Stone"; }
		public String biome() { return "Plains"; }
		int hurt;
		public int hurtTime() { return hurt; }
		boolean hitboxes;
		public boolean hitboxes() { return hitboxes; }
		public void setHitboxes(boolean shown) { hitboxes = shown; }
		boolean zoomKey;
		public boolean zoomKeyDown() { return zoomKey; }
		public void openMenu() { }
		public void notifyPlayer(String message) { }
		public void runOnMainThread(Runnable task) { task.run(); }
		public String sessionName() { return "Tester"; }
		public String sessionUuid() { return "00000000000000000000000000000000"; }
		boolean offline;
		public boolean sessionOffline() { return offline; }
		public boolean canSwitchSession() { return true; }
		public void switchSession(LauncherBridge.Session session) { }
	}

	public static void main(String[] args) throws Exception {
		Path game = Files.createTempDirectory("quartz-core-test");
		Path config = game.resolve("config");
		Files.createDirectories(config);
		// A schema-1 file from Doohickey Client 1.0, with the old separate minimap switch.
		Files.write(config.resolve("quartz-client.json"),
			("{\"minimap\": false, \"cape\": \"aurora\", \"modules\": "
				+ "{\"fps\": {\"enabled\": true, \"x\": 0.5, \"y\": 0.5, \"scale\": 1.0}}}").getBytes(StandardCharsets.UTF_8));

		FakeAdapter adapter = new FakeAdapter(config);
		Quartz.init(adapter);

		System.out.println("config migration");
		Path file = config.resolve("quartz").resolve("client.json");
		String json = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
		check(json.contains("\"schemaVersion\": 2"), "upgraded to schema 2");
		check(!json.contains("\"minimap\": false"), "old minimap switch removed");
		check(!ClientConfig.get().modules.get("minimap").enabled, "and folded into the minimap module");
		check("aurora".equals(ClientConfig.get().cape), "other settings kept");
		check(Files.exists(config.resolve("quartz-client.json.migrated")) && !Files.exists(config.resolve("quartz-client.json")),
			"old file kept as .migrated");
		check(Files.exists(config.resolve("quartz").resolve("compat-1.8.9.txt")), "compatibility report written on first launch");

		System.out.println("compatibility matrix");
		check(CompatRegistry.available(Feature.VOID_FOG_REMOVAL, McVersion.V1_8_9), "void fog removal on 1.8.9");
		check(!CompatRegistry.available(Feature.VOID_FOG_REMOVAL, McVersion.V26_3), "no void fog removal on 26.3 (removed in 1.18)");
		check(!CompatRegistry.available(Feature.SKY_COLOR, McVersion.V1_12_2), "nothing available on versions without a build");
		check(Hud.available().size() == 20 && HudOptions.all().size() == 20, "20 shared HUD elements on 1.8.9");
		check(WorldOptions.all().size() == 7, "7 World options on 1.8.9");
		adapter.version = McVersion.V26_3;
		check(WorldOptions.all().size() == 6, "6 World options on 26.3 (void fog hidden)");
		adapter.version = McVersion.V1_8_9;

		System.out.println("HUD");
		HudElement fps = Hud.ELEMENTS.get(0);
		Hud.Placement p = Hud.place(fps, adapter.backend);
		check(p.w == 56 && p.h == 16, "FPS box is 56x16");
		check(p.x == Math.round(0.5f * (400 - 56)) && p.y == Math.round(0.5f * (240 - 16)), "placed from the migrated fractions");
		HudEditor editor = new HudEditor();
		check(editor.press(adapter.backend, p.x + 5, p.y + 5), "editor picks up the element under the cursor");
		editor.drag(adapter.backend, 6, 300);
		editor.release();
		p = Hud.place(fps, adapter.backend);
		check(p.x == 0 && p.y == 240 - 16, "dragging snaps to the bottom-left edges");
		check(editor.scroll(adapter.backend, p.x + 1, p.y + 1, 120), "scrolling over it resizes");
		check(Math.abs(fps.state().scale - 1.1f) < 0.001, "by 10%");
		HudElement ping = Hud.available().stream().filter(e -> e.id.equals("ping")).findFirst().get();
		check(ping.width(adapter.backend) == Hud.place(ping, adapter.backend).w, "ping box measured");
		HudElement armor = Hud.available().stream().filter(e -> e.id.equals("armor")).findFirst().get();
		check(!armor.hasContent(), "armor hidden with nothing worn");
		HudElement potions = Hud.available().stream().filter(e -> e.id.equals("potions")).findFirst().get();
		check(potions.hasContent() && potions.height(adapter.backend) == 15, "one potion line");
		dev.quartz.core.hud.ReachTracker.record(3.14159);
		check(dev.quartz.core.hud.ReachTracker.last() > 3.14 && dev.quartz.core.hud.ReachTracker.last() < 3.15, "reach recorded");

		System.out.println("new HUD elements");
		check(dev.quartz.core.hud.PlayerStats.combo() == 1, "a landed hit starts a combo");
		dev.quartz.core.hud.ReachTracker.record(2.5);
		check(dev.quartz.core.hud.PlayerStats.combo() == 2, "and the next one extends it");
		dev.quartz.core.hud.PlayerStats.tick(adapter);
		adapter.position = new double[] {0.5, 64, 0};
		dev.quartz.core.hud.PlayerStats.tick(adapter);
		check(Math.abs(dev.quartz.core.hud.PlayerStats.speed() - 10.0) < 0.001, "half a block a tick is 10 m/s");
		adapter.hurt = 10;
		dev.quartz.core.hud.PlayerStats.tick(adapter);
		check(dev.quartz.core.hud.PlayerStats.combo() == 0, "taking damage ends the combo");
		check(textOf(adapter, "direction").equals("NE  45°"), "yaw 225 reads as north-east");
		check(textOf(adapter, "day").equals("Day 12"), "day counter");
		check(textOf(adapter, "block").equals("Stone"), "block info");

		System.out.println("zoom and hitboxes");
		check(dev.quartz.core.pvp.Zoom.apply(70f) == 70f, "no zoom without the key");
		adapter.zoomKey = true;
		ClientConfig.get().zoomSmooth = false;
		check(dev.quartz.core.pvp.Zoom.apply(70f) == 17.5f, "4x zoom divides the FOV by 4");
		adapter.zoomKey = false;
		check(dev.quartz.core.pvp.Zoom.apply(70f) == 70f, "and lets go when the key does");
		dev.quartz.core.ui.Option hitboxes = dev.quartz.core.ui.GameOptions.all().stream()
			.filter(o -> o.feature == Feature.HITBOXES).findFirst().get();
		hitboxes.click();
		check(adapter.hitboxes, "the Hitboxes option switches vanilla's hitbox view");

		System.out.println("world controls");
		EnvironmentSettings env = ClientConfig.get().environment;
		check(EnvironmentModule.skyColor() == EnvironmentModule.VANILLA, "vanilla sky by default");
		env.lockTime = true;
		env.timeOfDay = 6000;
		check(EnvironmentModule.clock(5 * 24000L + 1234) == 5 * 24000L + 6000, "time lock keeps the day, sets noon");
		env.weather = EnvironmentSettings.Weather.RAIN;
		check(EnvironmentModule.rainLevel(0f) == 1f && EnvironmentModule.thunderLevel(0.7f) == 0f, "rain override");
		env.fog = EnvironmentSettings.Fog.OFF;
		check(EnvironmentModule.fogStart(160, 120) > 100000, "fog off pushes fog past view distance");

		System.out.println("hot reload");
		ClientConfig.get().save();
		String edited = new String(Files.readAllBytes(file), StandardCharsets.UTF_8).replace("\"RAIN\"", "\"THUNDER\"");
		Files.write(file, edited.getBytes(StandardCharsets.UTF_8));
		Files.setLastModifiedTime(file, FileTime.fromMillis(System.currentTimeMillis() + 5000));
		ClientConfig before = ClientConfig.get();
		before.pollReload();
		check(ClientConfig.get() == before, "same config object after reload");
		check(EnvironmentModule.thunderLevel(0f) == 1f, "outside edit applied (weather is now thunder)");

		System.out.println("offline accounts");
		check(OfflineGuard.check("mc.hypixel.net") == null, "online session may join public servers");
		adapter.offline = true;
		check(OfflineGuard.check("mc.hypixel.net") != null, "offline session can't join a public server");
		check(OfflineGuard.check("192.168.1.20:25565") == null && OfflineGuard.check("localhost") == null, "but can join LAN and local servers");
		check(!OfflineGuard.isLanAddress("172.32.0.1") && OfflineGuard.isLanAddress("[fd12::1]:25565"), "private ranges match the launcher's rule");
		adapter.offline = false;

		System.out.println("performance");
		ClientConfig.get().particlePercent = 0;
		check(!Performance.keepParticle(), "particles off drops every particle");
		ClientConfig.get().particlePercent = 100;
		check(Performance.keepParticle(), "particles at 100% keeps them");
		ClientConfig.get().performance.entityDistance = 32;
		check(Performance.drawEntity(31 * 31) && !Performance.drawEntity(33 * 33), "entity distance cuts off past 32 blocks");
		ClientConfig.get().performance.dynamicRenderDistance = true;
		adapter.fps = 20;
		Performance.tick(adapter);
		Thread.sleep(2100);
		Performance.tick(adapter);
		check(adapter.renderDistance == 11, "low FPS for 2 s lowers the render distance by one");
		ClientConfig.get().performance.dynamicRenderDistance = false;
		Performance.tick(adapter);
		check(adapter.renderDistance == 12, "switching off restores the player's distance");

		System.out.println("crash guard");
		int[] calls = {0};
		for (int i = 0; i < 5; i++) {
			int v = Safe.call("test.hook", () -> {
				calls[0]++;
				throw new IllegalStateException("boom");
			}, 42);
			check(v == 42, "failing hook call " + (i + 1) + " falls back to vanilla");
		}
		check(calls[0] == 3, "hook switched off after 3 failures");

		System.out.println(failures == 0 ? "ALL PASSED" : failures + " FAILED");
		System.exit(failures == 0 ? 0 : 1);
	}
}
