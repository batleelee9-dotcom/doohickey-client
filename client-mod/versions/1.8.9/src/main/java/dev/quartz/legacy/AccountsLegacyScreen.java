package dev.quartz.legacy;

import dev.quartz.core.Quartz;
import dev.quartz.core.RenderBackend;
import dev.quartz.core.accounts.AccountSwitcher;
import dev.quartz.core.accounts.LauncherBridge;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;

import java.util.List;

/** Switch between the accounts added to the Doohickey launcher without restarting (1.8.9). */
public final class AccountsLegacyScreen extends Screen {
	/** Button id the title and multiplayer screens use for their "Account" button. */
	public static final int OPEN_BUTTON = 0x51A7;
	private static final int DONE = 1000;
	private static final int MAX_SHOWN = 8;

	private final Screen parent;
	private List<LauncherBridge.Account> accounts;
	private String status = "Loading accounts…";
	private boolean busy;

	public AccountsLegacyScreen(Screen parent) {
		this.parent = parent;
		AccountSwitcher.loadAccounts((list, error) -> {
			this.accounts = list;
			this.status = error != null ? error : list.isEmpty() ? "Add accounts in the Doohickey launcher to switch here." : null;
			refresh();
		});
	}

	/** The "Account: name" button for the title and multiplayer screens. */
	public static ButtonWidget openButton() {
		return new ButtonWidget(OPEN_BUTTON, 4, 4, 140, 20, "Account: " + Quartz.adapter().sessionName());
	}

	@Override
	public void init() {
		this.buttons.clear();
		int x = this.width / 2 - 110;
		if (accounts != null) {
			String current = Quartz.adapter().sessionUuid();
			for (int i = 0; i < Math.min(MAX_SHOWN, accounts.size()); i++) {
				LauncherBridge.Account account = accounts.get(i);
				boolean active = account.id.replace("-", "").equalsIgnoreCase(current);
				String kind = "microsoft".equals(account.kind) ? "Microsoft" : "Offline - SP/LAN only";
				ButtonWidget b = new ButtonWidget(i, x, 64 + i * 24, 220, 20, (active ? "> " : "") + account.username + "  -  " + kind);
				b.active = !active && !busy && Quartz.adapter().canSwitchSession();
				this.buttons.add(b);
			}
		}
		this.buttons.add(new ButtonWidget(DONE, x, this.height - 32, 220, 20, "Done"));
	}

	private void refresh() {
		if (this.client != null) {
			init();
		}
	}

	@Override
	protected void buttonClicked(ButtonWidget button) {
		if (button.id == DONE) {
			this.client.setScreen(parent);
			return;
		}
		LauncherBridge.Account account = accounts.get(button.id);
		busy = true;
		status = "Signing in as " + account.username + "…";
		refresh();
		AccountSwitcher.switchTo(account, error -> {
			busy = false;
			status = error == null ? "Signed in as " + account.username + "." : error;
			refresh();
		});
	}

	@Override
	public void render(int mouseX, int mouseY, float tickDelta) {
		this.renderBackground();
		RenderBackend r = Quartz.adapter().render();
		r.centeredText("Accounts", this.width / 2, 20, 0xFFFFFFFF, true);
		r.centeredText("Playing as " + Quartz.adapter().sessionName(), this.width / 2, 36, 0xFFB8ADFF, true);
		String line = status != null ? status : Quartz.adapter().canSwitchSession() ? null : "Leave the world to switch accounts.";
		if (line != null) {
			r.centeredText(line, this.width / 2, this.height - 48, 0xFFCCCCCC, true);
		}
		super.render(mouseX, mouseY, tickDelta);
	}
}
