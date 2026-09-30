package dev.quartz.client.map;

import net.minecraft.network.chat.Component;

/**
 * Honours the chat codes servers already use to restrict minimap mods
 * (introduced by Xaero's Minimap and widely supported). A server sends them
 * as invisible formatting in a chat message; they reset on disconnect.
 */
public final class FairPlay {
	/** Disables the minimap entirely. */
	private static final String NO_MINIMAP = "§n§o§m§i§n§i§m§a§p";
	/** "Fair play": no cave maps / seeing through terrain. */
	private static final String FAIR_PLAY = "§f§a§i§r§x§a§e§r§o";

	private static boolean minimapDisabled;
	private static boolean cavesDisabled;

	private FairPlay() {
	}

	public static void onMessage(Component message) {
		String text = message.getString();
		if (text.contains(NO_MINIMAP)) {
			minimapDisabled = true;
		}
		if (text.contains(FAIR_PLAY)) {
			cavesDisabled = true;
		}
	}

	public static void reset() {
		minimapDisabled = false;
		cavesDisabled = false;
	}

	public static boolean minimapDisabled() {
		return minimapDisabled;
	}

	public static boolean cavesDisabled() {
		return cavesDisabled;
	}
}
