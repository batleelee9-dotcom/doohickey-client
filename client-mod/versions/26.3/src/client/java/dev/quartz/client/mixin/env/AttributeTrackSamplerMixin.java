package dev.quartz.client.mixin.env;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.quartz.core.Safe;
import dev.quartz.core.env.EnvironmentModule;
import net.minecraft.client.ClientClockManager;
import net.minecraft.core.Holder;
import net.minecraft.world.clock.ClockInstance;
import net.minecraft.world.clock.ClockManager;
import net.minecraft.world.clock.WorldClock;
import net.minecraft.world.clock.WorldClocks;
import net.minecraft.world.timeline.AttributeTrackSampler;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Client-side time: the sky's timelines (sun, moon, stars, sky colour) read
 * the Overworld clock through here. Only the client's clock is touched, so
 * the integrated server's day cycle — mobs, crops, beds — runs normally.
 */
@Mixin(AttributeTrackSampler.class)
public abstract class AttributeTrackSamplerMixin {
	@Shadow
	@Final
	private Holder<WorldClock> clock;

	@Shadow
	@Final
	private ClockManager clockManager;

	@WrapOperation(method = "applyTimeBased", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/clock/ClockInstance;totalTicks()J"))
	private long quartz$lockTime(ClockInstance instance, Operation<Long> original) {
		long ticks = original.call(instance);
		if (!(clockManager instanceof ClientClockManager) || !clock.is(WorldClocks.OVERWORLD)) {
			return ticks;
		}
		return Safe.map("time.lock", EnvironmentModule::clock, ticks);
	}
}
