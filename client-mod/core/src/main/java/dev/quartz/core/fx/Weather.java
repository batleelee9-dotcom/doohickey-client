package dev.quartz.core.fx;

import dev.quartz.core.Feature;
import dev.quartz.core.Quartz;
import dev.quartz.core.RenderBackend;
import dev.quartz.core.VersionAdapter;
import dev.quartz.core.config.ClientConfig;
import dev.quartz.core.ui.Smooth;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Ambient weather of the client's own: soft snow, drifting blossoms,
 * fireflies, rising embers or stardust, in a box around the camera. Only
 * visual (the server's weather is untouched), fades out under a roof, and
 * a fixed small pool keeps it cheap.
 */
public final class Weather {
	public static final String[] KINDS = {"off", "snow", "blossoms", "fireflies", "embers", "stardust"};
	public static final String[] NAMES = {"Off", "Snowfall", "Blossoms", "Fireflies", "Embers", "Stardust"};
	private static final String[] SPRITE = {null, "p.snow", "p.petal", "p.dot", "p.dot", "p.sparkle"};
	// Per kind: fall speed (blocks/s, negative rises), drift, size (blocks), colours.
	private static final float[] FALL = {0, 1.1f, 0.7f, 0f, -0.9f, 0.25f};
	private static final float[] DRIFT = {0, 0.35f, 0.8f, 0.5f, 0.3f, 0.2f};
	private static final float[] SIZE = {0, 0.11f, 0.12f, 0.16f, 0.09f, 0.1f};
	private static final int[][] COLOURS = {
		{},
		{0xFFFFFFFF, 0xFFE6F3FF},
		{0xFFFFB7D5, 0xFFFF9EC4, 0xFFFFD6E7},
		{0xFFE8FF7A, 0xFFB8FF6A},
		{0xFFFF9A3C, 0xFFFF6A2A, 0xFFFFC46A},
		{0xFFB9AEFF, 0xFF7FE7FF, 0xFFFFFFFF},
	};
	private static final int COUNT = 90;
	private static final float BOX = 14;

	// World positions, kept inside a box that follows the camera (wrapping round its edges).
	private static final double[] X = new double[COUNT];
	private static final double[] Y = new double[COUNT];
	private static final double[] Z = new double[COUNT];
	private static final float[] PHASE = new float[COUNT];
	private static final float[] SCALE = new float[COUNT];
	private static final int[] COLOUR = new int[COUNT];
	private static final float[] P = new float[4];
	private static int kind;
	private static float visibility;
	private static long lastNanos;

	private Weather() {
	}

	/** The chosen kind's index, 0 for off. */
	public static int active() {
		if (!Quartz.available(Feature.SKY_TEXTURE)) {
			return 0;
		}
		String id = ClientConfig.get().atmosphereWeather;
		for (int i = 1; i < KINDS.length; i++) {
			if (KINDS[i].equals(id)) {
				return i;
			}
		}
		return 0;
	}

	/** {@link #render} on the version's current HUD backend. */
	public static void renderHud() {
		render(Quartz.adapter().render(), Quartz.adapter());
	}

	/** Steps and draws the weather; once a frame from the HUD pass. */
	public static void render(RenderBackend r, VersionAdapter a) {
		int k = active();
		if (k == 0 || !View.valid()) {
			kind = 0;
			lastNanos = 0;
			return;
		}
		long now = System.nanoTime();
		float dt = lastNanos == 0 ? 0 : Math.min(0.05f, (now - lastNanos) / 1e9f);
		lastNanos = now;
		if (k != kind) {
			kind = k;
			ThreadLocalRandom rnd = ThreadLocalRandom.current();
			for (int i = 0; i < COUNT; i++) {
				respawn(i, rnd);
			}
		}
		// Fade with open sky above you, so it never snows indoors.
		float target = a.skyVisible() ? 1f : 0f;
		visibility += (target - visibility) * Math.min(1f, dt * 2.5f);
		if (visibility < 0.02f) {
			return;
		}
		ThreadLocalRandom rnd = ThreadLocalRandom.current();
		float w = r.screenWidth();
		float h = r.screenHeight();
		float t = (now % 1_000_000_000_000L) / 1e9f;
		for (int i = 0; i < COUNT; i++) {
			// Positions live relative to the camera, wrapped inside a box around it.
			float sway = (float) Math.sin(t * 1.3f + PHASE[i] * 7) * DRIFT[k];
			X[i] += sway * dt;
			Z[i] += (float) Math.cos(t * 0.9f + PHASE[i] * 5) * DRIFT[k] * 0.6f * dt;
			Y[i] -= FALL[k] * dt * (0.7f + PHASE[i] * 0.6f);
			if (k == 3) {
				Y[i] += (float) Math.sin(t * 0.8f + PHASE[i] * 11) * 0.25f * dt;
			}
			X[i] = wrap(X[i], View.camX(), BOX);
			Y[i] = wrap(Y[i], View.camY(), BOX / 2);
			Z[i] = wrap(Z[i], View.camZ(), BOX);
			if (!View.project(X[i], Y[i], Z[i], w, h, P)) {
				continue;
			}
			float size = SIZE[k] * SCALE[i];
			float alpha = visibility;
			if (k == 3) {
				// Fireflies pulse.
				alpha *= 0.35f + 0.65f * (0.5f + 0.5f * (float) Math.sin(t * 3 + PHASE[i] * 20));
			}
			float sw = size * P[2];
			float sh = size * P[3];
			if (k == 2) {
				sw *= 0.3f + 0.7f * Math.abs((float) Math.cos(t * 2 + PHASE[i] * 9));
			}
			if (sh < 0.6f) {
				continue;
			}
			// Cap the size up close so nothing fills the screen.
			float cap = 26;
			if (sh > cap) {
				sw *= cap / sh;
				sh = cap;
			}
			int c = COLOUR[i];
			Smooth.sprite(r, SPRITE[k], P[0], P[1], sw, sh, ((int) ((c >>> 24) * alpha) << 24) | (c & 0xFFFFFF));
		}
	}

	/** Keeps {@code v} within {@code half} of {@code centre}, re-entering from the opposite side. */
	private static double wrap(double v, double centre, float half) {
		double d = v - centre;
		if (d > half) {
			return v - 2 * half;
		}
		if (d < -half) {
			return v + 2 * half;
		}
		return v;
	}

	private static void respawn(int i, ThreadLocalRandom rnd) {
		X[i] = View.camX() + (rnd.nextFloat() * 2 - 1) * BOX;
		Y[i] = View.camY() + (rnd.nextFloat() * 2 - 1) * BOX / 2;
		Z[i] = View.camZ() + (rnd.nextFloat() * 2 - 1) * BOX;
		PHASE[i] = rnd.nextFloat();
		SCALE[i] = 0.7f + rnd.nextFloat() * 0.6f;
		int[] palette = COLOURS[kind];
		COLOUR[i] = palette[rnd.nextInt(palette.length)];
	}
}
