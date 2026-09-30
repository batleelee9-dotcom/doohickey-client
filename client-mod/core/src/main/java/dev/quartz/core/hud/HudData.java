package dev.quartz.core.hud;

/** Plain data the version adapter hands the HUD, so elements never touch game classes. */
public final class HudData {
	private HudData() {
	}

	/** A worn or held item: the version's own stack (for drawing its icon) and its durability. */
	public static final class Item {
		public final Object stack;
		/** Uses left, or -1 when the item doesn't wear out. */
		public final int durability;
		public final int maxDurability;

		public Item(Object stack, int durability, int maxDurability) {
			this.stack = stack;
			this.durability = durability;
			this.maxDurability = maxDurability;
		}
	}

	/** An active potion effect, already translated. */
	public static final class Effect {
		public final String name;
		/** "1:23", or "**:**" for effects without a timer. */
		public final String time;
		public final boolean harmful;

		public Effect(String name, String time, boolean harmful) {
			this.name = name;
			this.time = time;
			this.harmful = harmful;
		}
	}
}
