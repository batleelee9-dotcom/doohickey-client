package dev.quartz.core.fx;

import dev.quartz.core.Feature;
import dev.quartz.core.Quartz;
import dev.quartz.core.RenderBackend;
import dev.quartz.core.VersionAdapter;
import dev.quartz.core.config.ClientConfig;
import dev.quartz.core.ui.Smooth;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Combat feedback: a hit marker round the crosshair when a hit of yours
 * lands, damage numbers that pop off whatever you hit, and a banner with
 * multi-kills and your streak when you finish someone. {@link Effects}
 * decides which damage was yours; this only shows it.
 */
public final class Combat {
	private static final String[] MULTI = {"ELIMINATED", "ELIMINATED", "DOUBLE KILL", "TRIPLE KILL", "QUAD KILL", "PENTA KILL"};
	/** Kills this close together count as one multi-kill. */
	private static final long MULTI_WINDOW_MS = 8000;
	private static final long BANNER_MS = 2600;
	private static final long SWING_MS = 1000;

	// Hit marker.
	private static long markerMs;
	private static boolean markerKill;

	// Kill banner.
	private static String bannerName = "";
	private static String bannerTitle = "";
	private static String bannerStreak = "";
	private static long bannerMs;
	private static long lastKillMs;
	private static int multi;
	private static int streak;

	// Health when you last swung, so a number still shows if the server's health update beats its hurt event.
	private static Object swingTarget;
	private static float swingHealth;
	private static long swingMs;

	// Hits waiting for the server's new health: entity, health before, ticks waited.
	private static final int PENDING = 8;
	private static final Object[] WAIT = new Object[PENDING];
	private static final float[] BEFORE = new float[PENDING];
	private static final int[] WAITED = new int[PENDING];

	// Floating numbers.
	private static final int MAX = 24;
	private static final double[] NX = new double[MAX];
	private static final double[] NY = new double[MAX];
	private static final double[] NZ = new double[MAX];
	private static final float[] NAGE = new float[MAX];
	private static final String[] NTEXT = new String[MAX];
	private static final int[] NCOLOUR = new int[MAX];
	private static int numbers;
	private static long lastNanos;

	private static final String[] TENTHS = new String[1001];
	private static final float[] P = new float[4];

	private Combat() {
	}

	private static ClientConfig c() {
		return ClientConfig.get();
	}

	private static boolean on(boolean setting, Feature feature) {
		return setting && Quartz.available(feature);
	}

	/** Anything to draw this frame. */
	public static boolean pending() {
		long now = System.currentTimeMillis();
		return numbers > 0 || now - markerMs < 500 || now - bannerMs < BANNER_MS;
	}

	/** You swung at {@code target}: remember its health before the hit. */
	public static void onSwing(VersionAdapter a, Object target) {
		swingTarget = target;
		swingHealth = a.entityHealth(target);
		swingMs = System.currentTimeMillis();
	}

	/** A hit of yours landed on {@code target}. */
	public static void onHit(VersionAdapter a, Object target) {
		markerMs = System.currentTimeMillis();
		markerKill = false;
		if (!on(c().damageNumbers, Feature.DAMAGE_NUMBERS)) {
			return;
		}
		float before = a.entityHealth(target);
		if (target == swingTarget && markerMs - swingMs < SWING_MS) {
			before = Math.max(before, swingHealth);
		}
		if (before <= 0) {
			return; // health unknown (hidden by the server): marker only
		}
		int free = -1;
		for (int i = 0; i < PENDING; i++) {
			if (WAIT[i] == target) {
				return; // already waiting on this one; the next health drop covers both hits
			}
			if (WAIT[i] == null && free < 0) {
				free = i;
			}
		}
		if (free >= 0) {
			WAIT[free] = target;
			BEFORE[free] = before;
			WAITED[free] = 0;
		}
	}

	/** Your target died: the marker turns red and the banner shows. */
	public static void onKill(VersionAdapter a, Object target) {
		long now = System.currentTimeMillis();
		markerMs = now;
		markerKill = true;
		multi = now - lastKillMs < MULTI_WINDOW_MS ? multi + 1 : 1;
		lastKillMs = now;
		streak++;
		if (!on(c().killBanner, Feature.KILL_BANNER)) {
			return;
		}
		bannerMs = now;
		bannerName = a.entityName(target);
		bannerTitle = MULTI[Math.min(multi, MULTI.length - 1)];
		bannerStreak = streak >= 2 ? streak + " kill streak" : "";
	}

	/** Every client tick. */
	public static void tick(VersionAdapter a) {
		if (a.position() == null || a.healthFraction() == 0) {
			// Out of the world, or dead: the streak ends.
			streak = 0;
			multi = 0;
			numbers = 0;
			for (int i = 0; i < PENDING; i++) {
				WAIT[i] = null;
			}
			return;
		}
		for (int i = 0; i < PENDING; i++) {
			Object e = WAIT[i];
			if (e == null) {
				continue;
			}
			float now = a.entityHealth(e);
			if (now >= 0 && now < BEFORE[i] - 0.05f) {
				spawn(a, e, BEFORE[i] - now);
				WAIT[i] = null;
			} else if (now < 0 || ++WAITED[i] > 10) {
				WAIT[i] = null; // gone, or no change came (absorbed, or the server hides health)
			}
		}
	}

	private static void spawn(VersionAdapter a, Object e, float damage) {
		double[] box = a.entityBox(e);
		if (box == null) {
			return;
		}
		int i = numbers < MAX ? numbers++ : 0;
		ThreadLocalRandom rnd = ThreadLocalRandom.current();
		NX[i] = box[0] + (rnd.nextDouble() - 0.5) * box[4] * 1.4;
		NY[i] = box[1] + box[3] + 0.15 + rnd.nextDouble() * 0.2;
		NZ[i] = box[2] + (rnd.nextDouble() - 0.5) * box[4] * 1.4;
		NAGE[i] = 0;
		NTEXT[i] = format(damage);
		// White for chip damage, through gold and orange to red for big hits.
		float heavy = Math.min(1f, damage / 8f);
		NCOLOUR[i] = heavy < 0.5f ? Smooth.mix(0xFFFFFFFF, 0xFFFFC44D, heavy * 2) : Smooth.mix(0xFFFFC44D, 0xFFFF4D5E, heavy * 2 - 1);
		if (numbers == 1) {
			lastNanos = 0;
		}
	}

	/** "3.5", "4": one decimal, dropped when whole. Cached per tenth. */
	static String format(float damage) {
		int tenths = Math.max(1, Math.min(TENTHS.length - 1, Math.round(damage * 10)));
		String s = TENTHS[tenths];
		return s != null ? s : (TENTHS[tenths] = tenths % 10 == 0 ? Integer.toString(tenths / 10) : (tenths / 10) + "." + (tenths % 10));
	}

	/** {@link #render} on the version's current HUD backend. */
	public static void renderHud() {
		render(Quartz.adapter().render());
	}

	public static void render(RenderBackend r) {
		float w = r.screenWidth();
		float h = r.screenHeight();
		renderNumbers(r, w, h);
		long now = System.currentTimeMillis();
		if (on(c().hitMarker, Feature.HIT_MARKER)) {
			renderMarker(r, w, h, now);
		}
		if (now - bannerMs < BANNER_MS && on(c().killBanner, Feature.KILL_BANNER)) {
			renderBanner(r, w, h, now - bannerMs);
		}
	}

	private static void renderNumbers(RenderBackend r, float w, float h) {
		long t = System.nanoTime();
		float dt = lastNanos == 0 ? 0f : Math.min(0.05f, (t - lastNanos) / 1e9f);
		lastNanos = numbers == 0 ? 0 : t;
		for (int i = 0; i < numbers; ) {
			NAGE[i] += dt;
			float life = NAGE[i] / 1.1f;
			if (life >= 1f) {
				int last = --numbers;
				NX[i] = NX[last];
				NY[i] = NY[last];
				NZ[i] = NZ[last];
				NAGE[i] = NAGE[last];
				NTEXT[i] = NTEXT[last];
				NCOLOUR[i] = NCOLOUR[last];
				continue;
			}
			// Rises and slows, pops in a touch large, fades over the last third.
			float rise = 0.7f * (1f - (1f - life) * (1f - life));
			if (View.valid() && View.project(NX[i], NY[i] + rise, NZ[i], w, h, P)) {
				float pop = NAGE[i] < 0.12f ? 1.45f - NAGE[i] / 0.12f * 0.45f : 1f;
				float alpha = life < 0.66f ? 1f : 1f - (life - 0.66f) / 0.34f;
				float size = Math.max(6f, Math.min(13f, 0.42f * P[3] / 1.6f)) * pop;
				int colour = ((int) (alpha * 255) << 24) | (NCOLOUR[i] & 0xFFFFFF);
				float heart = size * 0.8f;
				float tw = Smooth.width(r, NTEXT[i], size, true);
				float x = P[0] - (tw + heart + 1.5f) / 2;
				float y = P[1] - Smooth.lineHeight(r, size, true) / 2;
				// A soft drop shadow keeps pale numbers readable against the sky.
				Smooth.text(r, NTEXT[i], x + 0.7f, y + 0.7f, size, (int) (alpha * 120) << 24, true);
				float end = Smooth.text(r, NTEXT[i], x, y, size, colour, true);
				Smooth.icon(r, "heart", end + 1.5f, P[1] - heart / 2, heart, ((int) (alpha * 230) << 24) | 0xFF4D5E);
			}
			i++;
		}
	}

	private static void renderMarker(RenderBackend r, float w, float h, long now) {
		long age = now - markerMs;
		long life = markerKill ? 450 : 260;
		if (age >= life) {
			return;
		}
		float t = age / (float) life;
		float size = (markerKill ? 17f : 13f) * (0.85f + 0.15f * Math.min(1f, t * 4));
		int alpha = (int) (255 * (1f - t * t));
		float cx = w / 2;
		float cy = h / 2;
		Smooth.sprite(r, "hitmarker", cx + 0.5f, cy + 0.5f, size, size, (alpha * 3 / 5) << 24);
		Smooth.sprite(r, "hitmarker", cx, cy, size, size, alpha << 24 | (markerKill ? 0xFF4D5E : 0xFFFFFF));
	}

	private static void renderBanner(RenderBackend r, float w, float h, long age) {
		float t = age / 1000f;
		float in = Math.min(1f, t / 0.18f);
		float out = Math.min(1f, (BANNER_MS - age) / 400f);
		float alpha = Math.min(in, out);
		// Pops in from a little larger, eased.
		float scale = 1f + 0.25f * (1f - in) * (1f - in);
		float cx = w / 2;
		float top = Math.round(h * 0.2f);
		boolean multiKill = multi >= 2;
		int accent = multiKill ? 0xFFD166 : 0xFF4D5E;
		float titleSize = 7f;
		float nameSize = 12f;
		float streakSize = 6.5f;
		float titleW = Smooth.width(r, bannerTitle, titleSize, true);
		float nameW = Smooth.width(r, bannerName, nameSize, true);
		float streakW = bannerStreak.isEmpty() ? 0 : Smooth.width(r, bannerStreak, streakSize, false);
		float panelW = Math.round(Math.max(Math.max(titleW, nameW), streakW) + 28);
		// Whole pixels, so the rounded panel has no seams.
		float left = Math.round(cx - panelW / 2);
		float right = left + panelW;
		float panelH = bannerStreak.isEmpty() ? 34 : 43;
		r.push();
		r.translate(cx, top + panelH / 2);
		r.scale(scale);
		r.translate(-cx, -(top + panelH / 2));
		int a = (int) (alpha * 255);
		Smooth.roundRect(r, left, top, right, top + panelH, 7, ((int) (alpha * 150)) << 24 | 0x0B0C10);
		Smooth.rect(r, left + 7, top, right - 7, top + 1.5f, a << 24 | accent);
		Smooth.text(r, bannerTitle, cx - titleW / 2, top + 5, titleSize, a << 24 | accent, true);
		Smooth.text(r, bannerName, cx - nameW / 2, top + 14, nameSize, a << 24 | 0xFFFFFF, true);
		if (!bannerStreak.isEmpty()) {
			Smooth.text(r, bannerStreak, cx - streakW / 2, top + 31, streakSize, ((int) (alpha * 170)) << 24 | 0xFFFFFF, false);
		}
		r.pop();
	}
}
