package dev.quartz.core.hud;

import dev.quartz.core.Quartz;
import dev.quartz.core.RenderBackend;
import dev.quartz.core.Safe;
import dev.quartz.core.config.ClientConfig;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** The shared HUD: element registry, placement and drawing. */
public final class Hud {
	/** Every element the core provides; versions may add their own on top. */
	public static final List<HudElement> ELEMENTS = Collections.unmodifiableList(Arrays.<HudElement>asList(
		new Elements.Fps(), new Elements.Cps(), new Elements.Ping(), new Elements.Coordinates(), new Elements.Reach(),
		new Elements.Keystrokes(), new Elements.Potions(), new Elements.Armor(),
		new Elements.Clock(), new Elements.Session(), new Elements.Memory(), new Elements.Server(), new Elements.Direction(),
		new Elements.Speed(), new Elements.Day(), new Elements.Saturation(), new Elements.Arrows(), new Elements.Combo(),
		new Elements.BlockInfo(), new Elements.Biome()));

	private Hud() {
	}

	private static List<HudElement> available;
	private static Object availableFor;

	/** Elements this Minecraft version supports (see CompatRegistry); worked out once per version. */
	public static List<HudElement> available() {
		Object version = Quartz.adapter().version();
		if (available == null || availableFor != version) {
			List<HudElement> out = new ArrayList<>();
			for (HudElement e : ELEMENTS) {
				if (Quartz.available(e.feature)) {
					out.add(e);
				}
			}
			available = Collections.unmodifiableList(out);
			availableFor = version;
		}
		return available;
	}

	public static final class Placement {
		public final int x;
		public final int y;
		public final int w;
		public final int h;
		public final float scale;

		Placement(int x, int y, int w, int h, float scale) {
			this.x = x;
			this.y = y;
			this.w = w;
			this.h = h;
			this.scale = scale;
		}

		public boolean contains(double mx, double my) {
			return mx >= x && mx < x + w && my >= y && my < y + h;
		}
	}

	private static float clamp(float v, float min, float max) {
		return Math.max(min, Math.min(max, v));
	}

	/** Positions are fractions of the free space on each axis, so elements stay anchored to their edge. */
	public static Placement place(HudElement e, RenderBackend r) {
		ClientConfig.ModuleState s = e.state();
		float scale = clamp(s.scale, 0.5f, 2.5f);
		int w = Math.round(e.width(r) * scale);
		int h = Math.round(e.height(r) * scale);
		int x = Math.round(clamp(s.x, 0f, 1f) * Math.max(0, r.screenWidth() - w));
		int y = Math.round(clamp(s.y, 0f, 1f) * Math.max(0, r.screenHeight() - h));
		return new Placement(x, y, w, h, scale);
	}

	/** The in-game pass, called by each version's HUD hook. */
	public static void renderAll(RenderBackend r) {
		List<HudElement> elements = available();
		int sw = r.screenWidth();
		int sh = r.screenHeight();
		// An indexed loop with an inline guard: no iterator, lambda or string per element per frame.
		for (int i = 0; i < elements.size(); i++) {
			HudElement e = elements.get(i);
			if (!e.state().enabled || !Safe.enabled(e.hook)) {
				continue;
			}
			try {
				if (e.hasContent()) {
					draw(r, e, false, sw, sh);
				}
			} catch (Throwable t) {
				Safe.report(e.hook, t);
			}
		}
	}

	public static void draw(RenderBackend r, HudElement e, boolean preview) {
		draw(r, e, preview, r.screenWidth(), r.screenHeight());
	}

	/** Same maths as {@link #place}, without allocating a Placement every frame. */
	private static void draw(RenderBackend r, HudElement e, boolean preview, int sw, int sh) {
		ClientConfig.ModuleState s = e.state();
		float scale = clamp(s.scale, 0.5f, 2.5f);
		int w = Math.round(e.width(r) * scale);
		int h = Math.round(e.height(r) * scale);
		int x = Math.round(clamp(s.x, 0f, 1f) * Math.max(0, sw - w));
		int y = Math.round(clamp(s.y, 0f, 1f) * Math.max(0, sh - h));
		r.push();
		try {
			r.translate(x, y);
			r.scale(scale);
			e.render(r, preview);
		} finally {
			r.pop();
		}
	}
}
