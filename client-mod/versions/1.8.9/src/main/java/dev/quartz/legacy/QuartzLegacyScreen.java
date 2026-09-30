package dev.quartz.legacy;

import dev.quartz.core.Quartz;
import dev.quartz.core.RenderBackend;
import dev.quartz.core.Safe;
import dev.quartz.core.ui.GameOptions;
import dev.quartz.core.ui.HudOptions;
import dev.quartz.core.ui.Option;
import dev.quartz.core.ui.WorldOptions;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import org.lwjgl.input.Keyboard;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The Doohickey menu on 1.8.9 (Right Shift): HUD, World and Game columns,
 * built from the same option descriptions as every other version. The game
 * keeps running underneath.
 */
final class QuartzLegacyScreen extends Screen {
	private static final int DONE = 1000;
	private static final int EDIT_HUD = 1001;
	private static final int ROW = 21;
	private static final int COLUMN = 140;
	private static final int GAP = 6;
	private static final String[] TITLES = {"HUD", "World", "Game"};

	private final List<List<Option>> columns = new ArrayList<>();
	private final List<Option> options = new ArrayList<>();

	@Override
	public void init() {
		this.buttons.clear();
		this.options.clear();
		this.columns.clear();
		columns.addAll(Arrays.asList(HudOptions.all(), WorldOptions.all(), GameOptions.all()));
		int top = top();
		for (int c = 0; c < columns.size(); c++) {
			List<Option> column = columns.get(c);
			for (int i = 0; i < column.size(); i++) {
				this.buttons.add(new ButtonWidget(options.size(), left(c), top + i * ROW, COLUMN, 20, column.get(i).text()));
				options.add(column.get(i));
			}
		}
		int bottom = top + rows() * ROW + 6;
		if (!columns.get(0).isEmpty()) {
			this.buttons.add(new ButtonWidget(EDIT_HUD, left(0), bottom, COLUMN, 20, "Edit HUD layout"));
		}
		this.buttons.add(new ButtonWidget(DONE, left(2), bottom, COLUMN, 20, "Done"));
	}

	private int left(int column) {
		return this.width / 2 - (COLUMN * 3 + GAP * 2) / 2 + column * (COLUMN + GAP);
	}

	private int rows() {
		int rows = 0;
		for (List<Option> c : columns) {
			rows = Math.max(rows, c.size());
		}
		return rows;
	}

	private int top() {
		return Math.max(36, this.height / 2 - (rows() * ROW) / 2);
	}

	@Override
	protected void buttonClicked(ButtonWidget button) {
		if (button.id == DONE) {
			this.client.setScreen(null);
			return;
		}
		if (button.id == EDIT_HUD) {
			this.client.setScreen(new HudEditorLegacyScreen(this));
			return;
		}
		Option option = options.get(button.id);
		Safe.run("menu.click", option::click);
		// Some choices reveal others (e.g. the FPS target), so rebuild.
		init();
	}

	@Override
	protected void keyPressed(char character, int code) {
		// The key that opened the menu closes it too.
		if (code == Keyboard.KEY_RSHIFT) {
			this.client.setScreen(null);
			return;
		}
		super.keyPressed(character, code);
	}

	@Override
	public void render(int mouseX, int mouseY, float tickDelta) {
		RenderBackend r = Quartz.adapter().render();
		int left = left(0) - 10;
		int right = left(2) + COLUMN + 10;
		int top = top() - 34;
		int bottom = top() + rows() * ROW + 34;
		r.fill(0, 0, this.width, this.height, 0x60000000);
		r.fill(left, top, right, bottom, 0xE0101014);
		r.outline(left, top, right - left, bottom - top, 0xFF2A2A30);
		r.centeredText("Doohickey Client", this.width / 2, top + 7, 0xFFB8ADFF, false);
		for (int c = 0; c < TITLES.length; c++) {
			if (!columns.get(c).isEmpty()) {
				r.text(TITLES[c], left(c), top() - 11, 0xFF9A9AA6, false);
			}
		}
		super.render(mouseX, mouseY, tickDelta);
	}

	@Override
	public boolean shouldPauseGame() {
		return false;
	}
}
