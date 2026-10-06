package dev.quartz.core.fx;

import dev.quartz.core.Feature;
import dev.quartz.core.Quartz;
import dev.quartz.core.Safe;
import dev.quartz.core.VersionAdapter;
import dev.quartz.core.config.ClientConfig;
import dev.quartz.core.hud.PlayerStats;

/**
 * Custom hit effects, particle trails, kill effects and sounds. Everything is
 * spawned on your client only: nobody else sees or hears it, and nothing is
 * sent to the server. Each version maps the names below to its own particle
 * and sound types.
 */
public final class Effects {
	// The client's own particles (Sprites) first, then Minecraft's.
	public static final String[] HIT_STYLES = {"snow", "stars", "sparkles", "confetti", "petals", "bubbles",
		"crit", "magic", "hearts", "flames", "blood", "smoke", "notes", "sparkle", "lava"};
	public static final String[] HIT_STYLE_NAMES = {"Snowflakes", "Stars", "Sparkles", "Confetti", "Petals", "Bubbles",
		"Critical", "Magic", "Hearts", "Flames", "Blood", "Smoke", "Notes", "Glitter", "Lava"};
	public static final String[] TRAILS = {"hearts", "flames", "notes", "magic", "clouds", "portal", "sparkle", "smoke"};
	public static final String[] TRAIL_NAMES = {"Hearts", "Flames", "Notes", "Magic", "Clouds", "Portal", "Sparkle", "Smoke"};
	public static final String[] KILL_EFFECTS = {"confetti", "blizzard", "starburst", "burst", "hearts", "flames", "lava", "firework"};
	public static final String[] KILL_EFFECT_NAMES = {"Confetti", "Blizzard", "Starburst", "Burst", "Hearts", "Flames", "Lava", "Firework"};

	/** {@link #onDamaged} attribution: the version knows it was you, knows it wasn't, or can't tell. */
	public static final int YOURS = 1;
	public static final int NOT_YOURS = 0;
	public static final int UNKNOWN = -1;
	/** How long after a swing the server's "it took damage" still counts as that swing (covers ping). */
	private static final long MELEE_WINDOW_MS = 1000;
	public static final String[] SOUNDS = {"custom", "ding", "pling", "bell", "click", "bass", "xp"};
	public static final String[] SOUND_NAMES = {"Custom", "Ding", "Pling", "Bell", "Click", "Bass", "Experience"};
	public static final String[] KILL_SOUNDS = {"custom", "levelup", "firework", "ding", "anvil", "pling"};
	public static final String[] KILL_SOUND_NAMES = {"Custom", "Level up", "Firework", "Ding", "Anvil", "Pling"};

	private static final long KILL_WINDOW_MS = 3000;

	private static Object meleeTarget;
	private static long meleeMs;
	private static Object lastTarget;
	private static double[] lastBox;
	private static long lastHitMs;
	private static int ticks;

	private Effects() {
	}

	private static EffectSettings s() {
		return ClientConfig.get().effects;
	}

	/**
	 * You swung at {@code target} (each version's attack hook). Nothing plays
	 * yet: a swing during the target's damage cooldown does nothing, so effects
	 * wait for the server to say it actually took damage ({@link #onDamaged}).
	 */
	public static void onAttack(Object target) {
		meleeTarget = target;
		meleeMs = System.currentTimeMillis();
		Combat.onSwing(Quartz.adapter(), target);
	}

	/**
	 * {@code entity} (never you) just took damage: its hurt animation started.
	 * {@code attribution} is {@link #YOURS} or {@link #NOT_YOURS} when the
	 * version knows who caused it; with {@link #UNKNOWN}, a recent swing at it
	 * or one of your projectiles next to it counts as yours.
	 */
	public static void onDamaged(Object entity, int attribution) {
		VersionAdapter a = Quartz.adapter();
		boolean swung = entity == meleeTarget && System.currentTimeMillis() - meleeMs < MELEE_WINDOW_MS;
		boolean yours = attribution == YOURS || attribution == UNKNOWN && (swung || a.ownProjectileNear(entity));
		if (!yours) {
			return;
		}
		if (swung) {
			meleeTarget = null;
		}
		hit(a, entity);
	}

	/** Your hit landed: effects and sound on the target. */
	private static void hit(VersionAdapter a, Object target) {
		EffectSettings s = s();
		double[] box = a.entityBox(target);
		if (box == null) {
			return;
		}
		lastTarget = target;
		lastBox = box;
		lastHitMs = System.currentTimeMillis();
		Combat.onHit(a, target);
		if (s.hitEffects && Quartz.available(Feature.HIT_EFFECTS)) {
			// A burst around the upper body, scaled to the target's size.
			int sprite = Sprites.kind(s.hitEffect);
			if (sprite >= 0) {
				Sprites.spawn(sprite, box[0], box[1] + box[3] * 0.6, box[2], 5 * s.hitAmount, box[4] * 0.8);
			} else {
				a.particles(s.hitEffect, box[0], box[1] + box[3] * 0.6, box[2], 6 * s.hitAmount, box[4] * 0.6, 0.12);
			}
		}
		if (s.hitSounds && Quartz.available(Feature.HIT_SOUNDS)) {
			// A touch of pitch variety keeps fast hits from sounding mechanical.
			sound(a, s.hitSound, s.hitVolume / 100f, 0.95f + (float) Math.random() * 0.1f);
		}
	}

	/** Every client tick. */
	public static void tick(VersionAdapter a) {
		ticks++;
		EffectSettings s = s();
		double[] me = a.position();
		if (me == null) {
			lastTarget = null;
			meleeTarget = null;
			Sprites.clear();
			return;
		}

		// Kills: the last thing you hit died within a few seconds of the hit.
		if (lastTarget != null) {
			if (System.currentTimeMillis() - lastHitMs > KILL_WINDOW_MS) {
				lastTarget = null;
			} else if (a.entityDead(lastTarget)) {
				double[] box = a.entityBox(lastTarget);
				if (box == null) {
					box = lastBox;
				}
				kill(a, s, box);
				Combat.onKill(a, lastTarget);
				lastTarget = null;
			}
		}

		// Load the jar's own sounds ahead of the first hit.
		if (s.hitSounds && EmbeddedSounds.has(s.hitSound)) {
			EmbeddedSounds.warm(s.hitSound);
		}
		if (s.killSounds && EmbeddedSounds.has(s.killSound)) {
			EmbeddedSounds.warm(s.killSound);
		}

		if (s.trail && Quartz.available(Feature.TRAILS) && PlayerStats.speed() > 1.0 && ticks % 2 == 0) {
			a.particles(s.trailStyle, me[0], me[1] + 0.15, me[2], 1, 0.25, 0.01);
		}

		if (s.lowHealthAlert && Quartz.available(Feature.LOW_HEALTH_ALERT)) {
			float health = a.healthFraction();
			// A heartbeat every 1.5 s while under 30%, quicker under 15%.
			int every = health < 0.15f ? 20 : 30;
			if (health > 0 && health < 0.3f && ticks % every == 0) {
				a.playSound("heartbeat", 0.8f, 0.9f);
			}
		}
	}

	private static void kill(VersionAdapter a, EffectSettings s, double[] box) {
		if (s.killEffects && Quartz.available(Feature.KILL_EFFECTS)) {
			double cy = box[1] + box[3] / 2;
			switch (s.killEffect) {
				case "confetti":
					Sprites.spawn(3, box[0], cy, box[2], 45, box[4]);
					break;
				case "blizzard":
					Sprites.spawn(0, box[0], cy, box[2], 40, box[4] * 1.4);
					break;
				case "starburst":
					Sprites.spawn(1, box[0], cy, box[2], 24, box[4]);
					Sprites.spawn(2, box[0], cy, box[2], 16, box[4]);
					break;
				case "hearts":
					a.particles("hearts", box[0], cy, box[2], 14, box[4], 0.2);
					break;
				case "flames":
					a.particles("flames", box[0], cy, box[2], 40, box[4] * 0.8, 0.08);
					break;
				case "lava":
					a.particles("lava", box[0], cy, box[2], 18, box[4], 0.1);
					break;
				case "firework":
					a.particles("firework", box[0], cy, box[2], 50, 0.3, 0.25);
					break;
				default:
					a.particles("magic", box[0], cy, box[2], 30, box[4], 0.35);
					a.particles("smoke", box[0], cy, box[2], 12, box[4] * 0.6, 0.05);
			}
		}
		if (s.killSounds && Quartz.available(Feature.HIT_SOUNDS)) {
			sound(a, s.killSound, 0.9f, 1f);
		}
	}

	/** Plays a sound once from the menu, so picking one lets you hear it. */
	public static void preview(String sound, float volume) {
		sound(Quartz.adapter(), sound, volume, 1f);
	}

	/**
	 * Embedded sounds play as recorded (no pitch variety) through Java audio at
	 * the game's volume; game sounds go through the version's sound engine.
	 */
	private static void sound(VersionAdapter a, String name, float volume, float pitch) {
		if (!EmbeddedSounds.has(name)) {
			a.playSound(name, volume, pitch);
		} else if (!Safe.test("sound.embedded", () -> EmbeddedSounds.play(name, volume * a.soundVolume()), false)) {
			a.playSound("ding", volume, pitch);
		}
	}
}
