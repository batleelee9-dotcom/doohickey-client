package dev.quartz.core.fx;

import dev.quartz.core.RenderBackend;
import dev.quartz.core.ui.Smooth;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Hit particles Minecraft doesn't have (snowflakes, stars, sparkles,
 * confetti, petals, bubbles), simulated by the client and drawn as smooth
 * sprites over the world, placed with the game's own camera ({@link View}).
 * They draw on top of blocks, which suits short bursts on someone you just hit.
 *
 * A fixed pool in parallel arrays: spawning and drawing allocate nothing.
 */
public final class Sprites {
	public static final String[] KINDS = {"snow", "stars", "sparkles", "confetti", "petals", "bubbles"};
	private static final String[] SPRITES = {"p.snow", "p.star", "p.sparkle", "p.confetti", "p.petal", "p.bubble"};
	// Per kind: gravity (blocks/s², up is +), drag (1/s), burst speed (blocks/s), life (s), size (blocks).
	private static final float[] GRAVITY = {-1.2f, 0f, -1.5f, -7f, -1.4f, 1.2f};
	private static final float[] DRAG = {2.5f, 3.5f, 3f, 1.6f, 2.8f, 2f};
	private static final float[] SPEED = {1.6f, 3.2f, 3.6f, 4.2f, 1.8f, 1.2f};
	private static final float[] LIFE = {1.5f, 0.9f, 0.6f, 1.4f, 1.6f, 1.0f};
	private static final float[] SIZE = {0.17f, 0.2f, 0.16f, 0.09f, 0.14f, 0.15f};
	private static final int[][] COLOURS = {
		{0xFFFFFFFF, 0xFFDDF2FF, 0xFFBFE6FF},
		{0xFFFFD45E, 0xFFFFF1B8, 0xFFFFFFFF},
		{0xFF7FE7FF, 0xFFB9AEFF, 0xFFFFFFFF},
		{0xFFFF5A6E, 0xFFFFD23F, 0xFF3DD68C, 0xFF4F9DFF, 0xFFFF7AD9, 0xFFB98CFF},
		{0xFFFFB7D5, 0xFFFF9EC4, 0xFFFFD6E7},
		{0xFFAEE6FF, 0xFFD4F3FF, 0xFFFFFFFF},
	};

	private static final int MAX = 320;
	private static final double[] X = new double[MAX];
	private static final double[] Y = new double[MAX];
	private static final double[] Z = new double[MAX];
	private static final float[] VX = new float[MAX];
	private static final float[] VY = new float[MAX];
	private static final float[] VZ = new float[MAX];
	private static final float[] AGE = new float[MAX];
	private static final float[] LIVES = new float[MAX];
	private static final float[] SCALE = new float[MAX];
	private static final float[] PHASE = new float[MAX];
	private static final int[] COLOUR = new int[MAX];
	private static final byte[] KIND = new byte[MAX];
	private static int count;
	private static long lastNanos;
	private static final float[] P = new float[4];

	private Sprites() {
	}

	/** Index of a custom kind, or -1 for Minecraft's own particles. */
	public static int kind(String name) {
		for (int i = 0; i < KINDS.length; i++) {
			if (KINDS[i].equals(name)) {
				return i;
			}
		}
		return -1;
	}

	public static int alive() {
		return count;
	}

	/** A burst of {@code n} particles around a point, {@code spread} blocks wide. */
	public static void spawn(int kind, double x, double y, double z, int n, double spread) {
		if (count == 0) {
			lastNanos = 0;
		}
		ThreadLocalRandom rnd = ThreadLocalRandom.current();
		int[] palette = COLOURS[kind];
		for (int k = 0; k < n; k++) {
			// Full: replace the oldest-looking slot rather than dropping the newest burst.
			int i = count < MAX ? count++ : rnd.nextInt(MAX);
			// A random direction, nudged upwards so bursts bloom rather than splat.
			double u = rnd.nextDouble() * 2 - 1;
			double a = rnd.nextDouble() * Math.PI * 2;
			double s = Math.sqrt(1 - u * u);
			float speed = SPEED[kind] * (0.45f + rnd.nextFloat() * 0.75f);
			X[i] = x + (rnd.nextDouble() - 0.5) * spread;
			Y[i] = y + (rnd.nextDouble() - 0.5) * spread;
			Z[i] = z + (rnd.nextDouble() - 0.5) * spread;
			VX[i] = (float) (s * Math.cos(a)) * speed;
			VY[i] = (float) (u * 0.7 + 0.45) * speed;
			VZ[i] = (float) (s * Math.sin(a)) * speed;
			AGE[i] = 0;
			LIVES[i] = LIFE[kind] * (0.75f + rnd.nextFloat() * 0.5f);
			SCALE[i] = SIZE[kind] * (0.7f + rnd.nextFloat() * 0.6f);
			PHASE[i] = rnd.nextFloat() * 6.283f;
			COLOUR[i] = palette[rnd.nextInt(palette.length)];
			KIND[i] = (byte) kind;
		}
	}

	/** Steps and draws every live particle; call once a frame from the HUD pass. */
	public static void render(RenderBackend r) {
		long now = System.nanoTime();
		float dt = lastNanos == 0 ? 0f : Math.min(0.05f, (now - lastNanos) / 1e9f);
		lastNanos = count == 0 ? 0 : now;
		if (count == 0) {
			return;
		}
		float w = r.screenWidth();
		float h = r.screenHeight();
		for (int i = 0; i < count; ) {
			AGE[i] += dt;
			if (AGE[i] >= LIVES[i]) {
				remove(i);
				continue;
			}
			int kind = KIND[i];
			float drag = (float) Math.exp(-DRAG[kind] * dt);
			VX[i] *= drag;
			VY[i] = VY[i] * drag + GRAVITY[kind] * dt;
			VZ[i] *= drag;
			X[i] += VX[i] * dt;
			Y[i] += VY[i] * dt;
			Z[i] += VZ[i] * dt;
			float t = AGE[i] / LIVES[i];
			// Falling things sway; bubbles wobble.
			double sway = (kind == 0 || kind == 4 || kind == 5) ? Math.sin(PHASE[i] + AGE[i] * 4) * 0.25 * dt : 0;
			X[i] += sway;
			if (View.project(X[i], Y[i], Z[i], w, h, P)) {
				draw(r, i, kind, t);
			}
			i++;
		}
	}

	private static void draw(RenderBackend r, int i, int kind, float t) {
		// Pop in, fade out; stars twinkle, bubbles swell before popping.
		float in = Math.min(1f, AGE[i] * 12f);
		float size = SCALE[i] * in;
		if (kind == 1) {
			size *= 0.8f + 0.25f * (float) Math.sin(PHASE[i] + AGE[i] * 18);
		} else if (kind == 2) {
			size *= 1f - t * 0.6f;
		} else if (kind == 5) {
			size *= 1f + t * 0.5f;
		}
		float sw = size * P[2];
		float sh = size * P[3];
		if (kind == 3 || kind == 4) {
			// Confetti and petals tumble: squash one axis as they turn.
			sw *= 0.25f + 0.75f * Math.abs((float) Math.cos(PHASE[i] + AGE[i] * (kind == 3 ? 9 : 4)));
		}
		float alpha = 1f - t * t;
		int c = COLOUR[i];
		Smooth.sprite(r, SPRITES[kind], P[0], P[1], sw, sh, ((int) ((c >>> 24) * alpha) << 24) | (c & 0xFFFFFF));
	}

	private static void remove(int i) {
		int last = --count;
		X[i] = X[last];
		Y[i] = Y[last];
		Z[i] = Z[last];
		VX[i] = VX[last];
		VY[i] = VY[last];
		VZ[i] = VZ[last];
		AGE[i] = AGE[last];
		LIVES[i] = LIVES[last];
		SCALE[i] = SCALE[last];
		PHASE[i] = PHASE[last];
		COLOUR[i] = COLOUR[last];
		KIND[i] = KIND[last];
	}

	/** {@link #render} on the version's current HUD backend. */
	public static void renderHud() {
		render(dev.quartz.core.Quartz.adapter().render());
	}

	/** Drops every particle (leaving a world). */
	public static void clear() {
		count = 0;
		lastNanos = 0;
	}
}
