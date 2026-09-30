package dev.quartz.core;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Every hook into the game runs through here, so a Doohickey bug can never
 * crash Minecraft. A failing hook falls back to vanilla behaviour, is
 * logged once with its stack trace, and is switched off for the session
 * after {@link #LIMIT} failures (a broken per-frame hook would otherwise
 * flood the log).
 */
public final class Safe {
	private static final int LIMIT = 3;
	private static final Map<String, Integer> FAILURES = new ConcurrentHashMap<>();

	private Safe() {
	}

	public static boolean enabled(String hook) {
		Integer n = FAILURES.get(hook);
		return n == null || n < LIMIT;
	}

	public static void run(String hook, Runnable body) {
		if (!enabled(hook)) {
			return;
		}
		try {
			body.run();
		} catch (Throwable t) {
			fail(hook, t);
		}
	}

	/** The hook's result, or {@code fallback} (normally the vanilla value) if it throws. */
	public static <T> T call(String hook, Supplier<T> body, T fallback) {
		if (!enabled(hook)) {
			return fallback;
		}
		try {
			T value = body.get();
			return value == null ? fallback : value;
		} catch (Throwable t) {
			fail(hook, t);
			return fallback;
		}
	}

	private static void fail(String hook, Throwable t) {
		int n = FAILURES.merge(hook, 1, Integer::sum);
		if (n == 1) {
			Log.error("Hook '" + hook + "' failed; using vanilla behaviour instead", t);
		}
		if (n == LIMIT) {
			Log.warn("Hook '" + hook + "' failed " + LIMIT + " times and is off until the game restarts");
		}
	}

	/** Hooks that were switched off, for the compatibility report. */
	public static Map<String, Integer> failures() {
		return FAILURES;
	}
}
