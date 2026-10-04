package dev.quartz.core.fx;

import dev.quartz.core.Feature;
import dev.quartz.core.Quartz;
import dev.quartz.core.RenderBackend;
import dev.quartz.core.VersionAdapter;
import dev.quartz.core.config.ClientConfig;
import dev.quartz.core.ui.Smooth;

/**
 * TNT countdown: a small timer over each lit TNT nearby, from its own fuse,
 * turning from green to amber to red with a bar that runs out.
 */
public final class TntTimers {
	/** Receives each lit TNT: its position (interpolated) and fuse left in ticks. */
	public interface Sink {
		void tnt(double x, double y, double z, float fuseTicks);
	}

	private static final String[] LABELS = new String[200];
	private static final float[] P = new float[4];
	private static RenderBackend backend;
	private static float width;
	private static float height;
	private static final Sink DRAW = TntTimers::draw;

	private TntTimers() {
	}

	/** Fuse ticks left for a TNT whose fuse started at vanilla's 80 but really lasts the configured length. */
	public static float assumedFuse(float vanillaTicksLeft) {
		return vanillaTicksLeft - (80 - ClientConfig.get().tntFuse * 2);
	}

	public static boolean enabled() {
		return ClientConfig.get().tntCountdown && Quartz.available(Feature.TNT_COUNTDOWN);
	}

	/** {@link #render} on the version's current HUD backend. */
	public static void renderHud() {
		render(Quartz.adapter().render(), Quartz.adapter());
	}

	public static void render(RenderBackend r, VersionAdapter a) {
		if (!enabled() || !View.valid()) {
			return;
		}
		backend = r;
		width = r.screenWidth();
		height = r.screenHeight();
		a.primedTnt(24, DRAW);
		backend = null;
	}

	private static void draw(double x, double y, double z, float fuseTicks) {
		if (!View.project(x, y, z, width, height, P)) {
			return;
		}
		RenderBackend r = backend;
		float seconds = Math.max(0, fuseTicks) / 20f;
		float s = Math.max(0.5f, Math.min(1.2f, 0.3f * P[3] / 11f));
		s = Math.max(0.375f, Math.round(s * 8) / 8f);
		String label = label(seconds);
		float textSize = 7.5f * s;
		float h = Math.round(13.5f * s);
		float w = Math.round(Smooth.width(r, label, textSize, true) + 10 * s);
		float left = Math.round(P[0] - w / 2);
		float top = Math.round(P[1] - h);
		int colour = seconds > 2 ? 0xFF4ADE80 : seconds > 1 ? 0xFFFBBF24 : 0xFFF87171;
		Smooth.roundRect(r, left, top, left + w, top + h, h * 0.45f, 0xB00E0F14);
		Smooth.text(r, label, left + 5 * s, top + 5.6f * s - Smooth.lineHeight(r, textSize, true) / 2, textSize, colour, true);
		// The fuse burning down, against the configured fuse length.
		float frac = Math.min(1f, seconds / Math.max(0.5f, ClientConfig.get().tntFuse / 10f));
		float bar = (w - 10 * s) * frac;
		Smooth.rect(r, left + 5 * s, top + h - 3.2f * s, left + w - 5 * s, top + h - 2.2f * s, 0x30FFFFFF);
		Smooth.rect(r, left + 5 * s, top + h - 3.2f * s, left + 5 * s + bar, top + h - 2.2f * s, colour);
	}

	/** "2.4s", cached per tenth. */
	private static String label(float seconds) {
		int tenths = Math.max(0, Math.min(LABELS.length - 1, Math.round(seconds * 10)));
		String s = LABELS[tenths];
		return s != null ? s : (LABELS[tenths] = (tenths / 10) + "." + (tenths % 10) + "s");
	}
}
