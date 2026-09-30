package dev.quartz.core.perf;

/** Performance settings, stored in client.json. */
public final class PerformanceSettings {
	/** Lower the render distance while FPS is under target; restore it when there's headroom. */
	public boolean dynamicRenderDistance = false;
	public int targetFps = 60;
	public int minRenderDistance = 4;

	/** Don't draw entities further than this many blocks away (0 = no limit). */
	public int entityDistance = 0;

	public void sanitize() {
		targetFps = Math.max(20, Math.min(360, targetFps));
		minRenderDistance = Math.max(2, Math.min(16, minRenderDistance));
		entityDistance = Math.max(0, Math.min(512, entityDistance));
	}
}
