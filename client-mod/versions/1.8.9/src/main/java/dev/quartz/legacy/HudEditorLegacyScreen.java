package dev.quartz.legacy;

import dev.quartz.core.Quartz;
import dev.quartz.core.RenderBackend;
import dev.quartz.core.Safe;
import dev.quartz.core.hud.HudEditor;
import net.minecraft.client.gui.screen.Screen;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

/** Drag HUD elements to move them, scroll to resize (1.8.9). The editing logic is shared: {@link HudEditor}. */
final class HudEditorLegacyScreen extends Screen {
	private final Screen parent;
	private final HudEditor editor = new HudEditor();

	HudEditorLegacyScreen(Screen parent) {
		this.parent = parent;
	}

	private RenderBackend r() {
		return Quartz.adapter().render();
	}

	@Override
	public void render(int mouseX, int mouseY, float tickDelta) {
		r().fill(0, 0, this.width, this.height, 0x40000000);
		Safe.run("hud.editor", () -> editor.render(r(), mouseX, mouseY));
		super.render(mouseX, mouseY, tickDelta);
	}

	@Override
	protected void mouseClicked(int mouseX, int mouseY, int button) {
		if (button == 0) {
			editor.press(r(), mouseX, mouseY);
		}
	}

	@Override
	protected void mouseDragged(int mouseX, int mouseY, int button, long heldFor) {
		editor.drag(r(), mouseX, mouseY);
	}

	@Override
	protected void mouseReleased(int mouseX, int mouseY, int button) {
		editor.release();
	}

	@Override
	public void handleMouse() {
		super.handleMouse();
		int wheel = Mouse.getEventDWheel();
		if (wheel != 0) {
			int x = Mouse.getEventX() * this.width / this.client.width;
			int y = this.height - Mouse.getEventY() * this.height / this.client.height - 1;
			editor.scroll(r(), x, y, wheel);
		}
	}

	@Override
	protected void keyPressed(char character, int code) {
		if (code == Keyboard.KEY_RSHIFT || code == Keyboard.KEY_ESCAPE) {
			editor.release();
			this.client.setScreen(parent);
		}
	}

	@Override
	public boolean shouldPauseGame() {
		return false;
	}
}
