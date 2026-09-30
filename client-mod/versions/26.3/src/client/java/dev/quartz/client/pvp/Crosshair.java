package dev.quartz.client.pvp;

import dev.quartz.core.config.ClientConfig;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.AttackIndicatorStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.level.GameType;

/** Replaces the vanilla crosshair when enabled; otherwise defers to it. */
public final class Crosshair {
	public static final String[] STYLES = {"Cross", "Dot", "Circle", "T", "Cross + dot"};
	public static final int[] COLORS = {0xFFFFFFFF, 0xFF55FF55, 0xFF55FFFF, 0xFFFF5555, 0xFFFFFF55, 0xFFFF55FF, 0xFF000000};
	public static final String[] COLOR_NAMES = {"White", "Green", "Cyan", "Red", "Yellow", "Pink", "Black"};

	private Crosshair() {
	}

	public static HudElement wrap(HudElement vanilla) {
		return (g, delta) -> {
			if (!ClientConfig.get().customCrosshair) {
				vanilla.extractRenderState(g, delta);
				return;
			}
			Minecraft mc = Minecraft.getInstance();
			if (mc.gui.hud.isHidden() || !mc.options.getCameraType().isFirstPerson() || mc.player == null || mc.gameMode == null
				|| mc.gameMode.getPlayerMode() == GameType.SPECTATOR) {
				return;
			}
			draw(g, g.guiWidth() / 2, g.guiHeight() / 2);
			attackIndicator(g, mc);
		};
	}

	public static void draw(GuiGraphicsExtractor g, int cx, int cy) {
		ClientConfig c = ClientConfig.get();
		int size = Math.clamp(c.crosshairSize, 1, 12);
		int gap = Math.clamp(c.crosshairGap, 0, 8);
		int color = c.crosshairColor | 0xFF000000;
		switch (c.crosshairStyle) {
			case 1 -> square(g, cx - 1, cy - 1, 2, 2, color, c.crosshairOutline);
			case 2 -> {
				// A ring approximated by 1px squares; cheap and crisp at any GUI scale.
				int r = size + gap;
				for (int i = 0; i < 48; i++) {
					double a = i * Math.PI * 2 / 48;
					int x = cx + (int) Math.round(Math.cos(a) * r);
					int y = cy + (int) Math.round(Math.sin(a) * r);
					square(g, x, y, 1, 1, color, false);
				}
			}
			default -> {
				boolean top = c.crosshairStyle != 3;
				square(g, cx - gap - size, cy, size, 1, color, c.crosshairOutline);
				square(g, cx + gap + 1, cy, size, 1, color, c.crosshairOutline);
				if (top) {
					square(g, cx, cy - gap - size, 1, size, color, c.crosshairOutline);
				}
				square(g, cx, cy + gap + 1, 1, size, color, c.crosshairOutline);
				if (c.crosshairStyle == 4) {
					square(g, cx, cy, 1, 1, color, c.crosshairOutline);
				}
			}
		}
	}

	private static void square(GuiGraphicsExtractor g, int x, int y, int w, int h, int color, boolean outline) {
		if (outline) {
			g.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xC0000000);
		}
		g.fill(x, y, x + w, y + h, color);
	}

	/** The attack cooldown bar vanilla draws under its crosshair. */
	private static void attackIndicator(GuiGraphicsExtractor g, Minecraft mc) {
		if (mc.options.attackIndicator().get() != AttackIndicatorStatus.CROSSHAIR) {
			return;
		}
		float progress = mc.player.getAttackStrengthScale(0.0F);
		if (progress >= 1.0F) {
			return;
		}
		int x = g.guiWidth() / 2 - 8;
		int y = g.guiHeight() / 2 + 10;
		g.fill(x, y, x + 16, y + 2, 0x80000000);
		g.fill(x, y, x + Math.round(16 * progress), y + 2, 0xFFFFFFFF);
	}
}
