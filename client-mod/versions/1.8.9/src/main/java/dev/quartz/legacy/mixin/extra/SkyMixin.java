package dev.quartz.legacy.mixin.extra;

import dev.quartz.core.Safe;
import dev.quartz.legacy.LegacySky;
import net.minecraft.client.render.WorldRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Atmosphere: the client's painted sky instead of vanilla's (overworld only). */
@Mixin(WorldRenderer.class)
public abstract class SkyMixin {
	@Inject(method = "renderSky(FI)V", at = @At("HEAD"), cancellable = true)
	private void quartz$sky(float tickDelta, int anaglyphFilter, CallbackInfo ci) {
		if (Safe.test("sky", LegacySky::draw, false)) {
			ci.cancel();
		}
	}
}
