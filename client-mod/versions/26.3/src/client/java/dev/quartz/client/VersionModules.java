package dev.quartz.client;

import dev.quartz.client.cosmetics.Capes;
import dev.quartz.client.cosmetics.Cosmetics;
import dev.quartz.client.hud.HudModule;
import dev.quartz.client.hud.HudModules;
import dev.quartz.client.map.Minimap;
import dev.quartz.client.pvp.HitColor;
import dev.quartz.core.Feature;
import dev.quartz.core.config.ClientConfig;
import dev.quartz.core.ui.Module;
import dev.quartz.core.ui.Modules;
import dev.quartz.core.ui.Option;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Menu modules only 26.3 has: the minimap, toggle status, hit colour, damage tint and cosmetics. */
final class VersionModules {
	private VersionModules() {
	}

	static void register() {
		HudModule toggleStatus = HudModules.ALL.stream().filter(m -> m.id.equals("toggle_status")).findFirst().orElse(null);
		if (toggleStatus != null) {
			Modules.register(Modules.Category.HUD, () -> new Module(toggleStatus.name, "Shows when sprint or sneak is toggled", Feature.TOGGLE_SPRINT_SNEAK,
				Option.toggle(Feature.TOGGLE_SPRINT_SNEAK, toggleStatus.name, () -> toggleStatus.state().enabled, v -> toggleStatus.state().enabled = v)));
		}
		Modules.register(Modules.Category.HUD, () -> new Module("Minimap", "A map of the area, with waypoints", Feature.MINIMAP_WAYPOINTS,
			Option.toggle(Feature.MINIMAP_WAYPOINTS, "Minimap", () -> Minimap.MODULE.state().enabled, v -> Minimap.MODULE.state().enabled = v),
			Option.choice(Feature.MINIMAP_WAYPOINTS, "Zoom", ints(Minimap.ZOOMS), names(Minimap.ZOOMS, "x"), Minimap::zoom, v -> c().minimapZoom = v),
			Option.toggle(Feature.MINIMAP_WAYPOINTS, "Waypoint labels", () -> c().waypointsInWorld, v -> c().waypointsInWorld = v),
			Option.toggle(Feature.MINIMAP_WAYPOINTS, "Death waypoints", () -> c().deathWaypoints, v -> c().deathWaypoints = v)));

		List<Integer> hitColors = new ArrayList<>();
		hitColors.add(0);
		for (int color : HitColor.PRESETS) {
			hitColors.add(color);
		}
		List<String> hitNames = new ArrayList<>();
		hitNames.add("Vanilla");
		hitNames.addAll(Arrays.asList(HitColor.PRESET_NAMES));
		Modules.register(Modules.Category.PVP, () -> Module.of(Option.choice(Feature.HIT_COLOR, "Hit colour", hitColors, hitNames,
			() -> c().hitColorEnabled ? c().hitColor : 0,
			v -> {
				c().hitColorEnabled = v != 0;
				if (v != 0) {
					c().hitColor = v;
				}
				HitColor.apply();
			}), "The tint on entities you hit"));
		Modules.register(Modules.Category.PVP, () -> new Module("Damage tint", "A red edge when you're hurt or low", Feature.DAMAGE_TINT,
			Option.toggle(Feature.DAMAGE_TINT, "Damage tint", () -> c().damageTint, v -> c().damageTint = v)));

		Modules.register(Modules.Category.COSMETICS, () -> Module.of(Option.choice(Feature.CAPES, "Cape", Capes.IDS, Capes.NAMES,
			() -> c().cape, v -> c().cape = v), "Original designs, only you see them"));
		Modules.register(Modules.Category.COSMETICS, wearable("Hat", Cosmetics.HATS, () -> c().hat, v -> c().hat = v));
		Modules.register(Modules.Category.COSMETICS, wearable("Bandana", Cosmetics.BANDANAS, () -> c().bandana, v -> c().bandana = v));
		Modules.register(Modules.Category.COSMETICS, wearable("Wings", Cosmetics.WINGS, () -> c().wings, v -> c().wings = v));
	}

	private static Supplier<Module> wearable(String label, List<Cosmetics.Option> options, Supplier<String> get, Consumer<String> set) {
		List<String> ids = options.stream().map(Cosmetics.Option::id).toList();
		List<String> names = options.stream().map(Cosmetics.Option::name).toList();
		return () -> Module.of(Option.choice(Feature.WEARABLES, label, ids, names, get, set), "Hidden under helmets and elytras");
	}

	private static List<Integer> ints(int[] values) {
		return Arrays.stream(values).boxed().toList();
	}

	private static List<String> names(int[] values, String suffix) {
		return Arrays.stream(values).mapToObj(v -> v + suffix).toList();
	}

	private static ClientConfig c() {
		return ClientConfig.get();
	}
}
