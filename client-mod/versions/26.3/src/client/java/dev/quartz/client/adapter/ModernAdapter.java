package dev.quartz.client.adapter;

import dev.quartz.client.QuartzClient;
import dev.quartz.client.screen.QuartzScreen;
import dev.quartz.core.McVersion;
import dev.quartz.core.RenderBackend;
import dev.quartz.core.VersionAdapter;
import dev.quartz.core.accounts.LauncherBridge;
import dev.quartz.core.hud.HudData;
import dev.quartz.core.hud.Input;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.components.debug.DebugScreenEntries;
import net.minecraft.client.gui.components.debug.DebugScreenEntryStatus;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** {@link VersionAdapter} for Minecraft 26.3 on Fabric. */
public final class ModernAdapter implements VersionAdapter {
	@Override
	public McVersion version() {
		return McVersion.V26_3;
	}

	@Override
	public Path configDir() {
		return FabricLoader.getInstance().getConfigDir();
	}

	@Override
	public RenderBackend render() {
		return PipelineBackend.current();
	}

	@Override
	public boolean inWorld() {
		Minecraft mc = Minecraft.getInstance();
		return mc.level != null && mc.player != null;
	}

	@Override
	public String dimension() {
		Minecraft mc = Minecraft.getInstance();
		return mc.level == null ? "" : mc.level.dimension().identifier().toString();
	}

	@Override
	public String worldKey() {
		Minecraft mc = Minecraft.getInstance();
		ServerData server = mc.getCurrentServer();
		if (server != null) {
			return "server:" + server.ip.toLowerCase(Locale.ROOT);
		}
		if (mc.getSingleplayerServer() != null) {
			return "world:" + mc.getSingleplayerServer().getWorldData().getLevelName();
		}
		return "";
	}

	@Override
	public int fps() {
		return Minecraft.getInstance().getFps();
	}

	@Override
	public int renderDistance() {
		return Minecraft.getInstance().options.renderDistance().get();
	}

	@Override
	public void setRenderDistance(int chunks) {
		Minecraft.getInstance().options.renderDistance().set(chunks);
	}

	private static KeyMapping mapping(Input input) {
		Options o = Minecraft.getInstance().options;
		return switch (input) {
			case FORWARD -> o.keyUp;
			case LEFT -> o.keyLeft;
			case BACK -> o.keyDown;
			case RIGHT -> o.keyRight;
			case JUMP -> o.keyJump;
			case ATTACK -> o.keyAttack;
			case USE -> o.keyUse;
		};
	}

	@Override
	public boolean inputDown(Input input) {
		return mapping(input).isDown();
	}

	@Override
	public String inputLabel(Input input) {
		return mapping(input).getTranslatedKeyMessage().getString();
	}

	@Override
	public int @Nullable [] blockPosition() {
		Player p = Minecraft.getInstance().player;
		return p == null ? null : new int[] {p.getBlockX(), p.getBlockY(), p.getBlockZ()};
	}

	@Override
	public String facing() {
		Player p = Minecraft.getInstance().player;
		if (p == null) {
			return "";
		}
		return switch (p.getDirection()) {
			case NORTH -> "N";
			case SOUTH -> "S";
			case EAST -> "E";
			case WEST -> "W";
			default -> "";
		};
	}

	@Override
	public int ping() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.getConnection() == null || mc.isLocalServer()) {
			return -1;
		}
		PlayerInfo info = mc.getConnection().getPlayerInfo(mc.player.getUUID());
		return info == null ? -1 : info.getLatency();
	}

	@Override
	public List<HudData.Effect> effects() {
		Player p = Minecraft.getInstance().player;
		List<HudData.Effect> out = new ArrayList<>();
		if (p == null) {
			return out;
		}
		for (MobEffectInstance e : p.getActiveEffects()) {
			String name = e.getEffect().value().getDisplayName().getString();
			if (e.getAmplifier() > 0) {
				name += " " + Component.translatable("enchantment.level." + (e.getAmplifier() + 1)).getString();
			}
			String time;
			if (e.isInfiniteDuration()) {
				time = "∞";
			} else {
				int secs = e.getDuration() / 20;
				time = (secs / 60) + ":" + String.format(Locale.ROOT, "%02d", secs % 60);
			}
			out.add(new HudData.Effect(name, time, e.getEffect().value().getCategory() == MobEffectCategory.HARMFUL));
		}
		return out;
	}

	@Override
	public List<HudData.Item> armor() {
		Player p = Minecraft.getInstance().player;
		List<HudData.Item> out = new ArrayList<>();
		if (p == null) {
			return out;
		}
		for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.MAINHAND}) {
			ItemStack stack = p.getItemBySlot(slot);
			if (stack.isEmpty()) {
				continue;
			}
			boolean wears = stack.isDamageableItem();
			out.add(new HudData.Item(stack, wears ? stack.getMaxDamage() - stack.getDamageValue() : -1, wears ? stack.getMaxDamage() : 0));
		}
		return out;
	}

	@Override
	public double @Nullable [] position() {
		Player p = Minecraft.getInstance().player;
		return p == null ? null : new double[] {p.getX(), p.getY(), p.getZ()};
	}

	@Override
	public float yaw() {
		Player p = Minecraft.getInstance().player;
		return p == null ? 0f : p.getYRot();
	}

	@Override
	public long worldTime() {
		ClientLevel level = Minecraft.getInstance().level;
		return level == null ? -1 : level.getOverworldClockTime();
	}

	@Override
	public float saturation() {
		Player p = Minecraft.getInstance().player;
		return p == null ? -1f : p.getFoodData().getSaturationLevel();
	}

	@Override
	public int arrows() {
		Player p = Minecraft.getInstance().player;
		int n = 0;
		if (p != null) {
			Inventory inv = p.getInventory();
			for (int i = 0; i < inv.getContainerSize(); i++) {
				ItemStack stack = inv.getItem(i);
				if (stack.is(Items.ARROW)) {
					n += stack.getCount();
				}
			}
		}
		return n;
	}

	@Override
	public String targetBlock() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || !(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) {
			return "";
		}
		return mc.level.getBlockState(hit.getBlockPos()).getBlock().getName().getString();
	}

	@Override
	public String biome() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || mc.player == null) {
			return "";
		}
		return mc.level.getBiome(mc.player.blockPosition()).unwrapKey().map(k -> titleCase(k.identifier().getPath())).orElse("");
	}

	/** "dark_forest" → "Dark Forest". */
	private static String titleCase(String id) {
		StringBuilder out = new StringBuilder();
		for (String word : id.split("_")) {
			if (!word.isEmpty()) {
				out.append(out.isEmpty() ? "" : " ").append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
			}
		}
		return out.toString();
	}

	@Override
	public int hurtTime() {
		Player p = Minecraft.getInstance().player;
		return p == null ? 0 : p.hurtTime;
	}

	@Override
	public boolean hitboxes() {
		return Minecraft.getInstance().debugEntries.isCurrentlyEnabled(DebugScreenEntries.ENTITY_HITBOXES);
	}

	@Override
	public void setHitboxes(boolean shown) {
		Minecraft.getInstance().debugEntries.setStatus(DebugScreenEntries.ENTITY_HITBOXES,
			shown ? DebugScreenEntryStatus.ALWAYS_ON : DebugScreenEntryStatus.NEVER);
	}

	@Override
	public boolean zoomKeyDown() {
		return Minecraft.getInstance().gui.screen() == null && QuartzClient.zoomHeld();
	}

	@Override
	public void openMenu() {
		Minecraft.getInstance().gui.setScreen(new QuartzScreen());
	}

	@Override
	public void notifyPlayer(String message) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null) {
			mc.player.sendOverlayMessage(Component.literal(message));
		}
	}

	@Override
	public void runOnMainThread(Runnable task) {
		Minecraft.getInstance().execute(task);
	}

	@Override
	public String sessionName() {
		return Minecraft.getInstance().getUser().getName();
	}

	@Override
	public String sessionUuid() {
		return Minecraft.getInstance().getUser().getProfileId().toString().replace("-", "");
	}

	@Override
	public boolean sessionOffline() {
		// The launcher gives offline sessions a placeholder token.
		return "0".equals(Minecraft.getInstance().getUser().getAccessToken());
	}

	@Override
	public boolean canSwitchSession() {
		Minecraft mc = Minecraft.getInstance();
		return mc.level == null && mc.getConnection() == null;
	}

	@Override
	public void switchSession(LauncherBridge.Session session) {
		if (!canSwitchSession()) {
			throw new IllegalStateException("Leave the world first");
		}
		SessionSwitch.apply(session);
	}
}
