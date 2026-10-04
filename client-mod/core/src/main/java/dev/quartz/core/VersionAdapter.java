package dev.quartz.core;

import dev.quartz.core.accounts.LauncherBridge;
import dev.quartz.core.hud.HudData;
import dev.quartz.core.hud.Input;

import java.nio.file.Path;
import java.util.List;

/**
 * Everything feature code needs from the running game, behind one
 * interface. Each Minecraft version ships exactly one implementation; core
 * code never checks versions itself — it asks the adapter, and asks
 * {@link CompatRegistry} whether a feature exists here.
 *
 * The reverse direction (the game calling into Doohickey) goes through Mixin
 * hooks in each version module, which hand plain values to core modules
 * such as {@link dev.quartz.core.env.EnvironmentModule}.
 */
public interface VersionAdapter {
	McVersion version();

	/** The profile's config folder (`<game dir>/config`). */
	Path configDir();

	/** HUD/overlay drawing. Only valid while the game is drawing a frame. */
	RenderBackend render();

	/** A world is loaded and the player exists. */
	boolean inWorld();

	/** Resource id of the current dimension ("minecraft:overworld"), or "" outside a world. */
	String dimension();

	/** Singleplayer world name or server address, for per-world settings; "" outside a world. */
	String worldKey();

	int fps();

	/** Render distance in chunks. */
	int renderDistance();

	/** Changes the render distance for this session (dynamic render distance). */
	void setRenderDistance(int chunks);

	/** Whether the control is held right now. */
	boolean inputDown(Input input);

	/** The key the control is bound to, as the game names it ("W", "SPACE", "Button 1"). */
	String inputLabel(Input input);

	/** The player's block position {x, y, z}, or null outside a world. */
	int[] blockPosition();

	/** The player's facing: "N", "E", "S" or "W" ("" outside a world). */
	String facing();

	/** Latency to the server in ms, or -1 in singleplayer or before it's known. */
	int ping();

	/** Active potion effects, newest last. */
	List<HudData.Effect> effects();

	/** Helmet, chestplate, leggings, boots and the held item — whichever are present. */
	List<HudData.Item> armor();

	/** Precise position {x, y, z} (for the speedometer), or null outside a world. */
	double[] position();

	/** Yaw in degrees as the game reports it: 0 = south, 90 = west. */
	float yaw();

	/** World time in ticks (for the day counter), or -1 outside a world. */
	long worldTime();

	/** Food saturation, or -1 outside a world. */
	float saturation();

	/** Arrows in the player's inventory. */
	int arrows();

	/** Name of the block under the crosshair, or "" when it's on nothing. */
	String targetBlock();

	/** The biome at the player's feet, or "" outside a world. */
	String biome();

	/** Ticks left on the player's hurt animation; rises just after taking damage. */
	int hurtTime();

	/** Vanilla's hitbox view (F3+B). */
	boolean hitboxes();

	void setHitboxes(boolean shown);

	/** Whether the zoom key is held right now (never while a screen is open). */
	boolean zoomKeyDown();

	/** {x, y (feet), z, height, width} of an entity a hook handed us, or null if it's gone. */
	double[] entityBox(Object entity);

	/** Whether that entity has died (or been removed from the world). */
	boolean entityDead(Object entity);

	/**
	 * Spawns client-side particles that only this player sees. {@code kind} is
	 * one of the names in {@code Effects} ("crit", "hearts", ...), mapped to the
	 * version's own particle types. {@code spread} is the radius in blocks,
	 * {@code speed} roughly how fast they fly out.
	 */
	void particles(String kind, double x, double y, double z, int count, double spread, double speed);

	/** Plays a sound to this player only, by an {@code Effects} name ("ding", "pling", ...). */
	void playSound(String name, float volume, float pitch);

	/** The game's volume for player sounds (master × players), 0–1, for sounds played outside its engine. */
	default float soundVolume() {
		return 1f;
	}

	/**
	 * Whether one of your projectiles (arrow, rod, thrown item) is right next
	 * to {@code entity}: how a version that can't see who caused damage
	 * credits you for a projectile hit.
	 */
	default boolean ownProjectileNear(Object entity) {
		return false;
	}

	/** Fills {@code out} for a player's name tag (position at {@code View.partialTicks()}); false if it isn't a player. */
	default boolean nameTag(Object player, dev.quartz.core.fx.NameTags.Tag out) {
		return false;
	}

	/** Calls {@code sink} for each lit TNT within {@code range} blocks of the camera. */
	default void primedTnt(double range, dev.quartz.core.fx.TntTimers.Sink sink) {
	}

	/** Whether open sky is above the camera (ambient weather fades out under a roof). */
	default boolean skyVisible() {
		return true;
	}

	/** Your own player while the camera is in third person (for your own tag), else null. */
	default Object selfTagEntity() {
		return null;
	}

	/**
	 * Max FPS on: remember the player's video settings in {@code restore},
	 * then switch to the fastest ones. Off: put back what's in {@code restore}.
	 */
	default void applyMaxFps(boolean on, java.util.Map<String, String> restore) {
	}

	/** Health as a fraction of the maximum, or -1 outside a world. */
	float healthFraction();

	/** Opens the in-game Doohickey menu. */
	void openMenu();

	/** Shows a short message to the player (chat on old versions, toast/overlay on new ones). */
	void notifyPlayer(String message);

	/** Runs {@code task} on the game thread. */
	void runOnMainThread(Runnable task);

	/** The signed-in player's name. */
	String sessionName();

	/** The signed-in player's UUID, without dashes. */
	String sessionUuid();

	/** An offline account (no Minecraft services token): singleplayer and LAN only. */
	boolean sessionOffline();

	/** Sessions can only change outside a world: inside one, the current session is in use. */
	boolean canSwitchSession();

	/**
	 * Replaces the game's session — and everything tied to it: profile, chat
	 * signing keys, Minecraft services — without restarting.
	 */
	void switchSession(LauncherBridge.Session session);
}
