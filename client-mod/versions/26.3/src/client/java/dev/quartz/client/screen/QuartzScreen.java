package dev.quartz.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import dev.quartz.client.hud.HudModule;
import dev.quartz.client.hud.HudRenderer;
import dev.quartz.core.config.ClientConfig;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * The HUD editor: drag modules to move them, scroll over one to resize.
 * Settings live in the Doohickey menu ({@link MenuScreen}). The game keeps
 * running underneath.
 */
public final class QuartzScreen extends Screen {
	private @Nullable HudModule dragging;
	private double grabX;
	private double grabY;

	public QuartzScreen() {
		super(Component.literal("Doohickey Client"));
	}

	@Override
	protected void init() {
		int x = this.width / 2 - 154;
		add(x, 6, 100, "Settings", () -> this.minecraft.gui.setScreen(new MenuScreen()));
		add(x + 104, 6, 100, "Reset layout", () -> ClientConfig.get().modules.clear());
		add(x + 208, 6, 100, "Done", this::onClose);
	}

	private void add(int x, int y, int w, String label, Runnable action) {
		this.addRenderableWidget(Button.builder(Component.literal(label), b -> {
			action.run();
			ClientConfig.get().save();
		}).bounds(x, y, w, 20).build());
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		// A light dim instead of the menu blur: the HUD being arranged stays visible.
		g.fill(0, 0, this.width, this.height, 0x40000000);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		for (HudModule m : HudRenderer.modules()) {
			if (!m.state().enabled) {
				continue;
			}
			HudRenderer.draw(g, m, true);
			HudRenderer.Placement p = HudRenderer.place(m, this.width, this.height);
			boolean hot = m == dragging || p.contains(mouseX, mouseY);
			g.outline(p.x() - 1, p.y() - 1, p.w() + 2, p.h() + 2, hot ? 0xFFB8ADFF : 0x80FFFFFF);
		}
		g.centeredText(this.font, "Drag to move · Scroll to resize · Right Shift to close", this.width / 2, 32, 0xFFCCCCCC);
		super.extractRenderState(g, mouseX, mouseY, a);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (super.mouseClicked(event, doubleClick)) {
			return true;
		}
		List<HudModule> modules = HudRenderer.modules();
		for (int i = modules.size() - 1; i >= 0; i--) {
			HudModule m = modules.get(i);
			if (!m.state().enabled) {
				continue;
			}
			HudRenderer.Placement p = HudRenderer.place(m, this.width, this.height);
			if (p.contains(event.x(), event.y())) {
				dragging = m;
				grabX = event.x() - p.x();
				grabY = event.y() - p.y();
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (dragging == null) {
			return super.mouseDragged(event, dx, dy);
		}
		HudRenderer.Placement p = HudRenderer.place(dragging, this.width, this.height);
		int freeX = Math.max(1, this.width - p.w());
		int freeY = Math.max(1, this.height - p.h());
		dragging.state().x = (float) Math.clamp((event.x() - grabX) / freeX, 0.0, 1.0);
		dragging.state().y = (float) Math.clamp((event.y() - grabY) / freeY, 0.0, 1.0);
		return true;
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (dragging != null) {
			dragging = null;
			ClientConfig.get().save();
			return true;
		}
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		for (HudModule m : HudRenderer.modules()) {
			if (m.state().enabled && HudRenderer.place(m, this.width, this.height).contains(x, y)) {
				m.state().scale = (float) Math.clamp(m.state().scale + scrollY * 0.1, 0.5, 2.5);
				ClientConfig.get().save();
				return true;
			}
		}
		return super.mouseScrolled(x, y, scrollX, scrollY);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.key() == InputConstants.KEY_RSHIFT) {
			this.onClose();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void onClose() {
		ClientConfig.get().save();
		super.onClose();
	}
}
