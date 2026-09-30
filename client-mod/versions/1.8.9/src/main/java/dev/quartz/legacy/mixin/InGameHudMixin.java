package dev.quartz.legacy.mixin;

import dev.quartz.core.Safe;
import dev.quartz.legacy.QuartzLegacy;
import net.minecraft.client.gui.hud.InGameHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameHud.class)
public abstract class InGameHudMixin {
	@Inject(method = "render", at = @At("TAIL"))
	private void quartz$hud(float tickDelta, CallbackInfo ci) {
		Safe.run("hud", QuartzLegacy::renderHud);
	}
}
