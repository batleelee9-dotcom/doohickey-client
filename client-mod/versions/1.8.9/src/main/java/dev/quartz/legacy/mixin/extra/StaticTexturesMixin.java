package dev.quartz.legacy.mixin.extra;

import dev.quartz.core.perf.Performance;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.texture.SpriteAtlasTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Static textures: water, lava, fire and portals stop animating, so no
 * texture is re-uploaded every tick. Compasses and clocks are their own
 * sprite types and keep moving.
 */
@Mixin(SpriteAtlasTexture.class)
public abstract class StaticTexturesMixin {
	@Redirect(method = "update", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/texture/Sprite;update()V"))
	private void quartz$animate(Sprite sprite) {
		if (sprite.getClass() != Sprite.class || !Performance.staticTextures()) {
			sprite.update();
		}
	}
}
