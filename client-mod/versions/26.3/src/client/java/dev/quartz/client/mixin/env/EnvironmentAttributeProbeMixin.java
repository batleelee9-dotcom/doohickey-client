package dev.quartz.client.mixin.env;

import dev.quartz.core.Safe;
import dev.quartz.core.env.EnvironmentModule;
import net.minecraft.world.attribute.EnvironmentAttribute;
import net.minecraft.world.attribute.EnvironmentAttributeProbe;
import net.minecraft.world.attribute.EnvironmentAttributes;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Sky colour. The probe belongs to the camera — only the client's renderer
 * reads it — so overriding values here is purely visual.
 */
@Mixin(EnvironmentAttributeProbe.class)
public abstract class EnvironmentAttributeProbeMixin {
	@Inject(method = "getValue", at = @At("RETURN"), cancellable = true)
	private void quartz$sky(EnvironmentAttribute<?> attribute, float partialTicks, CallbackInfoReturnable<Object> cir) {
		if (attribute != EnvironmentAttributes.SKY_COLOR) {
			return;
		}
		int rgb = Safe.call("sky.color", EnvironmentModule::skyColor, EnvironmentModule.VANILLA);
		if (rgb != EnvironmentModule.VANILLA) {
			cir.setReturnValue(new Vector3f(EnvironmentModule.red(rgb), EnvironmentModule.green(rgb), EnvironmentModule.blue(rgb)));
		}
	}
}
