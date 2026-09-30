package dev.quartz.client.mixin;

import dev.quartz.core.Safe;
import dev.quartz.core.accounts.OfflineGuard;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.TransferState;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Offline accounts are for singleplayer and LAN: joining anything else stops here. */
@Mixin(ConnectScreen.class)
public abstract class ConnectScreenMixin {
	@Inject(method = "startConnecting", at = @At("HEAD"), cancellable = true)
	private static void quartz$offlineGuard(Screen parent, Minecraft minecraft, ServerAddress address, ServerData data, boolean quickPlay,
			@Nullable TransferState transfer, CallbackInfo ci) {
		String reason = Safe.call("offline.guard", () -> OfflineGuard.check(address.getHost()), null);
		if (reason != null) {
			minecraft.gui.setScreen(new DisconnectedScreen(parent, Component.literal("Can't join this server"), Component.literal(reason)));
			ci.cancel();
		}
	}
}
