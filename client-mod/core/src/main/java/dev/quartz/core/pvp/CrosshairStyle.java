package dev.quartz.core.pvp;

import dev.quartz.core.RenderBackend;
import dev.quartz.core.config.ClientConfig;

/** The custom crosshair, drawn the same way on every version. */
public final class CrosshairStyle {
	public static final String[] STYLES = {"Cross", "Dot", "Circle", "T", "Cross + dot"};
	public static final int[] COLORS = {0xFFFFFFFF, 0xFF55FF55, 0xFF55FFFF, 0xFFFF5555, 0xFFFFFF55, 0xFFFF55FF, 0xFF000000};
	public static final String[] COLOR_NAMES = {"White", "Green", "Cyan", "Red", "Yellow", "Pink", "Black"};

	private CrosshairStyle() {
	}

	private static int clamp(int v, int min, int max) {
		return Math.max(min, Math.min(max, v));
	}

	public static void draw(RenderBackend r, int cx, int cy) {
		ClientConfig c = ClientConfig.get();
		int size = clamp(c.crosshairSize, 1, 12);
		int gap = clamp(c.crosshairGap, 0, 8);
		int color = c.crosshairColor | 0xFF000000;
		boolean outline = c.crosshairOutline;
		switch (c.crosshairStyle) {
			case 1:
				square(r, cx - 1, cy - 1, 2, 2, color, outline);
				break;
			case 2: {
				// A ring of 1 px squares: crisp at any GUI scale.
				int radius = size + gap;
				for (int i = 0; i < 48; i++) {
					double a = i * Math.PI * 2 / 48;
					square(r, cx + (int) Math.round(Math.cos(a) * radius), cy + (int) Math.round(Math.sin(a) * radius), 1, 1, color, false);
				}
				break;
			}
			default:
				square(r, cx - gap - size, cy, size, 1, color, outline);
				square(r, cx + gap + 1, cy, size, 1, color, outline);
				if (c.crosshairStyle != 3) {
					square(r, cx, cy - gap - size, 1, size, color, outline);
				}
				square(r, cx, cy + gap + 1, 1, size, color, outline);
				if (c.crosshairStyle == 4) {
					square(r, cx, cy, 1, 1, color, outline);
				}
		}
	}

	private static void square(RenderBackend r, int x, int y, int w, int h, int color, boolean outline) {
		if (outline) {
			r.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xC0000000);
		}
		r.fill(x, y, x + w, y + h, color);
	}
}
