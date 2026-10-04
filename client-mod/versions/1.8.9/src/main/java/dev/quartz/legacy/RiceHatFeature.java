package dev.quartz.legacy;

import com.mojang.blaze3d.platform.GlStateManager;
import dev.quartz.core.Safe;
import dev.quartz.core.fx.RiceHat;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import org.lwjgl.opengl.GL11;

/** The rice hat on players (yours in third person and the inventory, others when set to). */
public final class RiceHatFeature implements FeatureRenderer<AbstractClientPlayerEntity> {
	private static final RiceHat.Sink SINK = (x, y, z, argb) -> Tessellator.getInstance().getBuffer()
		.vertex(x, y, z).color(argb >> 16 & 255, argb >> 8 & 255, argb & 255, argb >>> 24).next();

	private final PlayerEntityRenderer renderer;

	public RiceHatFeature(PlayerEntityRenderer renderer) {
		this.renderer = renderer;
	}

	@Override
	public void render(AbstractClientPlayerEntity player, float handSwing, float handSwingAmount, float tickDelta, float age, float headYaw, float headPitch, float scale) {
		if (player.isInvisible() || !RiceHat.enabled() || !RiceHat.shows(player == MinecraftClient.getInstance().player)) {
			return;
		}
		// Over a helmet or skull it sits a little higher instead of clipping.
		float lift = player.getArmorSlot(3) != null ? RiceHat.HELMET_LIFT : 0f;
		GlStateManager.pushMatrix();
		if (player.isSneaking()) {
			GlStateManager.translate(0.0F, 0.2F, 0.0F);
		}
		this.renderer.getModel().head.preRender(scale);
		GlStateManager.scale(scale, scale, scale);
		GlStateManager.disableTexture();
		GlStateManager.disableLighting();
		GlStateManager.disableCull();
		// Blend the baked shading across each face (entities draw flat-shaded).
		GlStateManager.shadeModel(GL11.GL_SMOOTH);
		GlStateManager.enableBlend();
		GlStateManager.blendFuncSeparate(770, 771, 1, 0);
		try {
			BufferBuilder buffer = Tessellator.getInstance().getBuffer();
			buffer.begin(GL11.GL_TRIANGLES, VertexFormats.POSITION_COLOR);
			RiceHat.build(SINK, (System.currentTimeMillis() % 1_000_000L) / 1000f, lift);
			Tessellator.getInstance().draw();
		} catch (Throwable t) {
			Safe.report("ricehat", t);
		}
		GlStateManager.shadeModel(GL11.GL_FLAT);
		GlStateManager.disableBlend();
		GlStateManager.enableCull();
		GlStateManager.enableLighting();
		GlStateManager.enableTexture();
		GlStateManager.popMatrix();
	}

	@Override
	public boolean combineTextures() {
		return false;
	}
}
