package dev.quartz.core.ui;

import dev.quartz.core.Feature;
import dev.quartz.core.Quartz;
import dev.quartz.core.config.ClientConfig;

/** Smooth hotbar: the selection box glides to the new slot instead of jumping. */
public final class SmoothHotbar {
	private static float shown = -1;
	private static long lastNanos;

	private SmoothHotbar() {
	}

	/** How far (in pixels, 20 per slot) the selector should sit from where vanilla puts it this frame. */
	public static int offset(int selected) {
		if (!ClientConfig.get().smoothHotbar || !Quartz.available(Feature.SMOOTH_HOTBAR)) {
			shown = selected;
			return 0;
		}
		long now = System.nanoTime();
		float dt = lastNanos == 0 ? 1f : Math.min(0.1f, (now - lastNanos) / 1e9f);
		lastNanos = now;
		if (shown < 0) {
			shown = selected;
		}
		// Time-based easing, the same at any frame rate.
		shown += (selected - shown) * (1f - (float) Math.exp(-dt * 22f));
		if (Math.abs(shown - selected) < 0.01f) {
			shown = selected;
		}
		return Math.round((shown - selected) * 20);
	}
}
