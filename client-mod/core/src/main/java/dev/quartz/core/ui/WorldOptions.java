package dev.quartz.core.ui;

import dev.quartz.core.Feature;
import dev.quartz.core.Quartz;
import dev.quartz.core.config.ClientConfig;
import dev.quartz.core.env.EnvironmentSettings;
import dev.quartz.core.env.EnvironmentSettings.Fog;
import dev.quartz.core.env.EnvironmentSettings.Weather;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** The "World" tab: sky, time, fog and weather — only what this version supports. */
public final class WorldOptions {
	/** -1 = vanilla; the rest are 0xRRGGBB. */
	private static final List<Integer> SKIES = Arrays.asList(-1, 0x7BA4FF, 0xFF9A62, 0x3B2F7A, 0x0B1026, 0x8B7CF6, 0x7FE0C0);
	private static final List<String> SKY_NAMES = Arrays.asList("Vanilla", "Clear blue", "Sunset", "Twilight", "Night", "Quartz", "Mint");
	/** -1 = real time; the rest are ticks into the day. */
	private static final List<Integer> TIMES = Arrays.asList(-1, 23500, 1000, 6000, 12500, 18000);
	private static final List<String> TIME_NAMES = Arrays.asList("Real", "Sunrise", "Morning", "Noon", "Sunset", "Midnight");
	private static final List<Fog> FOGS = Arrays.asList(Fog.VANILLA, Fog.OFF, Fog.LIGHT, Fog.THICK);
	private static final List<String> FOG_NAMES = Arrays.asList("Vanilla", "Off", "Light", "Thick");
	private static final List<Integer> FOG_COLORS = Arrays.asList(-1, 0xDCE6F5, 0xF2C49B, 0x8B7CF6, 0x10131F);
	private static final List<String> FOG_COLOR_NAMES = Arrays.asList("Vanilla", "Soft white", "Warm", "Quartz", "Night");
	private static final List<Weather> WEATHERS = Arrays.asList(Weather.VANILLA, Weather.CLEAR, Weather.RAIN, Weather.THUNDER);
	private static final List<String> WEATHER_NAMES = Arrays.asList("Vanilla", "Clear", "Rain", "Thunder");

	private WorldOptions() {
	}

	public static Option sky() {
		return Option.choice(Feature.SKY_COLOR, "Sky", SKIES, SKY_NAMES,
			() -> s().customSky ? s().skyColor : -1,
			v -> {
				s().customSky = v >= 0;
				if (v >= 0) {
					s().skyColor = v;
				}
			});
	}

	public static Option time() {
		return Option.choice(Feature.TIME_LOCK, "Time", TIMES, TIME_NAMES,
			() -> s().lockTime ? s().timeOfDay : -1,
			v -> {
				s().lockTime = v >= 0;
				if (v >= 0) {
					s().timeOfDay = v;
				}
			});
	}

	public static Option fog() {
		return Option.choice(Feature.FOG, "Fog", FOGS, FOG_NAMES, () -> s().fog, v -> s().fog = v);
	}

	public static Option fogColour() {
		return Option.choice(Feature.FOG_COLOR, "Fog colour", FOG_COLORS, FOG_COLOR_NAMES,
			() -> s().customFogColor ? s().fogColor : -1,
			v -> {
				s().customFogColor = v >= 0;
				if (v >= 0) {
					s().fogColor = v;
				}
			});
	}

	public static Option weather() {
		return Option.choice(Feature.WEATHER_OVERRIDE, "Weather", WEATHERS, WEATHER_NAMES, () -> s().weather, v -> s().weather = v);
	}

	public static Option fullbright() {
		return Option.toggle(Feature.FULLBRIGHT, "Fullbright", () -> s().fullbright, v -> s().fullbright = v);
	}

	public static Option voidFog() {
		return Option.toggle(Feature.VOID_FOG_REMOVAL, "Remove void fog", () -> s().removeVoidFog, v -> s().removeVoidFog = v);
	}

	public static List<Option> all() {
		List<Option> options = new ArrayList<>();
		for (Option o : new Option[] {sky(), time(), fog(), fogColour(), weather(), fullbright(), voidFog()}) {
			add(options, o);
		}
		return options;
	}

	/** Hidden, not broken: options for features this version lacks are left out. */
	private static void add(List<Option> options, Option option) {
		if (Quartz.available(option.feature)) {
			options.add(option);
		}
	}

	private static EnvironmentSettings s() {
		return ClientConfig.get().environment;
	}
}
