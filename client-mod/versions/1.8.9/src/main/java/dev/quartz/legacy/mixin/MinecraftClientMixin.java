package dev.quartz.legacy.mixin;

import dev.quartz.core.Safe;
import dev.quartz.legacy.QuartzLegacy;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin {
	@Inject(method = "tick", at = @At("TAIL"))
	private void quartz$tick(CallbackInfo ci) {
		Safe.run("tick", QuartzLegacy::tick);
	}
}
