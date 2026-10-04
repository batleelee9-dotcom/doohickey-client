package dev.quartz.client.mixin.extra;

import dev.quartz.core.Safe;
import dev.quartz.core.fx.Effects;
import net.minecraft.client.Minecraft;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hit effects and sounds play when the server says something took damage,
 * not on every click. The damage event names who caused it, so melee and
 * projectile hits (arrows, tridents, snowballs...) are credited exactly.
 */
@Mixin(LivingEntity.class)
public abstract class DamageEventMixin {
	@Inject(method = "handleDamageEvent", at = @At("HEAD"))
	private void quartz$damaged(DamageSource source, CallbackInfo ci) {
		Minecraft mc = Minecraft.getInstance();
		if ((Object) this != mc.player && mc.player != null) {
			Object self = this;
			int who = source.getEntity() == mc.player ? Effects.YOURS : Effects.NOT_YOURS;
			Safe.run("effects.damaged", () -> Effects.onDamaged(self, who));
		}
	}
}
