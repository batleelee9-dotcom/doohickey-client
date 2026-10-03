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

	/** Elements this Minecraft version supports (see CompatRegistry). */
	public static List<HudElement> available() {
		List<HudElement> out = new ArrayList<>();
		for (HudElement e : ELEMENTS) {
			if (Quartz.available(e.feature)) {
				out.add(e);
			}
		}
		return out;
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
		for (HudElement e : available()) {
			if (e.state().enabled && e.hasContent()) {
				Safe.run("hud." + e.id, () -> draw(r, e, false));
			}
		}
	}

	public static void draw(RenderBackend r, HudElement e, boolean preview) {
		Placement p = place(e, r);
		r.push();
		try {
			r.translate(p.x, p.y);
			r.scale(p.scale);
			e.render(r, preview);
		} finally {
			r.pop();
		}
	}
}
