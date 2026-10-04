package dev.quartz.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import dev.quartz.client.adapter.PipelineBackend;
import dev.quartz.core.config.ClientConfig;
import dev.quartz.core.ui.ClientMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * The Doohickey menu (Right Shift) on 26.3. The menu itself is the shared
 * {@link ClientMenu}, the same one 1.8.9 uses; this screen only forwards
 * input. The game keeps running underneath.
 */
public final class MenuScreen extends Screen implements ClientMenu.Host {
	private final ClientMenu menu = new ClientMenu(this);

	public MenuScreen() {
		super(Component.literal("Doohickey Client"));
	}

	@Override
	public void close() {
		this.onClose();
	}

	@Override
	public void openHudEditor() {
		this.minecraft.gui.setScreen(new QuartzScreen());
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		// The menu draws its own backdrop (dim + glows) instead of the vanilla blur.
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		menu.render(PipelineBackend.begin(g), mouseX, mouseY);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() == 0) {
			menu.click((int) event.x(), (int) event.y());
		} else if (event.button() == 1) {
			menu.rightClick((int) event.x(), (int) event.y());
		}
		return true;
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		if (scrollY != 0) {
			menu.scroll(scrollY < 0 ? 1 : -1);
		}
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		switch (event.key()) {
			case InputConstants.KEY_RSHIFT -> this.onClose();
			case InputConstants.KEY_ESCAPE -> {
				if (!menu.escape()) {
					this.onClose();
				}
			}
			case InputConstants.KEY_BACKSPACE -> menu.backspace();
			default -> {
				return super.keyPressed(event);
			}
		}
		return true;
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		if (Character.isBmpCodePoint(event.codepoint())) {
			menu.typed((char) event.codepoint());
		}
		return true;
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
