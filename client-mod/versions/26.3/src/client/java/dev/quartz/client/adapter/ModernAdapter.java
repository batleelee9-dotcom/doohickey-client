package dev.quartz.client.adapter;

import dev.quartz.client.QuartzClient;
import dev.quartz.client.screen.MenuScreen;
import dev.quartz.core.McVersion;
import dev.quartz.core.RenderBackend;
import dev.quartz.core.VersionAdapter;
import dev.quartz.core.accounts.LauncherBridge;
import dev.quartz.core.hud.HudData;
import dev.quartz.core.hud.Input;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.CloudStatus;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.components.debug.DebugScreenEntries;
import net.minecraft.client.gui.components.debug.DebugScreenEntryStatus;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
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
	public double @Nullable [] entityBox(Object entity) {
		if (!(entity instanceof Entity e)) {
			return null;
		}
		return new double[] {e.getX(), e.getY(), e.getZ(), e.getBbHeight(), e.getBbWidth()};
	}

	@Override
	public boolean entityDead(Object entity) {
		return !(entity instanceof Entity e) || e.isRemoved() || !e.isAlive();
	}

	private static ParticleOptions particle(String kind) {
		return switch (kind) {
			case "crit" -> ParticleTypes.CRIT;
			case "hearts" -> ParticleTypes.HEART;
			case "flames" -> ParticleTypes.FLAME;
			case "blood" -> ParticleTypes.DAMAGE_INDICATOR;
			case "smoke" -> ParticleTypes.LARGE_SMOKE;
			case "notes" -> ParticleTypes.NOTE;
			case "sparkle" -> ParticleTypes.HAPPY_VILLAGER;
			case "lava" -> ParticleTypes.LAVA;
			case "clouds" -> ParticleTypes.CLOUD;
			case "portal" -> ParticleTypes.PORTAL;
			case "firework" -> ParticleTypes.FIREWORK;
			default -> ParticleTypes.ENCHANTED_HIT;
		};
	}

	@Override
	public void particles(String kind, double x, double y, double z, int count, double spread, double speed) {
		ClientLevel level = Minecraft.getInstance().level;
		if (level == null) {
			return;
		}
		ParticleOptions type = particle(kind);
		RandomSource random = level.getRandom();
		for (int i = 0; i < count; i++) {
			double ox = (random.nextDouble() * 2 - 1) * spread;
			double oy = (random.nextDouble() * 2 - 1) * spread * 0.6;
			double oz = (random.nextDouble() * 2 - 1) * spread;
			// Notes read their x "velocity" as a colour.
			double vx = type == ParticleTypes.NOTE ? random.nextDouble() : (random.nextDouble() * 2 - 1) * speed;
			double vy = type == ParticleTypes.NOTE ? 0 : random.nextDouble() * speed;
			double vz = type == ParticleTypes.NOTE ? 0 : (random.nextDouble() * 2 - 1) * speed;
			level.addParticle(type, x + ox, y + oy, z + oz, vx, vy, vz);
		}
	}

	private static SoundEvent sound(String name) {
		return switch (name) {
			case "pling" -> SoundEvents.NOTE_BLOCK_PLING.value();
			case "bell" -> SoundEvents.NOTE_BLOCK_BELL.value();
			case "click" -> SoundEvents.UI_BUTTON_CLICK.value();
			case "bass" -> SoundEvents.NOTE_BLOCK_BASS.value();
			case "levelup" -> SoundEvents.PLAYER_LEVELUP;
			case "firework" -> SoundEvents.FIREWORK_ROCKET_BLAST;
			case "anvil" -> SoundEvents.ANVIL_LAND;
			case "heartbeat" -> SoundEvents.NOTE_BLOCK_BASEDRUM.value();
			default -> SoundEvents.EXPERIENCE_ORB_PICKUP;
		};
	}

	@Override
	public float soundVolume() {
		return Minecraft.getInstance().options.getFinalSoundSourceVolume(SoundSource.PLAYERS);
	}

	@Override
	public boolean nameTag(Object player, dev.quartz.core.fx.NameTags.Tag t) {
		if (!(player instanceof net.minecraft.world.entity.player.Player p)) {
			return false;
		}
		net.minecraft.world.phys.Vec3 pos = p.getPosition(dev.quartz.core.fx.View.partialTicks());
		t.x = pos.x;
		t.y = pos.y + p.getBbHeight() + 0.5;
		t.z = pos.z;
		t.name = p.getDisplayName().getString();
		t.nameColour = 0xFF000000 | p.getTeamColor();
		dev.quartz.core.fx.NameTags.health(t, scoreHealth(p), p.getHealth(), p.getMaxHealth(), p.getAbsorptionAmount(), p == Minecraft.getInstance().player);
		net.minecraft.world.entity.EquipmentSlot[] slots = {net.minecraft.world.entity.EquipmentSlot.HEAD, net.minecraft.world.entity.EquipmentSlot.CHEST,
			net.minecraft.world.entity.EquipmentSlot.LEGS, net.minecraft.world.entity.EquipmentSlot.FEET};
		for (int i = 0; i < 4; i++) {
			net.minecraft.world.item.ItemStack stack = p.getItemBySlot(slots[i]);
			t.items[i] = stack.isEmpty() ? null : stack;
		}
		t.items[4] = p.getMainHandItem().isEmpty() ? null : p.getMainHandItem();
		return true;
	}

	private static final net.minecraft.world.scores.DisplaySlot[] HEALTH_SLOTS = {
		net.minecraft.world.scores.DisplaySlot.BELOW_NAME, net.minecraft.world.scores.DisplaySlot.LIST};

	/** The server's health score for a player (below the name, else in the tab list), or -1 if it shows none. */
	private static int scoreHealth(net.minecraft.world.entity.player.Player p) {
		net.minecraft.world.scores.Scoreboard board = p.level().getScoreboard();
		for (net.minecraft.world.scores.DisplaySlot slot : HEALTH_SLOTS) {
			net.minecraft.world.scores.Objective objective = board.getDisplayObjective(slot);
			if (objective != null && (objective.getCriteria() == net.minecraft.world.scores.criteria.ObjectiveCriteria.HEALTH
				|| objective.getDisplayName().getString().indexOf('❤') >= 0)) {
				net.minecraft.world.scores.ReadOnlyScoreInfo info = board.getPlayerScoreInfo(p, objective);
				if (info != null) {
					return info.value();
				}
			}
		}
		return -1;
	}

	@Override
	public void primedTnt(double range, dev.quartz.core.fx.TntTimers.Sink sink) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) {
			return;
		}
		float pt = dev.quartz.core.fx.View.partialTicks();
		double r2 = range * range;
		for (net.minecraft.world.entity.Entity e : mc.level.entitiesForRendering()) {
			if (e instanceof net.minecraft.world.entity.item.PrimedTnt tnt) {
				net.minecraft.world.phys.Vec3 p = tnt.getPosition(pt);
				double dx = p.x - dev.quartz.core.fx.View.camX();
				double dy = p.y - dev.quartz.core.fx.View.camY();
				double dz = p.z - dev.quartz.core.fx.View.camZ();
				if (dx * dx + dy * dy + dz * dz <= r2) {
					sink.tnt(p.x, p.y + 1.3, p.z, tnt.getFuse() - pt);
				}
			}
		}
	}

	@Override
	public boolean skyVisible() {
		Minecraft mc = Minecraft.getInstance();
		return mc.level != null && mc.level.canSeeSky(net.minecraft.core.BlockPos.containing(
			dev.quartz.core.fx.View.camX(), dev.quartz.core.fx.View.camY(), dev.quartz.core.fx.View.camZ()));
	}

	@Override
	public Object selfTagEntity() {
		Minecraft mc = Minecraft.getInstance();
		return mc.player != null && !mc.options.getCameraType().isFirstPerson() && !mc.player.isDiscrete() ? mc.player : null;
	}

	@Override
	public void applyMaxFps(boolean on, java.util.Map<String, String> restore) {
		Options o = Minecraft.getInstance().options;
		if (on) {
			restore.put("vsync", String.valueOf(o.enableVsync().get()));
			restore.put("framerate", String.valueOf(o.framerateLimit().get()));
			restore.put("shadows", String.valueOf(o.entityShadows().get()));
			restore.put("clouds", o.cloudStatus().get().name());
			restore.put("ao", String.valueOf(o.ambientOcclusion().get()));
			restore.put("biomeBlend", String.valueOf(o.biomeBlendRadius().get()));
			// Each set() runs vanilla's own update (window vsync, chunk rebuild for AO and blending).
			o.enableVsync().set(false);
			o.framerateLimit().set(Options.UNLIMITED_FRAMERATE_CUTOFF);
			o.entityShadows().set(false);
			o.cloudStatus().set(CloudStatus.OFF);
			o.ambientOcclusion().set(false);
			o.biomeBlendRadius().set(0);
		} else if (!restore.isEmpty()) {
			o.enableVsync().set(Boolean.parseBoolean(restore.getOrDefault("vsync", "true")));
			o.framerateLimit().set(Integer.parseInt(restore.getOrDefault("framerate", "120")));
			o.entityShadows().set(Boolean.parseBoolean(restore.getOrDefault("shadows", "true")));
			o.cloudStatus().set(CloudStatus.valueOf(restore.getOrDefault("clouds", "FANCY")));
			o.ambientOcclusion().set(Boolean.parseBoolean(restore.getOrDefault("ao", "true")));
			o.biomeBlendRadius().set(Integer.parseInt(restore.getOrDefault("biomeBlend", "2")));
			restore.clear();
		}
		o.save();
	}

	@Override
	public void playSound(String name, float volume, float pitch) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) {
			return;
		}
		float p = "xp".equals(name) ? pitch * 0.8f : pitch;
		mc.getSoundManager().play(SimpleSoundInstance.forUI(sound(name), p, volume));
	}

	@Override
	public float healthFraction() {
		Player p = Minecraft.getInstance().player;
		return p == null ? -1f : p.getHealth() / Math.max(1f, p.getMaxHealth());
	}

	@Override
	public void openMenu() {
		Minecraft.getInstance().gui.setScreen(new MenuScreen());
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
