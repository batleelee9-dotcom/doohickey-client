package dev.quartz.core.ui;

import dev.quartz.core.Feature;
import dev.quartz.core.Quartz;
import dev.quartz.core.config.ClientConfig;
import dev.quartz.core.perf.PerformanceSettings;
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

	private GameOptions() {
	}

	public static List<Option> all() {
		List<Option> options = new ArrayList<>();
		add(options, Option.toggle(Feature.TOGGLE_SPRINT_SNEAK, "Toggle sprint", () -> c().sprintToggled, v -> c().sprintToggled = v));
		add(options, Option.toggle(Feature.TOGGLE_SPRINT_SNEAK, "Toggle sneak", () -> PvpState.sneakToggled, v -> PvpState.sneakToggled = v));
		add(options, Option.choice(Feature.PARTICLES, "Particles", PARTICLES, PARTICLE_NAMES, () -> c().particlePercent, v -> c().particlePercent = v));
		add(options, Option.toggle(Feature.DYNAMIC_RENDER_DISTANCE, "Dynamic render distance",
			() -> p().dynamicRenderDistance, v -> p().dynamicRenderDistance = v));
		if (p().dynamicRenderDistance) {
			add(options, Option.choice(Feature.DYNAMIC_RENDER_DISTANCE, "Keep FPS above", TARGETS, TARGET_NAMES, () -> p().targetFps, v -> p().targetFps = v));
		}
		add(options, Option.choice(Feature.ENTITY_DISTANCE, "Entity distance", ENTITY_DISTANCES, ENTITY_NAMES,
			() -> p().entityDistance, v -> p().entityDistance = v));
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
