package dev.quartz.client.mixin.extra;

import dev.quartz.core.fx.NameTags;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Custom name tags: where vanilla decided a player's tag shows (after team,
 * invisibility and distance rules), drop it from the render state and let
 * the client draw its own. Sneaking players keep vanilla's tag.
 */
@Mixin(EntityRenderer.class)
public abstract class NameTagMixin {
	@Inject(method = "extractNameTags(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/client/renderer/entity/state/EntityRenderState;F)V", at = @At("TAIL"))
	private void quartz$nameTag(Entity entity, EntityRenderState state, float partialTick, CallbackInfo ci) {
		if (state.nameTag != null && entity instanceof Player && !entity.isDiscrete() && NameTags.enabled()) {
			NameTags.mark(entity);
			state.nameTag = null;
		}
	}
}
