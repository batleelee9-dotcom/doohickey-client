package dev.quartz.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.quartz.client.adapter.ModernAdapter;
import dev.quartz.client.cosmetics.CosmeticsLayer;
import dev.quartz.client.hud.HudRenderer;
import dev.quartz.client.map.FairPlay;
import dev.quartz.client.map.Minimap;
import dev.quartz.client.map.Waypoints;
import dev.quartz.client.pvp.Crosshair;
import dev.quartz.client.pvp.PvpTweaks;
import dev.quartz.client.screen.AccountsScreen;
import dev.quartz.client.screen.MenuScreen;
import dev.quartz.core.Quartz;
import dev.quartz.core.Safe;
import dev.quartz.core.accounts.OfflineGuard;
import dev.quartz.core.config.ClientConfig;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionResult;

public final class QuartzClient implements ClientModInitializer {
	private static KeyMapping menuKey;
	private static KeyMapping sprintKey;
	private static KeyMapping sneakKey;
	private static KeyMapping waypointKey;
	private static KeyMapping zoomKey;
	private static KeyMapping fovZoomKey;

	private static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath("quartz", path);
	}

	@Override
	public void onInitializeClient() {
		Quartz.init(new ModernAdapter());
		VersionModules.register();

		KeyMapping.Category category = KeyMapping.Category.register(id("quartz"));
		menuKey = key("key.quartz.menu", InputConstants.KEY_RSHIFT, category);
		// Unbound by default so they never clash with a player's existing binds.
		sprintKey = key("key.quartz.toggle_sprint", InputConstants.UNKNOWN.getValue(), category);
		sneakKey = key("key.quartz.toggle_sneak", InputConstants.UNKNOWN.getValue(), category);
		waypointKey = key("key.quartz.waypoint", InputConstants.KEY_B, category);
		zoomKey = key("key.quartz.minimap_zoom", InputConstants.UNKNOWN.getValue(), category);
		// C, like OptiFine and most clients.
		fovZoomKey = key("key.quartz.zoom", InputConstants.KEY_C, category);

		// Damage tint sits under the vanilla HUD; our modules and waypoint
		// labels draw on top of it.
		HudElementRegistry.addFirst(id("world_overlays"), PvpTweaks::renderWorldOverlays);
		HudElementRegistry.addFirst(id("damage_tint"), (g, delta) -> PvpTweaks.renderDamageTint(g));
		HudElementRegistry.addLast(id("waypoints"), (g, delta) -> Waypoints.renderLabels(g));
		HudElementRegistry.addLast(id("hud"), (g, delta) -> HudRenderer.renderInGame(g));
		HudElementRegistry.replaceElement(VanillaHudElements.CROSSHAIR, Crosshair::wrap);

		LivingEntityRenderLayerRegistrationCallback.EVENT.register((type, renderer, helper, context) -> {
			if (renderer instanceof AvatarRenderer<?> avatar) {
				helper.register(new CosmeticsLayer(avatar));
			}
		});

		ClientTickEvents.END_CLIENT_TICK.register(QuartzClient::tick);

		// Account switching: title, multiplayer and pause screens (in a world it's view-only).
		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
			if (screen instanceof TitleScreen || screen instanceof JoinMultiplayerScreen || screen instanceof PauseScreen) {
				Safe.run("accounts.button", () -> {
					Component label = Component.literal("Account: " + Quartz.adapter().sessionName());
					int w = Math.min(160, Screens.getFont(screen).width(label) + 16);
					Screens.getWidgets(screen).add(Button.builder(label, b -> client.gui.setScreen(new AccountsScreen(screen))).bounds(4, 4, w, 20).build());
				});
				// Offline sessions can't join public servers; say so where servers are listed.
				if (screen instanceof JoinMultiplayerScreen && Quartz.adapter().sessionOffline()) {
					ScreenEvents.afterExtract(screen).register((s, g, mouseX, mouseY, delta) ->
						g.centeredText(Screens.getFont(s), OfflineGuard.NOTICE, s.width / 2, 8, 0xFFF5A524));
				}
			}
		});

		AttackEntityCallback.EVENT.register((player, level, hand, entity, hitResult) -> {
			if (level.isClientSide()) {
				PvpTweaks.onAttack(player, entity);
			}
			return InteractionResult.PASS;
		});
		ClientReceiveMessageEvents.GAME.register((message, overlay) -> FairPlay.onMessage(message));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> FairPlay.reset());
	}

	/** Held, not toggled: zoom lasts as long as the key is down. */
	public static boolean zoomHeld() {
		return fovZoomKey != null && fovZoomKey.isDown();
	}

	private static KeyMapping key(String name, int key, KeyMapping.Category category) {
		return KeyMappingHelper.registerKeyMapping(new KeyMapping(name, InputConstants.Type.KEYBOARD, key, category));
	}

	private static void tick(Minecraft mc) {
		Quartz.tick();
		while (menuKey.consumeClick()) {
			mc.gui.setScreen(new MenuScreen());
		}
		while (waypointKey.consumeClick()) {
			Waypoints.addHere(mc, null);
		}
		while (zoomKey.consumeClick()) {
			ClientConfig c = ClientConfig.get();
			c.minimapZoom = c.minimapZoom >= 4 ? 1 : c.minimapZoom * 2;
			c.save();
		}
		PvpTweaks.tick(mc, sprintKey, sneakKey);
		Waypoints.tick(mc);
		Minimap.tick(mc);
	}
}
