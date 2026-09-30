package dev.quartz.legacy;

import com.mojang.blaze3d.platform.GlStateManager;
import dev.quartz.core.RenderBackend;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.render.DiffuseLighting;
import net.minecraft.client.util.Window;
import net.minecraft.item.ItemStack;

/** {@link RenderBackend} for 1.8.9–1.12.2: fixed-function OpenGL through GlStateManager. */
final class LegacyGlBackend implements RenderBackend {
	static final LegacyGlBackend INSTANCE = new LegacyGlBackend();

	private LegacyGlBackend() {
	}

	@Override
	public Kind kind() {
		return Kind.LEGACY_OPENGL;
	}

	@Override
	public int screenWidth() {
		return new Window(MinecraftClient.getInstance()).getWidth();
	}

	@Override
	public int screenHeight() {
		return new Window(MinecraftClient.getInstance()).getHeight();
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
