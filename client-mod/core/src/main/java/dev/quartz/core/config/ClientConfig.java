package dev.quartz.core.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.quartz.core.Log;
import dev.quartz.core.env.EnvironmentSettings;
import dev.quartz.core.perf.PerformanceSettings;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Everything the player can change, in one file per profile:
 * {@code config/quartz/client.json}. Shared by every Minecraft version, so
 * a profile's settings survive switching versions; fields a version
 * doesn't use are kept untouched.
 *
 * The file carries a {@code schemaVersion}; older files are upgraded by
 * {@link Migrations} on load. Edits made outside the game (by hand, or by
 * the launcher) are picked up within a second — see {@link #pollReload()}.
 */
public final class ClientConfig {
	public static final int SCHEMA = 2;

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static ClientConfig instance;
	private static Path dir;
	private static FileTime lastSeen;

	/** A HUD module's placement. Positions are fractions (0..1) of the free
	 *  space on each axis, so modules stay anchored to their edge or corner
	 *  when the window or GUI scale changes. */
	public static final class ModuleState {
		public boolean enabled;
		public float x;
		public float y;
		public float scale = 1.0f;

		public ModuleState() {
		}

		public ModuleState(boolean enabled, float x, float y) {
			this.enabled = enabled;
			this.x = x;
			this.y = y;
		}
	}

	public int schemaVersion = SCHEMA;

	// HUD
	public Map<String, ModuleState> modules = new LinkedHashMap<>();
	public boolean textShadow = true;
	public boolean moduleBackground = true;
	public int textColor = 0xFFFFFFFF;

	// PvP
	public boolean customCrosshair = false;
	public int crosshairStyle = 0;
	public int crosshairColor = 0xFFFFFFFF;
	public int crosshairSize = 5;
	public int crosshairGap = 2;
	public boolean crosshairOutline = true;
	public boolean hitColorEnabled = false;
	public int hitColor = 0xB24080FF;
	public boolean damageTint = true;
	public int particlePercent = 100;
	public boolean sprintToggled = false;
	/** Zoom (hold C): how far in, and whether it eases in and out. */
	public boolean zoomEnabled = true;
	public float zoomFactor = 4.0f;
	public boolean zoomSmooth = true;
	/** Stretched view: "native" or a preset like "4:3" (see AspectRatio). */
	public String aspectRatio = "native";

	// Map
	public int minimapZoom = 1;
	public boolean waypointsInWorld = true;
	public boolean deathWaypoints = true;

	// Cosmetics
	public String cape = "none";
	public String hat = "none";
	public String bandana = "none";
	public String wings = "none";
	public boolean riceHat = false;
	/** RiceHat.COLOURS; 0 is rainbow. */
	public int riceHatColor = 0;
	/** RiceHat.EVERYONE, YOU or OTHERS. */
	public int riceHatWho = 0;
	/** Rice hat shape and placement: sizes in percent, offsets in model pixels, tilt in degrees, spin 0 (off) to 3. */
	public int riceHatSize = 100;
	public int riceHatHeight = 100;
	public int riceHatX = 0;
	public int riceHatY = 0;
	public int riceHatZ = 0;
	public int riceHatTilt = 0;
	public int riceHatOpacity = 100;
	public int riceHatSpin = 0;

	// Name tags: health 0 = HP, 1 = percent, 2 = off; opacity and scale in percent.
	public boolean nameTags = false;
	public int nameTagHealth = 0;
	public boolean nameTagItems = true;
	public int nameTagOpacity = 60;
	public int nameTagScale = 100;
	public boolean nameTagTeamColours = true;
	public boolean nameTagSelf = false;

	// Atmosphere: a sky from fx.Atmosphere.SKIES, fog tint (0 = match sky) and density (0 = untouched), weather from fx.Weather.KINDS.
	public String atmosphereSky = "off";
	public int atmosphereFogColor = 0;
	public int atmosphereFog = 0;
	public String atmosphereWeather = "off";
	public boolean atmosphereMotion = true;

	// Quality of life
	public boolean tntCountdown = true;
	/** TNT fuse in tenths of a second, for versions that don't send it (1.8.9). */
	public int tntFuse = 35;
	public boolean tabPing = true;
	public boolean smoothHotbar = true;
	public boolean lowFire = false;
	public boolean noPumpkinBlur = false;
	public boolean staticFov = false;
	public boolean mouseDelayFix = true;

	// World: sky, fog, weather, time
	public EnvironmentSettings environment = new EnvironmentSettings();

	// Performance: dynamic render distance, entity distance
	public PerformanceSettings performance = new PerformanceSettings();

	// Effects and sounds
	public dev.quartz.core.fx.EffectSettings effects = new dev.quartz.core.fx.EffectSettings();

	/** Called once at startup with the profile's config folder. */
	public static void init(Path configDir) {
		dir = configDir;
		instance = load();
	}

	public static ClientConfig get() {
		if (instance == null) {
			// Something reached the config before Quartz.init(): load it from the
			// game folder (the working directory the launcher starts the game in)
			// rather than failing.
			Log.warn("Settings used before Quartz.init(); loading from ./config");
			init(java.nio.file.Paths.get("config").toAbsolutePath());
		}
		return instance;
	}

	public static Path path() {
		return dir.resolve("quartz").resolve("client.json");
	}

	/** Where schema 1 (Doohickey Client 1.0 for 26.3) kept its settings. */
	private static Path legacyPath() {
		return dir.resolve("quartz-client.json");
	}

	private static ClientConfig load() {
		Path path = path();
		Path source = Files.exists(path) ? path : Files.exists(legacyPath()) ? legacyPath() : null;
		if (source == null) {
			return new ClientConfig();
		}
		try {
			JsonObject json = GSON.fromJson(new String(Files.readAllBytes(source), StandardCharsets.UTF_8), JsonObject.class);
			if (json == null) {
				return new ClientConfig();
			}
			int from = Migrations.version(json);
			boolean migrated = Migrations.upgrade(json);
			ClientConfig config = fromJson(json);
			if (migrated || !source.equals(path)) {
				config.save();
				Log.info("Upgraded settings from schema " + from + " to " + SCHEMA);
				if (!source.equals(path)) {
					Files.move(source, source.resolveSibling("quartz-client.json.migrated"), StandardCopyOption.REPLACE_EXISTING);
				}
			} else {
				lastSeen = Files.getLastModifiedTime(path);
			}
			return config;
		} catch (IOException | RuntimeException e) {
			// Keep the broken file for inspection and start from defaults.
			Log.error("Settings file " + source + " couldn't be read; starting from defaults", e);
			try {
				Files.move(source, source.resolveSibling(source.getFileName() + ".corrupt"), StandardCopyOption.REPLACE_EXISTING);
			} catch (IOException ignored) {
			}
			return new ClientConfig();
		}
	}

	private static ClientConfig fromJson(JsonElement json) {
		ClientConfig config = GSON.fromJson(json, ClientConfig.class);
		if (config == null) {
			config = new ClientConfig();
		}
		if (config.modules == null) {
			config.modules = new LinkedHashMap<>();
		}
		if (config.environment == null) {
			config.environment = new EnvironmentSettings();
		}
		config.environment.sanitize();
		if (config.performance == null) {
			config.performance = new PerformanceSettings();
		}
		config.performance.sanitize();
		if (config.effects == null) {
			config.effects = new dev.quartz.core.fx.EffectSettings();
		}
		config.effects.sanitize();
		// A newer Doohickey may have written a higher schema; keep its number so
		// saving from this version doesn't pretend the file is older.
		config.schemaVersion = Math.max(config.schemaVersion, SCHEMA);
		return config;
	}

	public void save() {
		Path path = path();
		try {
			Files.createDirectories(path.getParent());
			Path tmp = path.resolveSibling("client.json.tmp");
			Files.write(tmp, GSON.toJson(this).getBytes(StandardCharsets.UTF_8));
			Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			lastSeen = Files.getLastModifiedTime(path);
		} catch (IOException e) {
			Log.warn("Couldn't save settings: " + e.getMessage());
		}
	}

	/**
	 * Hot reload: if the file changed since we last read or wrote it, load it
	 * again — into this same object, so screens and hooks holding a reference
	 * see the new values. Cheap enough to call every second.
	 */
	public void pollReload() {
		Path path = path();
		try {
			if (!Files.exists(path)) {
				return;
			}
			FileTime modified = Files.getLastModifiedTime(path);
			if (modified.equals(lastSeen)) {
				return;
			}
			lastSeen = modified;
			JsonObject json = GSON.fromJson(new String(Files.readAllBytes(path), StandardCharsets.UTF_8), JsonObject.class);
			if (json == null) {
				return;
			}
			Migrations.upgrade(json);
			copyFrom(fromJson(json));
			Log.info("Reloaded settings (changed outside the game)");
		} catch (IOException | RuntimeException | ReflectiveOperationException e) {
			// A half-written file: try again on the next poll.
			lastSeen = null;
		}
	}

	private void copyFrom(ClientConfig other) throws IllegalAccessException {
		for (Field f : ClientConfig.class.getDeclaredFields()) {
			if (!Modifier.isStatic(f.getModifiers())) {
				f.set(this, f.get(other));
			}
		}
	}

	public ModuleState module(String id, boolean defaultEnabled, float defaultX, float defaultY) {
		ModuleState state = modules.get(id);
		if (state == null) {
			state = new ModuleState(defaultEnabled, defaultX, defaultY);
			modules.put(id, state);
		}
		return state;
	}
}
