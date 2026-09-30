package dev.quartz.client.mixin;

import dev.quartz.client.pvp.HitColor;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(OverlayTexture.class)
public abstract class OverlayTextureMixin {
	@Shadow
	@Final
	private DynamicTexture texture;

	@Inject(method = "<init>", at = @At("TAIL"))
	private void quartz$customHitColor(CallbackInfo ci) {
		HitColor.attach(this.texture);
	}
}
