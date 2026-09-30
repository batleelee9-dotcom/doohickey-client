package dev.quartz.client.map;

import com.mojang.blaze3d.platform.NativeImage;
import dev.quartz.core.config.ClientConfig;
import dev.quartz.client.hud.HudModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;

/**
 * A north-up terrain minimap. Colours come from the same MapColor data as
 * vanilla map items. The 128×128 texture is refreshed a slice of rows per
 * tick, so the cost stays flat (~2k block lookups per tick) no matter how
 * fast the player moves.
 */
public final class Minimap {
	public static final int TEXTURE = 128;
	public static final int[] ZOOMS = {1, 2, 4};
	private static final int ROWS_PER_TICK = 16;
	private static final Identifier ID = Identifier.fromNamespaceAndPath("quartz", "minimap");

	private static DynamicTexture texture;
	private static int nextRow;

	public static final HudModule MODULE = new MinimapModule();

	private Minimap() {
	}

	public static boolean active() {
		return MODULE.state().enabled && !FairPlay.minimapDisabled();
	}

	public static void tick(Minecraft mc) {
		ClientLevel level = mc.level;
		Player player = mc.player;
		if (!active() || level == null || player == null) {
			return;
		}
		if (texture == null) {
			texture = new DynamicTexture(() -> "Doohickey minimap", TEXTURE, TEXTURE, true);
			mc.getTextureManager().register(ID, texture);
		}
		int zoom = zoom();
		int cx = player.getBlockX();
		int cz = player.getBlockZ();
		// Dimensions with a roof (the Nether) have nothing useful on their
		// surface heightmap; scan down from the player instead — unless the
		// server asked for fair play, which forbids seeing through terrain.
		boolean caves = level.dimensionType().hasCeiling() && !FairPlay.cavesDisabled();
		int rows = caves ? ROWS_PER_TICK / 2 : ROWS_PER_TICK;
		NativeImage px = texture.getPixels();
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int i = 0; i < rows; i++) {
			int row = nextRow;
			nextRow = (nextRow + 1) % TEXTURE;
			int wz = cz + (row - TEXTURE / 2) * zoom;
			for (int col = 0; col < TEXTURE; col++) {
				int wx = cx + (col - TEXTURE / 2) * zoom;
				px.setPixel(col, row, caves ? caveColor(level, pos, wx, player.getBlockY(), wz) : surfaceColor(level, pos, wx, wz));
			}
		}
		texture.upload();
	}

	public static int zoom() {
		int z = ClientConfig.get().minimapZoom;
		return z == 2 || z == 4 ? z : 1;
	}

	private static int surfaceColor(ClientLevel level, BlockPos.MutableBlockPos pos, int x, int z) {
		if (!level.hasChunk(x >> 4, z >> 4)) {
			return 0xFF101014;
		}
		int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
		if (y < level.getMinY()) {
			return 0xFF000000;
		}
		pos.set(x, y, z);
		BlockState state = level.getBlockState(pos);
		MapColor color = state.getMapColor(level, pos);
		if (color == MapColor.NONE) {
			return 0xFF000000;
		}
		// Shade by slope towards the north, the way vanilla maps do.
		int north = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z - 1) - 1;
		MapColor.Brightness b = y > north ? MapColor.Brightness.HIGH : y < north ? MapColor.Brightness.LOW : MapColor.Brightness.NORMAL;
		return color.calculateARGBColor(b);
	}

	private static int caveColor(ClientLevel level, BlockPos.MutableBlockPos pos, int x, int fromY, int z) {
		if (!level.hasChunk(x >> 4, z >> 4)) {
			return 0xFF101014;
		}
		// First solid block below open air, searching 24 blocks down.
		boolean air = false;
		for (int y = fromY + 2; y > fromY - 22 && y > level.getMinY(); y--) {
			pos.set(x, y, z);
			BlockState state = level.getBlockState(pos);
			if (state.isAir()) {
				air = true;
			} else if (air) {
				MapColor color = state.getMapColor(level, pos);
				return color == MapColor.NONE ? 0xFF000000 : color.calculateARGBColor(y >= fromY ? MapColor.Brightness.HIGH : MapColor.Brightness.NORMAL);
			}
		}
		return 0xFF000000;
	}

	static final class MinimapModule extends HudModule {
		private static final int SIZE = 100;

		MinimapModule() {
			super("minimap", "Minimap", true, 1.0f, 0.0f);
		}

		public boolean hasContent() {
			return active() && texture != null;
		}

		public int width() {
			return SIZE;
		}

		public int height() {
			return SIZE + 12;
		}

		public void render(GuiGraphicsExtractor g, boolean preview) {
			Minecraft mc = Minecraft.getInstance();
			g.fill(-1, -1, SIZE + 1, SIZE + 1, 0xFF0A0A0B);
			if (texture != null) {
				g.blit(RenderPipelines.GUI_TEXTURED, ID, 0, 0, 0f, 0f, SIZE, SIZE, TEXTURE, TEXTURE, TEXTURE, TEXTURE);
			} else {
				g.fill(0, 0, SIZE, SIZE, 0xFF1C1C21);
			}
			Player player = mc.player;
			float scale = SIZE / (float) (TEXTURE * zoom());
			if (player != null) {
				for (Waypoints.Waypoint w : Waypoints.inDimension(mc)) {
					float dx = (float) (w.x + 0.5 - player.getX()) * scale;
					float dz = (float) (w.z + 0.5 - player.getZ()) * scale;
					int x = Math.round(Math.clamp(SIZE / 2f + dx, 2, SIZE - 3));
					int y = Math.round(Math.clamp(SIZE / 2f + dz, 2, SIZE - 3));
					g.fill(x - 2, y - 2, x + 2, y + 2, 0xFF000000);
					g.fill(x - 1, y - 1, x + 1, y + 1, w.color | 0xFF000000);
				}
				// Player marker, rotated to the facing direction.
				g.pose().pushMatrix();
				g.pose().translate(SIZE / 2f, SIZE / 2f);
				g.pose().rotate((float) Math.toRadians(player.getYRot() + 180));
				g.fill(-2, -2, 2, 2, 0xFF000000);
				g.fill(-1, -1, 1, 1, 0xFFFFFFFF);
				g.fill(-1, -5, 1, -2, 0xFFFFFFFF);
				g.pose().popMatrix();
			}
			g.centeredText(mc.font, "N", SIZE / 2, 2, 0xFFFFFFFF);
			String label = FairPlay.minimapDisabled() ? "Disabled by server" : zoom() + "x · " + Waypoints.current(mc).size() + " waypoints";
			g.centeredText(mc.font, label, SIZE / 2, SIZE + 3, 0xFFAAAAAA);
		}
	}
}
