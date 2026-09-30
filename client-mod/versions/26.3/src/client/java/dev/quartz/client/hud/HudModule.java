package dev.quartz.client.hud;

import dev.quartz.core.config.ClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** A draggable HUD element. Subclasses draw at (0, 0) in unscaled units;
 *  placement and scale are applied by {@link HudRenderer}. */
public abstract class HudModule {
	public final String id;
	public final String name;
	private final boolean defaultEnabled;
	private final float defaultX;
	private final float defaultY;

	protected HudModule(String id, String name, boolean defaultEnabled, float defaultX, float defaultY) {
		this.id = id;
		this.name = name;
		this.defaultEnabled = defaultEnabled;
		this.defaultX = defaultX;
		this.defaultY = defaultY;
	}

	public ClientConfig.ModuleState state() {
		return ClientConfig.get().module(id, defaultEnabled, defaultX, defaultY);
	}

	public abstract int width();

	public abstract int height();

	/** @param preview true in the HUD editor, where modules show sample data when idle. */
	public abstract void render(GuiGraphicsExtractor g, boolean preview);

	/** Whether there's anything to show right now (e.g. potions only while affected). */
	public boolean hasContent() {
		return true;
	}

	protected static Minecraft mc() {
		return Minecraft.getInstance();
	}

	protected static Font font() {
		return Minecraft.getInstance().font;
	}

	/** A padded text box — the look shared by the simple text modules. */
	protected static void textBox(GuiGraphicsExtractor g, String text, int width) {
		ClientConfig c = ClientConfig.get();
		if (c.moduleBackground) {
			g.fill(0, 0, width, 16, 0x70000000);
		}
		int textWidth = font().width(text);
		g.text(font(), text, (width - textWidth) / 2, 4, c.textColor | 0xFF000000, c.textShadow);
	}

	protected static int boxWidth(String text) {
		return Math.max(56, font().width(text) + 12);
	}
}
