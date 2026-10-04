package dev.quartz.legacy.mixin.extra;

import dev.quartz.core.Safe;
import dev.quartz.core.perf.Performance;
import net.minecraft.block.entity.BeaconBlockEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.render.block.entity.BlockEntityRenderDispatcher;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Block entity distance: signs (their text is costly), chests, heads and banners past the limit aren't drawn. Beacon beams always are. */
@Mixin(BlockEntityRenderDispatcher.class)
public abstract class BlockEntityDistanceMixin {
	@Shadow
	public double cameraX;
	@Shadow
	public double cameraY;
	@Shadow
	public double cameraZ;

	@Inject(method = "renderEntity(Lnet/minecraft/block/entity/BlockEntity;FI)V", at = @At("HEAD"), cancellable = true)
	private void quartz$distance(BlockEntity blockEntity, float tickDelta, int destroyProgress, CallbackInfo ci) {
		if (!(blockEntity instanceof BeaconBlockEntity)
			&& !Safe.test("blockentity.distance", Performance::drawBlockEntity, blockEntity.getSquaredDistance(cameraX, cameraY, cameraZ), true)) {
			ci.cancel();
		}
	}
}
