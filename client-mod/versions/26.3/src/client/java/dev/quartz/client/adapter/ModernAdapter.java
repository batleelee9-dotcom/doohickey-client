package dev.quartz.client.adapter;

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
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
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
