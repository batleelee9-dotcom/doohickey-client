package dev.quartz.core.fx;

import dev.quartz.core.Feature;
import dev.quartz.core.Quartz;
import dev.quartz.core.config.ClientConfig;

/**
 * Atmosphere: the client's own skies (painted procedurally into a skybox
 * once, in the background), fog tinted to match, and ambient weather
 * ({@link Weather}). A skybox is six textured quads, so it costs about
 * nothing per frame; the painting happens once per sky you pick.
 *
 * The skybox texture is a 3×2 atlas of cube faces; {@link #FACES} says how
 * each face maps to directions, and both the painter and each version's
 * drawing code use it, so they always agree.
 */
public final class Atmosphere {
	public static final String[] SKIES = {"off", "aurora", "nebula", "golden", "synthwave", "eclipse"};
	public static final String[] SKY_NAMES = {"Off", "Aurora", "Nebula", "Golden hour", "Synthwave", "Eclipse"};
	/** The horizon colour of each sky: fog matches it so distant land melts into the sky. */
	private static final int[] HORIZON = {0, 0x123050, 0x1A0F2E, 0xF2B48A, 0xC8407E, 0x3A0C10};
	/** 0 = match the sky, then custom tints. */
	public static final int[] FOG_COLOURS = {0, 0xB9AEFF, 0x7FE7FF, 0xFF8FB8, 0xFFC27A, 0xDDF2FF, 0x9CFFB8};
	public static final String[] FOG_NAMES = {"Match sky", "Violet", "Aqua", "Rose", "Amber", "Ice", "Mint"};
	public static final String[] DENSITY_NAMES = {"Off", "Light", "Medium", "Heavy"};

	/** Per face: forward, right and up axes (x, y, z each), with +y up. */
	public static final float[][] FACES = {
		{1, 0, 0, 0, 0, 1, 0, 1, 0},
		{-1, 0, 0, 0, 0, -1, 0, 1, 0},
		{0, 0, 1, -1, 0, 0, 0, 1, 0},
		{0, 0, -1, 1, 0, 0, 0, 1, 0},
		{0, 1, 0, 1, 0, 0, 0, 0, -1},
		{0, -1, 0, 1, 0, 0, 0, 0, 1},
	};
	/** Pixels per face edge; the atlas is 3 faces wide and 2 tall. */
	public static final int FACE = 320;

	private static volatile String painted;
	private static volatile int[] pixels;
	private static String painting;

	private Atmosphere() {
	}

	private static ClientConfig c() {
		return ClientConfig.get();
	}

	/** The chosen sky id, or null for vanilla's. */
	public static String sky() {
		String id = c().atmosphereSky;
		return id == null || id.equals("off") || !Quartz.available(Feature.SKY_TEXTURE) ? null : id;
	}

	/**
	 * The atlas for the chosen sky, or null while it's still being painted
	 * (vanilla's sky shows meanwhile). Starts painting on first ask.
	 */
	public static int[] atlas() {
		String id = sky();
		if (id == null) {
			return null;
		}
		if (id.equals(painted)) {
			return pixels;
		}
		synchronized (Atmosphere.class) {
			if (!id.equals(painting)) {
				painting = id;
				Thread t = new Thread(() -> {
					int[] out = SkyPainter.paint(id);
					synchronized (Atmosphere.class) {
						if (id.equals(painting)) {
							pixels = out;
							painted = id;
						}
					}
				}, "Doohickey sky painter");
				t.setDaemon(true);
				t.setPriority(Thread.MIN_PRIORITY);
				t.start();
			}
		}
		return null;
	}

	/** The texture key for the current atlas (changes with the sky, so versions upload each once). */
	public static String key() {
		return "atmosphere:" + painted;
	}

	/** Slow drift of the whole sky, in radians. */
	public static float rotation() {
		return c().atmosphereMotion ? (float) ((System.currentTimeMillis() % 3_600_000L) / 3_600_000.0 * Math.PI * 2) : 0f;
	}

	private static int index(String[] ids, String id) {
		for (int i = 0; i < ids.length; i++) {
			if (ids[i].equals(id)) {
				return i;
			}
		}
		return 0;
	}

	/** Fog colour as 0xRRGGBB, or -1 to leave it alone. */
	public static int fogColor() {
		int pick = c().atmosphereFogColor;
		if (pick > 0 && pick < FOG_COLOURS.length && Quartz.available(Feature.FOG_COLOR)) {
			return FOG_COLOURS[pick];
		}
		String id = sky();
		return id == null ? -1 : HORIZON[index(SKIES, id)];
	}

	/** 0 = untouched, 1..3 = light, medium, heavy. */
	public static int density() {
		return Quartz.available(Feature.FOG) ? Math.max(0, Math.min(3, c().atmosphereFog)) : 0;
	}
}
