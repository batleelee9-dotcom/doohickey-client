package dev.quartz.legacy.mixin.extra;

import dev.quartz.core.Feature;
import dev.quartz.core.Quartz;
import dev.quartz.core.config.ClientConfig;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** No hurt camera: skip the screen tilt when you take damage. */
@Mixin(GameRenderer.class)
public abstract class HurtCameraMixin {
	@Inject(method = "bobViewWhenHurt", at = @At("HEAD"), cancellable = true)
	private void quartz$noHurtCamera(float tickDelta, CallbackInfo ci) {
		if (ClientConfig.get().effects.noHurtCamera && Quartz.available(Feature.HURT_CAMERA)) {
			ci.cancel();
		}
	}
}
