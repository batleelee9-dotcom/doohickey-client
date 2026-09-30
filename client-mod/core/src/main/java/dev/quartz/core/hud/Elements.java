package dev.quartz.core.hud;

import dev.quartz.core.Feature;
import dev.quartz.core.Quartz;
import dev.quartz.core.RenderBackend;
import dev.quartz.core.VersionAdapter;
import dev.quartz.core.config.ClientConfig;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** The HUD elements every Minecraft version shares. Default positions are fractions of free space. */
final class Elements {
	private Elements() {
	}

	/** A single-line text element. */
	abstract static class TextElement extends HudElement {
		TextElement(String id, String name, Feature feature, float defaultX, float defaultY) {
			super(id, name, feature, true, defaultX, defaultY);
		}

		abstract String text();

		@Override
		public int width(RenderBackend r) {
			return boxWidth(r, text());
		}

		@Override
		public int height(RenderBackend r) {
			return 16;
		}

		@Override
		public void render(RenderBackend r, boolean preview) {
			textBox(r, text(), width(r));
		}
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

	/** Active effects with time left, harmful ones in red. */
	static final class Potions extends HudElement {
		private static final int LINE = 11;

		Potions() {
			super("potions", "Potion effects", Feature.HUD_POTIONS, true, 1.0f, 0.3f);
		}

		private static List<HudData.Effect> shown(boolean preview) {
			List<HudData.Effect> effects = Quartz.adapter().effects();
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
			return !Quartz.adapter().effects().isEmpty();
		}

		@Override
		public int width(RenderBackend r) {
			int w = 60;
			for (HudData.Effect e : shown(true)) {
				w = Math.max(w, r.textWidth(line(e)) + 8);
			}
			return w;
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
			return !Quartz.adapter().armor().isEmpty();
		}

		@Override
		public int width(RenderBackend r) {
			return 16 + 4 + r.textWidth("1561") + 4;
		}

		@Override
		public int height(RenderBackend r) {
			return Math.max(1, Quartz.adapter().armor().size()) * ROW;
		}

		@Override
		public void render(RenderBackend r, boolean preview) {
			ClientConfig c = ClientConfig.get();
			List<HudData.Item> items = Quartz.adapter().armor();
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

		@Override
		public void render(RenderBackend r, boolean preview) {
			int width = width(r);
			key(r, Input.FORWARD, KEY + GAP, 0, KEY, KEY, null);
			key(r, Input.LEFT, 0, KEY + GAP, KEY, KEY, null);
			key(r, Input.BACK, KEY + GAP, KEY + GAP, KEY, KEY, null);
			key(r, Input.RIGHT, (KEY + GAP) * 2, KEY + GAP, KEY, KEY, null);
			int mouseY = (KEY + GAP) * 2;
			int half = (width - GAP) / 2;
			key(r, Input.ATTACK, 0, mouseY, half, 18, "LMB " + CpsTracker.left());
			key(r, Input.USE, half + GAP, mouseY, half, 18, "RMB " + CpsTracker.right());
			key(r, Input.JUMP, 0, mouseY + 18 + GAP, width, 10, "—");
		}

		private static void key(RenderBackend r, Input input, int x, int y, int w, int h, String label) {
			ClientConfig c = ClientConfig.get();
			VersionAdapter a = Quartz.adapter();
			boolean down = a.inputDown(input);
			r.fill(x, y, x + w, y + h, down ? 0xD0FFFFFF : 0x70000000);
			String text = label != null ? label : a.inputLabel(input).toUpperCase();
			if (text.length() > 5) {
				text = text.substring(0, 5);
			}
			int color = down ? 0xFF111111 : (c.textColor | 0xFF000000);
			r.text(text, x + (w - r.textWidth(text)) / 2, y + (h - 8) / 2 + 1, color, !down && c.textShadow);
		}
	}
}
