package dev.quartz.client.mixin.extra;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.quartz.core.Feature;
import dev.quartz.core.Quartz;
import dev.quartz.core.config.ClientConfig;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** No hurt camera: skip the screen tilt when you take damage. */
@Mixin(GameRenderer.class)
public abstract class HurtCameraMixin {
	@Inject(method = "bobHurt", at = @At("HEAD"), cancellable = true)
	private void quartz$noHurtCamera(CameraRenderState camera, PoseStack pose, CallbackInfo ci) {
		if (ClientConfig.get().effects.noHurtCamera && Quartz.available(Feature.HURT_CAMERA)) {
			ci.cancel();
		}
	}
}
