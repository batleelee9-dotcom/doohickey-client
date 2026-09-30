package dev.quartz.core.env;

/** The "World" settings: sky, time, fog and weather. Stored in client.json. */
public final class EnvironmentSettings {
	public enum Fog { VANILLA, OFF, LIGHT, THICK }

	public enum Weather { VANILLA, CLEAR, RAIN, THUNDER }

	public boolean customSky = false;
	/** 0xRRGGBB */
	public int skyColor = 0x7BA4FF;

	public boolean lockTime = false;
	/** Ticks into the day: 0 sunrise, 6000 noon, 12000 sunset, 18000 midnight. */
	public int timeOfDay = 6000;

	public Fog fog = Fog.VANILLA;
	public boolean customFogColor = false;
	/** 0xRRGGBB */
	public int fogColor = 0xDCE6F5;

	public Weather weather = Weather.VANILLA;

	/** 1.8.9–1.16: no darkening near bedrock. */
	public boolean removeVoidFog = false;

	/** See everything as if in daylight. */
	public boolean fullbright = false;

	/** Unknown enum names in the file (from a newer Doohickey) load as null. */
	public void sanitize() {
		if (fog == null) {
			fog = Fog.VANILLA;
		}
		if (weather == null) {
			weather = Weather.VANILLA;
		}
		timeOfDay = Math.floorMod(timeOfDay, 24000);
	}
}
