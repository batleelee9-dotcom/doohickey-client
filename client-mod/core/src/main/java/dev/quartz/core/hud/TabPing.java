package dev.quartz.core.hud;

import dev.quartz.core.Feature;
import dev.quartz.core.Quartz;
import dev.quartz.core.config.ClientConfig;

/** Ping numbers in the tab list, coloured by how good they are, instead of the bar icons. */
public final class TabPing {
	private static final String[] LABELS = new String[1000];

	private TabPing() {
	}

	public static boolean enabled() {
		return ClientConfig.get().tabPing && Quartz.available(Feature.TAB_PING);
	}

	public static String label(int ping) {
		if (ping < 0) {
			return "?";
		}
		int p = Math.min(999, ping);
		String s = LABELS[p];
		return s != null ? s : (LABELS[p] = Integer.toString(p));
	}

	/** Green under 50 ms, through yellow and orange, to red over 300. */
	public static int colour(int ping) {
		return ping < 0 ? 0xFFAAAAAA : ping < 50 ? 0xFF4ADE80 : ping < 100 ? 0xFFA3E635 : ping < 175 ? 0xFFFBBF24 : ping < 300 ? 0xFFFB923C : 0xFFF87171;
	}
}
