package dev.quartz.core;

/**
 * Minimal logging that works on every version without depending on the
 * game's logger (log4j 2.0-beta9 in 1.8.9, SLF4J today): Minecraft routes
 * System.out/err into its own log, and the launcher's console shows both.
 */
public final class Log {
	private Log() {
	}

	public static void info(String message) {
		System.out.println("[Doohickey] " + message);
	}

	public static void warn(String message) {
		System.err.println("[Doohickey] WARN " + message);
	}

	public static void error(String message, Throwable t) {
		System.err.println("[Doohickey] ERROR " + message);
		t.printStackTrace();
	}
}
