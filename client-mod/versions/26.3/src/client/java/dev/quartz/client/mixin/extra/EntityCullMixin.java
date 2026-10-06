package dev.quartz.client.mixin.extra;

import dev.quartz.core.perf.Performance;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Entity render distance and hidden armor stands. Players are always
 * drawn: seeing opponents is gameplay, not decoration.
 */
@Mixin(EntityRenderDispatcher.class)
public abstract class EntityCullMixin {
	@Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
	private <E extends Entity> void quartz$cull(E entity, Frustum frustum, double x, double y, double z, float partialTicks,
			CallbackInfoReturnable<Boolean> cir) {
		if (entity instanceof Player) {
			return;
		}
		// Visible stands only, so floating text (invisible stands) stays.
		if (entity instanceof ArmorStand && !entity.isInvisible() && Performance.hideArmorStands()
				|| !Performance.drawEntity(entity.distanceToSqr(x, y, z))) {
			cir.setReturnValue(false);
		}
	}
}
