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
	public static final String[] HEALTH_NAMES = {"HP", "Percent", "Off"};

	/** What a version fills in for one player. */
	public static final class Tag {
		/** The anchor: where vanilla puts the tag, above the head. */
		public double x;
		public double y;
		public double z;
		public String name = "";
		public int nameColour = 0xFFFFFFFF;
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
		// Vanilla's size up close, still readable far away; snapped to steps so text atlases get reused.
		float s = Math.max(0.5f, Math.min(1.25f, 0.3f * P[3] / 13f)) * c.nameTagScale / 100f;
		s = Math.max(0.375f, Math.round(s * 8) / 8f);
		float pad = 4 * s;
		float gap = 3.5f * s;
		float height = 13 * s;
		float nameSize = 8 * s;
		float hpSize = 6.5f * s;

		String hp = health(t, c.nameTagHealth);
		float nameW = Smooth.width(r, t.name, nameSize, true);
		float hpW = hp == null ? 0 : Smooth.width(r, hp, hpSize, true) + 7 * s;
		int items = 0;
		if (c.nameTagItems) {
			for (Object item : t.items) {
				if (item != null) {
					items++;
				}
			}
		}
		float itemsW = items == 0 ? 0 : items * 10 * s + (items - 1) * s;
		float total = pad * 2 + nameW + (hp == null ? 0 : gap + hpW) + (items == 0 ? 0 : gap + itemsW);

		float x = Math.round(P[0] - total / 2);
		float y = Math.round(P[1] - height);
		int alpha = Math.round(c.nameTagOpacity * 2.55f);
		Smooth.roundRect(r, x, y, x + total, y + height, 3.5f * s, alpha << 24 | 0x0B0C10);
		float mid = y + height / 2;
		int colour = c.nameTagTeamColours ? t.nameColour : 0xFFFFFFFF;
		float cx = Smooth.text(r, t.name, x + pad, mid - Smooth.lineHeight(r, nameSize, true) / 2, nameSize, colour, true);
		if (hp != null) {
			cx += gap;
			Smooth.roundRect(r, cx, y + 2.5f * s, cx + hpW, y + height - 2.5f * s, 3 * s, healthColour(t));
			Smooth.text(r, hp, cx + 3.5f * s, mid - Smooth.lineHeight(r, hpSize, true) / 2, hpSize, 0xFFFFFFFF, true);
			cx += hpW;
		}
		if (items > 0) {
			cx += gap;
			float scale = 10 * s / 16f;
			for (Object item : t.items) {
				if (item == null) {
					continue;
				}
				r.push();
				r.translate(cx, y + 1.5f * s);
				r.scale(scale);
				r.item(item, 0, 0);
				r.pop();
				cx += 11 * s;
			}
		}
	}

	private static String health(Tag t, int style) {
		if (style == 2 || t.maxHealth <= 0) {
			return null;
		}
		float total = t.health + t.absorption;
		if (style == 1) {
			int p = Math.max(0, Math.min(100, Math.round(100 * t.health / t.maxHealth)));
			String s = PERCENT[p];
			return s != null ? s : (PERCENT[p] = p + "%");
		}
		int v = Math.max(0, Math.min(1000, (int) Math.ceil(total)));
		String s = HP[v];
		return s != null ? s : (HP[v] = v + "HP");
	}

	/** Gold with absorption, else green to amber to red as health drops. */
	private static int healthColour(Tag t) {
		if (t.absorption > 0) {
			return 0xFFD99A1E;
		}
		float f = t.health / t.maxHealth;
		return f > 0.6f ? 0xFF22A35A : f > 0.3f ? 0xFFE07B24 : 0xFFD93B3B;
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
