package dev.quartz.client.map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import dev.quartz.core.config.ClientConfig;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Named locations per server / singleplayer world, saved to config/quartz-waypoints.json. */
public final class Waypoints {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final int[] COLORS = {0x8B7CF6, 0x4F8EF7, 0x2BB8A6, 0x3DD68C, 0xF5A524, 0xF2555A, 0xEC5FB7};

	public static final class Waypoint {
		public String name;
		public int x;
		public int y;
		public int z;
		public int color;
		public String dimension;

		public Waypoint(String name, int x, int y, int z, int color, String dimension) {
			this.name = name;
			this.x = x;
			this.y = y;
			this.z = z;
			this.color = color;
			this.dimension = dimension;
		}
	}

	private static Map<String, List<Waypoint>> all;
	private static boolean wasDead;

	private Waypoints() {
	}

	private static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve("quartz-waypoints.json");
	}

	private static Map<String, List<Waypoint>> all() {
		if (all == null) {
			try {
				all = Files.exists(path())
					? GSON.fromJson(Files.readString(path()), new TypeToken<Map<String, List<Waypoint>>>() {}.getType())
					: null;
			} catch (IOException | RuntimeException e) {
				all = null;
			}
			if (all == null) {
				all = new HashMap<>();
			}
		}
		return all;
	}

	private static void save() {
		try {
			Files.createDirectories(path().getParent());
			Files.writeString(path(), GSON.toJson(all()));
		} catch (IOException e) {
			System.err.println("[Doohickey] Couldn't save waypoints: " + e.getMessage());
		}
	}

	/** Waypoints belong to the server address, or the singleplayer world's name. */
	private static String worldKey(Minecraft mc) {
		ServerData server = mc.getCurrentServer();
		if (server != null) {
			return "server:" + server.ip.toLowerCase(Locale.ROOT);
		}
		if (mc.getSingleplayerServer() != null) {
			return "world:" + mc.getSingleplayerServer().getWorldData().getLevelName();
		}
		return "unknown";
	}

	private static String dimension(Minecraft mc) {
		return mc.level == null ? "" : mc.level.dimension().identifier().toString();
	}

	public static List<Waypoint> current(Minecraft mc) {
		return all().computeIfAbsent(worldKey(mc), k -> new ArrayList<>());
	}

	public static List<Waypoint> inDimension(Minecraft mc) {
		String dim = dimension(mc);
		return current(mc).stream().filter(w -> w.dimension.equals(dim)).toList();
	}

	public static void addHere(Minecraft mc, String name) {
		Player p = mc.player;
		if (p == null) {
			return;
		}
		List<Waypoint> list = current(mc);
		String label = name != null ? name : "Waypoint " + (list.size() + 1);
		list.add(new Waypoint(label, p.getBlockX(), p.getBlockY(), p.getBlockZ(), COLORS[list.size() % COLORS.length], dimension(mc)));
		save();
	}

	public static void remove(Minecraft mc, Waypoint w) {
		current(mc).remove(w);
		save();
	}

	public static void clear(Minecraft mc) {
		current(mc).clear();
		save();
	}

	/** Drops a "Death" waypoint (replacing the previous one) when the player dies. */
	public static void tick(Minecraft mc) {
		Player p = mc.player;
		if (p == null || !ClientConfig.get().deathWaypoints) {
			wasDead = false;
			return;
		}
		boolean dead = p.isDeadOrDying();
		if (dead && !wasDead) {
			current(mc).removeIf(w -> w.name.equals("Death"));
			List<Waypoint> list = current(mc);
			list.add(new Waypoint("Death", p.getBlockX(), p.getBlockY(), p.getBlockZ(), 0xF2555A, dimension(mc)));
			save();
		}
		wasDead = dead;
	}

	/**
	 * Floating labels projected onto the HUD. Doing the projection here, from
	 * the camera's position and rotation, avoids hooking world rendering.
	 */
	public static void renderLabels(GuiGraphicsExtractor g) {
		Minecraft mc = Minecraft.getInstance();
		if (!ClientConfig.get().waypointsInWorld || mc.gui.hud.isHidden() || mc.player == null || FairPlay.minimapDisabled()) {
			return;
		}
		Camera camera = mc.gameRenderer.mainCamera();
		Vec3 eye = camera.position();
		double yaw = Math.toRadians(camera.yRot());
		double pitch = Math.toRadians(camera.xRot());
		// Camera basis: forward, right and up in world space.
		Vec3 forward = new Vec3(-Math.sin(yaw) * Math.cos(pitch), -Math.sin(pitch), Math.cos(yaw) * Math.cos(pitch));
		Vec3 right = forward.cross(new Vec3(0, 1, 0)).normalize();
		Vec3 up = right.cross(forward);
		double focal = (g.guiHeight() / 2.0) / Math.tan(Math.toRadians(mc.options.fov().get()) / 2.0);

		for (Waypoint w : inDimension(mc)) {
			Vec3 d = new Vec3(w.x + 0.5, w.y + 1.5, w.z + 0.5).subtract(eye);
			double depth = d.dot(forward);
			if (depth < 0.5) {
				continue; // behind the camera
			}
			int sx = (int) Math.round(g.guiWidth() / 2.0 + d.dot(right) / depth * focal);
			int sy = (int) Math.round(g.guiHeight() / 2.0 - d.dot(up) / depth * focal);
			if (sx < -50 || sx > g.guiWidth() + 50 || sy < -20 || sy > g.guiHeight() + 20) {
				continue;
			}
			String label = w.name + "  " + Math.round(d.length()) + "m";
			int width = mc.font.width(label) + 8;
			g.fill(sx - width / 2, sy - 6, sx + width / 2, sy + 6, 0x90000000);
			g.fill(sx - width / 2, sy - 6, sx - width / 2 + 2, sy + 6, w.color | 0xFF000000);
			g.centeredText(mc.font, label, sx + 1, sy - 4, 0xFFFFFFFF);
		}
	}
}
