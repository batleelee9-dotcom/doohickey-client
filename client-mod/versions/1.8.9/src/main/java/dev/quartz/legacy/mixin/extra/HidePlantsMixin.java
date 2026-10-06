package dev.quartz.legacy.mixin.extra;

import dev.quartz.core.perf.Performance;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.DeadBushBlock;
import net.minecraft.block.DoublePlantBlock;
import net.minecraft.block.FlowerBlock;
import net.minecraft.block.TallPlantBlock;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.block.BlockRenderManager;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Hide grass (and flowers): those blocks are left out of chunk meshes, so
 * plains and jungles have far less to draw. Runs on chunk-building threads.
 */
@Mixin(BlockRenderManager.class)
public abstract class HidePlantsMixin {
	@Inject(method = "renderBlock", at = @At("HEAD"), cancellable = true)
	private void quartz$hidePlants(BlockState state, BlockPos pos, BlockView world, BufferBuilder buffer, CallbackInfoReturnable<Boolean> cir) {
		Block block = state.getBlock();
		boolean hide;
		if (block instanceof TallPlantBlock || block instanceof DeadBushBlock) {
			hide = Performance.hideGrass();
		} else if (block instanceof FlowerBlock) {
			hide = Performance.hideFlowers();
		} else if (block instanceof DoublePlantBlock) {
			// One block for tall grass, large ferns and the tall flowers; the top half takes its kind from below.
			DoublePlantBlock.DoublePlantType kind = ((DoublePlantBlock) block).getVariant(world, pos);
			hide = kind == DoublePlantBlock.DoublePlantType.GRASS || kind == DoublePlantBlock.DoublePlantType.FERN
				? Performance.hideGrass() : Performance.hideFlowers();
		} else {
			return;
		}
		if (hide) {
			cir.setReturnValue(false);
		}
	}
}
