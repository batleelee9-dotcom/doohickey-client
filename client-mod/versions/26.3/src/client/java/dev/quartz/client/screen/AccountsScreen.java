package dev.quartz.client.screen;

import dev.quartz.core.Quartz;
import dev.quartz.core.accounts.AccountSwitcher;
import dev.quartz.core.accounts.LauncherBridge;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Switch between the accounts added to the Doohickey launcher without
 * restarting. Opened from the title and multiplayer screens.
 */
public final class AccountsScreen extends Screen {
	private static final int MAX_SHOWN = 8;

	private final Screen parent;
	private @Nullable List<LauncherBridge.Account> accounts;
	private @Nullable String status = "Loading accounts…";
	private boolean busy;

	public AccountsScreen(Screen parent) {
		super(Component.literal("Accounts"));
		this.parent = parent;
		AccountSwitcher.loadAccounts((list, error) -> {
			this.accounts = list;
			this.status = error != null ? error : list.isEmpty() ? "Add accounts in the Doohickey launcher to switch here." : null;
			refresh();
		});
	}

	@Override
	protected void init() {
		int x = this.width / 2 - 110;
		int y = 64;
		if (accounts != null) {
			String current = Quartz.adapter().sessionUuid();
			for (LauncherBridge.Account account : accounts.subList(0, Math.min(MAX_SHOWN, accounts.size()))) {
				boolean active = account.id.replace("-", "").equalsIgnoreCase(current);
				String kind = "microsoft".equals(account.kind) ? "Microsoft" : "Offline · SP/LAN only";
				Button b = this.addRenderableWidget(Button.builder(
					Component.literal((active ? "✔ " : "") + account.username + "  ·  " + kind), btn -> switchTo(account))
					.bounds(x, y, 220, 20)
					.build());
				b.active = !active && !busy && Quartz.adapter().canSwitchSession();
				y += 24;
			}
		}
		this.addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose()).bounds(x, this.height - 32, 220, 20).build());
	}

	private void switchTo(LauncherBridge.Account account) {
		busy = true;
		status = "Signing in as " + account.username + "…";
		refresh();
		AccountSwitcher.switchTo(account, error -> {
			busy = false;
			status = error == null ? "Signed in as " + account.username + "." : error;
			refresh();
		});
	}

	/** Widgets are rebuilt once the screen is open; callbacks can arrive before that. */
	private void refresh() {
		if (this.minecraft != null) {
			this.rebuildWidgets();
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		super.extractRenderState(g, mouseX, mouseY, a);
		g.centeredText(this.font, "Accounts", this.width / 2, 20, 0xFFFFFFFF);
		g.centeredText(this.font, "Playing as " + Quartz.adapter().sessionName(), this.width / 2, 36, 0xFFB8ADFF);
		String line = status != null ? status : Quartz.adapter().canSwitchSession() ? null : "Leave the world to switch accounts.";
		if (line != null) {
			g.centeredText(this.font, line, this.width / 2, this.height - 48, 0xFFCCCCCC);
		}
	}

	@Override
	public void onClose() {
		this.minecraft.gui.setScreen(parent);
	}
}
