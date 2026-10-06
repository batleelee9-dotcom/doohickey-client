package dev.quartz.client.pvp;

import com.mojang.blaze3d.platform.NativeImage;
import dev.quartz.client.adapter.PipelineBackend;
import dev.quartz.core.Safe;
import dev.quartz.core.config.ClientConfig;
import dev.quartz.core.fx.Combat;
import dev.quartz.core.fx.Effects;
import dev.quartz.core.fx.NameTags;
import dev.quartz.core.fx.Sprites;
import dev.quartz.core.fx.TntTimers;
import dev.quartz.core.fx.View;
import dev.quartz.core.fx.Weather;
import dev.quartz.core.hud.Pickups;
import dev.quartz.core.hud.ReachTracker;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/** Toggle sprint/sneak, reach measurement and the damage tint overlay. */
public final class PvpTweaks {
	private static final Identifier VIGNETTE = Identifier.fromNamespaceAndPath("quartz", "vignette");
	private static final int VIGNETTE_SIZE = 64;

	private static double lastReach = -1;
	private static boolean sneakToggled;
	private static boolean sprintHeldByUs;
	private static boolean sneakHeldByUs;
	private static boolean vignetteReady;

	private PvpTweaks() {
	}

	public static double lastReach() {
		return lastReach;
	}

	public static boolean sneakToggled() {
		return sneakToggled;
	}

	public static void tick(Minecraft mc, KeyMapping sprintKey, KeyMapping sneakKey) {
		ClientConfig c = ClientConfig.get();
		while (sprintKey.consumeClick()) {
			c.sprintToggled = !c.sprintToggled;
			c.save();
		}
		while (sneakKey.consumeClick()) {
			sneakToggled = !sneakToggled;
		}
		if (mc.player == null) {
			return;
		}
		// Holding the vanilla key for the player keeps every vanilla rule
		// (hunger, blindness, using items) in charge of whether sprinting happens.
		if (c.sprintToggled) {
			mc.options.keySprint.setDown(true);
			sprintHeldByUs = true;
		} else if (sprintHeldByUs) {
			mc.options.keySprint.setDown(false);
			sprintHeldByUs = false;
		}
		if (sneakToggled) {
			mc.options.keyShift.setDown(true);
			sneakHeldByUs = true;
		} else if (sneakHeldByUs) {
			mc.options.keyShift.setDown(false);
			sneakHeldByUs = false;
		}
	}

	/** Called when the local player attacks an entity. */
	public static void onAttack(Player player, Entity target) {
		Minecraft mc = Minecraft.getInstance();
		if (player != mc.player) {
			return;
		}
		Vec3 eye = player.getEyePosition();
		HitResult hit = mc.hitResult;
		Vec3 point = hit instanceof EntityHitResult ehr && ehr.getEntity() == target
			? hit.getLocation()
			: target.getBoundingBox().getCenter();
		lastReach = eye.distanceTo(point);
		ReachTracker.record(lastReach);
		Safe.run("effects.attack", () -> Effects.onAttack(target));
	}

	/** A red vignette when hurt or on low health. */
	private static final Matrix4f VIEW_MATRIX = new Matrix4f();
	private static final float[] VIEW = new float[16];

	/** Name tags, hit particles, combat feedback and pickups, placed with this frame's camera. Under the vanilla HUD. */
	public static void renderWorldOverlays(GuiGraphicsExtractor g, DeltaTracker delta) {
		if (Sprites.alive() == 0 && !NameTags.pending() && Weather.active() == 0 && !TntTimers.enabled() && !Combat.pending() && !Pickups.pending()) {
			return;
		}
		View.setPartialTicks(delta.getGameTimeDeltaPartialTick(false));
		Camera camera = Minecraft.getInstance().gameRenderer.mainCamera();
		camera.getViewRotationProjectionMatrix(VIEW_MATRIX).get(VIEW);
		Vec3 pos = camera.position();
		View.set(VIEW, pos.x, pos.y, pos.z);
		PipelineBackend.begin(g);
		Safe.run("weather", Weather::renderHud);
		Safe.run("nametags", NameTags::renderHud);
		Safe.run("tnt", TntTimers::renderHud);
		Safe.run("sprites", Sprites::renderHud);
		Safe.run("combat", Combat::renderHud);
		if (Pickups.pending()) {
			Safe.run("pickups", Pickups::renderHud);
		}
	}

	public static void renderDamageTint(GuiGraphicsExtractor g) {
		Minecraft mc = Minecraft.getInstance();
		Player p = mc.player;
		if (!ClientConfig.get().damageTint || p == null || mc.gui.hud.isHidden()) {
			return;
		}
		float strength = 0;
		if (p.hurtTime > 0 && p.hurtDuration > 0) {
			strength = p.hurtTime / (float) p.hurtDuration * 0.55f;
		}
		float health = p.getHealth() / Math.max(1f, p.getMaxHealth());
		if (health < 0.3f && p.isAlive()) {
			float pulse = 0.8f + 0.2f * (float) Math.sin(System.currentTimeMillis() / 180.0);
			strength = Math.max(strength, (0.3f - health) / 0.3f * 0.5f * pulse);
		}
		if (strength <= 0.01f) {
			return;
		}
		ensureVignette(mc);
		int alpha = Math.min(255, (int) (strength * 255));
		g.blit(RenderPipelines.GUI_TEXTURED, VIGNETTE, 0, 0, 0f, 0f, g.guiWidth(), g.guiHeight(),
			VIGNETTE_SIZE, VIGNETTE_SIZE, VIGNETTE_SIZE, VIGNETTE_SIZE, (alpha << 24) | 0xFF2020);
	}

	/** A white radial gradient, transparent in the middle; tinted red when drawn. */
	private static void ensureVignette(Minecraft mc) {
		if (vignetteReady) {
			return;
		}
		DynamicTexture texture = new DynamicTexture(() -> "Doohickey damage tint", VIGNETTE_SIZE, VIGNETTE_SIZE, true);
		NativeImage px = texture.getPixels();
		float half = VIGNETTE_SIZE / 2f;
		for (int y = 0; y < VIGNETTE_SIZE; y++) {
			for (int x = 0; x < VIGNETTE_SIZE; x++) {
				float dx = (x + 0.5f - half) / half;
				float dy = (y + 0.5f - half) / half;
				float d = (float) Math.sqrt(dx * dx + dy * dy);
				float a = Math.clamp((d - 0.55f) / 0.6f, 0f, 1f);
				px.setPixel(x, y, ((int) (a * a * 255) << 24) | 0xFFFFFF);
			}
		}
		texture.upload();
		mc.getTextureManager().register(VIGNETTE, texture);
		vignetteReady = true;
	}
}
