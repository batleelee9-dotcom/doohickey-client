package dev.quartz.core.fx;

import java.util.Arrays;

/** Hit effects, trails, kill effects and sounds, stored in client.json. All client-side. */
public final class EffectSettings {
	public boolean hitEffects = false;
	public String hitEffect = "snow";
	/** Particles per hit, as a multiple of the base burst (1–5). */
	public int hitAmount = 2;

	public boolean trail = false;
	public String trailStyle = "hearts";

	public boolean killEffects = false;
	public String killEffect = "confetti";

	public boolean hitSounds = false;
	public String hitSound = "custom";
	public int hitVolume = 70;

	public boolean killSounds = false;
	public String killSound = "levelup";

	public boolean lowHealthAlert = false;
	public boolean noHurtCamera = false;

	/** Unknown names (e.g. from a newer Doohickey) fall back to the defaults. */
	public void sanitize() {
		EffectSettings d = new EffectSettings();
		hitEffect = known(hitEffect, Effects.HIT_STYLES, d.hitEffect);
		trailStyle = known(trailStyle, Effects.TRAILS, d.trailStyle);
		killEffect = known(killEffect, Effects.KILL_EFFECTS, d.killEffect);
		hitSound = known(hitSound, Effects.SOUNDS, d.hitSound);
		killSound = known(killSound, Effects.KILL_SOUNDS, d.killSound);
		hitAmount = Math.max(1, Math.min(5, hitAmount));
		hitVolume = Math.max(10, Math.min(100, hitVolume));
	}

	private static String known(String value, String[] allowed, String fallback) {
		return value != null && Arrays.asList(allowed).contains(value) ? value : fallback;
	}
}
