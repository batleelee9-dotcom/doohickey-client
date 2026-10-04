package dev.quartz.legacy.mixin.extra;

import dev.quartz.core.Safe;
import dev.quartz.core.fx.View;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.entity.Entity;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.FloatBuffer;

/**
 * Keeps {@link View} in step with the camera the world is drawn with (zoom,
 * stretch, bobbing and all): hit particles are placed with it and off-screen
 * particles skipped. Vanilla reads the same matrices back every frame.
 */
@Mixin(GameRenderer.class)
public abstract class CameraCaptureMixin {
	private static final FloatBuffer QUARTZ_GL = BufferUtils.createFloatBuffer(16);
	private static final float[] QUARTZ_P = new float[16];
	private static final float[] QUARTZ_V = new float[16];
	private static final float[] QUARTZ_M = new float[16];

	@Inject(method = "setupCamera", at = @At("RETURN"))
	private void quartz$capture(float tickDelta, int anaglyphFilter, CallbackInfo ci) {
		Entity cam = MinecraftClient.getInstance().getCameraEntity();
		if (cam == null) {
			return;
		}
		try {
			QUARTZ_GL.clear();
			GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, QUARTZ_GL);
			QUARTZ_GL.get(QUARTZ_P);
			QUARTZ_GL.clear();
			GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, QUARTZ_GL);
			QUARTZ_GL.get(QUARTZ_V);
			// Column-major P × V.
			for (int c = 0; c < 4; c++) {
				for (int r = 0; r < 4; r++) {
					float s = 0;
					for (int k = 0; k < 4; k++) {
						s += QUARTZ_P[k * 4 + r] * QUARTZ_V[c * 4 + k];
					}
					QUARTZ_M[c * 4 + r] = s;
				}
			}
			// Chunks and entities are drawn relative to the interpolated camera entity.
			View.setPartialTicks(tickDelta);
			View.set(QUARTZ_M, cam.prevTickX + (cam.x - cam.prevTickX) * tickDelta,
				cam.prevTickY + (cam.y - cam.prevTickY) * tickDelta, cam.prevTickZ + (cam.z - cam.prevTickZ) * tickDelta);
		} catch (Throwable t) {
			View.clear();
			Safe.report("view", t);
		}
	}
}
