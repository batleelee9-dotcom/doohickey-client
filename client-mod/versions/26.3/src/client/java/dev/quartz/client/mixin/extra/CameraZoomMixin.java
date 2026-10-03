package dev.quartz.client.mixin.extra;

import dev.quartz.core.Safe;
import dev.quartz.core.pvp.Zoom;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Zoom: narrow the world's field of view while the zoom key is held (26.x keeps FOV on the camera). */
@Mixin(Camera.class)
public abstract class CameraZoomMixin {
	@Inject(method = "calculateFov(F)F", at = @At("RETURN"), cancellable = true)
	private void quartz$zoom(float partialTick, CallbackInfoReturnable<Float> cir) {
		float fov = cir.getReturnValueF();
		cir.setReturnValue(Safe.call("zoom", () -> Zoom.apply(fov), fov));
	}
}
