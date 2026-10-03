package dev.quartz.legacy.mixin.extra;

import dev.quartz.legacy.QuartzLegacy;
import net.minecraft.client.gui.hud.InGameHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Hides vanilla's crosshair while Doohickey's custom one is on (drawn in the HUD pass). */
@Mixin(InGameHud.class)
public abstract class CrosshairMixin {
	@Inject(method = "showCrosshair", at = @At("HEAD"), cancellable = true)
	private void quartz$hideVanilla(CallbackInfoReturnable<Boolean> cir) {
		if (QuartzLegacy.customCrosshair()) {
			cir.setReturnValue(false);
		}
	}
}
