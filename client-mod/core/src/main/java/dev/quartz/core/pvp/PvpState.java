package dev.quartz.core.pvp;

/** Session-only PvP state (sprint's toggle is saved in client.json; sneak's isn't, on purpose). */
public final class PvpState {
	/** Sneak held for you until switched off again. Resets on restart so nobody spawns in stuck crouching. */
	public static boolean sneakToggled;

	private PvpState() {
	}
}
