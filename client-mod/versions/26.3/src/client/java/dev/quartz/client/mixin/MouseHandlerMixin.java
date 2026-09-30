package dev.quartz.client.mixin;

import dev.quartz.core.hud.CpsTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Counts presses for the CPS modules (only in-game, not in menus). */
@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
	private static final int GLFW_PRESS = 1;

	@Inject(method = "onButton", at = @At("HEAD"))
	private void quartz$countClicks(long handle, MouseButtonInfo info, int action, CallbackInfo ci) {
		if (action == GLFW_PRESS && Minecraft.getInstance().gui.screen() == null) {
			CpsTracker.click(info.button());
		}
	}
}
