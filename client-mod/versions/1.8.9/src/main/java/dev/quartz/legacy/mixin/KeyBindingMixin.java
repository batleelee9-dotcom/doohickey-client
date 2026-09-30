package dev.quartz.legacy.mixin;

import dev.quartz.core.Safe;
import dev.quartz.legacy.QuartzLegacy;
import net.minecraft.client.option.KeyBinding;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Sees every key press, even quick taps between ticks. */
@Mixin(KeyBinding.class)
public abstract class KeyBindingMixin {
	@Inject(method = "onKeyPressed", at = @At("HEAD"))
	private static void quartz$key(int keyCode, CallbackInfo ci) {
		Safe.run("menu.key", () -> QuartzLegacy.onKeyPressed(keyCode));
	}
}
