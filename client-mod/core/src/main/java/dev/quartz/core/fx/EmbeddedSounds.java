package dev.quartz.core.fx;

import dev.quartz.core.Safe;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sounds that ship inside the client jar ({@code assets/quartz/sounds/<id>.wav}),
 * played through Java's own audio. That keeps them identical on every
 * Minecraft version and independent of resource packs, and needs no OGG
 * encoder. Each sound gets a few voices so fast hits can overlap.
 */
public final class EmbeddedSounds {
	/** Ids of the WAVs in assets/quartz/sounds. */
	public static final String[] IDS = {"custom"};

	private static final int VOICES = 3;
	private static final Clip[] LOADING = new Clip[0];
	private static final Clip[] FAILED = new Clip[0];
	private static final Map<String, Clip[]> CLIPS = new ConcurrentHashMap<>();
	private static final Map<String, Integer> NEXT = new ConcurrentHashMap<>();

	private EmbeddedSounds() {
	}

	public static boolean has(String id) {
		for (String s : IDS) {
			if (s.equals(id)) {
				return true;
			}
		}
		return false;
	}

	/** Loads the sound in the background so the first hit plays without a hitch. */
	public static void warm(String id) {
		if (CLIPS.putIfAbsent(id, LOADING) == null) {
			Thread t = new Thread(() -> CLIPS.put(id, load(id)), "Doohickey sound loader");
			t.setDaemon(true);
			t.start();
		}
	}

	/**
	 * Plays at {@code volume} (0–1, already scaled by the game's volume).
	 * False if the sound can't play here (still loading, or no audio device),
	 * so the caller can fall back to a game sound.
	 */
	public static boolean play(String id, float volume) {
		Clip[] clips = CLIPS.get(id);
		if (clips == null) {
			// Not warmed (a menu preview): load now, it's a few dozen KB.
			clips = load(id);
			CLIPS.put(id, clips);
		}
		if (clips.length == 0) {
			return false;
		}
		if (volume <= 0.001f) {
			return true;
		}
		int i = NEXT.merge(id, 1, Integer::sum) % clips.length;
		Clip clip = clips[i];
		clip.stop();
		clip.setFramePosition(0);
		if (clip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
			FloatControl gain = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
			float db = (float) (20 * Math.log10(volume));
			gain.setValue(Math.max(gain.getMinimum(), Math.min(gain.getMaximum(), db)));
		}
		clip.start();
		return true;
	}

	private static Clip[] load(String id) {
		try {
			byte[] pcm;
			AudioFormat format;
			try (InputStream raw = EmbeddedSounds.class.getResourceAsStream("/assets/quartz/sounds/" + id + ".wav")) {
				if (raw == null) {
					return FAILED;
				}
				AudioInputStream in = AudioSystem.getAudioInputStream(new BufferedInputStream(raw));
				format = in.getFormat();
				ByteArrayOutputStream out = new ByteArrayOutputStream();
				byte[] buf = new byte[8192];
				for (int n; (n = in.read(buf)) > 0; ) {
					out.write(buf, 0, n);
				}
				pcm = out.toByteArray();
			}
			Clip[] clips = new Clip[VOICES];
			for (int i = 0; i < VOICES; i++) {
				clips[i] = AudioSystem.getClip();
				clips[i].open(format, pcm, 0, pcm.length);
			}
			return clips;
		} catch (Throwable t) {
			// No audio device, a headless box, or a broken mixer: the game sound takes over.
			Safe.report("sound." + id, t);
			return FAILED;
		}
	}
}
