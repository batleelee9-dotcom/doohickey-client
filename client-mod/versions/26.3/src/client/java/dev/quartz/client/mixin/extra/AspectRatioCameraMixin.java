package dev.quartz.client.mixin.extra;

import dev.quartz.core.Safe;
import dev.quartz.core.pvp.AspectRatio;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Aspect ratio (stretched): the world projection and its culling frustum, kept in step so nothing at the edges vanishes. */
@Mixin(Camera.class)
public abstract class AspectRatioCameraMixin {
	@ModifyArg(method = "update", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;setupPerspective(FFFFF)V"), index = 3)
	private float quartz$width(float zNear, float zFar, float fov, float width, float height) {
		return AspectRatio.width(width, height);
	}

	@ModifyArg(method = "createProjectionMatrixForCulling", at = @At(value = "INVOKE", target = "Lorg/joml/Matrix4f;perspective(FFFFZ)Lorg/joml/Matrix4f;", remap = false), index = 1)
	private float quartz$cullAspect(float aspect) {
		return Safe.map("aspect", AspectRatio::apply, aspect);
	}
}
