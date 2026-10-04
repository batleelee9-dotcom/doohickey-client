package dev.quartz.legacy;

import dev.quartz.core.Quartz;
import dev.quartz.core.ui.ClientMenu;
import net.minecraft.client.gui.screen.Screen;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

/**
 * The Doohickey menu on 1.8.9 (Right Shift). The menu itself is drawn by the
 * shared {@link ClientMenu}; this screen only forwards input. The game keeps
 * running underneath.
 */
final class QuartzLegacyScreen extends Screen implements ClientMenu.Host {
	private final ClientMenu menu = new ClientMenu(this);

	@Override
	public void close() {
		this.client.setScreen(null);
	}

	@Override
	public void openHudEditor() {
		this.client.setScreen(new HudEditorLegacyScreen(this));
	}

	@Override
	public void render(int mouseX, int mouseY, float tickDelta) {
		menu.render(Quartz.adapter().render(), mouseX, mouseY);
	}

	@Override
	protected void mouseClicked(int mouseX, int mouseY, int button) {
		if (button == 0) {
			menu.click(mouseX, mouseY);
		} else if (button == 1) {
			menu.rightClick(mouseX, mouseY);
		}
	}

	@Override
	protected void mouseDragged(int mouseX, int mouseY, int button, long held) {
		if (button == 0) {
			menu.drag(mouseX, mouseY);
		}
	}

	@Override
	protected void mouseReleased(int mouseX, int mouseY, int button) {
		menu.release();
	}

	@Override
	public void handleMouse() {
		super.handleMouse();
		int wheel = Mouse.getEventDWheel();
		if (wheel != 0) {
			// 120 per notch on most mice; some drivers send smaller steps, count those as one notch.
			menu.scroll(Math.abs(wheel) >= 120 ? -wheel / 120.0 : -Math.signum(wheel));
		}
	}

	@Override
	protected void keyPressed(char character, int code) {
		if (code == Keyboard.KEY_RSHIFT) {
			close();
		} else if (code == Keyboard.KEY_ESCAPE) {
			if (!menu.escape()) {
				close();
			}
		} else if (code == Keyboard.KEY_BACK) {
			menu.backspace();
		} else {
			menu.typed(character);
		}
	}

	@Override
	public boolean shouldPauseGame() {
		return false;
	}
}
