package dev.quartz.legacy;

import com.mojang.blaze3d.platform.GlStateManager;
import dev.quartz.core.Quartz;
import dev.quartz.core.RenderBackend;
import dev.quartz.core.fx.Atmosphere;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormats;
import org.lwjgl.opengl.GL11;

/** Draws the chosen Atmosphere sky as a textured box around the camera, in place of vanilla's sky. */
public final class LegacySky {
	private LegacySky() {
	}

	/** True if it drew (and vanilla's sky should be skipped). */
	public static boolean draw() {
		MinecraftClient client = MinecraftClient.getInstance();
		// The overworld only: the Nether and the End keep their own.
		if (client.world == null || client.world.dimension.getType() != 0) {
			return false;
		}
		int[] atlas = Atmosphere.atlas();
		if (atlas == null) {
			return false;
		}
		RenderBackend r = Quartz.adapter().render();
		int texture = r.image(Atmosphere.key(), Atmosphere.FACE * 3, Atmosphere.FACE * 2, () -> atlas);
		if (texture < 0) {
			return false;
		}
		GlStateManager.disableFog();
		GlStateManager.disableAlphaTest();
		GlStateManager.disableBlend();
		GlStateManager.disableCull();
		GlStateManager.depthMask(false);
		GlStateManager.enableTexture();
		GlStateManager.color(1f, 1f, 1f, 1f);
		GlStateManager.bindTexture(texture);
		GlStateManager.pushMatrix();
		GlStateManager.rotate((float) Math.toDegrees(Atmosphere.rotation()), 0f, 1f, 0f);
		Tessellator tessellator = Tessellator.getInstance();
		BufferBuilder buffer = tessellator.getBuffer();
		buffer.begin(GL11.GL_QUADS, VertexFormats.POSITION_TEXTURE);
		float inset = 0.5f / Atmosphere.FACE;
		for (int face = 0; face < 6; face++) {
			float[] a = Atmosphere.FACES[face];
			float u0 = (face % 3 + inset) / 3f;
			float u1 = (face % 3 + 1 - inset) / 3f;
			float v0 = (face / 3 + inset) / 2f;
			float v1 = (face / 3 + 1 - inset) / 2f;
			corner(buffer, a, -1, 1, u0, v0);
			corner(buffer, a, 1, 1, u1, v0);
			corner(buffer, a, 1, -1, u1, v1);
			corner(buffer, a, -1, -1, u0, v1);
		}
		tessellator.draw();
		GlStateManager.popMatrix();
		GlStateManager.depthMask(true);
		GlStateManager.enableCull();
		GlStateManager.enableAlphaTest();
		GlStateManager.enableFog();
		return true;
	}

	/** A face corner: forward ± right ± up, 100 blocks out. */
	private static void corner(BufferBuilder buffer, float[] a, float s, float t, float u, float v) {
		float x = (a[0] + s * a[3] + t * a[6]) * 100;
		float y = (a[1] + s * a[4] + t * a[7]) * 100;
		float z = (a[2] + s * a[5] + t * a[8]) * 100;
		buffer.vertex(x, y, z).texture(u, v).next();
	}
}
