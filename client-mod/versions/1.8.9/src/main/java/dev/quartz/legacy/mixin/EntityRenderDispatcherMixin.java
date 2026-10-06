package dev.quartz.legacy.mixin;

import dev.quartz.core.Safe;
import dev.quartz.core.perf.Performance;
import net.minecraft.client.render.CameraView;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Entity render distance and hidden armor stands. Players
 * are always drawn — seeing opponents is gameplay, not decoration.
 */
@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherMixin {
	@Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
	private void quartz$entityDistance(Entity entity, CameraView view, double x, double y, double z, CallbackInfoReturnable<Boolean> cir) {
		if (entity instanceof PlayerEntity) {
			return;
		}
		// Hide armor stands: visible ones only, so floating text (invisible stands) stays.
		if (entity instanceof ArmorStandEntity && !entity.isInvisible() && Performance.hideArmorStands()) {
			cir.setReturnValue(false);
			return;
		}
		double dx = entity.x - x;
		double dy = entity.y - y;
		double dz = entity.z - z;
		if (!Safe.test("entity.distance", Performance::drawEntity, dx * dx + dy * dy + dz * dz, true)) {
			cir.setReturnValue(false);
		}
	}
}
