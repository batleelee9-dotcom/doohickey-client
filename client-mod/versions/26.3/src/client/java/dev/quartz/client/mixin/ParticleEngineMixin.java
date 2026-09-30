package dev.quartz.client.mixin;

import dev.quartz.core.config.ClientConfig;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.ThreadLocalRandom;

/** Particle multiplier: drop a share of new particles before they're simulated. */
@Mixin(ParticleEngine.class)
public abstract class ParticleEngineMixin {
	@Inject(method = "add", at = @At("HEAD"), cancellable = true)
	private void quartz$particleMultiplier(Particle particle, CallbackInfo ci) {
		int percent = ClientConfig.get().particlePercent;
		if (percent < 100 && ThreadLocalRandom.current().nextInt(100) >= percent) {
			ci.cancel();
		}
	}
}
