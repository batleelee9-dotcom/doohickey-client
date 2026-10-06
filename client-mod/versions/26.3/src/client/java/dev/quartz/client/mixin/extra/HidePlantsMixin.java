package dev.quartz.client.mixin.extra;

import dev.quartz.core.perf.Performance;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.BlockQuadOutput;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.chunk.SectionCompiler;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.CactusFlowerBlock;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.DryVegetationBlock;
import net.minecraft.world.level.block.FireflyBushBlock;
import net.minecraft.world.level.block.FlowerBedBlock;
import net.minecraft.world.level.block.FlowerBlock;
import net.minecraft.world.level.block.TallFlowerBlock;
import net.minecraft.world.level.block.TallGrassBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Hide grass (and flowers): those blocks are left out of chunk meshes, so
 * plains and jungles have far less to draw. Runs on chunk-building threads.
 */
@Mixin(SectionCompiler.class)
public abstract class HidePlantsMixin {
	@Redirect(method = "compile", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/renderer/block/ModelBlockRenderer;tesselateBlock(Lnet/minecraft/client/renderer/block/BlockQuadOutput;FFFLnet/minecraft/client/renderer/block/BlockAndTintGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/client/renderer/block/dispatch/BlockStateModel;J)V"))
	private void quartz$hidePlants(ModelBlockRenderer renderer, BlockQuadOutput output, float x, float y, float z, BlockAndTintGetter level, BlockPos pos,
			BlockState state, BlockStateModel model, long seed) {
		Block b = state.getBlock();
		boolean hide = false;
		if (b instanceof FlowerBlock || b instanceof TallFlowerBlock || b instanceof FlowerBedBlock || b instanceof CactusFlowerBlock) {
			hide = Performance.hideFlowers();
		} else if (b instanceof TallGrassBlock || b.getClass() == DoublePlantBlock.class || b instanceof DryVegetationBlock
				|| b instanceof BushBlock || b instanceof FireflyBushBlock) {
			// DoublePlantBlock itself is tall grass and large ferns; its subclasses are flowers, dripleaf and pitchers.
			hide = Performance.hideGrass();
		}
		if (!hide) {
			renderer.tesselateBlock(output, x, y, z, level, pos, state, model, seed);
		}
	}
}
