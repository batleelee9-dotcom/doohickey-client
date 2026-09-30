package dev.quartz.core.accounts;

import dev.quartz.core.Log;
import dev.quartz.core.Quartz;
import dev.quartz.core.VersionAdapter;

import java.util.List;
import java.util.function.Consumer;

/**
 * In-game account switching: fetches the account list and sessions from the
 * launcher off the game thread, then asks the version adapter to swap the
 * session on the game thread. Callbacks always run on the game thread.
 */
public final class AccountSwitcher {
	private AccountSwitcher() {
	}

	public interface Result<T> {
		void done(T value, String error);
	}

	public static void loadAccounts(Result<List<LauncherBridge.Account>> result) {
		async(() -> {
			try {
				List<LauncherBridge.Account> accounts = LauncherBridge.accounts();
				main(() -> result.done(accounts, null));
			} catch (Exception e) {
				main(() -> result.done(null, message(e)));
			}
		});
	}

	/** Switches to {@code account}; {@code result} gets null on success or a message for the player. */
	public static void switchTo(LauncherBridge.Account account, Consumer<String> result) {
		VersionAdapter adapter = Quartz.adapter();
		if (!adapter.canSwitchSession()) {
			result.accept("Leave the world first — your current session is in use.");
			return;
		}
		async(() -> {
			try {
				LauncherBridge.Session session = LauncherBridge.session(account.id);
				main(() -> {
					try {
						adapter.switchSession(session);
						Log.info("Switched account to " + session.username);
						result.accept(null);
					} catch (Exception e) {
						Log.error("Account switch failed", e);
						result.accept(message(e));
					}
				});
			} catch (Exception e) {
				main(() -> result.accept(message(e)));
			}
		});
	}

	private static String message(Exception e) {
		return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
	}

	private static void async(Runnable body) {
		Thread t = new Thread(body, "Doohickey account switcher");
		t.setDaemon(true);
		t.start();
	}

	private static void main(Runnable body) {
		Quartz.adapter().runOnMainThread(body);
	}
}
