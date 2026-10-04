package dev.quartz.core.pvp;

import dev.quartz.core.Feature;
import dev.quartz.core.Quartz;
import dev.quartz.core.Safe;
import dev.quartz.core.config.ClientConfig;

/**
 * Stretched resolution: the world is drawn at another aspect ratio and
 * stretched to fill the window, like playing 4:3 stretched. Only the 3D view
 * changes; the HUD and menus keep their shape. Each version's projection
 * hooks call {@link #apply} or {@link #width}.
 */
public final class AspectRatio {
	public static final String[] IDS = {"native", "4:3", "5:4", "3:2", "16:10", "16:9"};
	public static final String[] NAMES = {"Off", "4:3", "5:4", "3:2", "16:10", "16:9"};
	private static final float[] RATIOS = {0, 4 / 3f, 5 / 4f, 3 / 2f, 16 / 10f, 16 / 9f};

	private static String cachedId;
	private static float cachedRatio;

	private AspectRatio() {
	}

	/** The aspect ratio to draw the world at, given the window's own. */
	public static float apply(float windowAspect) {
		float r = ratio();
		return r > 0 ? r : windowAspect;
	}

	/** The projection width that gives the chosen ratio at this height (the window's own when native). Never throws. */
	public static float width(float width, float height) {
		float aspect = width / height;
		float chosen = Safe.map("aspect", AspectRatio::apply, aspect);
		return chosen == aspect ? width : chosen * height;
	}

	/** The chosen ratio, or 0 for the window's own. */
	public static float ratio() {
		String id = ClientConfig.get().aspectRatio;
		if (id != cachedId) {
			cachedId = id;
			cachedRatio = 0;
			for (int i = 0; i < IDS.length; i++) {
				if (IDS[i].equals(id)) {
					cachedRatio = RATIOS[i];
				}
			}
		}
		return cachedRatio > 0 && Quartz.available(Feature.ASPECT_RATIO) ? cachedRatio : 0;
	}
}
