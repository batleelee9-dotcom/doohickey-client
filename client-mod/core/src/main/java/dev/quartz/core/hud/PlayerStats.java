package dev.quartz.core.hud;

import dev.quartz.core.VersionAdapter;

/** Numbers sampled once a tick for the speed, combo and session elements. */
public final class PlayerStats {
	private static final double[] SPEEDS = new double[10];
	private static final long COMBO_TIMEOUT_MS = 2000;

	private static double[] lastPosition;
	private static int samples;
	private static double speed;
	private static int combo;
	private static long lastHitMs;
	private static int lastHurt;
	private static long joinedAtMs = -1;

	private PlayerStats() {
	}

	/** Called every client tick. */
	public static void tick(VersionAdapter adapter) {
		double[] p = adapter.position();
		if (p == null) {
			lastPosition = null;
			samples = 0;
			speed = 0;
			combo = 0;
			joinedAtMs = -1;
			return;
		}
		if (joinedAtMs < 0) {
			joinedAtMs = System.currentTimeMillis();
		}
		if (lastPosition != null) {
			double dx = p[0] - lastPosition[0];
			double dz = p[2] - lastPosition[2];
			SPEEDS[samples++ % SPEEDS.length] = Math.sqrt(dx * dx + dz * dz) * 20;
			double sum = 0;
			int n = Math.min(samples, SPEEDS.length);
			for (int i = 0; i < n; i++) {
				sum += SPEEDS[i];
			}
			speed = sum / n;
		}
		lastPosition = p;

		// Taking damage ends a combo, and so does going 2 s without a hit.
		int hurt = adapter.hurtTime();
		if (hurt > lastHurt) {
			combo = 0;
		}
		lastHurt = hurt;
		if (combo > 0 && System.currentTimeMillis() - lastHitMs > COMBO_TIMEOUT_MS) {
			combo = 0;
		}
	}

	/** One of your hits landed (each version's attack hook, via ReachTracker). */
	public static void hit() {
		combo++;
		lastHitMs = System.currentTimeMillis();
	}

	/** Horizontal speed in blocks per second, averaged over half a second. */
	public static double speed() {
		return speed;
	}

	public static int combo() {
		return combo;
	}

	/** Milliseconds since this world or server was joined, or 0 outside one. */
	public static long sessionMs() {
		return joinedAtMs < 0 ? 0 : System.currentTimeMillis() - joinedAtMs;
	}
}
