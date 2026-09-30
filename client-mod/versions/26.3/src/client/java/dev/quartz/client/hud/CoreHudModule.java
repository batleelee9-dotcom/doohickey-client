package dev.quartz.client.hud;

import dev.quartz.client.adapter.PipelineBackend;
import dev.quartz.core.config.ClientConfig;
import dev.quartz.core.hud.HudElement;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * A HUD element from the shared core, placed and edited alongside this
 * version's own modules. Same id, same saved layout on every version.
 */
final class CoreHudModule extends HudModule {
	private final HudElement element;

	CoreHudModule(HudElement element) {
		super(element.id, element.name, true, 0f, 0f);
		this.element = element;
	}

	@Override
	public ClientConfig.ModuleState state() {
		return element.state();
	}

	@Override
	public int width() {
		return element.width(PipelineBackend.measuring());
	}

	@Override
	public int height() {
		return element.height(PipelineBackend.measuring());
	}

	@Override
	public void render(GuiGraphicsExtractor g, boolean preview) {
		element.render(PipelineBackend.begin(g), preview);
	}

	@Override
	public boolean hasContent() {
		return element.hasContent();
	}
}
