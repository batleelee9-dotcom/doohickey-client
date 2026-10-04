package dev.quartz.legacy.mixin.extra;

import com.mojang.blaze3d.platform.GlStateManager;
import dev.quartz.core.Feature;
import dev.quartz.core.Quartz;
import dev.quartz.core.config.ClientConfig;
import net.minecraft.client.render.item.HeldItemRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Low fire: the burning overlay sits lower, so it doesn't cover the middle of the screen. */
@Mixin(HeldItemRenderer.class)
public abstract class FireOverlayMixin {
	@Unique
	private boolean quartz$lowered;

	@Inject(method = "renderFireOverlay", at = @At("HEAD"))
	private void quartz$lower(float tickDelta, CallbackInfo ci) {
		quartz$lowered = ClientConfig.get().lowFire && Quartz.available(Feature.CLEAN_VIEW);
		if (quartz$lowered) {
			GlStateManager.pushMatrix();
			GlStateManager.translate(0.0F, -0.35F, 0.0F);
		}
	}

	@Inject(method = "renderFireOverlay", at = @At("RETURN"))
	private void quartz$restore(float tickDelta, CallbackInfo ci) {
		if (quartz$lowered) {
			GlStateManager.popMatrix();
			quartz$lowered = false;
		}
	}
}
