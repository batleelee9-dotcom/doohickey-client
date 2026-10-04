package dev.quartz.core.fx;

import dev.quartz.core.Feature;
import dev.quartz.core.Quartz;
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
	public static final String[] HIT_STYLES = {"crit", "magic", "hearts", "flames", "blood", "smoke", "notes", "sparkle", "lava"};
	public static final String[] HIT_STYLE_NAMES = {"Critical", "Magic", "Hearts", "Flames", "Blood", "Smoke", "Notes", "Sparkle", "Lava"};
	public static final String[] TRAILS = {"hearts", "flames", "notes", "magic", "clouds", "portal", "sparkle", "smoke"};
	public static final String[] TRAIL_NAMES = {"Hearts", "Flames", "Notes", "Magic", "Clouds", "Portal", "Sparkle", "Smoke"};
	public static final String[] KILL_EFFECTS = {"burst", "hearts", "flames", "lava", "firework"};
	public static final String[] KILL_EFFECT_NAMES = {"Burst", "Hearts", "Flames", "Lava", "Firework"};
	public static final String[] SOUNDS = {"ding", "pling", "bell", "click", "bass", "xp"};
	public static final String[] SOUND_NAMES = {"Ding", "Pling", "Bell", "Click", "Bass", "Experience"};
	public static final String[] KILL_SOUNDS = {"levelup", "firework", "ding", "anvil", "pling"};
	public static final String[] KILL_SOUND_NAMES = {"Level up", "Firework", "Ding", "Anvil", "Pling"};

	private static final long KILL_WINDOW_MS = 3000;

	private static Object lastTarget;
	private static double[] lastBox;
	private static long lastHitMs;
	private static int ticks;

	private Effects() {
	}

	private static EffectSettings s() {
		return ClientConfig.get().effects;
	}

	/** One of your hits landed on {@code target} (each version's attack hook). */
	public static void onHit(Object target) {
		VersionAdapter a = Quartz.adapter();
		EffectSettings s = s();
		double[] box = a.entityBox(target);
		if (box == null) {
			return;
		}
		lastTarget = target;
		lastBox = box;
		lastHitMs = System.currentTimeMillis();
		if (s.hitEffects && Quartz.available(Feature.HIT_EFFECTS)) {
			// A burst around the upper body, scaled to the target's size.
			a.particles(s.hitEffect, box[0], box[1] + box[3] * 0.6, box[2], 6 * s.hitAmount, box[4] * 0.6, 0.12);
		}
		if (s.hitSounds && Quartz.available(Feature.HIT_SOUNDS)) {
			// A touch of pitch variety keeps fast hits from sounding mechanical.
			a.playSound(s.hitSound, s.hitVolume / 100f, 0.95f + (float) Math.random() * 0.1f);
		}
	}

	/** Every client tick. */
	public static void tick(VersionAdapter a) {
		ticks++;
		EffectSettings s = s();
		double[] me = a.position();
		if (me == null) {
			lastTarget = null;
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
				lastTarget = null;
			}
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
			a.playSound(s.killSound, 0.9f, 1f);
		}
	}

	/** Plays a sound once from the menu, so picking one lets you hear it. */
	public static void preview(String sound, float volume) {
		Quartz.adapter().playSound(sound, volume, 1f);
	}
}
