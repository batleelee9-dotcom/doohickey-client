package dev.quartz.core;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * The version compatibility matrix, as code. Two separate questions:
 *
 * <ul>
 * <li>{@link #support}: can this feature exist on that version at all?
 *     (A game limit — void fog doesn't exist after 1.18, 1.8.9 has no
 *     attack cooldown to draw.)</li>
 * <li>{@link #implemented}: has Doohickey built it for that version yet?</li>
 * </ul>
 *
 * The UI shows a feature only when {@link #available} — so a toggle is
 * hidden, not broken, wherever either answer is no.
 *
 * Run {@code main} to print the matrix as Markdown (docs/COMPATIBILITY.md).
 */
public final class CompatRegistry {
	public enum Support {
		FULL("✅"),
		PARTIAL("⚠"),
		NONE("❌"),
		NOT_APPLICABLE("N/A");

		public final String symbol;

		Support(String symbol) {
			this.symbol = symbol;
		}
	}

	/** Matrix columns, oldest first. */
	public static final McVersion[] COLUMNS = {
		McVersion.V1_8_9, McVersion.V1_12_2, McVersion.V1_16_5, McVersion.V1_18_2, McVersion.V1_20_1, McVersion.V1_21, McVersion.V26_3,
	};

	private static final class Row {
		final Support[] support;
		final String note;

		Row(Support[] support, String note) {
			this.support = support;
			this.note = note;
		}
	}

	private static final Map<Feature, Row> ROWS = new EnumMap<>(Feature.class);
	private static final Map<McVersion, Set<Feature>> IMPLEMENTED = new EnumMap<>(McVersion.class);

	// Y = full, P = partial, N = impossible, - = not applicable; one letter per column.
	static {
		row(Feature.SKY_COLOR, "YYYYYYY", null);
		row(Feature.SKY_TEXTURE, "YYYYYYY", "Shader packs draw their own sky and take precedence.");
		row(Feature.TIME_LOCK, "YYYYYYY", "Visual only: the server's time, mob spawning and crops are unaffected.");
		row(Feature.FOG, "YYYPYYY", "1.18.2 sets shader fog through RenderSystem, which shader packs override.");
		row(Feature.FOG_COLOR, "YYYYYYY", null);
		row(Feature.WEATHER_OVERRIDE, "YYYYYYY", "Visual only: the server still decides real weather (crops, lightning).");
		row(Feature.FULLBRIGHT, "YYYYYYY", "1.19+ clamps gamma, so newer versions brighten through the lightmap.");
		row(Feature.CLOUDS, "YYYYYYY", null);
		row(Feature.CELESTIAL, "YYYYYYY", null);
		row(Feature.VOID_FOG_REMOVAL, "YYPNNNN", "1.16.5 only darkens the sky near bedrock; 1.18 removed void fog entirely.");
		row(Feature.PARTICLES, "YYYYYYY", null);
		row(Feature.VIEW_BOBBING, "YYYYYYY", null);
		row(Feature.HURT_CAMERA, "YYYYYYY", null);
		row(Feature.DAMAGE_TINT, "YYYYYYY", null);
		row(Feature.CUSTOM_CROSSHAIR, "YYYYYYY", null);
		row(Feature.DYNAMIC_CROSSHAIR, "PYYYYYY", "1.8.9 has no attack cooldown; only bow charge can be shown.");
		row(Feature.BLOCK_OUTLINE, "YYYPPPP", "Core-profile OpenGL (1.17+) can't draw lines thicker than 1 px on every GPU.");
		row(Feature.ITEM_PHYSICS, "YYYYYYY", null);
		row(Feature.CHUNK_BORDERS, "YYYYYYY", null);
		row(Feature.LIGHT_OVERLAY, "YYYYYYY", null);

		row(Feature.HUD_EDITOR, "YYYYYYY", null);
		row(Feature.HUD_FPS, "YYYYYYY", null);
		row(Feature.HUD_CPS, "YYYYYYY", null);
		row(Feature.HUD_PING, "YYYYYYY", null);
		row(Feature.HUD_TPS, "PPPPPPP", "Servers don't report TPS; it's estimated from world-time packets.");
		row(Feature.HUD_COORDINATES, "YYYYYYY", null);
		row(Feature.HUD_BIOME, "YYYYYYY", null);
		row(Feature.HUD_KEYSTROKES, "YYYYYYY", null);
		row(Feature.HUD_ARMOR, "YYYYYYY", null);
		row(Feature.HUD_POTIONS, "YYYYYYY", null);
		row(Feature.HUD_REACH, "YYYYYYY", "Measured from your own hits; purely informational.");
		row(Feature.HUD_COMBO, "YYYYYYY", null);
		row(Feature.HUD_SCOREBOARD, "YYYYYYY", null);
		row(Feature.HUD_CHAT, "YYYYYYY", null);
		row(Feature.HUD_ITEM_COUNTER, "YYYYYYY", null);
		row(Feature.HUD_GRAPHS, "YYYYYYY", null);
		row(Feature.HUD_CLOCK, "YYYYYYY", null);
		row(Feature.HUD_SERVER_IP, "YYYYYYY", null);
		row(Feature.HUD_MEMORY, "YYYYYYY", null);
		row(Feature.HUD_DIRECTION, "YYYYYYY", null);
		row(Feature.HUD_SPEED, "YYYYYYY", null);
		row(Feature.HUD_DAY, "YYYYYYY", null);
		row(Feature.HUD_SATURATION, "YYYYYYY", null);
		row(Feature.HUD_BLOCK_INFO, "YYYYYYY", null);

		row(Feature.TOGGLE_SPRINT_SNEAK, "YYYYYYY", null);
		row(Feature.SPRINT_RESET, "Y------", "Automates a combat technique; many servers treat that as a macro.");
		row(Feature.OLD_ANIMATIONS, "-YYYYYY", "1.8.9 already has the old animations.");
		row(Feature.BLOCKHIT_ANIMATION, "YPPPPPP", "Sword blocking was removed in 1.9; newer versions can only restyle the swing.");
		row(Feature.HIT_COLOR, "YYYYYYY", null);
		row(Feature.NO_HIT_DELAY, "Y------", "Removes 1.8.9's click delay after a miss; a gameplay change some servers ban.");
		row(Feature.HITBOXES, "YYYYYYY", null);
		row(Feature.NAMETAGS, "YYYYYYY", "Replaces vanilla player tags only where vanilla would show one (teams, sneaking and invisibility still apply).");
		row(Feature.AUTO_TOOL, "YYYYYYY", "Switches hotbar slots for you; forbidden on many PvP servers.");
		row(Feature.RAW_INPUT, "PYYYYYY", "1.8.9 (LWJGL 2) needs a separate raw-input library.");
		row(Feature.ZOOM, "YYYYYYY", null);
		row(Feature.ASPECT_RATIO, "YYYYYYY", "Stretches the 3D view only; the HUD and menus keep their shape.");
		row(Feature.FREELOOK, "YYYYYYY", "Some servers (e.g. Hypixel) ask clients to disable it.");
		row(Feature.PING_REACH_DISPLAY, "YYYYYYY", "Display only; never changes reach.");

		row(Feature.SODIUM_INTEGRATION, "--YYYYY", "Sodium exists from 1.16.");
		row(Feature.ENTITY_DISTANCE, "YYYYYYY", null);
		row(Feature.ENTITY_CULLING, "YYYYYYY", null);
		row(Feature.TILE_ENTITY_CULLING, "YYYYYYY", null);
		row(Feature.PARTICLE_CULLING, "YYYYYYY", null);
		row(Feature.CHUNK_THROTTLE, "YYYPPPP", "1.18+ schedules chunk builds itself; only the budget can be tuned.");
		row(Feature.FPS_CAP, "YYYYYYY", null);
		row(Feature.DYNAMIC_RENDER_DISTANCE, "YYYYYYY", null);
		row(Feature.SMART_ANIMATIONS, "YYYYYYY", null);
		row(Feature.BORDERLESS, "PYYYYYY", "LWJGL 2 has no borderless mode; 1.8.9 needs a window-style workaround.");
		row(Feature.THREADED_CHUNKS, "P------", "1.8.9 already builds chunks on worker threads; Doohickey can only tune the count.");

		row(Feature.SOUND_VOLUMES, "YYYYYYY", null);
		row(Feature.SOUND_PACKS, "YYYYYYY", null);
		row(Feature.SOUND_POSITIONING, "YYYYYYY", null);
		row(Feature.SOUND_MUTE, "YYYYYYY", null);
		row(Feature.HIT_SOUNDS, "YYYYYYY", "Played on your client only.");
		row(Feature.LOW_HEALTH_ALERT, "YYYYYYY", null);

		row(Feature.CAPES, "YYYYYYY", "Client-side: only you see them without a cosmetics server.");
		row(Feature.WEARABLES, "YYYYYYY", "Client-side: only you see them without a cosmetics server.");
		row(Feature.RICE_HAT, "YYYYYYY", "Client-side: only you see it.");
		row(Feature.EMOTES, "YYYYYYY", null);
		row(Feature.KILL_EFFECTS, "YYYYYYY", null);
		row(Feature.BREAK_PARTICLES, "YYYYYYY", null);
		row(Feature.TOTEM_EFFECT, "-YYYYYY", "Totems arrived in 1.11.");
		row(Feature.HIT_EFFECTS, "YYYYYYY", "Client-side particles: only you see them.");
		row(Feature.TRAILS, "YYYYYYY", "Client-side particles: only you see them.");

		row(Feature.MINIMAP_WAYPOINTS, "YYYYYYY", "Honours servers' minimap/fair-play codes; no entity radar.");
		row(Feature.SCREENSHOTS, "YYYYYYY", null);
		row(Feature.REPLAY, "YYYYYYY", "Recording is client-side; some servers forbid it.");
		row(Feature.MACROS, "YYYYYYY", "Automating input breaks the rules of most servers.");
		row(Feature.SERVER_PROFILES, "YYYYYYY", null);
		row(Feature.HUD_PROFILES, "YYYYYYY", null);
		row(Feature.ACCOUNT_SWITCHER, "YYYYYYY", "Switches between accounts added to the Doohickey launcher.");

		implemented(McVersion.V1_8_9,
			Feature.SKY_COLOR, Feature.TIME_LOCK, Feature.FOG, Feature.FOG_COLOR, Feature.WEATHER_OVERRIDE, Feature.VOID_FOG_REMOVAL,
			Feature.FULLBRIGHT, Feature.DYNAMIC_RENDER_DISTANCE, Feature.PARTICLES, Feature.ENTITY_DISTANCE, Feature.TOGGLE_SPRINT_SNEAK,
			Feature.HUD_EDITOR, Feature.HUD_FPS, Feature.HUD_CPS, Feature.HUD_PING, Feature.HUD_COORDINATES, Feature.HUD_KEYSTROKES,
			Feature.HUD_ARMOR, Feature.HUD_POTIONS, Feature.HUD_REACH, Feature.HUD_MEMORY, Feature.HUD_CLOCK, Feature.HUD_SERVER_IP,
			Feature.HUD_DIRECTION, Feature.HUD_SPEED, Feature.HUD_DAY, Feature.HUD_SATURATION, Feature.HUD_ITEM_COUNTER,
			Feature.HUD_COMBO, Feature.HUD_BLOCK_INFO, Feature.HUD_BIOME,
			Feature.CUSTOM_CROSSHAIR, Feature.ZOOM, Feature.ASPECT_RATIO, Feature.HITBOXES,
			Feature.RICE_HAT, Feature.NAMETAGS, Feature.SKY_TEXTURE, Feature.FPS_CAP, Feature.TILE_ENTITY_CULLING, Feature.PARTICLE_CULLING,
			Feature.HIT_EFFECTS, Feature.TRAILS, Feature.KILL_EFFECTS, Feature.HIT_SOUNDS, Feature.LOW_HEALTH_ALERT, Feature.HURT_CAMERA,
			Feature.ACCOUNT_SWITCHER);
		implemented(McVersion.V26_3,
			Feature.SKY_COLOR, Feature.TIME_LOCK, Feature.FOG, Feature.FOG_COLOR, Feature.WEATHER_OVERRIDE,
			Feature.FULLBRIGHT, Feature.DYNAMIC_RENDER_DISTANCE,
			Feature.PARTICLES, Feature.DAMAGE_TINT, Feature.CUSTOM_CROSSHAIR,
			Feature.HUD_EDITOR, Feature.HUD_FPS, Feature.HUD_CPS, Feature.HUD_PING, Feature.HUD_COORDINATES, Feature.HUD_KEYSTROKES,
			Feature.HUD_ARMOR, Feature.HUD_POTIONS, Feature.HUD_REACH, Feature.HUD_MEMORY, Feature.HUD_CLOCK, Feature.HUD_SERVER_IP,
			Feature.HUD_DIRECTION, Feature.HUD_SPEED, Feature.HUD_DAY, Feature.HUD_SATURATION, Feature.HUD_ITEM_COUNTER,
			Feature.HUD_COMBO, Feature.HUD_BLOCK_INFO, Feature.HUD_BIOME,
			Feature.ZOOM, Feature.ASPECT_RATIO, Feature.HITBOXES,
			Feature.RICE_HAT, Feature.NAMETAGS, Feature.SKY_TEXTURE, Feature.FPS_CAP,
			Feature.HIT_EFFECTS, Feature.TRAILS, Feature.KILL_EFFECTS, Feature.HIT_SOUNDS, Feature.LOW_HEALTH_ALERT, Feature.HURT_CAMERA,
			Feature.TOGGLE_SPRINT_SNEAK, Feature.HIT_COLOR, Feature.CAPES, Feature.WEARABLES, Feature.MINIMAP_WAYPOINTS,
			Feature.ACCOUNT_SWITCHER);

		for (Feature f : Feature.values()) {
			if (!ROWS.containsKey(f)) {
				throw new IllegalStateException("CompatRegistry has no row for " + f);
			}
		}
	}

	private CompatRegistry() {
	}

	private static void row(Feature feature, String cells, String note) {
		if (cells.length() != COLUMNS.length) {
			throw new IllegalStateException(feature + ": expected " + COLUMNS.length + " cells, got " + cells);
		}
		Support[] support = new Support[COLUMNS.length];
		for (int i = 0; i < cells.length(); i++) {
			switch (cells.charAt(i)) {
				case 'Y': support[i] = Support.FULL; break;
				case 'P': support[i] = Support.PARTIAL; break;
				case 'N': support[i] = Support.NONE; break;
				case '-': support[i] = Support.NOT_APPLICABLE; break;
				default: throw new IllegalStateException(feature + ": bad cell '" + cells.charAt(i) + "'");
			}
		}
		ROWS.put(feature, new Row(support, note));
	}

	private static void implemented(McVersion version, Feature... features) {
		Set<Feature> set = EnumSet.noneOf(Feature.class);
		for (Feature f : features) {
			if (!possible(support(f, version))) {
				throw new IllegalStateException(f + " can't be implemented on " + version.id);
			}
			set.add(f);
		}
		IMPLEMENTED.put(version, Collections.unmodifiableSet(set));
	}

	private static boolean possible(Support s) {
		return s == Support.FULL || s == Support.PARTIAL;
	}

	public static Support support(Feature feature, McVersion version) {
		return ROWS.get(feature).support[Arrays.asList(COLUMNS).indexOf(version)];
	}

	public static String note(Feature feature) {
		return ROWS.get(feature).note;
	}

	public static boolean implemented(Feature feature, McVersion version) {
		Set<Feature> set = IMPLEMENTED.get(version);
		return set != null && set.contains(feature);
	}

	/** Show this feature's controls on this version? */
	public static boolean available(Feature feature, McVersion version) {
		return possible(support(feature, version)) && implemented(feature, version);
	}

	/** Plain-text report for the game log, written once per version. */
	public static String report(McVersion version) {
		StringBuilder out = new StringBuilder("Doohickey Client compatibility report for Minecraft " + version.id + "\n");
		for (Feature.Category category : Feature.Category.values()) {
			out.append('\n').append(category).append('\n');
			for (Feature f : Feature.values()) {
				if (f.category != category) {
					continue;
				}
				Support s = support(f, version);
				String state = !possible(s) ? (s == Support.NONE ? "not possible" : "not applicable")
					: implemented(f, version) ? (s == Support.PARTIAL ? "available (partial)" : "available") : "not built yet";
				out.append(String.format("  %-34s %s", f.displayName(), state));
				if (note(f) != null && s != Support.FULL) {
					out.append(" - ").append(note(f));
				}
				out.append('\n');
			}
		}
		return out.toString();
	}

	/** The matrix as a Markdown table: support, with ● where Doohickey ships the feature today. */
	public static String markdown() {
		StringBuilder out = new StringBuilder("| Feature |");
		for (McVersion v : COLUMNS) {
			out.append(' ').append(v.id).append(" |");
		}
		out.append(" Notes |\n|---|");
		for (int i = 0; i < COLUMNS.length; i++) {
			out.append("---|");
		}
		out.append("---|\n");
		Feature.Category current = null;
		for (Feature f : Feature.values()) {
			if (f.category != current) {
				current = f.category;
				out.append("| **").append(current).append("** |");
				for (int i = 0; i <= COLUMNS.length; i++) {
					out.append(" |");
				}
				out.append('\n');
			}
			out.append("| ").append(f.displayName()).append(" |");
			for (McVersion v : COLUMNS) {
				out.append(' ').append(support(f, v).symbol).append(implemented(f, v) ? " ●" : "").append(" |");
			}
			out.append(' ').append(note(f) == null ? "" : note(f)).append(" |\n");
		}
		return out.toString();
	}

	public static void main(String[] args) {
		System.out.print(markdown());
	}
}
