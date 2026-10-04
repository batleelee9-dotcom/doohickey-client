package dev.quartz.legacy.mixin.extra;

import dev.quartz.core.fx.View;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Particle culling: 1.8.9 builds every particle each frame, even behind you. Skip the ones out of view. */
@Mixin(ParticleManager.class)
public abstract class ParticleCullMixin {
	@Redirect(method = "renderParticles", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/particle/Particle;draw(Lnet/minecraft/client/render/BufferBuilder;Lnet/minecraft/entity/Entity;FFFFFF)V"))
	private void quartz$cull(Particle particle, BufferBuilder buffer, Entity camera, float tickDelta, float a, float b, float c, float d, float e) {
		if (View.visible(particle.x, particle.y, particle.z, 1f)) {
			particle.draw(buffer, camera, tickDelta, a, b, c, d, e);
		}
	}
}
