package dev.quartz.client.mixin;

import dev.quartz.client.cosmetics.Capes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.ClientAsset;
import net.minecraft.world.entity.player.PlayerSkin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Client-side capes: swaps the cape (and elytra) texture of the local
 * player only. Nothing is sent to servers, so other players keep seeing
 * your real cape — cosmetics here are purely personal.
 */
@Mixin(AbstractClientPlayer.class)
public abstract class AbstractClientPlayerMixin {
	@Inject(method = "getSkin", at = @At("RETURN"), cancellable = true)
	private void quartz$cape(CallbackInfoReturnable<PlayerSkin> cir) {
		if ((Object) this != Minecraft.getInstance().player) {
			return;
		}
		ClientAsset.Texture cape = Capes.selectedTexture();
		if (cape == null) {
			return;
		}
		PlayerSkin skin = cir.getReturnValue();
		cir.setReturnValue(new PlayerSkin(skin.body(), cape, cape, skin.model(), skin.secure()));
	}
}
