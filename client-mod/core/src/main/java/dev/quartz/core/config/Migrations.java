package dev.quartz.core.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * Upgrades client.json from any older schema to {@link ClientConfig#SCHEMA},
 * one step at a time. Add a step whenever a field is renamed, moved or
 * changes meaning; new fields with defaults need no step.
 */
final class Migrations {
	private Migrations() {
	}

	/** Schema 1 files predate the field. */
	static int version(JsonObject json) {
		JsonElement v = json.get("schemaVersion");
		return v != null && v.isJsonPrimitive() ? v.getAsInt() : 1;
	}

	/** Returns true if anything changed. */
	static boolean upgrade(JsonObject json) {
		int version = version(json);
		boolean changed = false;
		while (version < ClientConfig.SCHEMA) {
			switch (version) {
				case 1:
					v1to2(json);
					break;
				default:
					throw new IllegalStateException("No migration from schema " + version);
			}
			version++;
			json.addProperty("schemaVersion", version);
			changed = true;
		}
		return changed;
	}

	/**
	 * Schema 2 (multi-version client): the file moved to config/quartz/, and
	 * the separate "minimap" switch merged into the minimap HUD module.
	 */
	private static void v1to2(JsonObject json) {
		JsonElement minimap = json.remove("minimap");
		if (minimap != null && minimap.isJsonPrimitive() && !minimap.getAsBoolean()) {
			JsonObject modules = json.has("modules") && json.get("modules").isJsonObject() ? json.getAsJsonObject("modules") : new JsonObject();
			JsonObject state = modules.has("minimap") && modules.get("minimap").isJsonObject() ? modules.getAsJsonObject("minimap") : new JsonObject();
			state.addProperty("enabled", false);
			modules.add("minimap", state);
			json.add("modules", modules);
		}
	}
}
