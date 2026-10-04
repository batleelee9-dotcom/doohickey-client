package dev.quartz.core.fx;

import dev.quartz.core.Feature;
import dev.quartz.core.Quartz;
import dev.quartz.core.config.ClientConfig;

/**
 * The rice hat: a solid, shallow cone with a rim and an underside, shaded
 * from a fixed light so it reads as 3D at any angle. Settings and geometry
 * live here; each version draws the triangles on player models. Client-side:
 * you see it on whoever the setting says, nobody else sees it at all.
 */
public final class RiceHat {
	/** 0 is rainbow. */
	public static final int[] COLOURS = {0, 0xFFB9AEFF, 0xFF7FE7FF, 0xFFFF7AD9, 0xFFFFD45E, 0xFFFFFFFF, 0xFF2A2A33};
	public static final String[] COLOUR_NAMES = {"Rainbow", "Violet", "Aqua", "Pink", "Gold", "White", "Black"};
	/** Who wears it: RiceHat.EVERYONE, YOU or OTHERS. */
	public static final int EVERYONE = 0;
	public static final int YOU = 1;
	public static final int OTHERS = 2;
	public static final String[] WHO_NAMES = {"Everyone", "Just you", "Other players"};
	/** How far to raise it over a helmet or skull, in model pixels. */
	public static final float HELMET_LIFT = 1.3f;

	/** Receives triangles: three vertices each, in model pixels from the head pivot (y up is negative). */
	public interface Sink {
		void vertex(float x, float y, float z, int argb);
	}

	private static final int SEGMENTS = 48;
	private static final float RADIUS = 10f;
	private static final float RIM_Y = -8.1f;
	private static final float APEX_Y = -13.6f;
	/** Brim thickness, and how far up the hollow underside reaches. */
	private static final float RIM_DEPTH = 0.7f;
	private static final float INNER_APEX_Y = -12.2f;
	// Light from above, front-right, in model space (y up is negative).
	private static final float LX = 0.36f;
	private static final float LY = -0.86f;
	private static final float LZ = -0.36f;

	private RiceHat() {
	}

	public static boolean enabled() {
		return ClientConfig.get().riceHat && Quartz.available(Feature.RICE_HAT);
	}

	/** Whether a player wears it: {@code you} for your own player. */
	public static boolean shows(boolean you) {
		int who = ClientConfig.get().riceHatWho;
		return who == EVERYONE || (who == YOU) == you;
	}

	/**
	 * The hat's triangles, raised by {@code lift} model pixels (over a
	 * helmet). {@code seconds} drives the rainbow's slow spin.
	 */
	public static void build(Sink sink, float seconds, float lift) {
		int colour = ClientConfig.get().riceHatColor;
		float apexY = APEX_Y - lift;
		float rimY = RIM_Y - lift;
		float bottomY = rimY + RIM_DEPTH;
		float innerY = INNER_APEX_Y - lift;
		// Cone surface normal tilt: rises (rimY - apexY) over RADIUS.
		float rise = rimY - apexY;
		float len = (float) Math.sqrt(rise * rise + RADIUS * RADIUS);
		float nh = rise / len;
		float nv = -RADIUS / len;
		for (int i = 0; i < SEGMENTS; i++) {
			float a0 = (float) (Math.PI * 2 * i / SEGMENTS);
			float a1 = (float) (Math.PI * 2 * (i + 1) / SEGMENTS);
			float am = (a0 + a1) / 2;
			float c0 = (float) Math.cos(a0);
			float s0 = (float) Math.sin(a0);
			float c1 = (float) Math.cos(a1);
			float s1 = (float) Math.sin(a1);
			int base0 = base(colour, a0, seconds);
			int base1 = base(colour, a1, seconds);
			int baseM = base(colour, am, seconds);
			float x0 = c0 * RADIUS;
			float z0 = s0 * RADIUS;
			float x1 = c1 * RADIUS;
			float z1 = s1 * RADIUS;

			// Top: lit by how much each part faces the light.
			sink.vertex(0, apexY, 0, shade(baseM, top(am, nh, nv), 0.12f));
			sink.vertex(x1, rimY, z1, shade(base1, top(a1, nh, nv), 0f));
			sink.vertex(x0, rimY, z0, shade(base0, top(a0, nh, nv), 0f));

			// The brim's edge: a thin vertical band.
			float e0 = side(a0);
			float e1 = side(a1);
			sink.vertex(x0, rimY, z0, shade(base0, e0, 0f));
			sink.vertex(x1, rimY, z1, shade(base1, e1, 0f));
			sink.vertex(x1, bottomY, z1, shade(base1, e1 * 0.8f, 0f));
			sink.vertex(x0, rimY, z0, shade(base0, e0, 0f));
			sink.vertex(x1, bottomY, z1, shade(base1, e1 * 0.8f, 0f));
			sink.vertex(x0, bottomY, z0, shade(base0, e0 * 0.8f, 0f));

			// Underside: hollow and in shadow, a little lighter towards the brim.
			sink.vertex(0, innerY, 0, shade(baseM, 0.22f, 0f));
			sink.vertex(x0, bottomY, z0, shade(base0, 0.42f, 0f));
			sink.vertex(x1, bottomY, z1, shade(base1, 0.42f, 0f));
		}
	}

	/** Brightness of the top surface at angle {@code a}: ambient plus diffuse. */
	private static float top(float a, float nh, float nv) {
		float d = (float) Math.cos(a) * nh * LX + nv * LY + (float) Math.sin(a) * nh * LZ;
		return 0.5f + 0.5f * Math.max(0f, d);
	}

	private static float side(float a) {
		float d = (float) Math.cos(a) * LX + (float) Math.sin(a) * LZ;
		return 0.42f + 0.38f * Math.max(0f, d / 0.51f);
	}

	private static int base(int colour, float angle, float seconds) {
		if (colour != 0) {
			return colour & 0xFFFFFF;
		}
		float hue = (float) (angle / (Math.PI * 2)) + seconds * 0.12f;
		return java.awt.Color.HSBtoRGB(hue, 0.6f, 1f) & 0xFFFFFF;
	}

	/** {@code rgb} at {@code light} brightness, plus a little white highlight. */
	private static int shade(int rgb, float light, float highlight) {
		int r = channel(rgb >> 16 & 255, light, highlight);
		int g = channel(rgb >> 8 & 255, light, highlight);
		int b = channel(rgb & 255, light, highlight);
		return 0xFF000000 | r << 16 | g << 8 | b;
	}

	private static int channel(int c, float light, float highlight) {
		float v = c * Math.min(1f, light) + (255 - c * Math.min(1f, light)) * highlight;
		return Math.max(0, Math.min(255, Math.round(v)));
	}
}
