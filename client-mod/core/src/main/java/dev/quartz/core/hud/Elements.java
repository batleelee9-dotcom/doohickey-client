package dev.quartz.core.hud;

import dev.quartz.core.Feature;
import dev.quartz.core.Quartz;
import dev.quartz.core.RenderBackend;
import dev.quartz.core.VersionAdapter;
import dev.quartz.core.config.ClientConfig;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** The HUD elements every Minecraft version shares. Default positions are fractions of free space. */
final class Elements {
	private Elements() {
	}

	/**
	 * How often HUD values are re-read: 20 times a second. Plenty for numbers
	 * people read, and it spares rebuilding every string (and measuring it)
	 * on every frame at 200+ FPS.
	 */
	static final long REFRESH_NS = 50_000_000L;

	/** A single-line text element. */
	abstract static class TextElement extends HudElement {
		private String cachedText;
		private int cachedWidth;
		private long cachedAt;

		TextElement(String id, String name, Feature feature, float defaultX, float defaultY) {
			this(id, name, feature, true, defaultX, defaultY);
		}

		TextElement(String id, String name, Feature feature, boolean enabled, float defaultX, float defaultY) {
			super(id, name, feature, enabled, defaultX, defaultY);
		}

		abstract String text();

		private String current(RenderBackend r) {
			long now = System.nanoTime();
			if (cachedText == null || now - cachedAt > REFRESH_NS) {
				String text = text();
				if (!text.equals(cachedText)) {
					cachedText = text;
					cachedWidth = boxWidth(r, text);
				}
				cachedAt = now;
			}
			return cachedText;
		}

		@Override
		public int width(RenderBackend r) {
			current(r);
			return cachedWidth;
		}

		@Override
		public int height(RenderBackend r) {
			return 16;
		}

		@Override
		public void render(RenderBackend r, boolean preview) {
			textBox(r, current(r), cachedWidth);
		}
	}

	private static List<HudData.Effect> effects;
	private static long effectsAt;
	private static List<HudData.Item> armor;
	private static long armorAt;

	/** The adapter's effect list, re-read at most every {@link #REFRESH_NS}. */
	static List<HudData.Effect> effects() {
		long now = System.nanoTime();
		if (effects == null || now - effectsAt > REFRESH_NS) {
			effects = Quartz.adapter().effects();
			effectsAt = now;
		}
		return effects;
	}

	static List<HudData.Item> armor() {
		long now = System.nanoTime();
		if (armor == null || now - armorAt > REFRESH_NS) {
			armor = Quartz.adapter().armor();
			armorAt = now;
		}
		return armor;
	}

	static final class Fps extends TextElement {
		Fps() {
			super("fps", "FPS", Feature.HUD_FPS, 0.0f, 0.0f);
		}

		@Override
		String text() {
			return Quartz.adapter().fps() + " FPS";
		}
	}

	static final class Cps extends TextElement {
		Cps() {
			super("cps", "CPS", Feature.HUD_CPS, 0.0f, 0.06f);
		}

		@Override
		String text() {
			return CpsTracker.left() + " | " + CpsTracker.right() + " CPS";
		}
	}

	static final class Coordinates extends TextElement {
		Coordinates() {
			super("coordinates", "Coordinates", Feature.HUD_COORDINATES, 0.0f, 0.18f);
		}

		@Override
		String text() {
			VersionAdapter a = Quartz.adapter();
			int[] pos = a.blockPosition();
			if (pos == null) {
				return "X 0  Y 64  Z 0  N";
			}
			return "X " + pos[0] + "  Y " + pos[1] + "  Z " + pos[2] + "  " + a.facing();
		}
	}

	static final class Ping extends TextElement {
		Ping() {
			super("ping", "Ping", Feature.HUD_PING, 0.0f, 0.12f);
		}

		@Override
		String text() {
			int ms = Quartz.adapter().ping();
			return (ms < 0 ? 0 : ms) + " ms";
		}
	}

	static final class Reach extends TextElement {
		Reach() {
			super("reach", "Reach", Feature.HUD_REACH, 0.0f, 0.24f);
		}

		@Override
		String text() {
			double last = ReachTracker.last();
			return last < 0 ? "0.00 blocks" : String.format(Locale.ROOT, "%.2f blocks", last);
		}
	}

	// ---- Off by default: switch them on in the HUD menu --------------------

	static final class Clock extends TextElement {
		private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT);

		Clock() {
			super("clock", "Clock", Feature.HUD_CLOCK, false, 1.0f, 0.0f);
		}

		@Override
		String text() {
			return LocalTime.now().format(FORMAT);
		}
	}

	static final class Session extends TextElement {
		Session() {
			super("session", "Session time", Feature.HUD_CLOCK, false, 1.0f, 0.06f);
		}

		@Override
		String text() {
			long s = PlayerStats.sessionMs() / 1000;
			return s >= 3600
				? String.format(Locale.ROOT, "%d:%02d:%02d", s / 3600, s / 60 % 60, s % 60)
				: String.format(Locale.ROOT, "%d:%02d", s / 60, s % 60);
		}
	}

	static final class Memory extends TextElement {
		Memory() {
			super("memory", "Memory", Feature.HUD_MEMORY, false, 1.0f, 0.12f);
		}

		@Override
		String text() {
			Runtime rt = Runtime.getRuntime();
			long used = (rt.totalMemory() - rt.freeMemory()) >> 20;
			long max = rt.maxMemory() >> 20;
			return used * 100 / Math.max(1, max) + "% " + used + "/" + max + " MB";
		}
	}

	static final class Server extends TextElement {
		Server() {
			super("server", "Server address", Feature.HUD_SERVER_IP, false, 0.5f, 0.0f);
		}

		@Override
		public boolean hasContent() {
			return !Quartz.adapter().worldKey().isEmpty();
		}

		@Override
		String text() {
			String key = Quartz.adapter().worldKey();
			return key.startsWith("server:") ? key.substring("server:".length()) : "Singleplayer";
		}
	}

	static final class Direction extends TextElement {
		private static final String[] POINTS = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};

		Direction() {
			super("direction", "Direction", Feature.HUD_DIRECTION, false, 0.5f, 0.06f);
		}

		@Override
		String text() {
			// The game's yaw is 0 at south and turns clockwise; a compass bearing is 0 at north.
			float bearing = ((Quartz.adapter().yaw() + 180f) % 360f + 360f) % 360f;
			return POINTS[Math.round(bearing / 45f) % 8] + "  " + Math.round(bearing) + "°";
		}
	}

	static final class Speed extends TextElement {
		Speed() {
			super("speed", "Speed", Feature.HUD_SPEED, false, 0.0f, 0.3f);
		}

		@Override
		String text() {
			return String.format(Locale.ROOT, "%.2f m/s", PlayerStats.speed());
		}
	}

	static final class Day extends TextElement {
		Day() {
			super("day", "Day counter", Feature.HUD_DAY, false, 1.0f, 0.18f);
		}

		@Override
		public boolean hasContent() {
			return Quartz.adapter().worldTime() >= 0;
		}

		@Override
		String text() {
			return "Day " + Math.max(0, Quartz.adapter().worldTime()) / 24000;
		}
	}

	static final class Saturation extends TextElement {
		Saturation() {
			super("saturation", "Saturation", Feature.HUD_SATURATION, false, 0.0f, 0.36f);
		}

		@Override
		String text() {
			return String.format(Locale.ROOT, "Saturation %.1f", Math.max(0f, Quartz.adapter().saturation()));
		}
	}

	static final class Arrows extends TextElement {
		Arrows() {
			super("arrows", "Arrow counter", Feature.HUD_ITEM_COUNTER, false, 1.0f, 0.8f);
		}

		@Override
		public boolean hasContent() {
			return Quartz.adapter().arrows() > 0;
		}

		@Override
		String text() {
			int n = Quartz.adapter().arrows();
			return n + (n == 1 ? " arrow" : " arrows");
		}
	}

	static final class Combo extends TextElement {
		Combo() {
			super("combo", "Combo counter", Feature.HUD_COMBO, false, 0.0f, 0.42f);
		}

		@Override
		String text() {
			int n = PlayerStats.combo();
			return n == 0 ? "No combo" : n + " combo";
		}
	}

	static final class BlockInfo extends TextElement {
		BlockInfo() {
			super("block", "Block info", Feature.HUD_BLOCK_INFO, false, 0.5f, 0.12f);
		}

		@Override
		public boolean hasContent() {
			return !Quartz.adapter().targetBlock().isEmpty();
		}

		@Override
		String text() {
			String block = Quartz.adapter().targetBlock();
			return block.isEmpty() ? "Grass Block" : block;
		}
	}

	static final class Biome extends TextElement {
		Biome() {
			super("biome", "Biome", Feature.HUD_BIOME, false, 0.0f, 0.48f);
		}

		@Override
		String text() {
			String biome = Quartz.adapter().biome();
			return biome.isEmpty() ? "Plains" : biome;
		}
	}

	/** Active effects with time left, harmful ones in red. */
	static final class Potions extends HudElement {
		private static final int LINE = 11;

		Potions() {
			super("potions", "Potion effects", Feature.HUD_POTIONS, true, 1.0f, 0.3f);
		}

		private static List<HudData.Effect> shown(boolean preview) {
			List<HudData.Effect> effects = Elements.effects();
			if (effects.isEmpty() && preview) {
				return Arrays.asList(new HudData.Effect("Speed II", "1:30", false), new HudData.Effect("Poison", "0:12", true));
			}
			return effects;
		}

		private static String line(HudData.Effect e) {
			return e.name + "  " + e.time;
		}

		@Override
		public boolean hasContent() {
			return !Elements.effects().isEmpty();
		}

		private List<HudData.Effect> measured;
		private int measuredWidth;

		@Override
		public int width(RenderBackend r) {
			// Re-measured only when the (cached) effect list changes.
			List<HudData.Effect> shown = shown(true);
			if (shown != measured) {
				int w = 60;
				for (HudData.Effect e : shown) {
					w = Math.max(w, r.textWidth(line(e)) + 8);
				}
				measured = shown;
				measuredWidth = w;
			}
			return measuredWidth;
		}

		@Override
		public int height(RenderBackend r) {
			return Math.max(1, shown(true).size()) * LINE + 4;
		}

		@Override
		public void render(RenderBackend r, boolean preview) {
			ClientConfig c = ClientConfig.get();
			List<HudData.Effect> effects = shown(preview);
			if (c.moduleBackground) {
				r.fill(0, 0, width(r), height(r), 0x70000000);
			}
			int y = 3;
			for (HudData.Effect e : effects) {
				r.text(line(e), 4, y, e.harmful ? 0xFFFF6B6B : (c.textColor | 0xFF000000), c.textShadow);
				y += LINE;
			}
		}
	}

	/** Worn armor and the held item as icons, with uses left. */
	static final class Armor extends HudElement {
		private static final int ROW = 17;

		Armor() {
			super("armor", "Armor status", Feature.HUD_ARMOR, true, 1.0f, 0.55f);
		}

		@Override
		public boolean hasContent() {
			return !Elements.armor().isEmpty();
		}

		@Override
		public int width(RenderBackend r) {
			return 16 + 4 + r.textWidth("1561") + 4;
		}

		@Override
		public int height(RenderBackend r) {
			return Math.max(1, Elements.armor().size()) * ROW;
		}

		@Override
		public void render(RenderBackend r, boolean preview) {
			ClientConfig c = ClientConfig.get();
			List<HudData.Item> items = Elements.armor();
			if (items.isEmpty()) {
				if (preview) {
					r.fill(0, 0, width(r), ROW, 0x70000000);
					r.text("Armor", 4, 5, c.textColor | 0xFF000000, c.textShadow);
				}
				return;
			}
			int y = 0;
			for (HudData.Item item : items) {
				r.item(item.stack, 0, y);
				if (item.durability >= 0) {
					float left = item.maxDurability > 0 ? item.durability / (float) item.maxDurability : 1f;
					int color = left > 0.5f ? 0xFF7CFC7C : left > 0.2f ? 0xFFFFD166 : 0xFFFF6B6B;
					r.text(String.valueOf(item.durability), 20, y + 4, color, c.textShadow);
				}
				y += ROW;
			}
		}
	}

	/** WASD, mouse buttons (with CPS) and jump, lit while held. */
	static final class Keystrokes extends HudElement {
		private static final int KEY = 22;
		private static final int GAP = 2;

		Keystrokes() {
			super("keystrokes", "Keystrokes", Feature.HUD_KEYSTROKES, true, 0.0f, 0.62f);
		}

		@Override
		public int width(RenderBackend r) {
			return KEY * 3 + GAP * 2;
		}

		@Override
		public int height(RenderBackend r) {
			return KEY * 2 + GAP * 2 + 18 + GAP + 10;
		}

		/** Key labels and their widths, rebuilt every {@link #REFRESH_NS} instead of every frame. */
		private final String[] labels = new String[Input.values().length];
		private final int[] labelWidths = new int[Input.values().length];
		private long labelsAt;

		@Override
		public void render(RenderBackend r, boolean preview) {
			long now = System.nanoTime();
			if (labels[0] == null || now - labelsAt > REFRESH_NS) {
				VersionAdapter a = Quartz.adapter();
				for (Input input : Input.values()) {
					String text;
					switch (input) {
						case ATTACK: text = "LMB " + CpsTracker.left(); break;
						case USE: text = "RMB " + CpsTracker.right(); break;
						case JUMP: text = "—"; break;
						default: text = a.inputLabel(input).toUpperCase(Locale.ROOT);
					}
					if (text.length() > 5) {
						text = text.substring(0, 5);
					}
					labels[input.ordinal()] = text;
					labelWidths[input.ordinal()] = r.textWidth(text);
				}
				labelsAt = now;
			}
			int width = width(r);
			key(r, Input.FORWARD, KEY + GAP, 0, KEY, KEY);
			key(r, Input.LEFT, 0, KEY + GAP, KEY, KEY);
			key(r, Input.BACK, KEY + GAP, KEY + GAP, KEY, KEY);
			key(r, Input.RIGHT, (KEY + GAP) * 2, KEY + GAP, KEY, KEY);
			int mouseY = (KEY + GAP) * 2;
			int half = (width - GAP) / 2;
			key(r, Input.ATTACK, 0, mouseY, half, 18);
			key(r, Input.USE, half + GAP, mouseY, half, 18);
			key(r, Input.JUMP, 0, mouseY + 18 + GAP, width, 10);
		}

		private void key(RenderBackend r, Input input, int x, int y, int w, int h) {
			ClientConfig c = ClientConfig.get();
			// Pressed state is read live every frame, so taps never lag.
			boolean down = Quartz.adapter().inputDown(input);
			r.fill(x, y, x + w, y + h, down ? 0xD0FFFFFF : 0x70000000);
			int color = down ? 0xFF111111 : (c.textColor | 0xFF000000);
			r.text(labels[input.ordinal()], x + (w - labelWidths[input.ordinal()]) / 2, y + (h - 8) / 2 + 1, color, !down && c.textShadow);
		}
	}
}
