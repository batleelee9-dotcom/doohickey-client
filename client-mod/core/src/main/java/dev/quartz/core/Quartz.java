package dev.quartz.core;

import dev.quartz.core.accounts.LauncherBridge;
import dev.quartz.core.config.ClientConfig;
import dev.quartz.core.hud.PlayerStats;
import dev.quartz.core.perf.Performance;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Core entry point: each version's initializer calls {@link #init} with its adapter. */
public final class Quartz {
	private static VersionAdapter adapter;
	private static int ticks;

	private Quartz() {
	}

	public static void init(VersionAdapter versionAdapter) {
		adapter = versionAdapter;
		ClientConfig.init(versionAdapter.configDir());
		LauncherBridge.init(versionAdapter.configDir().getParent());
		Log.info("Doohickey Client for Minecraft " + versionAdapter.version().id + " (" + versionAdapter.version().render + " rendering)");
		writeCompatReportOnce(versionAdapter.version());
	}

	public static VersionAdapter adapter() {
		if (adapter == null) {
			throw new IllegalStateException("Quartz.init() hasn't run");
		}
		return adapter;
	}

	public static McVersion version() {
		return adapter().version();
	}

	/** Is this feature built and possible on the running version? */
	public static boolean available(Feature feature) {
		return adapter != null && CompatRegistry.available(feature, adapter.version());
	}

	/** Once per client tick, from each version's tick hook. */
	public static void tick() {
		Safe.run("perf.tick", () -> Performance.tick(adapter));
		Safe.run("stats.tick", () -> PlayerStats.tick(adapter));
		Safe.run("effects.tick", () -> dev.quartz.core.fx.Effects.tick(adapter));
		if (++ticks % 20 == 0) {
			Safe.run("config.reload", () -> ClientConfig.get().pollReload());
		}
	}

	/** The first launch on each Minecraft version logs what's available there. */
	private static void writeCompatReportOnce(McVersion version) {
		Path file = adapter.configDir().resolve("quartz").resolve("compat-" + version.id + ".txt");
		if (Files.exists(file)) {
			return;
		}
		String report = CompatRegistry.report(version);
		Log.info(report);
		try {
			Files.createDirectories(file.getParent());
			Files.write(file, report.getBytes(StandardCharsets.UTF_8));
		} catch (IOException e) {
			Log.warn("Couldn't write the compatibility report: " + e.getMessage());
		}
	}
}
