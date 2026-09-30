package dev.quartz.core.accounts;

import dev.quartz.core.Quartz;

import java.util.Locale;

/**
 * Offline accounts are for singleplayer and LAN only. Each version's
 * connect hook asks here before joining a server; the launcher enforces the
 * same rule for quick-join (`servers::is_lan_address`).
 */
public final class OfflineGuard {
	public static final String NOTICE = "Offline account: singleplayer and LAN only";

	private OfflineGuard() {
	}

	/** Null if joining {@code host} is fine, otherwise the reason to show the player. */
	public static String check(String host) {
		if (!Quartz.adapter().sessionOffline() || isLanAddress(host)) {
			return null;
		}
		return "Offline accounts can only join LAN servers. Use the Account button to switch to a Microsoft account to play on "
			+ host + ".";
	}

	/** Loopback, private and link-local addresses, "localhost", and .local/.lan names. Other names count as public. */
	public static boolean isLanAddress(String address) {
		String host = address.trim().toLowerCase(Locale.ROOT);
		if (host.startsWith("[")) {
			int end = host.indexOf(']');
			host = end > 0 ? host.substring(1, end) : host.substring(1);
		} else if (host.indexOf(':') >= 0 && host.indexOf(':') == host.lastIndexOf(':')) {
			host = host.substring(0, host.indexOf(':'));
		}
		if (host.equals("localhost") || host.endsWith(".local") || host.endsWith(".lan")) {
			return true;
		}
		String[] parts = host.split("\\.");
		if (parts.length == 4) {
			int[] n = new int[4];
			for (int i = 0; i < 4; i++) {
				if (!parts[i].matches("\\d{1,3}")) {
					return false;
				}
				n[i] = Integer.parseInt(parts[i]);
			}
			return n[0] == 127 || n[0] == 10 || (n[0] == 192 && n[1] == 168) || (n[0] == 172 && n[1] >= 16 && n[1] <= 31)
				|| (n[0] == 169 && n[1] == 254);
		}
		return host.equals("::1") || host.matches("f[cd][0-9a-f]{0,2}:.*") || host.matches("fe[89ab][0-9a-f]?:.*");
	}
}
