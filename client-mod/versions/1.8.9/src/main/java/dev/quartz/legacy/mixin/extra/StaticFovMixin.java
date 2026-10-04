package dev.quartz.legacy.mixin.extra;

import dev.quartz.core.Feature;
import dev.quartz.core.Quartz;
import dev.quartz.core.config.ClientConfig;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** No speed FOV: sprinting, speed and slowness stop changing the field of view. */
@Mixin(AbstractClientPlayerEntity.class)
public abstract class StaticFovMixin {
	@Inject(method = "getSpeed", at = @At("HEAD"), cancellable = true)
	private void quartz$staticFov(CallbackInfoReturnable<Float> cir) {
		if (ClientConfig.get().staticFov && Quartz.available(Feature.STATIC_FOV)) {
			cir.setReturnValue(1.0F);
		}
	}
}
