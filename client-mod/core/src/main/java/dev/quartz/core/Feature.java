package dev.quartz.core;

/**
 * Every in-game feature Doohickey Client can offer. Whether one exists on a
 * given Minecraft version — and whether it's built yet — is answered by
 * {@link CompatRegistry}, never by version checks in feature code.
 *
 * {@link #risk} marks grey-area features: they're opt-in, off by default,
 * and labelled [OPT-IN / RISK] because some servers forbid them.
 */
public enum Feature {
	// Visual / world
	SKY_COLOR(Category.WORLD, "Sky colour"),
	SKY_TEXTURE(Category.WORLD, "Atmosphere: custom skies and ambient weather"),
	TIME_LOCK(Category.WORLD, "Client-side time"),
	FOG(Category.WORLD, "Fog distance"),
	FOG_COLOR(Category.WORLD, "Fog colour"),
	WEATHER_OVERRIDE(Category.WORLD, "Weather override"),
	FULLBRIGHT(Category.WORLD, "Fullbright"),
	CLOUDS(Category.WORLD, "Cloud control"),
	CELESTIAL(Category.WORLD, "Sun, moon and stars"),
	VOID_FOG_REMOVAL(Category.WORLD, "Remove void fog"),
	PARTICLES(Category.WORLD, "Particle control"),
	VIEW_BOBBING(Category.WORLD, "View and hand bobbing"),
	HURT_CAMERA(Category.WORLD, "Hurt camera shake"),
	DAMAGE_TINT(Category.WORLD, "Damage tint"),
	CUSTOM_CROSSHAIR(Category.WORLD, "Custom crosshair"),
	DYNAMIC_CROSSHAIR(Category.WORLD, "Dynamic crosshair"),
	BLOCK_OUTLINE(Category.WORLD, "Block outline"),
	ITEM_PHYSICS(Category.WORLD, "Item physics / 2D items"),
	CHUNK_BORDERS(Category.WORLD, "Chunk borders"),
	LIGHT_OVERLAY(Category.WORLD, "Light level overlay"),
	TNT_COUNTDOWN(Category.WORLD, "TNT countdown"),
	CLEAN_VIEW(Category.WORLD, "Low fire and no pumpkin blur"),

	// HUD
	HUD_EDITOR(Category.HUD, "Draggable HUD editor"),
	HUD_FPS(Category.HUD, "FPS"),
	HUD_CPS(Category.HUD, "CPS"),
	HUD_PING(Category.HUD, "Ping"),
	HUD_TPS(Category.HUD, "TPS"),
	HUD_COORDINATES(Category.HUD, "Coordinates and facing"),
	HUD_BIOME(Category.HUD, "Biome and dimension"),
	HUD_KEYSTROKES(Category.HUD, "Keystrokes"),
	HUD_ARMOR(Category.HUD, "Armor status"),
	HUD_POTIONS(Category.HUD, "Potion effects"),
	HUD_REACH(Category.HUD, "Reach display"),
	HUD_COMBO(Category.HUD, "Combo counter"),
	HUD_SCOREBOARD(Category.HUD, "Scoreboard"),
	HUD_CHAT(Category.HUD, "Chat customization"),
	HUD_ITEM_COUNTER(Category.HUD, "Item and arrow counter"),
	HUD_GRAPHS(Category.HUD, "Ping and FPS graphs"),
	HUD_CLOCK(Category.HUD, "Clock and session timer"),
	HUD_SERVER_IP(Category.HUD, "Server address"),
	HUD_MEMORY(Category.HUD, "Memory usage"),
	HUD_DIRECTION(Category.HUD, "Direction / compass"),
	HUD_SPEED(Category.HUD, "Speedometer"),
	HUD_DAY(Category.HUD, "Day counter"),
	HUD_SATURATION(Category.HUD, "Saturation"),
	HUD_BLOCK_INFO(Category.HUD, "Block info"),
	TAB_PING(Category.HUD, "Ping numbers in the tab list"),
	SMOOTH_HOTBAR(Category.HUD, "Smooth hotbar"),
	PICKUP_FEED(Category.HUD, "Item pickup feed"),

	// PvP / gameplay
	TOGGLE_SPRINT_SNEAK(Category.PVP, "Toggle sprint and sneak"),
	SPRINT_RESET(Category.PVP, "Sprint reset on hit", true),
	OLD_ANIMATIONS(Category.PVP, "1.7/1.8 animations"),
	BLOCKHIT_ANIMATION(Category.PVP, "1.7 blockhit animation"),
	HIT_COLOR(Category.PVP, "Hit colour"),
	NO_HIT_DELAY(Category.PVP, "No hit delay", true),
	HITBOXES(Category.PVP, "Hitboxes"),
	NAMETAGS(Category.PVP, "Custom name tags (health, armour)"),
	AUTO_TOOL(Category.PVP, "Auto tool / weapon", true),
	RAW_INPUT(Category.PVP, "Raw mouse input"),
	ZOOM(Category.PVP, "Zoom"),
	ASPECT_RATIO(Category.PVP, "Aspect ratio (stretched)"),
	FREELOOK(Category.PVP, "Freelook", true),
	PING_REACH_DISPLAY(Category.PVP, "Ping-based reach display"),
	STATIC_FOV(Category.PVP, "No speed FOV"),
	MOUSE_DELAY_FIX(Category.PVP, "Mouse delay fix"),
	HIT_MARKER(Category.PVP, "Hit marker"),
	DAMAGE_NUMBERS(Category.PVP, "Damage numbers"),
	KILL_BANNER(Category.PVP, "Kill banner and streaks"),

	// Performance
	SODIUM_INTEGRATION(Category.PERFORMANCE, "Sodium / Iris integration"),
	ENTITY_DISTANCE(Category.PERFORMANCE, "Entity render distance"),
	ENTITY_CULLING(Category.PERFORMANCE, "Entity culling"),
	TILE_ENTITY_CULLING(Category.PERFORMANCE, "Block entity culling"),
	PARTICLE_CULLING(Category.PERFORMANCE, "Particle culling"),
	CHUNK_THROTTLE(Category.PERFORMANCE, "Chunk update throttling"),
	FPS_CAP(Category.PERFORMANCE, "Max FPS preset (no vsync or cap, fast graphics)"),
	DYNAMIC_RENDER_DISTANCE(Category.PERFORMANCE, "Dynamic render distance"),
	SMART_ANIMATIONS(Category.PERFORMANCE, "Smart animations"),
	BORDERLESS(Category.PERFORMANCE, "Borderless fullscreen"),
	THREADED_CHUNKS(Category.PERFORMANCE, "Multithreaded chunk building"),
	HIDE_PLANTS(Category.PERFORMANCE, "Hide grass and flowers"),
	STATIC_TEXTURES(Category.PERFORMANCE, "Static water, lava and fire"),
	SIMPLE_ITEMS(Category.PERFORMANCE, "One model per dropped stack"),
	HIDE_ARMOR_STANDS(Category.PERFORMANCE, "Hide armor stands"),

	// Audio
	SOUND_VOLUMES(Category.AUDIO, "Per-category volume"),
	SOUND_PACKS(Category.AUDIO, "Custom sound packs"),
	SOUND_POSITIONING(Category.AUDIO, "Sound positioning"),
	SOUND_MUTE(Category.AUDIO, "Mute specific sounds"),
	HIT_SOUNDS(Category.AUDIO, "Hit and kill sounds"),
	LOW_HEALTH_ALERT(Category.AUDIO, "Low health alert"),

	// Cosmetics
	CAPES(Category.COSMETICS, "Capes"),
	WEARABLES(Category.COSMETICS, "Wings, hats, bandanas, halos"),
	RICE_HAT(Category.COSMETICS, "Rice hat"),
	EMOTES(Category.COSMETICS, "Emotes"),
	KILL_EFFECTS(Category.COSMETICS, "Kill effects"),
	BREAK_PARTICLES(Category.COSMETICS, "Block-break particles"),
	TOTEM_EFFECT(Category.COSMETICS, "Totem pop effect"),
	HIT_EFFECTS(Category.COSMETICS, "Hit effects"),
	TRAILS(Category.COSMETICS, "Particle trails"),

	// Utility
	MINIMAP_WAYPOINTS(Category.UTILITY, "Minimap and waypoints"),
	SCREENSHOTS(Category.UTILITY, "Screenshot manager"),
	REPLAY(Category.UTILITY, "Replay recording"),
	MACROS(Category.UTILITY, "Macros / scripted keybinds", true),
	SERVER_PROFILES(Category.UTILITY, "Per-server config profiles"),
	HUD_PROFILES(Category.UTILITY, "HUD profiles"),
	ACCOUNT_SWITCHER(Category.UTILITY, "In-game account switcher");

	public enum Category { WORLD, HUD, PVP, PERFORMANCE, AUDIO, COSMETICS, UTILITY }

	public final Category category;
	public final String label;
	public final boolean risk;

	Feature(Category category, String label) {
		this(category, label, false);
	}

	Feature(Category category, String label, boolean risk) {
		this.category = category;
		this.label = label;
		this.risk = risk;
	}

	/** The label as the UI shows it, with the grey-area marker where it applies. */
	public String displayName() {
		return risk ? label + " [OPT-IN / RISK]" : label;
	}
}
