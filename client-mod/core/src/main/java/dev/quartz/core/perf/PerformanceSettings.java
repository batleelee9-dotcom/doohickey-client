package dev.quartz.core.perf;

/** Performance settings, stored in client.json. */
public final class PerformanceSettings {
	/** Lower the render distance while FPS is under target; restore it when there's headroom. */
	public boolean dynamicRenderDistance = false;
	public int targetFps = 60;
	public int minRenderDistance = 4;

	/** Don't draw entities further than this many blocks away (0 = no limit). */
	public int entityDistance = 0;
	/** Don't draw signs, chests, heads and banners further than this many blocks away (0 = vanilla, 64). */
	public int blockEntityDistance = 48;
	/** Fastest video settings (no vsync or FPS cap, fast graphics...). The player's own come back when it's switched off. */
	public boolean maxFps = true;
	public boolean maxFpsApplied = false;
	public java.util.Map<String, String> maxFpsRestore = new java.util.HashMap<>();
	/** Leave grass, ferns and dead bushes (and flowers) out of the world's meshes. */
	public boolean hideGrass = false;
	public boolean hideFlowers = false;
	/** Water, lava, fire and portals stop animating: no texture uploads every tick. */
	public boolean staticTextures = false;
	/** One model per dropped stack instead of up to five. */
	public boolean simpleItems = false;
	/** Skip armor stand models; invisible ones (floating text) still show their text. */
	public boolean hideArmorStands = false;

	public void sanitize() {
		targetFps = Math.max(20, Math.min(360, targetFps));
		minRenderDistance = Math.max(2, Math.min(16, minRenderDistance));
		entityDistance = Math.max(0, Math.min(512, entityDistance));
		blockEntityDistance = Math.max(0, Math.min(64, blockEntityDistance));
		if (maxFpsRestore == null) {
			maxFpsRestore = new java.util.HashMap<>();
		}
	}
}
