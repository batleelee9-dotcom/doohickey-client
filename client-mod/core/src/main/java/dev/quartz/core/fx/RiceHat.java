package dev.quartz.core.fx;

import dev.quartz.core.Feature;
import dev.quartz.core.Quartz;
import dev.quartz.core.config.ClientConfig;

/**
 * The rice hat: a wide translucent cone over your head. Settings and
 * geometry live here; each version draws the triangles on its player
 * model. Like the other cosmetics, only you see it.
 */
public final class RiceHat {
	/** 0 is rainbow. */
	public static final int[] COLOURS = {0, 0xFFB9AEFF, 0xFF7FE7FF, 0xFFFF7AD9, 0xFFFFD45E, 0xFFFFFFFF, 0xFF20202A};
	public static final String[] COLOUR_NAMES = {"Rainbow", "Violet", "Aqua", "Pink", "Gold", "White", "Black"};

	/** Receives triangles: three vertices each, in model pixels from the head pivot (y up is negative). */
	public interface Sink {
		void vertex(float x, float y, float z, int argb);
	}

	private static final int SEGMENTS = 40;
	private static final float RADIUS = 9.5f;
	private static final float RIM_Y = -8.2f;
	private static final float APEX_Y = -13.2f;
	private static final int ALPHA = 0xA8;

	private RiceHat() {
	}

	public static boolean enabled() {
		return ClientConfig.get().riceHat && Quartz.available(Feature.RICE_HAT);
	}

	/** The cone's triangles. {@code seconds} drives the rainbow's slow spin. */
	public static void build(Sink sink, float seconds) {
		int colour = ClientConfig.get().riceHatColor;
		boolean rainbow = colour == 0;
		int apex = rainbow ? argb(0xFFFFFF, 0xD0) : argb(mix(colour, 0xFFFFFF, 0.45f), 0xD0);
		for (int i = 0; i < SEGMENTS; i++) {
			double a0 = Math.PI * 2 * i / SEGMENTS;
			double a1 = Math.PI * 2 * (i + 1) / SEGMENTS;
			int c0 = rimColour(rainbow, colour, a0, seconds);
			int c1 = rimColour(rainbow, colour, a1, seconds);
			sink.vertex(0, APEX_Y, 0, apex);
			sink.vertex((float) Math.cos(a1) * RADIUS, RIM_Y, (float) Math.sin(a1) * RADIUS, c1);
			sink.vertex((float) Math.cos(a0) * RADIUS, RIM_Y, (float) Math.sin(a0) * RADIUS, c0);
		}
	}

	private static int rimColour(boolean rainbow, int colour, double angle, float seconds) {
		if (!rainbow) {
			return argb(colour, ALPHA);
		}
		float hue = (float) (angle / (Math.PI * 2)) + seconds * 0.15f;
		return argb(java.awt.Color.HSBtoRGB(hue, 0.55f, 1f), ALPHA);
	}

	private static int argb(int rgb, int alpha) {
		return (alpha << 24) | (rgb & 0xFFFFFF);
	}

	private static int mix(int a, int b, float t) {
		int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
		int g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
		int bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
		return (r << 16) | (g << 8) | bl;
	}
}
