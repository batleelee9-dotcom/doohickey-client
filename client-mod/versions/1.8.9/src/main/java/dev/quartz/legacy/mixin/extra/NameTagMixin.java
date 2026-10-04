package dev.quartz.legacy.mixin.extra;

import dev.quartz.core.fx.NameTags;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Custom name tags: where vanilla would show a player's tag (after its team,
 * invisibility and distance rules), hide it and let the client draw its own.
 * Sneaking players keep vanilla's dimmed, wall-hidden tag.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class NameTagMixin {
	@Inject(method = "hasLabel(Lnet/minecraft/entity/LivingEntity;)Z", at = @At("RETURN"), cancellable = true)
	private void quartz$nameTag(LivingEntity entity, CallbackInfoReturnable<Boolean> cir) {
		if (cir.getReturnValueZ() && entity instanceof PlayerEntity && !entity.isSneaking() && NameTags.enabled()) {
			NameTags.mark(entity);
			cir.setReturnValue(false);
		}
	}
}
