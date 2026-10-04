package dev.quartz.client.mixin.extra;

import dev.quartz.core.Feature;
import dev.quartz.core.Quartz;
import dev.quartz.core.config.ClientConfig;
import dev.quartz.core.ui.SmoothHotbar;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Smooth hotbar (the selector glides) and no pumpkin blur. */
@Mixin(Hud.class)
public abstract class HudExtrasMixin {
	@ModifyArg(method = "extractItemHotbar", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitSprite(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIII)V",
		ordinal = 1), index = 2)
	private int quartz$slide(int x) {
		Minecraft mc = Minecraft.getInstance();
		return mc.player == null ? x : x + SmoothHotbar.offset(mc.player.getInventory().getSelectedSlot());
	}

	@Inject(method = "extractTextureOverlay", at = @At("HEAD"), cancellable = true)
	private void quartz$pumpkin(GuiGraphicsExtractor g, Identifier texture, float alpha, CallbackInfo ci) {
		if (texture.getPath().contains("pumpkinblur") && ClientConfig.get().noPumpkinBlur && Quartz.available(Feature.CLEAN_VIEW)) {
			ci.cancel();
		}
	}
}
