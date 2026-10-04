package dev.quartz.client.mixin.env;

import dev.quartz.core.Safe;
import dev.quartz.core.env.EnvironmentModule;
import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import net.minecraft.client.renderer.state.LightmapRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Fullbright: light the world as night vision does, without the effect. */
@Mixin(LightmapRenderStateExtractor.class)
public abstract class LightmapMixin {
	@Inject(method = "extract", at = @At("TAIL"))
	private void quartz$fullbright(LightmapRenderState state, float partialTicks, CallbackInfo ci) {
		if (state.needsUpdate && Safe.test("fullbright", EnvironmentModule::fullbright, false)) {
			state.nightVisionEffectIntensity = 1.0f;
		}
	}
}
