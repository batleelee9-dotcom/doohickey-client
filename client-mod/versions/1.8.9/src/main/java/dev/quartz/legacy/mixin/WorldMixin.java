package dev.quartz.legacy.mixin;

import dev.quartz.core.Safe;
import dev.quartz.core.env.EnvironmentModule;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.dimension.Dimension;
import net.minecraft.world.level.LevelProperties;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Sky colour, client-side time and weather on 1.8.9. World is shared with
 * the integrated server, so every hook acts only on the client's world.
 */
@Mixin(World.class)
public abstract class WorldMixin {
	@Shadow
	@Final
	public Dimension dimension;

	@Shadow
	protected LevelProperties levelProperties;

	private boolean quartz$client() {
		return (Object) this instanceof ClientWorld;
	}

	/** World#getSkyColor (unnamed in 1.8.9 yarn). */
	@Inject(method = "method_3631", at = @At("RETURN"), cancellable = true)
	private void quartz$skyColor(Entity entity, float tickDelta, CallbackInfoReturnable<Vec3d> cir) {
		if (!quartz$client()) {
			return;
		}
		int rgb = Safe.get("sky.color", EnvironmentModule::skyColor, EnvironmentModule.VANILLA);
		if (rgb != EnvironmentModule.VANILLA) {
			cir.setReturnValue(new Vec3d(EnvironmentModule.red(rgb), EnvironmentModule.green(rgb), EnvironmentModule.blue(rgb)));
		}
	}

	@Inject(method = "getSkyAngle", at = @At("HEAD"), cancellable = true)
	private void quartz$time(float tickDelta, CallbackInfoReturnable<Float> cir) {
		if (!quartz$client()) {
			return;
		}
		long vanilla = this.levelProperties.getTimeOfDay();
		long locked = Safe.map("time.lock", EnvironmentModule::clock, vanilla);
		if (locked != vanilla) {
			cir.setReturnValue(this.dimension.getSkyAngle(locked, tickDelta));
		}
	}

	@Inject(method = "getRainGradient", at = @At("RETURN"), cancellable = true)
	private void quartz$rain(float offset, CallbackInfoReturnable<Float> cir) {
		if (quartz$client()) {
			float vanilla = cir.getReturnValueF();
			float level = Safe.map("weather.rain", EnvironmentModule::rainLevel, vanilla);
			if (level != vanilla) {
				cir.setReturnValue(level);
			}
		}
	}

	@Inject(method = "getThunderGradient", at = @At("RETURN"), cancellable = true)
	private void quartz$thunder(float offset, CallbackInfoReturnable<Float> cir) {
		if (quartz$client()) {
			float vanilla = cir.getReturnValueF();
			float level = Safe.map("weather.thunder", EnvironmentModule::thunderLevel, vanilla);
			if (level != vanilla) {
				cir.setReturnValue(level);
			}
		}
	}
}
