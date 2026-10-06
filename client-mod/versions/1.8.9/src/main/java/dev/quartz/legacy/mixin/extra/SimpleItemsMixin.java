package dev.quartz.legacy.mixin.extra;

import dev.quartz.core.perf.Performance;
import net.minecraft.client.render.entity.ItemEntityRenderer;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Simple dropped items: one model per stack instead of up to five (method_10222 picks how many). */
@Mixin(ItemEntityRenderer.class)
public abstract class SimpleItemsMixin {
	@Inject(method = "method_10222", at = @At("HEAD"), cancellable = true)
	private void quartz$oneModel(ItemStack stack, CallbackInfoReturnable<Integer> cir) {
		if (Performance.simpleItems()) {
			cir.setReturnValue(1);
		}
	}
}
