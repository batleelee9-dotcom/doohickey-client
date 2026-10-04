package dev.quartz.legacy.mixin;

import com.mojang.blaze3d.platform.GlStateManager;
import dev.quartz.core.Safe;
import dev.quartz.core.env.EnvironmentModule;
import net.minecraft.block.material.Material;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.world.dimension.Dimension;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fog on 1.8.9: colour (updateFog), distance (renderFog) and void fog,
 * applied only in open air so water, lava and blindness keep their fog.
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
	@Shadow
	private MinecraftClient client;

	@Shadow
	private float viewDistance;

	@Shadow
	private boolean thickFog;

	@Shadow
	private float fogRed;

	@Shadow
	private float fogGreen;

	@Shadow
	private float fogBlue;

	private boolean quartz$openAir(float tickDelta) {
		Entity entity = this.client.getCameraEntity();
		if (entity == null || this.client.world == null) {
			return false;
		}
		if (entity instanceof LivingEntity && ((LivingEntity) entity).hasStatusEffect(StatusEffect.BLINDNESS)) {
			return false;
		}
		Material material = Camera.getSubmergedBlock(this.client.world, entity, tickDelta).getMaterial();
		return material != Material.WATER && material != Material.LAVA;
	}

	@Inject(method = "updateFog", at = @At("TAIL"))
	private void quartz$fogColor(float tickDelta, CallbackInfo ci) {
		Safe.run("fog.color", () -> {
			int rgb = EnvironmentModule.fogColor();
			if (rgb != EnvironmentModule.VANILLA && quartz$openAir(tickDelta)) {
				this.fogRed = EnvironmentModule.red(rgb);
				this.fogGreen = EnvironmentModule.green(rgb);
				this.fogBlue = EnvironmentModule.blue(rgb);
				GlStateManager.clearColor(this.fogRed, this.fogGreen, this.fogBlue, 0.0F);
			}
		});
	}

	/** Terrain fog passes only (-1 is the sky pass, which keeps its horizon). */
	@Inject(method = "renderFog", at = @At("TAIL"))
	private void quartz$fogDistance(int pass, float tickDelta, CallbackInfo ci) {
		Safe.run("fog.distance", () -> {
			if (pass == -1 || this.thickFog || !EnvironmentModule.overridesFog() || !quartz$openAir(tickDelta)) {
				return;
			}
			GlStateManager.fogMode(9729); // GL_LINEAR
			GlStateManager.fogStart(EnvironmentModule.fogStart(this.viewDistance, this.viewDistance * 0.75F));
			GlStateManager.fogEnd(EnvironmentModule.fogEnd(this.viewDistance, this.viewDistance));
		});
	}

	/** Fullbright: 1.8.9 doesn't clamp gamma, so the lightmap simply reads a high value. */
	@Redirect(method = "updateLightmap", at = @At(value = "FIELD", target = "Lnet/minecraft/client/option/GameOptions;gamma:F"))
	private float quartz$fullbright(GameOptions options) {
		return Safe.test("fullbright", EnvironmentModule::fullbright, false) ? 12.0F : options.gamma;
	}

	/**
	 * Void fog darkens the world near bedrock: the camera height is scaled by
	 * the dimension's horizon ratio (1/32 in the Overworld). Superflat uses
	 * 1.0, which means no darkening above y=1, and so do we.
	 */
	@Redirect(method = "updateFog", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/dimension/Dimension;method_3994()D"))
	private double quartz$voidFog(Dimension dimension) {
		double vanilla = dimension.method_3994();
		return Safe.map("fog.void", v -> EnvironmentModule.removeVoidFog() ? Math.max(v, 1.0) : v, vanilla);
	}
}
