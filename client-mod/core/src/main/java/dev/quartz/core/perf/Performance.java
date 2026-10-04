package dev.quartz.core.perf;

import dev.quartz.core.Feature;
import dev.quartz.core.Quartz;
import dev.quartz.core.Safe;
import dev.quartz.core.VersionAdapter;
import dev.quartz.core.config.ClientConfig;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Dynamic render distance, particle limiting and entity render distance —
 * decisions only; each version's hooks apply them.
 */
public final class Performance {
	/** The render distance the player chose (we only ever lower it temporarily). */
	private static int preferred = -1;
	/** What we last set, to notice when the player changes it themselves. */
	private static int lastSet = -1;
	private static long lowSince;
	private static long highSince;

	private Performance() {
	}

	private static PerformanceSettings settings() {
		return ClientConfig.get().performance;
	}

	/** Once per client tick. */
	public static void tick(VersionAdapter adapter) {
		PerformanceSettings s = settings();
		// Max FPS: applied or undone once each time the switch changes (it starts on).
		if (s.maxFps != s.maxFpsApplied && Quartz.available(Feature.FPS_CAP)) {
			boolean on = s.maxFps;
			// Marked first, so a failure doesn't retry every tick.
			s.maxFpsApplied = on;
			Safe.run("perf.maxfps", () -> adapter.applyMaxFps(on, s.maxFpsRestore));
			ClientConfig.get().save();
		}
		boolean on = Quartz.available(Feature.DYNAMIC_RENDER_DISTANCE) && s.dynamicRenderDistance && adapter.inWorld();
		if (!on) {
			// Leaving a world or switching off: give the player their distance back.
			if (lastSet >= 0 && preferred >= 0 && adapter.renderDistance() == lastSet) {
				adapter.setRenderDistance(preferred);
			}
			preferred = lastSet = -1;
			lowSince = highSince = 0;
			return;
		}
		int current = adapter.renderDistance();
		if (lastSet < 0 || current != lastSet) {
			preferred = current; // first tick, or the player changed it
			lastSet = current;
		}
		int fps = adapter.fps();
		long now = System.currentTimeMillis();
		if (fps < s.targetFps) {
			highSince = 0;
			if (lowSince == 0) {
				lowSince = now;
			} else if (now - lowSince > 2000 && current > s.minRenderDistance) {
				set(adapter, current - 1);
				lowSince = now;
			}
		} else if (fps > s.targetFps * 1.3) {
			lowSince = 0;
			if (highSince == 0) {
				highSince = now;
			} else if (now - highSince > 5000 && current < preferred) {
				set(adapter, current + 1);
				highSince = now;
			}
		} else {
			lowSince = highSince = 0;
		}
	}

	private static void set(VersionAdapter adapter, int chunks) {
		adapter.setRenderDistance(chunks);
		lastSet = chunks;
	}

	/** Particle limiter: whether to keep a newly spawned particle. */
	public static boolean keepParticle() {
		int percent = ClientConfig.get().particlePercent;
		if (!Quartz.available(Feature.PARTICLES) || percent >= 100) {
			return true;
		}
		return percent > 0 && ThreadLocalRandom.current().nextInt(100) < percent;
	}

	/** Whether a block entity (sign, chest, head...) this far away (squared blocks) should be drawn. */
	public static boolean drawBlockEntity(double distanceSquared) {
		int limit = settings().blockEntityDistance;
		return limit <= 0 || !Quartz.available(Feature.TILE_ENTITY_CULLING) || distanceSquared <= (double) limit * limit;
	}

	/** Whether an entity this far away (squared blocks) should be drawn. */
	public static boolean drawEntity(double distanceSquared) {
		int limit = settings().entityDistance;
		return limit <= 0 || !Quartz.available(Feature.ENTITY_DISTANCE) || distanceSquared <= (double) limit * limit;
	}
}
