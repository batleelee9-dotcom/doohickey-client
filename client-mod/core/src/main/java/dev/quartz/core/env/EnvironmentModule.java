package dev.quartz.core.env;

import dev.quartz.core.Feature;
import dev.quartz.core.fx.Atmosphere;
import dev.quartz.core.Quartz;
import dev.quartz.core.config.ClientConfig;

/**
 * Sky, time, fog and weather — the decisions, independent of any Minecraft
 * version. Each version's hooks pass in what vanilla computed and apply
 * what comes back; a feature that isn't available on the running version
 * (see CompatRegistry) always returns the vanilla value.
 *
 * Everything here is visual and client-side: the server's time, weather,
 * spawning and crops are never touched.
 */
public final class EnvironmentModule {
	public static final int VANILLA = -1;
	private static final float FAR = 1.0e6f;
	private static final long DAY = 24000L;

	private EnvironmentModule() {
	}

	private static EnvironmentSettings settings() {
		return ClientConfig.get().environment;
	}

	/** Sky colour as 0xRRGGBB, or {@link #VANILLA}. */
	public static int skyColor() {
		EnvironmentSettings s = settings();
		return Quartz.available(Feature.SKY_COLOR) && s.customSky ? s.skyColor & 0xFFFFFF : VANILLA;
	}

	/** The client's clock, given vanilla's total ticks: same day, locked time of day. */
	public static long clock(long vanillaTicks) {
		EnvironmentSettings s = settings();
		if (!Quartz.available(Feature.TIME_LOCK) || !s.lockTime) {
			return vanillaTicks;
		}
		return Math.floorDiv(vanillaTicks, DAY) * DAY + s.timeOfDay;
	}

	public static float rainLevel(float vanilla) {
		if (!Quartz.available(Feature.WEATHER_OVERRIDE)) {
			return vanilla;
		}
		switch (settings().weather) {
			case CLEAR: return 0f;
			case RAIN:
			case THUNDER: return 1f;
			default: return vanilla;
		}
	}

	/** Final thunder level (vanilla multiplies it by the rain level already). */
	public static float thunderLevel(float vanilla) {
		if (!Quartz.available(Feature.WEATHER_OVERRIDE)) {
			return vanilla;
		}
		switch (settings().weather) {
			case CLEAR:
			case RAIN: return 0f;
			case THUNDER: return 1f;
			default: return vanilla;
		}
	}

	/** Whether the open-air fog distance is being replaced. */
	public static boolean overridesFog() {
		return Quartz.available(Feature.FOG) && (settings().fog != EnvironmentSettings.Fog.VANILLA || Atmosphere.density() > 0);
	}

	// Atmosphere fog: where it starts and ends, as shares of the render distance (light, medium, heavy).
	private static final float[] ATMOSPHERE_START = {0, 0.55f, 0.22f, 0.02f};
	private static final float[] ATMOSPHERE_END = {0, 1.0f, 0.7f, 0.38f};

	/** Fog start in blocks for open air, given the render distance and vanilla's value. */
	public static float fogStart(float renderDistanceBlocks, float vanilla) {
		if (!overridesFog()) {
			return vanilla;
		}
		int density = Atmosphere.density();
		if (density > 0) {
			return renderDistanceBlocks * ATMOSPHERE_START[density];
		}
		switch (settings().fog) {
			case OFF: return FAR;
			case LIGHT: return renderDistanceBlocks * 0.9f;
			case THICK: return renderDistanceBlocks * 0.05f;
			default: return vanilla;
		}
	}

	public static float fogEnd(float renderDistanceBlocks, float vanilla) {
		if (!overridesFog()) {
			return vanilla;
		}
		int density = Atmosphere.density();
		if (density > 0) {
			return renderDistanceBlocks * ATMOSPHERE_END[density];
		}
		switch (settings().fog) {
			case OFF: return FAR + 1f;
			case LIGHT: return renderDistanceBlocks;
			case THICK: return renderDistanceBlocks * 0.45f;
			default: return vanilla;
		}
	}

	/** Fog colour as 0xRRGGBB, or {@link #VANILLA}. */
	public static int fogColor() {
		// An atmosphere sky tints the fog to its horizon (or the chosen colour), so land melts into it.
		int atmosphere = Atmosphere.fogColor();
		if (atmosphere >= 0) {
			return atmosphere;
		}
		EnvironmentSettings s = settings();
		return Quartz.available(Feature.FOG_COLOR) && s.customFogColor ? s.fogColor & 0xFFFFFF : VANILLA;
	}

	public static boolean removeVoidFog() {
		return Quartz.available(Feature.VOID_FOG_REMOVAL) && settings().removeVoidFog;
	}

	public static boolean fullbright() {
		return Quartz.available(Feature.FULLBRIGHT) && settings().fullbright;
	}

	public static float red(int rgb) {
		return (rgb >> 16 & 0xFF) / 255f;
	}

	public static float green(int rgb) {
		return (rgb >> 8 & 0xFF) / 255f;
	}

	public static float blue(int rgb) {
		return (rgb & 0xFF) / 255f;
	}
}
