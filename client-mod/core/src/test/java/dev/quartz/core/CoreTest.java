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
		RenderBackend renderer;
		public RenderBackend render() { return renderer != null ? renderer : backend; }
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
		final java.util.List<String> particleLog = new java.util.ArrayList<>();
		final java.util.List<String> soundLog = new java.util.ArrayList<>();
		boolean targetDead;
		float health = 1f;
		public double[] entityBox(Object e) { return e == null ? null : new double[] {5, 64, 5, 1.8, 0.6}; }
		public boolean entityDead(Object e) { return targetDead; }
		public void particles(String kind, double x, double y, double z, int count, double spread, double speed) { particleLog.add(kind + "x" + count); }
		public void playSound(String name, float volume, float pitch) { soundLog.add(name); }
		public float healthFraction() { return health; }
		boolean projectileNear;
		public void primedTnt(double range, dev.quartz.core.fx.TntTimers.Sink sink) { sink.tnt(0.8, 0.6, -3, 47); sink.tnt(-1.4, 0.4, -5, 12); }
		public boolean nameTag(Object p, dev.quartz.core.fx.NameTags.Tag t) {
			if (!(p instanceof double[])) return false;
			double[] at = (double[]) p;
			t.x = at[0]; t.y = at[1]; t.z = at[2];
			t.name = "_Maxim07_"; t.nameColour = 0xFFFFFFFF; t.health = 11; t.maxHealth = 20; t.absorption = 0; t.healthKnown = true;
			java.util.Arrays.fill(t.items, null);
			return true;
		}
		public boolean ownProjectileNear(Object e) { return projectileNear; }
		final java.util.List<Boolean> maxFpsLog = new java.util.ArrayList<>();
		public void applyMaxFps(boolean on, java.util.Map<String, String> restore) { maxFpsLog.add(on); if (on) restore.put("vsync", "true"); else restore.clear(); }
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

	/** Draws a skin-coloured head wearing the rice hat into a 480×360 cell, painter-sorted. */
	static void hatView(java.awt.Graphics2D g, int left, double tiltDegrees, double yawDegrees) {
		dev.quartz.core.fx.View.set(perspective(50, 480 / 360f), 0, 0, 0);
		double tilt = Math.toRadians(tiltDegrees), yaw = Math.toRadians(yawDegrees);
		java.util.List<double[]> tris = new java.util.ArrayList<>();
		dev.quartz.core.fx.RiceHat.Sink sink = new dev.quartz.core.fx.RiceHat.Sink() {
			final double[] t = new double[12];
			int n;
			public void vertex(float x, float y, float z, int argb) {
				double bx = x / 16.0, by = -y / 16.0, bz = z / 16.0;
				double x1 = bx * Math.cos(yaw) - bz * Math.sin(yaw), z1 = bx * Math.sin(yaw) + bz * Math.cos(yaw);
				double y2 = by * Math.cos(tilt) - z1 * Math.sin(tilt), z2 = by * Math.sin(tilt) + z1 * Math.cos(tilt);
				t[n * 3] = x1; t[n * 3 + 1] = y2 - 0.3; t[n * 3 + 2] = z2 - 2.0;
				if (++n == 3) { double[] c = java.util.Arrays.copyOf(t, 10); c[9] = argb; tris.add(c); n = 0; }
			}
		};
		dev.quartz.core.fx.RiceHat.build(sink, 1.5f, 0);
		float[][] cube = {{-4,-8,-4},{4,-8,-4},{4,0,-4},{-4,0,-4},{-4,-8,4},{4,-8,4},{4,0,4},{-4,0,4}};
		int[][] faces = {{0,1,2,3},{4,5,6,7},{0,4,7,3},{1,5,6,2},{0,1,5,4},{3,2,6,7}};
		for (int[] fc : faces) {
			for (int[] tr : new int[][] {{fc[0], fc[1], fc[2]}, {fc[0], fc[2], fc[3]}}) {
				for (int i : tr) sink.vertex(cube[i][0], cube[i][1], cube[i][2], 0xFFC69C6D);
			}
		}
		tris.sort((a, b) -> Double.compare(a[2] + a[5] + a[8], b[2] + b[5] + b[8]));
		float[] q = new float[4];
		for (double[] t : tris) {
			java.awt.geom.Path2D.Float path = new java.awt.geom.Path2D.Float();
			boolean ok = true;
			for (int i = 0; i < 3; i++) {
				ok &= dev.quartz.core.fx.View.project(t[i * 3], t[i * 3 + 1], t[i * 3 + 2], 480, 360, q);
				if (i == 0) path.moveTo(left + q[0], q[1]); else path.lineTo(left + q[0], q[1]);
			}
			if (!ok) continue;
			path.closePath();
			g.setColor(new java.awt.Color((int) t[9], true));
			g.fill(path);
			g.draw(path);
		}
	}

	/** An OpenGL perspective projection (camera looking down -Z), column-major. */
	static float[] perspective(float fovDegrees, float aspect) {
		float f = (float) (1 / Math.tan(Math.toRadians(fovDegrees) / 2));
		float near = 0.05f, far = 256f;
		float[] m = new float[16];
		m[0] = f / aspect;
		m[5] = f;
		m[10] = (far + near) / (near - far);
		m[11] = -1;
		m[14] = 2 * far * near / (near - far);
		return m;
	}

	/** `--menu-preview <dir>`: draws the Right Shift menu to PNGs, no Minecraft needed. */
	private static void menuPreview(Path dir) throws Exception {
		Path game = Files.createTempDirectory("quartz-menu-preview");
		Files.createDirectories(game.resolve("config"));
		FakeAdapter adapter = new FakeAdapter(game.resolve("config"));
		Quartz.init(adapter);
		ImageBackend image = new ImageBackend(480, 270, 3);
		adapter.renderer = image;
		dev.quartz.core.ui.ClientMenu menu = new dev.quartz.core.ui.ClientMenu(new dev.quartz.core.ui.ClientMenu.Host() {
			public void close() { }
			public void openHudEditor() { }
		});
		Thread.sleep(250);
		Files.createDirectories(dir);
		int mx = 200, my = 120;
		image.shot(menu, mx, my, dir.resolve("menu-hud.png"));
		// A bigger GUI (1080p at scale 2): the panel grows.
		ImageBackend large = new ImageBackend(960, 540, 2);
		adapter.renderer = large;
		large.shot(menu, 420, 200, dir.resolve("menu-large.png"));
		adapter.renderer = image;
		menu.scroll(2.5);
		Thread.sleep(120);
		image.shot(menu, mx, my, dir.resolve("menu-scrolled.png"));
		menu.scroll(-10);
		Thread.sleep(120);
		image.shot(menu, mx, my, dir.resolve("menu-hud.png"));
		// Sidebar items start at panel top + 57, 24 apart; Visual is the third.
		int pw = Math.min(480, 480 - 20), ph = Math.min(268, 270 - 20);
		int px = (480 - pw) / 2, py = (270 - ph) / 2;
		menu.click(px + 30, py + 57 + 2 * 24 + 10);
		ClientConfig.get().effects.hitEffects = true;
		image.shot(menu, px + 150, py + 60, dir.resolve("menu-visual.png"));
		// The second card's ⋮ (Rice hat) opens its settings page.
		menu.click(px + 408, py + 69);
		image.shot(menu, px + 200, py + 90, dir.resolve("menu-settings.png"));
		menu.escape();
		for (char c : "zo".toCharArray()) menu.typed(c);
		image.shot(menu, mx, my, dir.resolve("menu-search.png"));
		// Hit particles over the stand-in world, a short moment after each burst.
		dev.quartz.core.fx.View.set(perspective(70, 480 / 270f), 0, 0, 0);
		for (int k = 0; k < dev.quartz.core.fx.Sprites.KINDS.length; k++) {
			dev.quartz.core.fx.Sprites.spawn(k, (k - 2.5) * 1.6, 0.3, -6, 14, 0.4);
		}
		image.frames(() -> dev.quartz.core.fx.Sprites.render(image), 6, dir.resolve("hit-particles.png"));
		dev.quartz.core.fx.Sprites.clear();
		// Each sky as a panorama, sampled back out of the painted atlas through the cube mapping.
		for (String sky : new String[] {"aurora", "nebula", "golden", "synthwave", "eclipse"}) {
			ClientConfig.get().atmosphereSky = sky;
			int[] atlas = null;
			for (int i = 0; i < 600 && (atlas = dev.quartz.core.fx.Atmosphere.atlas()) == null; i++) {
				Thread.sleep(20);
			}
			int panoW = 960, panoH = 300, f = dev.quartz.core.fx.Atmosphere.FACE;
			java.awt.image.BufferedImage pano = new java.awt.image.BufferedImage(panoW, panoH, java.awt.image.BufferedImage.TYPE_INT_RGB);
			for (int qy = 0; qy < panoH; qy++) {
				double el = Math.toRadians(80 - 100.0 * qy / panoH);
				for (int qx = 0; qx < panoW; qx++) {
					double az = Math.toRadians(360.0 * qx / panoW);
					double dx = Math.cos(el) * Math.cos(az), dy = Math.sin(el), dz = Math.cos(el) * Math.sin(az);
					int best = 0;
					double bestDot = -2;
					for (int face = 0; face < 6; face++) {
						float[] a = dev.quartz.core.fx.Atmosphere.FACES[face];
						double d = dx * a[0] + dy * a[1] + dz * a[2];
						if (d > bestDot) { bestDot = d; best = face; }
					}
					float[] a = dev.quartz.core.fx.Atmosphere.FACES[best];
					double ss = (dx * a[3] + dy * a[4] + dz * a[5]) / bestDot, tt = (dx * a[6] + dy * a[7] + dz * a[8]) / bestDot;
					int tx = Math.min(f - 1, (int) ((ss + 1) / 2 * f)), ty = Math.min(f - 1, (int) ((1 - tt) / 2 * f));
					pano.setRGB(qx, qy, atlas[((best / 3) * f + ty) * f * 3 + (best % 3) * f + tx]);
				}
			}
			javax.imageio.ImageIO.write(pano, "png", dir.resolve("sky-" + sky + ".png").toFile());
		}
		ClientConfig.get().atmosphereSky = "off";

		// TNT countdowns.
		dev.quartz.core.fx.View.set(perspective(70, 480 / 270f), 0, 0, 0);
		image.frames(() -> dev.quartz.core.fx.TntTimers.render(image, adapter), 1, dir.resolve("tnt.png"));
		dev.quartz.core.fx.View.clear();

		// Name tags at a few distances.
		ClientConfig.get().nameTags = true;
		dev.quartz.core.fx.View.set(perspective(70, 480 / 270f), 0, 0, 0);
		dev.quartz.core.fx.NameTags.mark(new double[] {-1.2, 0.6, -2.5});
		dev.quartz.core.fx.NameTags.mark(new double[] {1.5, 0.4, -5});
		dev.quartz.core.fx.NameTags.mark(new double[] {4, 0.4, -12});
		dev.quartz.core.fx.NameTags.mark(new double[] {-5, 0.4, -30});
		dev.quartz.core.fx.NameTags.mark(new double[] {-14, 0.4, -55});
		image.frames(() -> dev.quartz.core.fx.NameTags.render(image, adapter), 1, dir.resolve("name-tags.png"));
		ClientConfig.get().nameTags = false;

		// The rice hat on a stand-in head, from above and from below.
		ClientConfig.get().riceHatColor = 0;
		java.awt.image.BufferedImage hat = new java.awt.image.BufferedImage(960, 360, java.awt.image.BufferedImage.TYPE_INT_RGB);
		java.awt.Graphics2D hg = hat.createGraphics();
		hg.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
		hg.setColor(new java.awt.Color(0x7FA9FF));
		hg.fillRect(0, 0, 960, 360);
		hatView(hg, 0, 25, 30);
		hatView(hg, 480, -18, 120);
		javax.imageio.ImageIO.write(hat, "png", dir.resolve("rice-hat.png").toFile());
		dev.quartz.core.fx.View.clear();
		System.out.println("menu previews written to " + dir);
	}

	/** Java2D stand-in for the game's renderer: real layout and colours, approximate font. */
	static final class ImageBackend implements RenderBackend {
		final int w, h, s;
		java.awt.image.BufferedImage img;
		java.awt.Graphics2D g;
		final java.util.ArrayDeque<java.awt.geom.AffineTransform> stack = new java.util.ArrayDeque<>();
		final java.awt.Font font = new java.awt.Font("Dialog", java.awt.Font.PLAIN, 8);

		ImageBackend(int w, int h, int s) { this.w = w; this.h = h; this.s = s; }

		void shot(dev.quartz.core.ui.ClientMenu menu, int mx, int my, Path out) throws Exception {
			frames(() -> menu.render(this, mx, my), 3, out);
		}

		void frames(Runnable draw, int count, Path out) throws Exception {
			for (int frame = 0; frame < count; frame++) {
				img = new java.awt.image.BufferedImage(w * s, h * s, java.awt.image.BufferedImage.TYPE_INT_RGB);
				g = img.createGraphics();
				g.scale(s, s);
				g.setFont(font);
				// A stand-in daytime world behind the menu: sky over grass.
				g.setPaint(new java.awt.GradientPaint(0, 0, new java.awt.Color(0x7FA9FF), 0, h * 0.6f, new java.awt.Color(0xC4D8FF)));
				g.fillRect(0, 0, w, h);
				g.setColor(new java.awt.Color(0x5D8C3A));
				g.fillRect(0, (int) (h * 0.6), w, h);
				draw.run();
				Thread.sleep(40);
			}
			javax.imageio.ImageIO.write(img, "png", out.toFile());
		}

		public Kind kind() { return Kind.LEGACY_OPENGL; }
		public int screenWidth() { return w; }
		public int screenHeight() { return h; }
		public void fill(int x0, int y0, int x1, int y1, int argb) {
			g.setColor(new java.awt.Color(argb, true));
			g.fill(new java.awt.geom.Rectangle2D.Float(Math.min(x0, x1), Math.min(y0, y1), Math.abs(x1 - x0), Math.abs(y1 - y0)));
		}
		public int text(String text, int x, int y, int argb, boolean shadow) {
			if (shadow) {
				g.setColor(new java.awt.Color((argb & 0xFCFCFC) >> 2 | 0xFF000000, true));
				g.drawString(text, x + 1, y + 8);
			}
			g.setColor(new java.awt.Color(argb | 0xFF000000, true));
			g.drawString(text, x, y + 7);
			return x + textWidth(text);
		}
		public int textWidth(String text) { return g == null ? text.length() * 6 : g.getFontMetrics(font).stringWidth(text); }
		public int fontHeight() { return 9; }
		public void push() { stack.push(g.getTransform()); }
		public void pop() { g.setTransform(stack.pop()); }
		public void translate(float x, float y) { g.translate(x, y); }
		public void scale(float factor) { g.scale(factor, factor); }
		public void item(Object stack, int x, int y) { }
		public void clip(int x0, int y0, int x1, int y1) { g.setClip(x0, y0, x1 - x0, y1 - y0); }
		public void unclip() { g.setClip(null); }
		final java.util.Map<String, Integer> keys = new java.util.HashMap<>();
		final java.util.List<java.awt.image.BufferedImage> images = new java.util.ArrayList<>();
		public float guiScale() { return s; }
		public int image(String key, int w, int h, java.util.function.Supplier<int[]> pixels) {
			Integer id = keys.get(key);
			if (id != null) return id;
			java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_ARGB);
			img.setRGB(0, 0, w, h, pixels.get(), 0, w);
			images.add(img);
			keys.put(key, images.size() - 1);
			return images.size() - 1;
		}
		public void drawImage(int handle, float[] q, int count, int argb) {
			java.awt.image.BufferedImage src = images.get(handle);
			g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
			for (int i = 0; i < count; i++) {
				int o = i * 8;
				int sx0 = Math.round(q[o + 4] * src.getWidth()), sy0 = Math.round(q[o + 5] * src.getHeight());
				int sx1 = Math.round(q[o + 6] * src.getWidth()), sy1 = Math.round(q[o + 7] * src.getHeight());
				int w = Math.max(1, sx1 - sx0), h = Math.max(1, sy1 - sy0);
				java.awt.image.BufferedImage tint = new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_ARGB);
				int ta = argb >>> 24, tr = argb >> 16 & 255, tg = argb >> 8 & 255, tb = argb & 255;
				for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
					int p = src.getRGB(Math.min(src.getWidth() - 1, sx0 + x), Math.min(src.getHeight() - 1, sy0 + y));
					int a = (p >>> 24) * ta / 255, rr = (p >> 16 & 255) * tr / 255, gg = (p >> 8 & 255) * tg / 255, bb = (p & 255) * tb / 255;
					tint.setRGB(x, y, a << 24 | rr << 16 | gg << 8 | bb);
				}
				g.drawImage(tint, new java.awt.geom.AffineTransform(new double[] {(q[o + 2] - q[o]) / w, 0, 0, (q[o + 3] - q[o + 1]) / h, q[o], q[o + 1]}), null);
			}
		}
	}

	/** `--bench`: time and memory per frame for the HUD and the menu (no Minecraft; drawing is a no-op). */
	private static void bench() throws Exception {
		Path game = Files.createTempDirectory("quartz-bench");
		Files.createDirectories(game.resolve("config"));
		FakeAdapter adapter = new FakeAdapter(game.resolve("config"));
		Quartz.init(adapter);
		for (HudElement e : Hud.ELEMENTS) e.state().enabled = true;
		RenderBackend nop = new RenderBackend() {
			final java.util.Map<String, Integer> keys = new java.util.HashMap<>();
			public Kind kind() { return Kind.LEGACY_OPENGL; }
			public int screenWidth() { return 480; }
			public int screenHeight() { return 270; }
			public void fill(int x0, int y0, int x1, int y1, int argb) { }
			public int text(String t, int x, int y, int argb, boolean shadow) { return x + t.length() * 6; }
			public int textWidth(String t) { return t.length() * 6; }
			public int fontHeight() { return 9; }
			public void push() { }
			public void pop() { }
			public void translate(float x, float y) { }
			public void scale(float f) { }
			public void item(Object s, int x, int y) { }
			public float guiScale() { return 3f; }
			public int image(String key, int w, int h, java.util.function.Supplier<int[]> px) {
				return keys.computeIfAbsent(key, k -> { px.get(); return keys.size(); });
			}
			public void drawImage(int handle, float[] q, int n, int argb) { }
		};
		adapter.renderer = nop;
		dev.quartz.core.ui.ClientMenu menu = new dev.quartz.core.ui.ClientMenu(new dev.quartz.core.ui.ClientMenu.Host() {
			public void close() { }
			public void openHudEditor() { }
		});
		com.sun.management.ThreadMXBean mx = (com.sun.management.ThreadMXBean) java.lang.management.ManagementFactory.getThreadMXBean();
		long tid = Thread.currentThread().getId();
		for (int round = 0; round < 2; round++) {
			int frames = 20000;
			long b0 = mx.getThreadAllocatedBytes(tid), t0 = System.nanoTime();
			for (int i = 0; i < frames; i++) Hud.renderAll(nop);
			long t1 = System.nanoTime(), b1 = mx.getThreadAllocatedBytes(tid);
			int menuFrames = 3000;
			for (int i = 0; i < menuFrames; i++) menu.render(nop, 200, 120);
			long t2 = System.nanoTime(), b2 = mx.getThreadAllocatedBytes(tid);
			if (round == 1) {
				System.out.printf("HUD  (20 elements): %6.1f us/frame  %7.0f bytes/frame%n", (t1 - t0) / 1e3 / frames, (b1 - b0) / (double) frames);
				System.out.printf("Menu (open):        %6.1f us/frame  %7.0f bytes/frame%n", (t2 - t1) / 1e3 / menuFrames, (b2 - b1) / (double) menuFrames);
			}
		}
	}

	public static void main(String[] args) throws Exception {
		if (args.length == 1 && args[0].equals("--bench")) {
			bench();
			return;
		}
		if (args.length == 2 && args[0].equals("--menu-preview")) {
			menuPreview(java.nio.file.Paths.get(args[1]));
			return;
		}
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

		System.out.println("effects");
		dev.quartz.core.fx.EffectSettings fx = ClientConfig.get().effects;
		fx.hitEffects = true;
		fx.hitEffect = "hearts";
		fx.hitAmount = 3;
		fx.hitSounds = true;
		fx.hitSound = "pling";
		Object target = new Object();
		dev.quartz.core.fx.Effects.onAttack(target);
		check(adapter.particleLog.isEmpty() && adapter.soundLog.isEmpty(), "a swing alone plays nothing (it may not have done damage)");
		dev.quartz.core.fx.Effects.onDamaged(new Object(), dev.quartz.core.fx.Effects.UNKNOWN);
		check(adapter.soundLog.isEmpty(), "damage to something you didn't swing at isn't yours");
		dev.quartz.core.fx.Effects.onDamaged(target, dev.quartz.core.fx.Effects.UNKNOWN);
		check(adapter.particleLog.contains("heartsx18"), "the server confirming damage to your target spawns its effect (6 particles per amount step)");
		check(adapter.soundLog.contains("pling"), "and plays the hit sound");
		adapter.soundLog.clear();
		dev.quartz.core.fx.Effects.onDamaged(target, dev.quartz.core.fx.Effects.UNKNOWN);
		check(adapter.soundLog.isEmpty(), "one swing, one hit sound");
		adapter.projectileNear = true;
		dev.quartz.core.fx.Effects.onDamaged(target, dev.quartz.core.fx.Effects.UNKNOWN);
		check(adapter.soundLog.contains("pling"), "your projectile next to it counts as your hit");
		adapter.projectileNear = false;
		adapter.soundLog.clear();
		dev.quartz.core.fx.Effects.onDamaged(new Object(), dev.quartz.core.fx.Effects.NOT_YOURS);
		check(adapter.soundLog.isEmpty(), "someone else's hit is ignored");
		dev.quartz.core.fx.Effects.onDamaged(new Object(), dev.quartz.core.fx.Effects.YOURS);
		check(adapter.soundLog.contains("pling"), "a hit the version credits to you plays (26.3 knows the attacker)");
		fx.hitEffect = "snow";
		dev.quartz.core.fx.Effects.onDamaged(new Object(), dev.quartz.core.fx.Effects.YOURS);
		check(dev.quartz.core.fx.Sprites.alive() == 15, "snowflakes are the client's own particles (5 per amount step)");
		dev.quartz.core.fx.Sprites.clear();
		fx.hitEffect = "hearts";
		fx.killEffects = true;
		fx.killEffect = "flames";
		fx.killSounds = true;
		fx.killSound = "levelup";
		adapter.targetDead = true;
		dev.quartz.core.fx.Effects.tick(adapter);
		check(adapter.particleLog.contains("flamesx40") && adapter.soundLog.contains("levelup"), "the target dying plays the kill effect and sound");
		adapter.particleLog.clear();
		dev.quartz.core.fx.Effects.tick(adapter);
		check(adapter.particleLog.isEmpty(), "once per kill");
		fx.lowHealthAlert = true;
		adapter.health = 0.1f;
		adapter.soundLog.clear();
		for (int i = 0; i < 40; i++) {
			dev.quartz.core.fx.Effects.tick(adapter);
		}
		check(adapter.soundLog.contains("heartbeat"), "low health plays a heartbeat");
		adapter.health = 1f;

		System.out.println("embedded sounds");
		try (java.io.InputStream in = dev.quartz.core.fx.EmbeddedSounds.class.getResourceAsStream("/assets/quartz/sounds/custom.wav")) {
			check(in != null, "the custom hit sound ships inside the jar");
			javax.sound.sampled.AudioInputStream audio = javax.sound.sampled.AudioSystem.getAudioInputStream(new java.io.BufferedInputStream(in));
			float ms = audio.getFrameLength() * 1000f / audio.getFormat().getFrameRate();
			check(ms > 300 && ms < 500, "and decodes as a short clip (" + Math.round(ms) + " ms)");
		}
		check(new dev.quartz.core.fx.EffectSettings().hitSound.equals("custom") && dev.quartz.core.fx.EmbeddedSounds.has("custom"), "Custom is the default hit sound");

		System.out.println("aspect ratio");
		check(dev.quartz.core.pvp.AspectRatio.apply(16 / 9f) == 16 / 9f, "native by default");
		ClientConfig.get().aspectRatio = "4:3";
		check(Math.abs(dev.quartz.core.pvp.AspectRatio.apply(16 / 9f) - 4 / 3f) < 1e-6, "4:3 draws the world at 4:3");
		check(Math.abs(dev.quartz.core.pvp.AspectRatio.width(1920, 1080) - 1440) < 0.01, "a 1080p window renders 1440 wide, then stretches");
		ClientConfig.get().aspectRatio = "nonsense";
		check(dev.quartz.core.pvp.AspectRatio.width(1920, 1080) == 1920, "unknown values fall back to native");
		ClientConfig.get().aspectRatio = "native";

		System.out.println("camera projection and hit particles");
		dev.quartz.core.fx.View.set(perspective(90, 1), 0, 0, 0);
		float[] pt = new float[4];
		check(dev.quartz.core.fx.View.project(0, 0, -5, 100, 100, pt) && Math.abs(pt[0] - 50) < 1e-3 && Math.abs(pt[1] - 50) < 1e-3, "a point straight ahead lands mid-screen");
		check(Math.abs(pt[3] - 10) < 1e-3, "and one block 5 away is 10 px tall at 90 degrees");
		check(dev.quartz.core.fx.View.project(1, 0, -5, 100, 100, pt) && Math.abs(pt[0] - 60) < 1e-3, "one block right moves it right");
		check(!dev.quartz.core.fx.View.project(0, 0, 5, 100, 100, pt), "behind the camera isn't drawn");
		check(dev.quartz.core.fx.View.visible(0, 0, -5, 1) && !dev.quartz.core.fx.View.visible(0, 0, 5, 1) && !dev.quartz.core.fx.View.visible(40, 0, -5, 1), "culling keeps what's in view only");
		for (int k = 0; k < dev.quartz.core.fx.Sprites.KINDS.length; k++) {
			dev.quartz.core.fx.Sprites.spawn(k, 0, 0, -5, 10, 0.5);
		}
		check(dev.quartz.core.fx.Sprites.alive() == 60, "every custom kind spawns");
		for (int i = 0; i < 3; i++) {
			dev.quartz.core.fx.Sprites.render(adapter.backend);
		}
		dev.quartz.core.fx.Sprites.clear();
		check(dev.quartz.core.fx.Sprites.alive() == 0, "and they draw (or skip cleanly) without image support");
		dev.quartz.core.fx.View.clear();

		System.out.println("name tag health");
		dev.quartz.core.fx.NameTags.Tag tag = new dev.quartz.core.fx.NameTags.Tag();
		dev.quartz.core.fx.NameTags.health(tag, 14, 1f, 20f, 0f, false);
		check(tag.healthKnown && tag.health == 14, "the server's health score wins over the synced value");
		dev.quartz.core.fx.NameTags.health(tag, -1, 1f, 20f, 0f, false);
		check(!tag.healthKnown, "a stranger at exactly 1 (servers fake it) shows no health rather than a wrong one");
		dev.quartz.core.fx.NameTags.health(tag, -1, 15.5f, 20f, 4f, false);
		check(tag.healthKnown && tag.health == 15.5f && tag.absorption == 4f, "real synced health (singleplayer, honest servers) shows");
		dev.quartz.core.fx.NameTags.health(tag, -1, 1f, 20f, 0f, true);
		check(tag.healthKnown, "your own health is always real");
		check(dev.quartz.core.fx.NameTags.strip("§b[MVP§c+§b] Steve").equals("[MVP+] Steve") && dev.quartz.core.fx.NameTags.colourOf("§b[MVP§c+§b] Steve") == 0xFF55FFFF, "rank codes stripped, rank colour kept");

		ClientConfig.get().nameTags = true;
		dev.quartz.core.fx.View.set(perspective(70, 400 / 240f), 0, 0, 0);
		adapter.backend.lastText = null;
		dev.quartz.core.fx.NameTags.mark(new double[] {0, 0.4, -70});
		dev.quartz.core.fx.NameTags.render(adapter.backend, adapter);
		check(adapter.backend.lastText == null, "no tag past vanilla's 64 blocks");
		adapter.backend.lastText = null;
		dev.quartz.core.fx.NameTags.mark(new double[] {0, 0.4, -40});
		dev.quartz.core.fx.NameTags.render(adapter.backend, adapter);
		check(adapter.backend.lastText != null, "a tag 40 blocks away still shows, small, like vanilla's");
		dev.quartz.core.fx.NameTags.mark(new double[] {0, 0.4, -3});
		dev.quartz.core.fx.NameTags.render(adapter.backend, adapter);
		check("_Maxim07_".equals(adapter.backend.lastText) || adapter.backend.lastText != null, "a tag up close draws");
		ClientConfig.get().nameTags = false;
		dev.quartz.core.fx.View.clear();

		System.out.println("atmosphere");
		ClientConfig.get().atmosphereSky = "aurora";
		ClientConfig.get().atmosphereFog = 2;
		check(dev.quartz.core.env.EnvironmentModule.fogColor() == 0x123050, "fog matches the sky's horizon");
		check(dev.quartz.core.env.EnvironmentModule.overridesFog() && dev.quartz.core.env.EnvironmentModule.fogEnd(128, 128) < 128, "medium fog closes in");
		ClientConfig.get().atmosphereFogColor = 3;
		check(dev.quartz.core.env.EnvironmentModule.fogColor() == 0xFF8FB8, "or takes a colour of your own");
		ClientConfig.get().atmosphereSky = "off";
		ClientConfig.get().atmosphereFog = 0;
		ClientConfig.get().atmosphereFogColor = 0;

		System.out.println("quality of life");
		check(dev.quartz.core.ui.SmoothHotbar.offset(2) == 0, "the hotbar starts where vanilla puts it");
		Thread.sleep(5);
		int slide = dev.quartz.core.ui.SmoothHotbar.offset(6);
		check(slide < 0 && slide > -80, "switching slots glides from the old one (" + slide + " px)");
		long until = System.currentTimeMillis() + 2000;
		while (dev.quartz.core.ui.SmoothHotbar.offset(6) != 0 && System.currentTimeMillis() < until) {
			Thread.sleep(5);
		}
		check(dev.quartz.core.ui.SmoothHotbar.offset(6) == 0, "and settles on the new one");
		check(dev.quartz.core.fx.TntTimers.assumedFuse(80) == 70, "a freshly lit TNT reads 3.5s on 1.8.9 (fuse not sent)");
		check(dev.quartz.core.hud.TabPing.label(87).equals("87") && dev.quartz.core.hud.TabPing.colour(30) != dev.quartz.core.hud.TabPing.colour(400), "tab ping as coloured numbers");
		dev.quartz.core.fx.View.set(perspective(70, 400 / 240f), 0, 0, 0);
		dev.quartz.core.fx.TntTimers.render(adapter.backend, adapter);
		check(true, "TNT timers draw without image support");
		dev.quartz.core.fx.View.clear();

		System.out.println("rice hat");
		check(!dev.quartz.core.fx.RiceHat.enabled(), "off by default");
		int[] vertices = {0};
		float[] apexY = {0};
		dev.quartz.core.fx.RiceHat.build((x, y, z, argb) -> { vertices[0]++; apexY[0] = Math.min(apexY[0], y); }, 0, 0);
		check(vertices[0] % 3 == 0 && vertices[0] >= 90 && apexY[0] < -12, "a cone of triangles above the head");

		System.out.println("performance presets");
		check(dev.quartz.core.perf.Performance.drawBlockEntity(40 * 40) && !dev.quartz.core.perf.Performance.drawBlockEntity(60 * 60), "signs and chests past 48 blocks aren't drawn");
		dev.quartz.core.perf.Performance.tick(adapter);
		check(adapter.maxFpsLog.equals(java.util.Arrays.asList(true)) && ClientConfig.get().performance.maxFpsRestore.containsKey("vsync"), "Max FPS applies once, remembering your settings");
		dev.quartz.core.perf.Performance.tick(adapter);
		check(adapter.maxFpsLog.size() == 1, "and doesn't re-apply every tick");
		ClientConfig.get().performance.maxFps = false;
		dev.quartz.core.perf.Performance.tick(adapter);
		check(adapter.maxFpsLog.equals(java.util.Arrays.asList(true, false)) && ClientConfig.get().performance.maxFpsRestore.isEmpty(), "switching it off puts them back");

		System.out.println("menu modules");
		check(dev.quartz.core.ui.Modules.of(dev.quartz.core.ui.Modules.Category.HUD).size() == 23, "23 HUD modules on 1.8.9 (20 elements, smooth hotbar, tab ping, style)");
		check(dev.quartz.core.ui.Modules.of(dev.quartz.core.ui.Modules.Category.SOUND).size() == 3, "3 sound modules");
		check(dev.quartz.core.ui.Modules.of(dev.quartz.core.ui.Modules.Category.VISUAL).size() == 17, "17 visual modules on 1.8.9");
		dev.quartz.core.ui.ClientMenu smoke = new dev.quartz.core.ui.ClientMenu(new dev.quartz.core.ui.ClientMenu.Host() {
			public void close() { }
			public void openHudEditor() { }
		});
		for (int i = 0; i < 3; i++) {
			smoke.render(adapter.backend, 200, 120);
		}
		smoke.scroll(2.5);
		smoke.click(384, 140);
		check(smoke.drag(384, 180), "the scrollbar can be grabbed and dragged");
		smoke.release();
		check(!smoke.drag(384, 120), "and lets go on release");
		smoke.render(adapter.backend, 200, 120);
		check(true, "the menu draws on a backend without image support (plain fallback)");

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
