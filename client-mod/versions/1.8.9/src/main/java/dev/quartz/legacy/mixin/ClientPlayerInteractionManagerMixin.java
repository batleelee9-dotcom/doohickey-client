package dev.quartz.legacy.mixin;

import dev.quartz.core.Safe;
import dev.quartz.core.fx.Effects;
import dev.quartz.core.hud.ReachTracker;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Your attacks: reach display and hit effects. Read only; nothing is sent differently. */
@Mixin(ClientPlayerInteractionManager.class)
public abstract class ClientPlayerInteractionManagerMixin {
	@Inject(method = "attackEntity", at = @At("HEAD"))
	private void quartz$reach(PlayerEntity player, Entity target, CallbackInfo ci) {
		Safe.run("reach", () -> {
			BlockHitResult hit = MinecraftClient.getInstance().result;
			if (hit != null && hit.entity == target && hit.pos != null) {
				ReachTracker.record(player.getCameraPosVec(1.0f).distanceTo(hit.pos));
			}
		});
		Safe.run("effects.hit", () -> Effects.onHit(target));
	}
}
