package dev.quartz.legacy;

import dev.quartz.core.Feature;
import dev.quartz.core.Quartz;
import dev.quartz.core.RenderBackend;
import dev.quartz.core.Safe;
import dev.quartz.core.config.ClientConfig;
import dev.quartz.core.hud.CpsTracker;
import dev.quartz.core.hud.Hud;
import dev.quartz.core.pvp.CrosshairStyle;
import dev.quartz.core.pvp.PvpState;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import org.lwjgl.input.Keyboard;

/** Doohickey Client on Minecraft 1.8.9 (Legacy Fabric). */
public final class QuartzLegacy implements ClientModInitializer {
	/** Mouse buttons arrive as key codes: button - 100. */
	private static final int LEFT_MOUSE = -100;
	private static final int RIGHT_MOUSE = -99;
	private static boolean sprintHeld;
	private static boolean sneakHeld;

	@Override
	public void onInitializeClient() {
		Quartz.init(new LegacyAdapter());
	}

	/** Every client tick (hooked in MinecraftClient#tick). */
	public static void tick() {
		Quartz.tick();
		holdToggledKeys(MinecraftClient.getInstance());
	}

	/**
	 * Toggle sprint/sneak hold the vanilla keys for the player, so every
	 * vanilla rule (hunger, blindness, using items) still decides.
	 */
	private static void holdToggledKeys(MinecraftClient client) {
		if (!Quartz.available(Feature.TOGGLE_SPRINT_SNEAK) || client.player == null) {
			return;
		}
		sprintHeld = hold(client.options.sprintKey, ClientConfig.get().sprintToggled, sprintHeld);
		sneakHeld = hold(client.options.sneakKey, PvpState.sneakToggled && client.currentScreen == null, sneakHeld);
	}

	private static boolean hold(KeyBinding key, boolean on, boolean wasHeld) {
		if (on) {
			KeyBinding.setKeyPressed(key.getCode(), true);
		} else if (wasHeld) {
			KeyBinding.setKeyPressed(key.getCode(), false);
		}
		return on;
	}

	/** Every key or mouse press in game, before screens see it (hooked in KeyBinding#onKeyPressed). */
	public static void onKeyPressed(int keyCode) {
		if (keyCode == LEFT_MOUSE) {
			CpsTracker.click(0);
		} else if (keyCode == RIGHT_MOUSE) {
			CpsTracker.click(1);
		}
		MinecraftClient client = MinecraftClient.getInstance();
		if (keyCode == Keyboard.KEY_RSHIFT && client.currentScreen == null && client.world != null) {
			Quartz.adapter().openMenu();
		}
	}

	/** Doohickey's crosshair is drawn instead of vanilla's (CrosshairMixin hides that one). */
	public static boolean customCrosshair() {
		return ClientConfig.get().customCrosshair && Quartz.available(Feature.CUSTOM_CROSSHAIR);
	}

	/** After the vanilla HUD (hooked in InGameHud#render). */
	public static void renderHud() {
		MinecraftClient client = MinecraftClient.getInstance();
		// F1 hides everything; F3's debug text owns the top-left corner.
		if (client.options.hudHidden || client.options.debugEnabled || client.currentScreen instanceof HudEditorLegacyScreen) {
			return;
		}
		RenderBackend r = Quartz.adapter().render();
		if (customCrosshair()) {
			Safe.run("crosshair", () -> CrosshairStyle.draw(r, r.screenWidth() / 2, r.screenHeight() / 2));
		}
		Hud.renderAll(r);
	}
}
