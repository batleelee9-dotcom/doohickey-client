package dev.quartz.legacy;

import com.mojang.blaze3d.platform.GlStateManager;
import dev.quartz.core.RenderBackend;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.DiffuseLighting;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.util.Window;
import net.minecraft.item.ItemStack;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/** {@link RenderBackend} for 1.8.9–1.12.2: fixed-function OpenGL through GlStateManager. */
final class LegacyGlBackend implements RenderBackend {
	static final LegacyGlBackend INSTANCE = new LegacyGlBackend();

	private LegacyGlBackend() {
	}

	@Override
	public Kind kind() {
		return Kind.LEGACY_OPENGL;
	}

	// The scaled window, recomputed only when the size or GUI scale changes (it was
	// rebuilt for every query, dozens of times a frame).
	private int keyW = -1;
	private int keyH;
	private int keyScale;
	private boolean keyUnicode;
	private Window window;

	private Window window() {
		MinecraftClient mc = MinecraftClient.getInstance();
		boolean unicode = mc.forcesUnicodeFont();
		if (window == null || mc.width != keyW || mc.height != keyH || mc.options.guiScale != keyScale || unicode != keyUnicode) {
			window = new Window(mc);
			keyW = mc.width;
			keyH = mc.height;
			keyScale = mc.options.guiScale;
			keyUnicode = unicode;
		}
		return window;
	}

	@Override
	public int screenWidth() {
		return window().getWidth();
	}

	@Override
	public int screenHeight() {
		return window().getHeight();
	}

	@Override
	public void fill(int x0, int y0, int x1, int y1, int argb) {
		DrawableHelper.fill(x0, y0, x1, y1, argb);
	}

	@Override
	public int text(String text, int x, int y, int argb, boolean shadow) {
		return MinecraftClient.getInstance().textRenderer.draw(text, (float) x, (float) y, argb, shadow);
	}

	@Override
	public int textWidth(String text) {
		return MinecraftClient.getInstance().textRenderer.getStringWidth(text);
	}

	@Override
	public int fontHeight() {
		return MinecraftClient.getInstance().textRenderer.fontHeight;
	}

	@Override
	public void push() {
		GlStateManager.pushMatrix();
	}

	@Override
	public void pop() {
		GlStateManager.popMatrix();
	}

	@Override
	public void translate(float x, float y) {
		GlStateManager.translate(x, y, 0f);
	}

	@Override
	public void scale(float factor) {
		GlStateManager.scale(factor, factor, 1f);
	}

	@Override
	public float guiScale() {
		return window().getScaleFactor();
	}

	/** Uploaded once and kept for the session; GL frees them with the context. */
	private final Map<String, Integer> images = new HashMap<>();

	@Override
	public int image(String key, int width, int height, Supplier<int[]> pixels) {
		Integer id = images.get(key);
		if (id != null) {
			return id;
		}
		NativeImageBackedTexture texture = new NativeImageBackedTexture(width, height);
		int[] data = pixels.get();
		System.arraycopy(data, 0, texture.getPixels(), 0, Math.min(data.length, width * height));
		texture.upload();
		// Linear filtering: glyphs and corners are drawn at their native size, but this keeps
		// any fractional placement smooth instead of blocky.
		GlStateManager.bindTexture(texture.getGlId());
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
		images.put(key, texture.getGlId());
		return texture.getGlId();
	}

	@Override
	public void drawImage(int handle, float[] q, int count, int argb) {
		GlStateManager.enableTexture();
		GlStateManager.enableBlend();
		GlStateManager.blendFuncSeparate(770, 771, 1, 0);
		// The GUI's alpha test would cut off the soft, low-alpha edges that make this smooth.
		GlStateManager.disableAlphaTest();
		GlStateManager.bindTexture(handle);
		GlStateManager.color((argb >> 16 & 255) / 255f, (argb >> 8 & 255) / 255f, (argb & 255) / 255f, (argb >>> 24) / 255f);
		Tessellator tessellator = Tessellator.getInstance();
		BufferBuilder buffer = tessellator.getBuffer();
		buffer.begin(GL11.GL_QUADS, VertexFormats.POSITION_TEXTURE);
		for (int i = 0; i < count; i++) {
			int o = i * 8;
			buffer.vertex(q[o], q[o + 3], 0).texture(q[o + 4], q[o + 7]).next();
			buffer.vertex(q[o + 2], q[o + 3], 0).texture(q[o + 6], q[o + 7]).next();
			buffer.vertex(q[o + 2], q[o + 1], 0).texture(q[o + 6], q[o + 5]).next();
			buffer.vertex(q[o], q[o + 1], 0).texture(q[o + 4], q[o + 5]).next();
		}
		tessellator.draw();
		GlStateManager.enableAlphaTest();
		GlStateManager.color(1f, 1f, 1f, 1f);
	}

	@Override
	public void item(Object stack, int x, int y) {
		if (!(stack instanceof ItemStack)) {
			return;
		}
		GlStateManager.enableRescaleNormal();
		DiffuseLighting.enable();
		try {
			MinecraftClient.getInstance().getItemRenderer().renderInGuiWithOverrides((ItemStack) stack, x, y);
		} finally {
			DiffuseLighting.disable();
			GlStateManager.disableRescaleNormal();
			// The item renderer leaves blending off; HUD text and fills after us expect it on.
			GlStateManager.enableBlend();
		}
	}
}
