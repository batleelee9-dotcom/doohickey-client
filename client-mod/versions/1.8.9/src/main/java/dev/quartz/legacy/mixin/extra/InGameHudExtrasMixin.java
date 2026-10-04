package dev.quartz.legacy.mixin.extra;

import dev.quartz.core.config.ClientConfig;
import dev.quartz.core.Feature;
import dev.quartz.core.Quartz;
import dev.quartz.core.ui.SmoothHotbar;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.util.Window;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Smooth hotbar (the selector glides) and no pumpkin blur. */
@Mixin(InGameHud.class)
public abstract class InGameHudExtrasMixin {
	@Shadow
	@Final
	private MinecraftClient client;

	@ModifyArg(method = "renderHotbar", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/hud/InGameHud;drawTexture(IIIIII)V", ordinal = 1), index = 0)
	private int quartz$slide(int x) {
		if (this.client.getCameraEntity() instanceof PlayerEntity) {
			return x + SmoothHotbar.offset(((PlayerEntity) this.client.getCameraEntity()).inventory.selectedSlot);
		}
		return x;
	}

	@Inject(method = "renderPumpkinBlur", at = @At("HEAD"), cancellable = true)
	private void quartz$pumpkin(Window window, CallbackInfo ci) {
		if (ClientConfig.get().noPumpkinBlur && Quartz.available(Feature.CLEAN_VIEW)) {
			ci.cancel();
		}
	}
}
