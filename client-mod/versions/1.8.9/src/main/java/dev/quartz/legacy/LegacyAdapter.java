package dev.quartz.legacy;

import dev.quartz.core.McVersion;
import dev.quartz.core.RenderBackend;
import dev.quartz.core.VersionAdapter;
import dev.quartz.core.accounts.LauncherBridge;
import dev.quartz.core.hud.HudData;
import dev.quartz.core.hud.Input;
import dev.quartz.legacy.mixin.MinecraftClientAccessor;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.client.util.Session;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.LiteralText;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.input.Keyboard;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** {@link VersionAdapter} for Minecraft 1.8.9 on Legacy Fabric. */
final class LegacyAdapter implements VersionAdapter {
	@Override
	public McVersion version() {
		return McVersion.V1_8_9;
	}

	@Override
	public Path configDir() {
		return FabricLoader.getInstance().getConfigDir();
	}

	@Override
	public RenderBackend render() {
		return LegacyGlBackend.INSTANCE;
	}

	@Override
	public boolean inWorld() {
		MinecraftClient client = MinecraftClient.getInstance();
		return client.world != null && client.player != null;
	}

	@Override
	public String dimension() {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.world == null) {
			return "";
		}
		switch (client.world.dimension.getType()) {
			case -1: return "minecraft:the_nether";
			case 1: return "minecraft:the_end";
			default: return "minecraft:overworld";
		}
	}

	@Override
	public String worldKey() {
		MinecraftClient client = MinecraftClient.getInstance();
		ServerInfo server = client.getCurrentServerEntry();
		if (server != null) {
			return "server:" + server.address.toLowerCase(Locale.ROOT);
		}
		if (client.isIntegratedServerRunning() && client.getServer() != null) {
			return "world:" + client.getServer().getLevelName();
		}
		return "";
	}

	@Override
	public int fps() {
		return MinecraftClient.getCurrentFps();
	}

	@Override
	public int renderDistance() {
		return MinecraftClient.getInstance().options.viewDistance;
	}

	@Override
	public void setRenderDistance(int chunks) {
		// The world renderer notices the change on its next frame.
		MinecraftClient.getInstance().options.viewDistance = chunks;
	}

	private static KeyBinding binding(Input input) {
		GameOptions o = MinecraftClient.getInstance().options;
		switch (input) {
			case FORWARD: return o.forwardKey;
			case LEFT: return o.leftKey;
			case BACK: return o.backKey;
			case RIGHT: return o.rightKey;
			case JUMP: return o.jumpKey;
			case ATTACK: return o.attackKey;
			default: return o.useKey;
		}
	}

	@Override
	public boolean inputDown(Input input) {
		return binding(input).isPressed();
	}

	@Override
	public String inputLabel(Input input) {
		return GameOptions.getFormattedNameForKeyCode(binding(input).getCode());
	}

	@Override
	public int[] blockPosition() {
		PlayerEntity player = MinecraftClient.getInstance().player;
		if (player == null) {
			return null;
		}
		BlockPos pos = new BlockPos(player);
		return new int[] {pos.getX(), pos.getY(), pos.getZ()};
	}

	@Override
	public String facing() {
		PlayerEntity player = MinecraftClient.getInstance().player;
		if (player == null) {
			return "";
		}
		switch (player.getHorizontalDirection()) {
			case NORTH: return "N";
			case SOUTH: return "S";
			case EAST: return "E";
			case WEST: return "W";
			default: return "";
		}
	}

	@Override
	public int ping() {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player == null || client.getNetworkHandler() == null || client.isIntegratedServerRunning()) {
			return -1;
		}
		PlayerListEntry entry = client.getNetworkHandler().getPlayerListEntry(client.player.getUuid());
		return entry == null ? -1 : entry.getLatency();
	}

	@Override
	public List<HudData.Effect> effects() {
		PlayerEntity player = MinecraftClient.getInstance().player;
		List<HudData.Effect> out = new ArrayList<>();
		if (player == null) {
			return out;
		}
		for (StatusEffectInstance instance : player.getStatusEffectInstances()) {
			StatusEffect effect = StatusEffect.STATUS_EFFECTS[instance.getEffectId()];
			if (effect == null) {
				continue;
			}
			String name = I18n.translate(effect.getTranslationKey());
			int level = instance.getAmplifier() + 1;
			if (level > 1) {
				name += " " + I18n.translate("enchantment.level." + level);
			}
			out.add(new HudData.Effect(name, StatusEffect.getFormattedDuration(instance), effect.isNegative()));
		}
		return out;
	}

	@Override
	public List<HudData.Item> armor() {
		PlayerEntity player = MinecraftClient.getInstance().player;
		List<HudData.Item> out = new ArrayList<>();
		if (player == null) {
			return out;
		}
		// armor[] runs boots → helmet; show helmet first.
		for (int i = 3; i >= 0; i--) {
			add(out, player.inventory.armor[i]);
		}
		add(out, player.inventory.getMainHandStack());
		return out;
	}

	private static void add(List<HudData.Item> out, ItemStack stack) {
		if (stack == null) {
			return;
		}
		boolean wears = stack.isDamageable();
		out.add(new HudData.Item(stack, wears ? stack.getMaxDamage() - stack.getDamage() : -1, wears ? stack.getMaxDamage() : 0));
	}

	@Override
	public double[] position() {
		PlayerEntity p = MinecraftClient.getInstance().player;
		return p == null ? null : new double[] {p.x, p.y, p.z};
	}

	@Override
	public float yaw() {
		PlayerEntity p = MinecraftClient.getInstance().player;
		return p == null ? 0f : p.yaw;
	}

	@Override
	public long worldTime() {
		MinecraftClient client = MinecraftClient.getInstance();
		return client.world == null ? -1 : client.world.getTimeOfDay();
	}

	@Override
	public float saturation() {
		PlayerEntity p = MinecraftClient.getInstance().player;
		return p == null ? -1f : p.getHungerManager().getSaturationLevel();
	}

	@Override
	public int arrows() {
		PlayerEntity p = MinecraftClient.getInstance().player;
		int n = 0;
		if (p != null) {
			for (ItemStack stack : p.inventory.main) {
				if (stack != null && stack.getItem() == Items.ARROW) {
					n += stack.count;
				}
			}
		}
		return n;
	}

	@Override
	public String targetBlock() {
		MinecraftClient client = MinecraftClient.getInstance();
		BlockHitResult hit = client.result;
		if (client.world == null || hit == null || hit.type != BlockHitResult.Type.BLOCK || hit.getBlockPos() == null) {
			return "";
		}
		return client.world.getBlockState(hit.getBlockPos()).getBlock().getTranslatedName();
	}

	@Override
	public String biome() {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.world == null || client.player == null) {
			return "";
		}
		return client.world.getBiome(new BlockPos(client.player)).name;
	}

	@Override
	public int hurtTime() {
		PlayerEntity p = MinecraftClient.getInstance().player;
		return p == null ? 0 : p.hurtTime;
	}

	@Override
	public boolean hitboxes() {
		return MinecraftClient.getInstance().getEntityRenderManager().getRenderHitboxes();
	}

	@Override
	public void setHitboxes(boolean shown) {
		MinecraftClient.getInstance().getEntityRenderManager().setRenderHitboxes(shown);
	}

	@Override
	public boolean zoomKeyDown() {
		return MinecraftClient.getInstance().currentScreen == null && Keyboard.isKeyDown(Keyboard.KEY_C);
	}

	@Override
	public void openMenu() {
		MinecraftClient.getInstance().setScreen(new QuartzLegacyScreen());
	}

	@Override
	public void notifyPlayer(String message) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.inGameHud != null) {
			client.inGameHud.getChatHud().addMessage(new LiteralText("[Doohickey] " + message));
		}
	}

	@Override
	public void runOnMainThread(Runnable task) {
		MinecraftClient.getInstance().submit(task);
	}

	@Override
	public String sessionName() {
		return MinecraftClient.getInstance().getSession().getUsername();
	}

	@Override
	public String sessionUuid() {
		return MinecraftClient.getInstance().getSession().getUuid().replace("-", "");
	}

	@Override
	public boolean sessionOffline() {
		// The launcher gives offline sessions a placeholder token.
		return "0".equals(MinecraftClient.getInstance().getSession().getAccessToken());
	}

	@Override
	public boolean canSwitchSession() {
		return MinecraftClient.getInstance().world == null;
	}

	@Override
	public void switchSession(LauncherBridge.Session session) {
		if (!canSwitchSession()) {
			throw new IllegalStateException("Leave the world first");
		}
		// 1.8.9 predates Microsoft accounts; their tokens work as "mojang" sessions.
		String type = session.online() ? "mojang" : "legacy";
		((MinecraftClientAccessor) MinecraftClient.getInstance())
			.quartz$setSession(new Session(session.username, session.uuid, session.accessToken, type));
	}
}
