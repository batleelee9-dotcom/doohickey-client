package dev.quartz.client.adapter;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.FilterMode;
import dev.quartz.core.RenderBackend;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

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
	public float guiScale() {
		return Minecraft.getInstance().getWindow().getGuiScale();
	}

	/** A texture sampled smoothly (vanilla's DynamicTexture is nearest-neighbour). */
	private static final class SmoothTexture extends DynamicTexture {
		SmoothTexture(String name, int width, int height) {
			super(() -> name, width, height, true);
			this.sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
		}
	}

	private final Map<String, Integer> handles = new HashMap<>();
	private final List<Identifier> ids = new ArrayList<>();
	private final List<int[]> sizes = new ArrayList<>();

	@Override
	public int image(String key, int width, int height, Supplier<int[]> pixels) {
		Integer handle = handles.get(key);
		if (handle != null) {
			return handle;
		}
		int[] data = pixels.get();
		SmoothTexture texture = new SmoothTexture("Doohickey " + key, width, height);
		NativeImage image = texture.getPixels();
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				image.setPixel(x, y, data[y * width + x]);
			}
		}
		texture.upload();
		Identifier id = Identifier.fromNamespaceAndPath("quartz", "smooth/" + ids.size());
		Minecraft.getInstance().getTextureManager().register(id, texture);
		ids.add(id);
		sizes.add(new int[] {width, height});
		handles.put(key, ids.size() - 1);
		return ids.size() - 1;
	}

	@Override
	public void drawImage(int handle, float[] q, int count, int argb) {
		Identifier id = ids.get(handle);
		int tw = sizes.get(handle)[0];
		int th = sizes.get(handle)[1];
		float s = guiScale();
		for (int i = 0; i < count; i++) {
			int o = i * 8;
			// blit takes whole pixels, so draw in screen pixels: shift to the quad, then scale GUI → screen.
			int dw = Math.max(1, Math.round((q[o + 2] - q[o]) * s));
			int dh = Math.max(1, Math.round((q[o + 3] - q[o + 1]) * s));
			int rw = Math.max(1, Math.round((q[o + 6] - q[o + 4]) * tw));
			int rh = Math.max(1, Math.round((q[o + 7] - q[o + 5]) * th));
			g.pose().pushMatrix();
			g.pose().translate(q[o], q[o + 1]);
			g.pose().scale(1f / s, 1f / s);
			g.blit(RenderPipelines.GUI_TEXTURED, id, 0, 0, q[o + 4] * tw, q[o + 5] * th, dw, dh, rw, rh, tw, th, argb);
			g.pose().popMatrix();
		}
	}

	@Override
	public void item(Object stack, int x, int y) {
		if (stack instanceof ItemStack item && !item.isEmpty()) {
			g.item(item, x, y);
		}
	}
}
