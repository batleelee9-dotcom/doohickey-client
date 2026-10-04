package dev.quartz.client.mixin.extra;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.renderpearl.api.commands.RenderPass;
import dev.quartz.client.ModernSky;
import dev.quartz.core.Safe;
import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.world.level.MoonPhase;
import org.joml.Vector3fc;
import org.joml.Vector4fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Atmosphere: the client's painted sky replaces the overworld's sky disc,
 * sunrise glow, sun, moon, stars and dark lower disc (the painting has its
 * own). The End and anything else keep vanilla's.
 */
@Mixin(SkyRenderer.class)
public abstract class SkyRendererMixin {
	@Shadow
	private void renderSkyDisc(RenderPass pass, Vector3fc color) {
		throw new AssertionError();
	}

	@Shadow
	private void renderSunriseAndSunset(RenderPass pass, PoseStack poseStack, float sunAngle, Vector4fc color) {
		throw new AssertionError();
	}

	@Shadow
	private void renderSunMoonAndStars(RenderPass pass, PoseStack poseStack, float sunAngle, float moonAngle, float starAngle, MoonPhase moonPhase,
			float rainBrightness, float starBrightness) {
		throw new AssertionError();
	}

	@Shadow
	private void renderDarkDisc(RenderPass pass) {
		throw new AssertionError();
	}

	@Redirect(method = "render", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/renderer/SkyRenderer;renderSkyDisc(Lcom/mojang/renderpearl/api/commands/RenderPass;Lorg/joml/Vector3fc;)V"))
	private void quartz$sky(SkyRenderer self, RenderPass pass, Vector3fc color) {
		if (!Safe.test("sky", () -> ModernSky.draw(pass), false)) {
			renderSkyDisc(pass, color);
		}
	}

	@Redirect(method = "render", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/renderer/SkyRenderer;renderSunriseAndSunset(Lcom/mojang/renderpearl/api/commands/RenderPass;Lcom/mojang/blaze3d/vertex/PoseStack;FLorg/joml/Vector4fc;)V"))
	private void quartz$sunrise(SkyRenderer self, RenderPass pass, PoseStack poseStack, float sunAngle, Vector4fc color) {
		if (!ModernSky.drawn()) {
			renderSunriseAndSunset(pass, poseStack, sunAngle, color);
		}
	}

	@Redirect(method = "render", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/renderer/SkyRenderer;renderSunMoonAndStars(Lcom/mojang/renderpearl/api/commands/RenderPass;Lcom/mojang/blaze3d/vertex/PoseStack;FFFLnet/minecraft/world/level/MoonPhase;FF)V"))
	private void quartz$celestial(SkyRenderer self, RenderPass pass, PoseStack poseStack, float sunAngle, float moonAngle, float starAngle,
			MoonPhase moonPhase, float rainBrightness, float starBrightness) {
		if (!ModernSky.drawn()) {
			renderSunMoonAndStars(pass, poseStack, sunAngle, moonAngle, starAngle, moonPhase, rainBrightness, starBrightness);
		}
	}

	@Redirect(method = "render", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/renderer/SkyRenderer;renderDarkDisc(Lcom/mojang/renderpearl/api/commands/RenderPass;)V"))
	private void quartz$darkDisc(SkyRenderer self, RenderPass pass) {
		if (!ModernSky.drawn()) {
			renderDarkDisc(pass);
		}
	}
}
