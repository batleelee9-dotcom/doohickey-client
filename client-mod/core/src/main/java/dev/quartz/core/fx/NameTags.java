package dev.quartz.core.fx;

import dev.quartz.core.Feature;
import dev.quartz.core.Quartz;
import dev.quartz.core.RenderBackend;
import dev.quartz.core.VersionAdapter;
import dev.quartz.core.config.ClientConfig;
import dev.quartz.core.ui.Smooth;

/**
 * Custom player name tags: a rounded tag with the name, a health badge and
 * armour and held-item icons. Each version's tag hook {@link #mark}s players
 * whose vanilla tag would show this frame (so team rules, sneaking and
 * invisibility still decide) and hides theirs; the HUD pass draws ours at
 * the same spot, placed with {@link View}.
 */
public final class NameTags {
	public static final String[] HEALTH_NAMES = {"Health", "Percent", "Off"};

	/**
	 * Picks the health to show. {@code scoreboard} is the server's health
	 * score for the player (below their name or in the tab list), or -1 if it
	 * shows none; {@code synced} is the health the server sent with the
	 * player, which many servers fake as 1.
	 */
	public static void health(Tag t, int scoreboard, float synced, float max, float absorption, boolean you) {
		if (scoreboard >= 0) {
			t.healthKnown = true;
			t.health = scoreboard;
			t.maxHealth = Math.max(20, max);
			t.absorption = 0;
		} else {
			// Your own is always real; a stranger at exactly 1 (or less) is almost always the fake.
			t.healthKnown = you || synced > 1.0f;
			t.health = synced;
			t.maxHealth = max;
			t.absorption = absorption;
		}
	}

	/** What a version fills in for one player. */
	public static final class Tag {
		/** The anchor: where vanilla puts the tag, above the head. */
		public double x;
		public double y;
		public double z;
		public String name = "";
		public int nameColour = 0xFFFFFFFF;
		/**
		 * Whether the health below is real. Many servers send every other
		 * player's health as 1 so mods can't read it; versions use the
		 * scoreboard's health when the server shows one, and otherwise
		 * leave the badge out rather than show a fake number.
		 */
		public boolean healthKnown;
		public float health;
		public float maxHealth;
		public float absorption;
		/** Helmet, chestplate, leggings, boots, main hand; null where empty. */
		public final Object[] items = new Object[5];
	}

	private static final Object[] MARKED = new Object[128];
	private static int marked;
	private static final Tag TAG = new Tag();
	private static final float[] P = new float[4];
	private static final String[] HP = new String[1001];
	private static final String[] PERCENT = new String[101];

	private NameTags() {
	}

	public static boolean enabled() {
		return ClientConfig.get().nameTags && Quartz.available(Feature.NAMETAGS);
	}

	/** Whether there's anything to draw this frame. */
	public static boolean pending() {
		return marked > 0 || enabled() && ClientConfig.get().nameTagSelf;
	}

	/** A player whose vanilla tag would show this frame; ours is drawn instead. */
	public static void mark(Object player) {
		for (int i = 0; i < marked; i++) {
			if (MARKED[i] == player) {
				return;
			}
		}
		if (marked < MARKED.length) {
			MARKED[marked++] = player;
		}
	}

	/** {@link #render} on the version's current HUD backend. */
	public static void renderHud() {
		render(Quartz.adapter().render(), Quartz.adapter());
	}

	/** Once a frame, from the HUD pass. */
	public static void render(RenderBackend r, VersionAdapter a) {
		int n = marked;
		marked = 0;
		boolean on = enabled();
		float w = r.screenWidth();
		float h = r.screenHeight();
		for (int i = 0; i < n; i++) {
			Object player = MARKED[i];
			MARKED[i] = null;
			if (on && a.nameTag(player, TAG)) {
				draw(r, TAG, w, h);
			}
		}
		if (on && ClientConfig.get().nameTagSelf) {
			Object self = a.selfTagEntity();
			if (self != null && a.nameTag(self, TAG)) {
				draw(r, TAG, w, h);
			}
		}
	}

	private static void draw(RenderBackend r, Tag t, float w, float h) {
		if (!View.project(t.x, t.y, t.z, w, h, P)) {
			return;
		}
		ClientConfig c = ClientConfig.get();
		// Vanilla's limit: no tags past 64 blocks.
		double dx = t.x - View.camX();
		double dy = t.y - View.camY();
		double dz = t.z - View.camZ();
		if (dx * dx + dy * dy + dz * dz > 64 * 64) {
			return;
		}
		// Sized in the world like vanilla's tag (about a quarter of a block tall), so it
		// shrinks with distance all the way out. Text can't be rasterised below a few
		// pixels, so a far tag is drawn at a readable size and scaled down as a whole,
		// the way vanilla scales its own.
		float size = Math.min(2f, 0.27f * P[3] / 12f * c.nameTagScale / 100f);
		float smallest = 9f / (7.5f * Math.max(1f, r.guiScale()));
		float s = (float) Math.ceil(Math.max(size, smallest) * 16) / 16f;
		float shrink = size / s;
		if (shrink < 0.999f) {
			r.push();
			r.translate(P[0], P[1]);
			r.scale(shrink);
			r.translate(-P[0], -P[1]);
		}
		float height = Math.round(12 * s);
		float pad = 6 * s;
		float gap = 4 * s;
		float nameSize = 7.5f * s;
		float hpSize = 7 * s;
		float heart = 6.5f * s;

		String hp = health(t, c.nameTagHealth);
		float nameW = Smooth.width(r, t.name, nameSize, true);
		float hpW = hp == null ? 0 : heart + 2 * s + Smooth.width(r, hp, hpSize, true);
		int items = 0;
		if (c.nameTagItems) {
			for (Object item : t.items) {
				if (item != null) {
					items++;
				}
			}
		}
		float itemsW = items == 0 ? 0 : items * 9 * s + (items - 1) * s;
		float divider = 1 + 2 * gap;
		float total = pad * 2 + nameW + (hp == null ? 0 : divider + hpW) + (items == 0 ? 0 : (hp == null ? divider : gap * 1.5f) + itemsW);

		total = Math.round(total);
		float x = Math.round(P[0] - total / 2);
		float y = Math.round(P[1] - height);
		float mid = y + height / 2;
		int alpha = Math.round(c.nameTagOpacity * 2.55f);
		// A slim pill: rounded fully at the ends.
		Smooth.roundRect(r, x, y, x + total, y + height, height * 0.45f, alpha << 24 | 0x0E0F14);
		int colour = c.nameTagTeamColours ? t.nameColour : 0xFFFFFFFF;
		float cx = Smooth.text(r, t.name, x + pad, mid - Smooth.lineHeight(r, nameSize, true) / 2, nameSize, colour, true);
		if (hp != null || items > 0) {
			cx += gap;
			Smooth.rect(r, cx, mid - 3.5f * s, cx + 1, mid + 3.5f * s, 0x38FFFFFF);
			cx += 1 + gap;
		}
		if (hp != null) {
			Smooth.icon(r, "heart", cx, mid - heart / 2, heart, healthColour(t));
			Smooth.text(r, hp, cx + heart + 2 * s, mid - Smooth.lineHeight(r, hpSize, true) / 2, hpSize, 0xFFF4F4F6, true);
			cx += hpW;
			if (items > 0) {
				cx += gap * 1.5f;
			}
		}
		if (items > 0) {
			float scale = 9 * s / 16f;
			for (Object item : t.items) {
				if (item == null) {
					continue;
				}
				r.push();
				r.translate(cx, mid - 4.5f * s);
				r.scale(scale);
				r.item(item, 0, 0);
				r.pop();
				cx += 10 * s;
			}
		}
		if (shrink < 0.999f) {
			r.pop();
		}
	}

	private static String health(Tag t, int style) {
		if (style == 2 || !t.healthKnown || t.maxHealth <= 0) {
			return null;
		}
		if (style == 1) {
			int p = Math.max(0, Math.min(100, Math.round(100 * t.health / t.maxHealth)));
			String s = PERCENT[p];
			return s != null ? s : (PERCENT[p] = p + "%");
		}
		int v = Math.max(0, Math.min(1000, (int) Math.ceil(t.health + t.absorption)));
		String s = HP[v];
		return s != null ? s : (HP[v] = Integer.toString(v));
	}

	/** Gold with absorption, else green to amber to red as health drops. */
	private static int healthColour(Tag t) {
		if (t.absorption > 0) {
			return 0xFFFACC15;
		}
		float f = t.health / t.maxHealth;
		return f > 0.6f ? 0xFF4ADE80 : f > 0.3f ? 0xFFFBBF24 : 0xFFF87171;
	}

	/** A §-formatted name without its codes. */
	public static String strip(String formatted) {
		if (formatted.indexOf('§') < 0) {
			return formatted;
		}
		StringBuilder b = new StringBuilder(formatted.length());
		for (int i = 0; i < formatted.length(); i++) {
			char ch = formatted.charAt(i);
			if (ch == '§') {
				i++;
			} else {
				b.append(ch);
			}
		}
		return b.toString();
	}

	private static final int[] FORMAT_COLOURS = {
		0x000000, 0x0000AA, 0x00AA00, 0x00AAAA, 0xAA0000, 0xAA00AA, 0xFFAA00, 0xAAAAAA,
		0x555555, 0x5555FF, 0x55FF55, 0x55FFFF, 0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF,
	};

	/** The last colour code before the name's first letter (a team or rank colour), white without one. */
	public static int colourOf(String formatted) {
		int colour = 0xFFFFFF;
		for (int i = 0; i + 1 < formatted.length(); i++) {
			if (formatted.charAt(i) != '§') {
				// Past the prefix codes: a rank in brackets keeps going, a letter means the name started.
				if (Character.isLetterOrDigit(formatted.charAt(i)) || formatted.charAt(i) == '_') {
					break;
				}
				continue;
			}
			int code = Character.digit(formatted.charAt(++i), 16);
			if (code >= 0) {
				colour = FORMAT_COLOURS[code];
			}
		}
		return 0xFF000000 | colour;
	}
}
