package dev.quartz.legacy.mixin;

import dev.quartz.core.Quartz;
import dev.quartz.core.Safe;
import dev.quartz.core.accounts.OfflineGuard;
import dev.quartz.legacy.AccountsLegacyScreen;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Adds the "Account: name" button to the title, multiplayer and pause screens. */
@Mixin({TitleScreen.class, MultiplayerScreen.class, GameMenuScreen.class})
public abstract class AccountButtonMixin extends Screen {
	@Inject(method = "init", at = @At("TAIL"))
	private void quartz$addAccountButton(CallbackInfo ci) {
		Safe.run("accounts.button", () -> this.buttons.add(AccountsLegacyScreen.openButton()));
	}

	/** Offline sessions can't join public servers; say so where servers are listed. */
	@Inject(method = "render", at = @At("TAIL"))
	private void quartz$offlineNotice(int mouseX, int mouseY, float tickDelta, CallbackInfo ci) {
		if ((Object) this instanceof MultiplayerScreen) {
			Safe.run("offline.notice", () -> {
				if (Quartz.adapter().sessionOffline()) {
					this.drawCenteredString(this.textRenderer, OfflineGuard.NOTICE, this.width / 2, 6, 0xF5A524);
				}
			});
		}
	}

	@Inject(method = "buttonClicked", at = @At("HEAD"), cancellable = true)
	private void quartz$openAccounts(ButtonWidget button, CallbackInfo ci) {
		if (button.id == AccountsLegacyScreen.OPEN_BUTTON) {
			this.client.setScreen(new AccountsLegacyScreen(this));
			ci.cancel();
		}
	}
}
