package dev.quartz.client.mixin.extra;

import dev.quartz.core.perf.Performance;
import net.minecraft.client.renderer.entity.state.ItemClusterRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Simple dropped items: one model per stack instead of up to five. */
@Mixin(ItemClusterRenderState.class)
public abstract class SimpleItemsMixin {
	@Inject(method = "getRenderedAmount", at = @At("HEAD"), cancellable = true)
	private static void quartz$oneModel(int count, CallbackInfoReturnable<Integer> cir) {
		if (Performance.simpleItems()) {
			cir.setReturnValue(1);
		}
	}
}
