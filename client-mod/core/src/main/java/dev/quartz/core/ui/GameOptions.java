package dev.quartz.core.ui;

import dev.quartz.core.Feature;
import dev.quartz.core.Quartz;
import dev.quartz.core.config.ClientConfig;
import dev.quartz.core.perf.PerformanceSettings;
import dev.quartz.core.pvp.CrosshairStyle;
import dev.quartz.core.pvp.PvpState;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Performance and PvP switches shared by every version's menu (only what this version supports). */
public final class GameOptions {
	private static final List<Integer> PARTICLES = Arrays.asList(100, 75, 50, 25, 0);
	private static final List<String> PARTICLE_NAMES = Arrays.asList("All", "75%", "50%", "25%", "None");
	private static final List<Integer> TARGETS = Arrays.asList(30, 60, 90, 120, 144);
	private static final List<String> TARGET_NAMES = Arrays.asList("30 FPS", "60 FPS", "90 FPS", "120 FPS", "144 FPS");
	private static final List<Integer> ENTITY_DISTANCES = Arrays.asList(0, 32, 48, 64, 96, 128);
	private static final List<String> ENTITY_NAMES = Arrays.asList("Unlimited", "32 blocks", "48 blocks", "64 blocks", "96 blocks", "128 blocks");
	private static final List<Float> ZOOM_LEVELS = Arrays.asList(2f, 3f, 4f, 6f, 8f);
	private static final List<String> ZOOM_NAMES = Arrays.asList("2x", "3x", "4x", "6x", "8x");
	private static final List<Integer> CROSSHAIR_STYLES = Arrays.asList(0, 1, 2, 3, 4);
	private static final List<Integer> CROSSHAIR_COLORS = new ArrayList<>();
	private static final List<Integer> CROSSHAIR_SIZES = Arrays.asList(2, 3, 4, 5, 6, 8, 10);
	private static final List<String> CROSSHAIR_SIZE_NAMES = Arrays.asList("2", "3", "4", "5", "6", "8", "10");
	private static final List<Integer> CROSSHAIR_GAPS = Arrays.asList(0, 1, 2, 3, 4, 6);
	private static final List<String> CROSSHAIR_GAP_NAMES = Arrays.asList("0", "1", "2", "3", "4", "6");

	static {
		for (int color : CrosshairStyle.COLORS) {
			CROSSHAIR_COLORS.add(color);
		}
	}

	private GameOptions() {
	}

	public static Option toggleSprint() {
		return Option.toggle(Feature.TOGGLE_SPRINT_SNEAK, "Toggle sprint", () -> c().sprintToggled, v -> c().sprintToggled = v);
	}

	public static Option toggleSneak() {
		return Option.toggle(Feature.TOGGLE_SPRINT_SNEAK, "Toggle sneak", () -> PvpState.sneakToggled, v -> PvpState.sneakToggled = v);
	}

	public static Option particles() {
		return Option.choice(Feature.PARTICLES, "Particles", PARTICLES, PARTICLE_NAMES, () -> c().particlePercent, v -> c().particlePercent = v);
	}

	public static Option dynamicRenderDistance() {
		return Option.toggle(Feature.DYNAMIC_RENDER_DISTANCE, "Dynamic render distance", () -> p().dynamicRenderDistance, v -> p().dynamicRenderDistance = v);
	}

	public static Option targetFps() {
		return Option.choice(Feature.DYNAMIC_RENDER_DISTANCE, "Keep FPS above", TARGETS, TARGET_NAMES, () -> p().targetFps, v -> p().targetFps = v);
	}

	public static Option entityDistance() {
		return Option.choice(Feature.ENTITY_DISTANCE, "Entity distance", ENTITY_DISTANCES, ENTITY_NAMES, () -> p().entityDistance, v -> p().entityDistance = v);
	}

	public static Option zoom() {
		return Option.toggle(Feature.ZOOM, "Zoom (hold C)", () -> c().zoomEnabled, v -> c().zoomEnabled = v);
	}

	public static Option zoomLevel() {
		return Option.choice(Feature.ZOOM, "Zoom level", ZOOM_LEVELS, ZOOM_NAMES, () -> c().zoomFactor, v -> c().zoomFactor = v);
	}

	public static Option smoothZoom() {
		return Option.toggle(Feature.ZOOM, "Smooth zoom", () -> c().zoomSmooth, v -> c().zoomSmooth = v);
	}

	public static Option hitboxes() {
		return Option.toggle(Feature.HITBOXES, "Hitboxes", () -> Quartz.adapter().hitboxes(), v -> Quartz.adapter().setHitboxes(v));
	}

	public static Option crosshair() {
		return Option.toggle(Feature.CUSTOM_CROSSHAIR, "Custom crosshair", () -> c().customCrosshair, v -> c().customCrosshair = v);
	}

	public static Option crosshairStyle() {
		return Option.choice(Feature.CUSTOM_CROSSHAIR, "Crosshair", CROSSHAIR_STYLES, Arrays.asList(CrosshairStyle.STYLES),
			() -> c().crosshairStyle, v -> c().crosshairStyle = v);
	}

	public static Option crosshairColour() {
		return Option.choice(Feature.CUSTOM_CROSSHAIR, "Crosshair colour", CROSSHAIR_COLORS, Arrays.asList(CrosshairStyle.COLOR_NAMES),
			() -> c().crosshairColor, v -> c().crosshairColor = v);
	}

	public static Option crosshairSize() {
		return Option.choice(Feature.CUSTOM_CROSSHAIR, "Size", CROSSHAIR_SIZES, CROSSHAIR_SIZE_NAMES, () -> c().crosshairSize, v -> c().crosshairSize = v);
	}

	public static Option crosshairGap() {
		return Option.choice(Feature.CUSTOM_CROSSHAIR, "Gap", CROSSHAIR_GAPS, CROSSHAIR_GAP_NAMES, () -> c().crosshairGap, v -> c().crosshairGap = v);
	}

	public static Option crosshairOutline() {
		return Option.toggle(Feature.CUSTOM_CROSSHAIR, "Outline", () -> c().crosshairOutline, v -> c().crosshairOutline = v);
	}

	/** The flat list older menus show; follow-up settings appear once their switch is on. */
	public static List<Option> all() {
		List<Option> options = new ArrayList<>();
		add(options, toggleSprint());
		add(options, toggleSneak());
		add(options, particles());
		add(options, dynamicRenderDistance());
		if (p().dynamicRenderDistance) {
			add(options, targetFps());
		}
		add(options, entityDistance());
		add(options, zoom());
		if (c().zoomEnabled) {
			add(options, zoomLevel());
			add(options, smoothZoom());
		}
		add(options, hitboxes());
		add(options, crosshair());
		if (c().customCrosshair) {
			add(options, crosshairStyle());
			add(options, crosshairColour());
		}
		return options;
	}

	/** Only the options for these features (for menus that show the rest elsewhere). */
	public static List<Option> of(Feature... features) {
		List<Feature> wanted = Arrays.asList(features);
		List<Option> out = new ArrayList<>();
		for (Option o : all()) {
			if (wanted.contains(o.feature)) {
				out.add(o);
			}
		}
		return out;
	}

	private static void add(List<Option> options, Option option) {
		if (Quartz.available(option.feature)) {
			options.add(option);
		}
	}

	private static ClientConfig c() {
		return ClientConfig.get();
	}

	private static PerformanceSettings p() {
		return ClientConfig.get().performance;
	}
}
