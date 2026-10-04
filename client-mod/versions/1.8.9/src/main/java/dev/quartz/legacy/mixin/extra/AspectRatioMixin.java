package dev.quartz.legacy.mixin.extra;

import dev.quartz.core.Safe;
import dev.quartz.core.pvp.AspectRatio;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Aspect ratio (stretched): every world projection (camera, hand, sky pass,
 * clouds) at the chosen ratio. Culling reads the GL matrices back, so it
 * follows along.
 */
@Mixin(GameRenderer.class)
public abstract class AspectRatioMixin {
	@ModifyArg(method = {"setupCamera", "renderHand", "renderWorld(IFJ)V", "renderClouds"},
		at = @At(value = "INVOKE", target = "Lorg/lwjgl/util/glu/Project;gluPerspective(FFFF)V", remap = false), index = 1)
	private float quartz$aspect(float aspect) {
		return Safe.map("aspect", AspectRatio::apply, aspect);
	}
}
