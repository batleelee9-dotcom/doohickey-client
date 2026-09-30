package dev.quartz.client.mixin.env;

import dev.quartz.core.Safe;
import dev.quartz.core.env.EnvironmentModule;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.material.FogType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Fog distance and colour, applied after vanilla computes its fog — and
 * only for open air: water, lava, powder snow, blindness and darkness keep
 * their fog, because those are gameplay information.
 */
@Mixin(FogRenderer.class)
public abstract class FogRendererMixin {
	@Shadow
	private FogType getFogType(Camera camera) {
		throw new AssertionError();
	}

	@Inject(method = "setupFog", at = @At("RETURN"))
	private void quartz$fog(Camera camera, int renderDistanceInChunks, DeltaTracker deltaTracker, float darkenWorldAmount, ClientLevel level,
			CallbackInfoReturnable<FogData> cir) {
		Safe.run("fog", () -> {
			if (getFogType(camera) != FogType.ATMOSPHERIC || camera.entity() instanceof LivingEntity living
					&& (living.hasEffect(MobEffects.BLINDNESS) || living.hasEffect(MobEffects.DARKNESS))) {
				return;
			}
			FogData fog = cir.getReturnValue();
			float blocks = renderDistanceInChunks * 16f;
			if (EnvironmentModule.overridesFog()) {
				fog.environmentalStart = EnvironmentModule.fogStart(blocks, fog.environmentalStart);
				fog.environmentalEnd = EnvironmentModule.fogEnd(blocks, fog.environmentalEnd);
				fog.renderDistanceStart = EnvironmentModule.fogStart(blocks, fog.renderDistanceStart);
				fog.renderDistanceEnd = EnvironmentModule.fogEnd(blocks, fog.renderDistanceEnd);
			}
			int rgb = EnvironmentModule.fogColor();
			if (rgb != EnvironmentModule.VANILLA) {
				fog.color.set(EnvironmentModule.red(rgb), EnvironmentModule.green(rgb), EnvironmentModule.blue(rgb), 1f);
			}
		});
	}
}
