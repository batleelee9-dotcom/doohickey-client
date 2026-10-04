package dev.quartz.client.mixin.extra;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.quartz.core.Feature;
import dev.quartz.core.Quartz;
import dev.quartz.core.config.ClientConfig;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Low fire: the burning overlay sits lower, so it doesn't cover the middle of the screen. */
@Mixin(ScreenEffectRenderer.class)
public abstract class FireOverlayMixin {
	@Inject(method = "submitFire", at = @At("HEAD"))
	private static void quartz$lower(PoseStack poseStack, SubmitNodeCollector collector, TextureAtlasSprite sprite, CallbackInfo ci) {
		poseStack.pushPose();
		if (ClientConfig.get().lowFire && Quartz.available(Feature.CLEAN_VIEW)) {
			poseStack.translate(0.0F, -0.35F, 0.0F);
		}
	}

	@Inject(method = "submitFire", at = @At("TAIL"))
	private static void quartz$restore(PoseStack poseStack, SubmitNodeCollector collector, TextureAtlasSprite sprite, CallbackInfo ci) {
		poseStack.popPose();
	}
}
