package dev.quartz.legacy.mixin;

import dev.quartz.core.Safe;
import dev.quartz.core.perf.Performance;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Particle limiter: drop a share of new particles before they're simulated or drawn. */
@Mixin(ParticleManager.class)
public abstract class ParticleManagerMixin {
	// Two overloads exist (by id, and by Particle); hook the one every particle goes through.
	@Inject(method = "addParticle(Lnet/minecraft/client/particle/Particle;)V", at = @At("HEAD"), cancellable = true)
	private void quartz$limit(Particle particle, CallbackInfo ci) {
		if (!Safe.call("particles", Performance::keepParticle, true)) {
			ci.cancel();
		}
	}
}
