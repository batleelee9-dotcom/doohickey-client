package dev.quartz.client.mixin.extra;

import dev.quartz.core.perf.Performance;
import net.minecraft.client.renderer.texture.TextureAtlas;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Static textures: water, lava, fire and portals stop animating, so no
 * animation frames are drawn into the atlases every tick. Compasses and
 * clocks pick a model rather than animate, so they keep moving.
 */
@Mixin(TextureAtlas.class)
public abstract class StaticTexturesMixin {
	@Inject(method = "cycleAnimationFrames", at = @At("HEAD"), cancellable = true)
	private void quartz$static(CallbackInfo ci) {
		if (Performance.staticTextures()) {
			ci.cancel();
		}
	}
}
