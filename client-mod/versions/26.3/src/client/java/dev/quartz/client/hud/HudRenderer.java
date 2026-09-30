package dev.quartz.client.hud;

import dev.quartz.client.adapter.PipelineBackend;
import dev.quartz.client.map.Minimap;
import dev.quartz.client.screen.QuartzScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.List;

/** Places, scales and draws every enabled module. */
public final class HudRenderer {
	private HudRenderer() {
	}

	/** Every module the player can arrange, including the minimap. */
	public static List<HudModule> modules() {
		List<HudModule> all = new ArrayList<>(HudModules.ALL);
		all.add(Minimap.MODULE);
		return all;
	}

	public record Placement(int x, int y, int w, int h, float scale) {
		public boolean contains(double mx, double my) {
			return mx >= x && mx < x + w && my >= y && my < y + h;
		}
	}

	public static Placement place(HudModule m, int screenW, int screenH) {
		var s = m.state();
		float scale = Math.clamp(s.scale, 0.5f, 2.5f);
		int w = Math.round(m.width() * scale);
		int h = Math.round(m.height() * scale);
		int x = Math.round(Math.clamp(s.x, 0f, 1f) * Math.max(0, screenW - w));
		int y = Math.round(Math.clamp(s.y, 0f, 1f) * Math.max(0, screenH - h));
		return new Placement(x, y, w, h, scale);
	}

	/** The in-game pass. The editor screen draws its own preview instead. */
	public static void renderInGame(GuiGraphicsExtractor g) {
		PipelineBackend.begin(g);
		Minecraft mc = Minecraft.getInstance();
		if (mc.gui.hud.isHidden() || mc.player == null || mc.gui.screen() instanceof QuartzScreen) {
			return;
		}
		for (HudModule m : modules()) {
			if (m.state().enabled && m.hasContent()) {
				draw(g, m, false);
			}
		}
	}

	public static void draw(GuiGraphicsExtractor g, HudModule m, boolean preview) {
		Placement p = place(m, g.guiWidth(), g.guiHeight());
		g.pose().pushMatrix();
		g.pose().translate(p.x(), p.y());
		g.pose().scale(p.scale(), p.scale());
		m.render(g, preview);
		g.pose().popMatrix();
	}
}
