package dev.quartz.core.hud;

import java.util.ArrayDeque;
import java.util.Deque;

/** Clicks per second over a sliding one-second window, per mouse button. */
public final class CpsTracker {
	private static final Deque<Long> LEFT = new ArrayDeque<>();
	private static final Deque<Long> RIGHT = new ArrayDeque<>();

	private CpsTracker() {
	}

	/** Each version's mouse hook calls this on every press: 0 = left, 1 = right. */
	public static void click(int button) {
		Deque<Long> clicks = button == 0 ? LEFT : button == 1 ? RIGHT : null;
		if (clicks != null) {
			synchronized (clicks) {
				clicks.addLast(System.currentTimeMillis());
			}
		}
	}

	public static int left() {
		return count(LEFT);
	}

	public static int right() {
		return count(RIGHT);
	}

	private static int count(Deque<Long> clicks) {
		long cutoff = System.currentTimeMillis() - 1000;
		synchronized (clicks) {
			while (!clicks.isEmpty() && clicks.peekFirst() < cutoff) {
				clicks.removeFirst();
			}
			return clicks.size();
		}
	}
}
