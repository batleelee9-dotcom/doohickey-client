package dev.quartz.core.hud;

import dev.quartz.core.Feature;
import dev.quartz.core.RenderBackend;
import dev.quartz.core.config.ClientConfig;

/**
 * A draggable HUD element, written once for every Minecraft version: it
 * draws only through {@link RenderBackend} and reads the game only through
 * the version adapter. It draws at (0, 0) in unscaled units; placement and
 * scale are applied by {@link Hud}.
 */
public abstract class HudElement {
	public final String id;
	public final String name;
	public final Feature feature;
	private final boolean defaultEnabled;
	private final float defaultX;
	private final float defaultY;

	protected HudElement(String id, String name, Feature feature, boolean defaultEnabled, float defaultX, float defaultY) {
		this.id = id;
		this.name = name;
		this.feature = feature;
		this.defaultEnabled = defaultEnabled;
		this.defaultX = defaultX;
		this.defaultY = defaultY;
	}

	public ClientConfig.ModuleState state() {
		return ClientConfig.get().module(id, defaultEnabled, defaultX, defaultY);
	}

	public abstract int width(RenderBackend r);

	public abstract int height(RenderBackend r);

	/** @param preview true in the HUD editor, where elements show sample data when idle. */
	public abstract void render(RenderBackend r, boolean preview);

	/** Whether there's anything to show right now. */
	public boolean hasContent() {
		return true;
	}

	/** A padded text box: the look shared by the simple text elements. */
	protected static void textBox(RenderBackend r, String text, int width) {
		ClientConfig c = ClientConfig.get();
		if (c.moduleBackground) {
			r.fill(0, 0, width, 16, 0x70000000);
		}
		r.text(text, (width - r.textWidth(text)) / 2, 4, c.textColor | 0xFF000000, c.textShadow);
	}

	protected static int boxWidth(RenderBackend r, String text) {
		return Math.max(56, r.textWidth(text) + 12);
	}
}
