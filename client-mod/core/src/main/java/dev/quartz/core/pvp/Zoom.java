package dev.quartz.core.pvp;

import dev.quartz.core.Feature;
import dev.quartz.core.Quartz;
import dev.quartz.core.config.ClientConfig;

/** Hold the zoom key to narrow the field of view. Each version's FOV hook calls {@link #apply}. */
public final class Zoom {
	private static float current = 1f;
	private static long lastNanos;

	private Zoom() {
	}

	/** The FOV to use this frame, given the game's own. */
	public static float apply(float fov) {
		ClientConfig c = ClientConfig.get();
		boolean on = c.zoomEnabled && Quartz.available(Feature.ZOOM) && Quartz.adapter().zoomKeyDown();
		float target = on ? Math.max(1.5f, Math.min(10f, c.zoomFactor)) : 1f;
		long now = System.nanoTime();
		float dt = lastNanos == 0 ? 1f : Math.min(1f, (now - lastNanos) / 1e9f);
		lastNanos = now;
		if (c.zoomSmooth) {
			// Time-based easing, so it feels the same at 60 and 300 FPS.
			current += (target - current) * (1f - (float) Math.exp(-dt * 14f));
			if (Math.abs(current - target) < 0.005f) {
				current = target;
			}
		} else {
			current = target;
		}
		return fov / current;
	}

	/** Whether the view is zoomed in at all (for slowing the mouse, if a version wants to). */
	public static boolean active() {
		return current > 1.01f;
	}
}
