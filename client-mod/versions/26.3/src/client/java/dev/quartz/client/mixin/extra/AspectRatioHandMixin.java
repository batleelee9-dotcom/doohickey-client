package dev.quartz.client.mixin.extra;

import dev.quartz.core.pvp.AspectRatio;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Aspect ratio (stretched): the held item, stretched the same way as the world. */
@Mixin(GameRenderer.class)
public abstract class AspectRatioHandMixin {
	@ModifyArg(method = "render3dHud", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/Projection;setupPerspective(FFFFF)V"), index = 3)
	private float quartz$width(float zNear, float zFar, float fov, float width, float height) {
		return AspectRatio.width(width, height);
	}
}
