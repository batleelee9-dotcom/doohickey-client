package dev.quartz.core.hud;

/** The distance of your last hit, from each version's attack hook. Display only. */
public final class ReachTracker {
	private static volatile double last = -1;

	private ReachTracker() {
	}

	public static void record(double blocks) {
		last = blocks;
	}

	/** Blocks, or -1 before the first hit. */
	public static double last() {
		return last;
	}
}
