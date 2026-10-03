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
import java.util.List;

/**
 * The Doohickey menu on 1.8.9 (Right Shift): HUD, World and Game tabs, each a
 * three-column grid built from the same option descriptions as every other
 * version. The game keeps running underneath.
 */
final class QuartzLegacyScreen extends Screen {
	private static final int DONE = 1000;
	private static final int EDIT_HUD = 1001;
	private static final int TAB = 2000;
	private static final int ROW = 22;
	private static final int COLUMN = 130;
	private static final int GAP = 6;
	private static final int COLUMNS = 3;
	private static final int WIDTH = COLUMN * COLUMNS + GAP * (COLUMNS - 1);
	private static final String[] TABS = {"HUD", "World", "Game"};

	/** Remembered while the game runs, so reopening lands where you left off. */
	private static int tab;

	private final List<Option> options = new ArrayList<>();

	private static List<Option> optionsFor(int tab) {
		switch (tab) {
			case 1: return WorldOptions.all();
			case 2: return GameOptions.all();
			default: return HudOptions.all();
		}
	}

	@Override
	public void init() {
		this.buttons.clear();
		this.options.clear();
		this.options.addAll(optionsFor(tab));
		int left = left();
		int top = top();

		int tabWidth = (WIDTH - GAP * (TABS.length - 1)) / TABS.length;
		for (int t = 0; t < TABS.length; t++) {
			ButtonWidget b = new ButtonWidget(TAB + t, left + t * (tabWidth + GAP), top, tabWidth, 20, TABS[t]);
			b.active = t != tab;
			this.buttons.add(b);
		}

		int gridTop = top + 28;
		for (int i = 0; i < options.size(); i++) {
			int x = left + (i % COLUMNS) * (COLUMN + GAP);
			int y = gridTop + (i / COLUMNS) * ROW;
			this.buttons.add(new ButtonWidget(i, x, y, COLUMN, 20, options.get(i).text()));
		}
		if (options.isEmpty()) {
			// Nothing on this tab for this version; the Done button still works.
			gridTop += ROW;
		}

		int bottom = gridTop + rows() * ROW + 6;
		if (tab == 0 && !options.isEmpty()) {
			this.buttons.add(new ButtonWidget(EDIT_HUD, left, bottom, COLUMN, 20, "Edit HUD layout"));
		}
		this.buttons.add(new ButtonWidget(DONE, left + WIDTH - COLUMN, bottom, COLUMN, 20, "Done"));
	}

	private int rows() {
		return Math.max(1, (options.size() + COLUMNS - 1) / COLUMNS);
	}

	private int left() {
		return (this.width - WIDTH) / 2;
	}

	/** Tabs, the grid and the bottom row, centred; never above the title. */
	private int top() {
		int height = 28 + rows() * ROW + 6 + 20;
		return Math.max(32, (this.height - height) / 2);
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
		if (button.id >= TAB) {
			tab = button.id - TAB;
			init();
			return;
		}
		Option option = options.get(button.id);
		Safe.run("menu.click", option::click);
		// Some choices reveal others (e.g. the zoom level), so rebuild.
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
		int left = left() - 12;
		int right = left() + WIDTH + 12;
		int top = top() - 26;
		int bottom = top() + 28 + rows() * ROW + 6 + 20 + 12;
		r.fill(0, 0, this.width, this.height, 0x70000000);
		r.fill(left, top, right, bottom, 0xE60E0D14);
		r.fill(left, top, right, top + 2, 0xFF8B7CF6);
		r.outline(left, top, right - left, bottom - top, 0xFF2A2833);
		r.centeredText("Doohickey Client", this.width / 2, top + 9, 0xFFFFFFFF, true);
		super.render(mouseX, mouseY, tickDelta);
	}

	@Override
	public boolean shouldPauseGame() {
		return false;
	}
}
