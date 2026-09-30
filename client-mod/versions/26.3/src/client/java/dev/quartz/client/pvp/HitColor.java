package dev.quartz.client.pvp;

import com.mojang.blaze3d.platform.NativeImage;
import dev.quartz.core.config.ClientConfig;
import net.minecraft.client.renderer.texture.DynamicTexture;

/**
 * The red flash on hurt entities is the top half of a 16×16 "overlay"
 * texture. Repainting those rows changes the hit colour for every entity
 * renderer at once, with no per-entity hooks.
 */
public final class HitColor {
	/** Vanilla's value: ARGB 0xB2FF0000. */
	public static final int VANILLA = 0xB2FF0000;
	public static final int[] PRESETS = {0xB24080FF, 0xB240FF80, 0xB2FFD040, 0xB2FF40FF, 0xB2FFFFFF};
	public static final String[] PRESET_NAMES = {"Blue", "Green", "Gold", "Pink", "White"};

	private static DynamicTexture texture;

	private HitColor() {
	}

	/** Called by the mixin once the game creates its overlay texture. */
	public static void attach(DynamicTexture overlay) {
		texture = overlay;
		apply();
	}

	public static void apply() {
		if (texture == null) {
			return;
		}
		ClientConfig c = ClientConfig.get();
		int color = c.hitColorEnabled ? c.hitColor : VANILLA;
		NativeImage pixels = texture.getPixels();
		if (pixels == null) {
			return;
		}
		for (int y = 0; y < 8; y++) {
			for (int x = 0; x < 16; x++) {
				pixels.setPixel(x, y, color);
			}
		}
		texture.upload();
	}
}
