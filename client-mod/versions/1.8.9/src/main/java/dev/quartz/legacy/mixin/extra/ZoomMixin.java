package dev.quartz.legacy.mixin.extra;

import dev.quartz.core.Safe;
import dev.quartz.core.pvp.Zoom;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Zoom: narrow the world's field of view while the zoom key is held. */
@Mixin(GameRenderer.class)
public abstract class ZoomMixin {
	@Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
	private void quartz$zoom(float tickDelta, boolean changingFov, CallbackInfoReturnable<Float> cir) {
		// The held item uses the same method with changingFov = false; leave it alone.
		if (changingFov) {
			float fov = cir.getReturnValueF();
			cir.setReturnValue(Safe.call("zoom", () -> Zoom.apply(fov), fov));
		}
	}
}
