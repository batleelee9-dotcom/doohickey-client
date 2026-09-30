package dev.quartz.client.mixin.env;

import dev.quartz.core.Safe;
import dev.quartz.core.env.EnvironmentModule;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Weather override for the client's level only — rain particles, sounds
 * and sky darkening follow it; the server's weather is unchanged.
 */
@Mixin(Level.class)
public abstract class LevelMixin {
	@Inject(method = "getRainLevel", at = @At("RETURN"), cancellable = true)
	private void quartz$rain(float partialTicks, CallbackInfoReturnable<Float> cir) {
		if ((Object) this instanceof ClientLevel) {
			float vanilla = cir.getReturnValueF();
			cir.setReturnValue(Safe.call("weather.rain", () -> EnvironmentModule.rainLevel(vanilla), vanilla));
		}
	}

	@Inject(method = "getThunderLevel", at = @At("RETURN"), cancellable = true)
	private void quartz$thunder(float partialTicks, CallbackInfoReturnable<Float> cir) {
		if ((Object) this instanceof ClientLevel) {
			float vanilla = cir.getReturnValueF();
			cir.setReturnValue(Safe.call("weather.thunder", () -> EnvironmentModule.thunderLevel(vanilla), vanilla));
		}
	}
}
