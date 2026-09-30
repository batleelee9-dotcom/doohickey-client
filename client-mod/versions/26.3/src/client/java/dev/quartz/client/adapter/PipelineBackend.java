package dev.quartz.client.adapter;

import dev.quartz.core.RenderBackend;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;

/**
 * {@link RenderBackend} for 1.20+ style GUI rendering (26.x: draw calls are
 * extracted into render state by {@link GuiGraphicsExtractor}). Bound to
 * one frame's extractor by {@link #begin}.
 */
public final class PipelineBackend implements RenderBackend {
	private static final PipelineBackend INSTANCE = new PipelineBackend();
	private GuiGraphicsExtractor g;

	private PipelineBackend() {
	}

	/** Binds the backend to the extractor of the frame being drawn. */
	public static PipelineBackend begin(GuiGraphicsExtractor graphics) {
		INSTANCE.g = graphics;
		return INSTANCE;
	}

	/** For measuring text outside a frame; drawing still needs {@link #begin}. */
	public static PipelineBackend measuring() {
		return INSTANCE;
	}

	static PipelineBackend current() {
		if (INSTANCE.g == null) {
			throw new IllegalStateException("No frame is being drawn");
		}
		return INSTANCE;
	}

	@Override
	public Kind kind() {
		return Kind.PIPELINE;
	}

	@Override
	public int screenWidth() {
		return g.guiWidth();
	}

	@Override
	public int screenHeight() {
		return g.guiHeight();
	}

	@Override
	public void fill(int x0, int y0, int x1, int y1, int argb) {
		g.fill(x0, y0, x1, y1, argb);
	}

	@Override
	public int text(String text, int x, int y, int argb, boolean shadow) {
		g.text(Minecraft.getInstance().font, text, x, y, argb, shadow);
		return x + textWidth(text);
	}

	@Override
	public int textWidth(String text) {
		return Minecraft.getInstance().font.width(text);
	}

	@Override
	public int fontHeight() {
		return Minecraft.getInstance().font.lineHeight;
	}

	@Override
	public void push() {
		g.pose().pushMatrix();
	}

	@Override
	public void pop() {
		g.pose().popMatrix();
	}

	@Override
	public void translate(float x, float y) {
		g.pose().translate(x, y);
	}

	@Override
	public void scale(float factor) {
		g.pose().scale(factor, factor);
	}

	@Override
	public void item(Object stack, int x, int y) {
		if (stack instanceof ItemStack item && !item.isEmpty()) {
			g.item(item, x, y);
		}
	}
}
