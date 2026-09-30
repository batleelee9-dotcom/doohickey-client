package dev.quartz.core.hud;

import dev.quartz.core.RenderBackend;
import dev.quartz.core.config.ClientConfig;

import java.util.List;

/**
 * Drag-to-move, scroll-to-resize HUD editing, independent of any GUI
 * toolkit: each version's editor screen forwards its mouse events here and
 * calls {@link #render} to draw.
 */
public final class HudEditor {
	/** Positions snap to a 4-pixel grid, and to the screen edges when close. */
	private static final int GRID = 4;

	private HudElement dragging;
	private double grabX;
	private double grabY;

	private static List<HudElement> elements() {
		return Hud.available();
	}

	public void render(RenderBackend r, int mouseX, int mouseY) {
		for (HudElement e : elements()) {
			if (!e.state().enabled) {
				continue;
			}
			Hud.draw(r, e, true);
			Hud.Placement p = Hud.place(e, r);
			boolean hot = e == dragging || p.contains(mouseX, mouseY);
			r.outline(p.x - 1, p.y - 1, p.w + 2, p.h + 2, hot ? 0xFFB8ADFF : 0x80FFFFFF);
		}
		r.centeredText("Drag to move · Scroll to resize · Right Shift to close", r.screenWidth() / 2, 32, 0xFFCCCCCC, true);
	}

	/** Returns true if an element was picked up. */
	public boolean press(RenderBackend r, double mouseX, double mouseY) {
		List<HudElement> all = elements();
		for (int i = all.size() - 1; i >= 0; i--) {
			HudElement e = all.get(i);
			if (!e.state().enabled) {
				continue;
			}
			Hud.Placement p = Hud.place(e, r);
			if (p.contains(mouseX, mouseY)) {
				dragging = e;
				grabX = mouseX - p.x;
				grabY = mouseY - p.y;
				return true;
			}
		}
		return false;
	}

	public void drag(RenderBackend r, double mouseX, double mouseY) {
		if (dragging == null) {
			return;
		}
		Hud.Placement p = Hud.place(dragging, r);
		int freeX = Math.max(1, r.screenWidth() - p.w);
		int freeY = Math.max(1, r.screenHeight() - p.h);
		double x = snap(mouseX - grabX, freeX);
		double y = snap(mouseY - grabY, freeY);
		dragging.state().x = (float) Math.max(0, Math.min(1, x / freeX));
		dragging.state().y = (float) Math.max(0, Math.min(1, y / freeY));
	}

	/** Snaps to the grid, and firmly to the edges. */
	private static double snap(double pos, int free) {
		if (pos < GRID * 2) {
			return 0;
		}
		if (pos > free - GRID * 2) {
			return free;
		}
		return Math.round(pos / GRID) * GRID;
	}

	public void release() {
		if (dragging != null) {
			dragging = null;
			ClientConfig.get().save();
		}
	}

	/** Returns true if the wheel resized an element. */
	public boolean scroll(RenderBackend r, double mouseX, double mouseY, double amount) {
		for (HudElement e : elements()) {
			if (e.state().enabled && Hud.place(e, r).contains(mouseX, mouseY)) {
				ClientConfig.ModuleState s = e.state();
				s.scale = (float) Math.max(0.5, Math.min(2.5, s.scale + Math.signum(amount) * 0.1));
				ClientConfig.get().save();
				return true;
			}
		}
		return false;
	}
}
