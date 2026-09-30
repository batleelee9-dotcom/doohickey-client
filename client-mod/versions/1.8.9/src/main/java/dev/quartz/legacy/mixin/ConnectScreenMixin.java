package dev.quartz.legacy.mixin;

import dev.quartz.core.Safe;
import dev.quartz.core.accounts.OfflineGuard;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ConnectScreen;
import net.minecraft.client.gui.screen.DisconnectedScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.LiteralText;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Offline accounts are for singleplayer and LAN. On 1.8.9 the connection
 * starts inside ConnectScreen's constructor, before the screen is shown, so
 * the blocked join is remembered and replaced with an explanation on init.
 */
@Mixin(ConnectScreen.class)
public abstract class ConnectScreenMixin extends Screen {
	@Shadow
	@Final
	private Screen parent;

	private String quartz$blocked;

	@Inject(method = "connect", at = @At("HEAD"), cancellable = true)
	private void quartz$offlineGuard(String address, int port, CallbackInfo ci) {
		String reason = Safe.call("offline.guard", () -> OfflineGuard.check(address), null);
		if (reason != null) {
			quartz$blocked = reason;
			ci.cancel();
		}
	}

	@Inject(method = "init", at = @At("TAIL"))
	private void quartz$explain(CallbackInfo ci) {
		if (quartz$blocked != null) {
			MinecraftClient.getInstance().setScreen(new DisconnectedScreen(parent, "connect.failed", new LiteralText(quartz$blocked)));
		}
	}
}
