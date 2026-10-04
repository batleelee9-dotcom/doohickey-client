package dev.quartz.core.ui;

import dev.quartz.core.Feature;
import dev.quartz.core.Quartz;
import dev.quartz.core.config.ClientConfig;
import dev.quartz.core.fx.EffectSettings;
import dev.quartz.core.fx.Effects;
import dev.quartz.core.fx.RiceHat;
import dev.quartz.core.hud.Hud;
import dev.quartz.core.hud.HudElement;
import dev.quartz.core.pvp.AspectRatio;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Every module in the menu, by category. Modules for features this version lacks are left out. */
public final class Modules {
	public enum Category {
		HUD("HUD", "Everything drawn on your screen", "hud"),
		PVP("PvP", "Zoom, crosshair and controls", "game"),
		VISUAL("Visual", "Hit effects, trails and the world", "sparkle"),
		SOUND("Sound", "Hit, kill and alert sounds", "sound"),
		PERFORMANCE("Performance", "Smoother frames, further views", "bolt"),
		COSMETICS("Cosmetics", "Capes and wearables (only you see them)", "shirt");

		public final String title;
		public final String subtitle;
		public final String icon;

		Category(String title, String subtitle, String icon) {
			this.title = title;
			this.subtitle = subtitle;
			this.icon = icon;
		}
	}

	private static final Map<String, String> HUD_DESCRIPTIONS = new HashMap<>();

	static {
		String[][] d = {
			{"fps", "Frames per second"},
			{"cps", "Clicks per second, left and right"},
			{"ping", "Your latency to the server"},
			{"coordinates", "Where you are and which way you face"},
			{"reach", "Distance of your last hit"},
			{"keystrokes", "WASD, mouse and jump as you press them"},
			{"potions", "Active effects and time left"},
			{"armor", "Armor and held item, with durability"},
			{"clock", "The time on your computer"},
			{"session", "How long you've been in this world"},
			{"memory", "Java memory in use"},
			{"server", "The address you're playing on"},
			{"direction", "Compass heading in degrees"},
			{"speed", "How fast you're moving"},
			{"day", "In-game days passed"},
			{"saturation", "Hidden food saturation"},
			{"arrows", "Arrows left in your inventory"},
			{"combo", "Hits in a row without taking one"},
			{"block", "The block you're looking at"},
			{"biome", "The biome you're standing in"},
		};
		for (String[] entry : d) {
			HUD_DESCRIPTIONS.put(entry[0], entry[1]);
		}
	}

	private static final List<Integer> TEXT_COLORS = Arrays.asList(0xFFFFFFFF, 0xFFB9AEFF, 0xFF7FE0D3, 0xFFFFD166, 0xFF7CFC7C, 0xFFFF8FA3);
	private static final List<String> TEXT_COLOR_NAMES = Arrays.asList("White", "Violet", "Teal", "Gold", "Green", "Pink");
	private static final List<Integer> AMOUNTS = Arrays.asList(1, 2, 3, 4, 5);
	private static final List<String> AMOUNT_NAMES = Arrays.asList("1x", "2x", "3x", "4x", "5x");
	private static final List<Integer> VOLUMES = Arrays.asList(30, 50, 70, 100);
	private static final List<Integer> HAT_COLOURS = java.util.stream.IntStream.of(RiceHat.COLOURS).boxed().collect(java.util.stream.Collectors.toList());
	private static final List<Integer> BLOCK_ENTITY_DISTANCES = Arrays.asList(0, 16, 24, 32, 48);
	private static final List<String> BLOCK_ENTITY_DISTANCE_NAMES = Arrays.asList("Vanilla", "16", "24", "32", "48");
	private static final List<String> VOLUME_NAMES = Arrays.asList("Quiet", "Medium", "Loud", "Full");

	private Modules() {
	}

	private static final Map<Category, List<java.util.function.Supplier<Module>>> EXTRA = new java.util.EnumMap<>(Category.class);

	/** Adds a module only one Minecraft version has (e.g. 26.3's minimap or cosmetics). */
	public static void register(Category category, java.util.function.Supplier<Module> module) {
		EXTRA.computeIfAbsent(category, c -> new ArrayList<>()).add(module);
	}

	public static List<Module> of(Category category) {
		List<Module> out = new ArrayList<>();
		switch (category) {
			case HUD:
				for (HudElement e : Hud.available()) {
					String desc = HUD_DESCRIPTIONS.containsKey(e.id) ? HUD_DESCRIPTIONS.get(e.id) : "";
					add(out, new Module(e.name, desc, e.feature,
						Option.toggle(e.feature, e.name, () -> e.state().enabled, v -> e.state().enabled = v)));
				}
				add(out, new Module("HUD style", "Backgrounds, shadows and text colour", Feature.HUD_EDITOR, null,
					Option.toggle(Feature.HUD_EDITOR, "Backgrounds", () -> c().moduleBackground, v -> c().moduleBackground = v),
					Option.toggle(Feature.HUD_EDITOR, "Text shadow", () -> c().textShadow, v -> c().textShadow = v),
					Option.choice(Feature.HUD_EDITOR, "Text colour", TEXT_COLORS, TEXT_COLOR_NAMES, () -> c().textColor | 0xFF000000, v -> c().textColor = v)));
				break;
			case PVP:
				add(out, new Module("Zoom", "Hold C to zoom in", Feature.ZOOM, GameOptions.zoom(), GameOptions.zoomLevel(), GameOptions.smoothZoom()));
				add(out, new Module("Crosshair", "Your own crosshair shape and colour", Feature.CUSTOM_CROSSHAIR, GameOptions.crosshair(),
					GameOptions.crosshairStyle(), GameOptions.crosshairColour(), GameOptions.crosshairSize(), GameOptions.crosshairGap(), GameOptions.crosshairOutline()));
				add(out, new Module("Toggle sprint", "Sprint without holding the key", Feature.TOGGLE_SPRINT_SNEAK, GameOptions.toggleSprint()));
				add(out, new Module("Toggle sneak", "Sneak without holding the key", Feature.TOGGLE_SPRINT_SNEAK, GameOptions.toggleSneak()));
				add(out, new Module("Hitboxes", "Show entity hitboxes", Feature.HITBOXES, GameOptions.hitboxes()));
				break;
			case VISUAL:
				add(out, Module.of(Option.choice(Feature.ASPECT_RATIO, "Aspect ratio", list(AspectRatio.IDS), list(AspectRatio.NAMES),
					() -> c().aspectRatio, v -> c().aspectRatio = v), "Stretch the view, like 4:3 stretched"));
				add(out, new Module("Rice hat", "A wide cone hat (only you see it)", Feature.RICE_HAT,
					Option.toggle(Feature.RICE_HAT, "Rice hat", () -> c().riceHat, v -> c().riceHat = v),
					Option.choice(Feature.RICE_HAT, "Colour", HAT_COLOURS, list(RiceHat.COLOUR_NAMES), () -> c().riceHatColor, v -> c().riceHatColor = v)));
				add(out, new Module("Hit effects", "Particles where your hits land", Feature.HIT_EFFECTS,
					Option.toggle(Feature.HIT_EFFECTS, "Hit effects", () -> fx().hitEffects, v -> fx().hitEffects = v),
					Option.choice(Feature.HIT_EFFECTS, "Effect", list(Effects.HIT_STYLES), list(Effects.HIT_STYLE_NAMES), () -> fx().hitEffect, v -> fx().hitEffect = v),
					Option.choice(Feature.HIT_EFFECTS, "Amount", AMOUNTS, AMOUNT_NAMES, () -> fx().hitAmount, v -> fx().hitAmount = v)));
				add(out, new Module("Trails", "Particles behind you as you move", Feature.TRAILS,
					Option.toggle(Feature.TRAILS, "Particle trail", () -> fx().trail, v -> fx().trail = v),
					Option.choice(Feature.TRAILS, "Style", list(Effects.TRAILS), list(Effects.TRAIL_NAMES), () -> fx().trailStyle, v -> fx().trailStyle = v)));
				add(out, new Module("Kill effects", "A burst when your target dies", Feature.KILL_EFFECTS,
					Option.toggle(Feature.KILL_EFFECTS, "Kill effects", () -> fx().killEffects, v -> fx().killEffects = v),
					Option.choice(Feature.KILL_EFFECTS, "Effect", list(Effects.KILL_EFFECTS), list(Effects.KILL_EFFECT_NAMES), () -> fx().killEffect, v -> fx().killEffect = v)));
				add(out, new Module("No hurt cam", "No screen tilt when you're hit", Feature.HURT_CAMERA,
					Option.toggle(Feature.HURT_CAMERA, "No hurt camera", () -> fx().noHurtCamera, v -> fx().noHurtCamera = v)));
				add(out, new Module("Fullbright", "See clearly in the dark", Feature.FULLBRIGHT, WorldOptions.fullbright()));
				add(out, Module.of(WorldOptions.sky(), "Your own sky colour"));
				add(out, Module.of(WorldOptions.time(), "Freeze the time of day (just for you)"));
				add(out, Module.of(WorldOptions.weather(), "Clear skies or a storm (just for you)"));
				add(out, Module.of(WorldOptions.fog(), "How far you see before fog"));
				add(out, Module.of(WorldOptions.fogColour(), "Tint the distance"));
				add(out, new Module("No void fog", "No darkness near the bottom of the world", Feature.VOID_FOG_REMOVAL, WorldOptions.voidFog()));
				break;
			case SOUND:
				add(out, new Module("Hit sounds", "A sound each time your hit lands", Feature.HIT_SOUNDS,
					Option.toggle(Feature.HIT_SOUNDS, "Hit sounds", () -> fx().hitSounds, v -> fx().hitSounds = v),
					Option.choice(Feature.HIT_SOUNDS, "Sound", list(Effects.SOUNDS), list(Effects.SOUND_NAMES), () -> fx().hitSound, v -> {
						fx().hitSound = v;
						Effects.preview(v, fx().hitVolume / 100f);
					}),
					Option.choice(Feature.HIT_SOUNDS, "Volume", VOLUMES, VOLUME_NAMES, () -> fx().hitVolume, v -> {
						fx().hitVolume = v;
						Effects.preview(fx().hitSound, v / 100f);
					})));
				add(out, new Module("Kill sounds", "A sound when your target dies", Feature.HIT_SOUNDS,
					Option.toggle(Feature.HIT_SOUNDS, "Kill sounds", () -> fx().killSounds, v -> fx().killSounds = v),
					Option.choice(Feature.HIT_SOUNDS, "Sound", list(Effects.KILL_SOUNDS), list(Effects.KILL_SOUND_NAMES), () -> fx().killSound, v -> {
						fx().killSound = v;
						Effects.preview(v, 0.9f);
					})));
				add(out, new Module("Low health alert", "A heartbeat when you're nearly dead", Feature.LOW_HEALTH_ALERT,
					Option.toggle(Feature.LOW_HEALTH_ALERT, "Low health alert", () -> fx().lowHealthAlert, v -> fx().lowHealthAlert = v)));
				break;
			case COSMETICS:
				break;
			default:
				add(out, new Module("Max FPS", "No vsync or FPS cap, fast graphics", Feature.FPS_CAP,
					Option.toggle(Feature.FPS_CAP, "Max FPS", () -> c().performance.maxFps, v -> c().performance.maxFps = v)));
				add(out, new Module("Dynamic render distance", "Shorter view while FPS is low", Feature.DYNAMIC_RENDER_DISTANCE,
					GameOptions.dynamicRenderDistance(), GameOptions.targetFps()));
				add(out, Module.of(Option.choice(Feature.TILE_ENTITY_CULLING, "Block entities", BLOCK_ENTITY_DISTANCES, BLOCK_ENTITY_DISTANCE_NAMES,
					() -> c().performance.blockEntityDistance, v -> c().performance.blockEntityDistance = v), "How far signs, chests and heads are drawn"));
				add(out, Module.of(GameOptions.particles(), "Fewer particles, more frames"));
				add(out, Module.of(GameOptions.entityDistance(), "Skip drawing far-away entities"));
		}
		if (EXTRA.containsKey(category)) {
			for (java.util.function.Supplier<Module> extra : EXTRA.get(category)) {
				add(out, extra.get());
			}
		}
		return out;
	}

	private static void add(List<Module> out, Module m) {
		if (Quartz.available(m.feature)) {
			out.add(m);
		}
	}

	private static List<String> list(String[] values) {
		return Arrays.asList(values);
	}

	private static ClientConfig c() {
		return ClientConfig.get();
	}

	private static EffectSettings fx() {
		return ClientConfig.get().effects;
	}
}
