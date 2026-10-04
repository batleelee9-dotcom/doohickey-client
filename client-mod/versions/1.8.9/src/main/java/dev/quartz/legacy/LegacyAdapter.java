package dev.quartz.legacy;

import dev.quartz.core.McVersion;
import dev.quartz.core.RenderBackend;
import dev.quartz.core.VersionAdapter;
import dev.quartz.core.accounts.LauncherBridge;
import dev.quartz.core.fx.NameTags;
import dev.quartz.core.fx.View;
import dev.quartz.core.hud.HudData;
import dev.quartz.core.hud.Input;
import dev.quartz.legacy.mixin.MinecraftClientAccessor;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.particle.ParticleType;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.resource.language.I18n;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.sound.SoundCategory;
import net.minecraft.client.util.Session;
import net.minecraft.entity.Entity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.AbstractArrowEntity;
import net.minecraft.entity.projectile.FishingBobberEntity;
import net.minecraft.entity.thrown.ThrowableEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.LiteralText;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.Display;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.WeakHashMap;

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
	public double[] entityBox(Object entity) {
		if (!(entity instanceof Entity)) {
			return null;
		}
		Entity e = (Entity) entity;
		return new double[] {e.x, e.y, e.z, e.height, e.width};
	}

	// Thrown items (snowballs, eggs, pearls) don't tell the client who threw
	// them: one that first shows up right at your eyes is yours.
	private final Set<Entity> seenThrown = Collections.newSetFromMap(new WeakHashMap<>());
	private final Set<Entity> ownThrown = Collections.newSetFromMap(new WeakHashMap<>());

	/** Every tick: notice items you just threw. */
	void trackThrown() {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.world == null || client.player == null) {
			return;
		}
		PlayerEntity p = client.player;
		double eyeY = p.y + p.getEyeHeight();
		for (Entity e : client.world.loadedEntities) {
			if (e instanceof ThrowableEntity && seenThrown.add(e) && e.squaredDistanceTo(p.x, eyeY, p.z) < 9) {
				ownThrown.add(e);
			}
		}
	}

	@Override
	public boolean ownProjectileNear(Object entity) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (!(entity instanceof Entity) || client.world == null || client.player == null) {
			return false;
		}
		Entity target = (Entity) entity;
		for (Entity e : client.world.loadedEntities) {
			boolean mine = e instanceof AbstractArrowEntity && ((AbstractArrowEntity) e).owner == client.player
				|| e instanceof FishingBobberEntity && ((FishingBobberEntity) e).thrower == client.player
				|| e instanceof ThrowableEntity && ownThrown.contains(e);
			// Still moving: an arrow stuck in the ground from earlier doesn't count.
			boolean flying = e.velocityX * e.velocityX + e.velocityY * e.velocityY + e.velocityZ * e.velocityZ > 0.01;
			if (mine && flying && near(e, target)) {
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean nameTag(Object player, NameTags.Tag t) {
		if (!(player instanceof PlayerEntity)) {
			return false;
		}
		PlayerEntity p = (PlayerEntity) player;
		float pt = View.partialTicks();
		t.x = p.prevTickX + (p.x - p.prevTickX) * pt;
		t.y = p.prevTickY + (p.y - p.prevTickY) * pt + p.height + 0.5;
		t.z = p.prevTickZ + (p.z - p.prevTickZ) * pt;
		// The display name carries the team prefix and its colour codes.
		String formatted = p.getName().asFormattedString();
		t.name = NameTags.strip(formatted);
		t.nameColour = NameTags.colourOf(formatted);
		t.health = p.getHealth();
		t.maxHealth = p.getMaxHealth();
		t.absorption = p.getAbsorption();
		for (int i = 0; i < 4; i++) {
			t.items[i] = p.getArmorSlot(3 - i);
		}
		t.items[4] = p.getStackInHand();
		return true;
	}

	@Override
	public Object selfTagEntity() {
		MinecraftClient client = MinecraftClient.getInstance();
		return client.player != null && client.options.perspective > 0 && !client.player.isSneaking() ? client.player : null;
	}

	/** Within 3 blocks of the target's box: projectiles cover up to ~3 blocks a tick. */
	private static boolean near(Entity projectile, Entity target) {
		double reach = target.width / 2 + 3;
		double dy = projectile.y - target.y;
		return Math.abs(projectile.x - target.x) <= reach && Math.abs(projectile.z - target.z) <= reach && dy >= -3 && dy <= target.height + 3;
	}

	@Override
	public void applyMaxFps(boolean on, Map<String, String> restore) {
		MinecraftClient client = MinecraftClient.getInstance();
		GameOptions o = client.options;
		if (on) {
			restore.put("maxFramerate", String.valueOf(o.maxFramerate));
			restore.put("vsync", String.valueOf(o.vsync));
			restore.put("vbo", String.valueOf(o.vbo));
			restore.put("fancyGraphics", String.valueOf(o.fancyGraphics));
			restore.put("ao", String.valueOf(o.ao));
			restore.put("cloudMode", String.valueOf(o.cloudMode));
			restore.put("entityShadows", String.valueOf(o.entityShadows));
			// 260 is "Unlimited"; VBOs batch chunk geometry on the GPU.
			o.maxFramerate = 260;
			o.vsync = false;
			o.vbo = true;
			o.fancyGraphics = false;
			o.ao = 0;
			o.cloudMode = 0;
			o.entityShadows = false;
		} else if (!restore.isEmpty()) {
			o.maxFramerate = Integer.parseInt(restore.getOrDefault("maxFramerate", "120"));
			o.vsync = Boolean.parseBoolean(restore.getOrDefault("vsync", "true"));
			o.vbo = Boolean.parseBoolean(restore.getOrDefault("vbo", "false"));
			o.fancyGraphics = Boolean.parseBoolean(restore.getOrDefault("fancyGraphics", "true"));
			o.ao = Integer.parseInt(restore.getOrDefault("ao", "2"));
			o.cloudMode = Integer.parseInt(restore.getOrDefault("cloudMode", "2"));
			o.entityShadows = Boolean.parseBoolean(restore.getOrDefault("entityShadows", "true"));
			restore.clear();
		}
		Display.setVSyncEnabled(o.vsync);
		// VBOs, fancy leaves and smooth lighting only change when chunks are rebuilt.
		if (client.worldRenderer != null && client.world != null) {
			client.worldRenderer.reload();
		}
		o.save();
	}

	@Override
	public boolean entityDead(Object entity) {
		return !(entity instanceof Entity) || ((Entity) entity).removed || !((Entity) entity).isAlive();
	}

	private static ParticleType particle(String kind) {
		switch (kind) {
			case "crit": return ParticleType.CRIT;
			case "magic": return ParticleType.CRIT_MAGIC;
			case "hearts": return ParticleType.HEART;
			case "flames": return ParticleType.FIRE;
			case "blood": return ParticleType.REDSTONE;
			case "smoke": return ParticleType.SMOKE_LARGE;
			case "notes": return ParticleType.NOTE;
			case "sparkle": return ParticleType.HAPPY_VILLAGER;
			case "lava": return ParticleType.LAVA;
			case "clouds": return ParticleType.CLOUD;
			case "portal": return ParticleType.NETHER_PORTAL;
			case "firework": return ParticleType.FIREWORK_SPARK;
			default: return ParticleType.CRIT_MAGIC;
		}
	}

	@Override
	public void particles(String kind, double x, double y, double z, int count, double spread, double speed) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.world == null) {
			return;
		}
		ParticleType type = particle(kind);
		Random random = RANDOM;
		for (int i = 0; i < count; i++) {
			double ox = (random.nextDouble() * 2 - 1) * spread;
			double oy = (random.nextDouble() * 2 - 1) * spread * 0.6;
			double oz = (random.nextDouble() * 2 - 1) * spread;
			// Redstone and notes read their "velocity" as a colour, so leave it at zero for those.
			boolean coloured = type == ParticleType.REDSTONE || type == ParticleType.NOTE;
			double vx = coloured ? 0 : (random.nextDouble() * 2 - 1) * speed;
			double vy = coloured ? 0 : random.nextDouble() * speed;
			double vz = coloured ? 0 : (random.nextDouble() * 2 - 1) * speed;
			if (type == ParticleType.NOTE) {
				vx = random.nextDouble();
			}
			client.world.addParticle(type, x + ox, y + oy, z + oz, vx, vy, vz);
		}
	}

	private static final Random RANDOM = new Random();

	private static String sound(String name) {
		switch (name) {
			case "ding": return "random.orb";
			case "pling": return "note.pling";
			case "bell": return "note.harp";
			case "click": return "random.click";
			case "bass": return "note.bass";
			case "xp": return "random.orb";
			case "levelup": return "random.levelup";
			case "firework": return "fireworks.blast";
			case "anvil": return "random.anvil_land";
			case "heartbeat": return "note.bd";
			default: return "random.orb";
		}
	}

	@Override
	public float soundVolume() {
		MinecraftClient client = MinecraftClient.getInstance();
		return client.options.getSoundVolume(SoundCategory.MASTER) * client.options.getSoundVolume(SoundCategory.PLAYERS);
	}

	@Override
	public void playSound(String name, float volume, float pitch) {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client.player == null) {
			return;
		}
		// 1.8.9 has no bell; a high harp note stands in for it.
		float p = "bell".equals(name) ? pitch * 1.6f : "xp".equals(name) ? pitch * 0.8f : pitch;
		PlayerEntity me = client.player;
		client.getSoundManager().play(new PositionedSoundInstance(new Identifier(sound(name)), volume, p, (float) me.x, (float) me.y, (float) me.z));
	}

	@Override
	public float healthFraction() {
		PlayerEntity p = MinecraftClient.getInstance().player;
		return p == null ? -1f : p.getHealth() / Math.max(1f, p.getMaxHealth());
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
