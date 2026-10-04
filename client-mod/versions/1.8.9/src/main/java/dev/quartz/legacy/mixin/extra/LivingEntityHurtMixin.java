package dev.quartz.legacy.mixin.extra;

import dev.quartz.core.Safe;
import dev.quartz.core.fx.Effects;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hit effects and sounds play when the server says something took damage
 * (status 2: the hurt animation), not on every click. 1.8.9 doesn't say who
 * caused it, so Effects matches it to your swing or your projectile.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityHurtMixin {
	@Inject(method = "handleStatus", at = @At("HEAD"))
	private void quartz$hurt(byte status, CallbackInfo ci) {
		if (status == 2 && (Object) this != MinecraftClient.getInstance().player) {
			Object self = this;
			Safe.run("effects.damaged", () -> Effects.onDamaged(self, Effects.UNKNOWN));
		}
	}
}
