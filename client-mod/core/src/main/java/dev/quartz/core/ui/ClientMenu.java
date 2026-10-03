package dev.quartz.core.ui;

import dev.quartz.core.Quartz;
import dev.quartz.core.RenderBackend;
import dev.quartz.core.Safe;
import dev.quartz.core.VersionAdapter;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The Doohickey menu (Right Shift), drawn entirely through {@link RenderBackend}
 * so every Minecraft version gets the same look. Each version's screen only
 * forwards input: {@link #render}, {@link #click}, {@link #scroll} and the key
 * methods. Immediate-mode: every frame draws the menu and records what's
 * clickable, and clicks are matched against the last frame.
 */
public final class ClientMenu {
	/** What the menu needs from the version's screen. */
	public interface Host {
		void close();

		void openHudEditor();
	}

	// Palette: the launcher's night sky, violet into teal.
	private static final int ACCENT = 0xFF8B7CF6;
	private static final int TEAL = 0xFF46E0D3;
	private static final int PANEL = 0xF20D0C15;
	private static final int SIDEBAR = 0xF2121020;
	private static final int CARD = 0xFF181624;
	private static final int CARD_HOVER = 0xFF211E33;
	private static final int BORDER = 0xFF2A2740;
	private static final int TEXT = 0xFFF1EFFF;
	private static final int MUTED = 0xFF9A95B4;
	private static final int FAINT = 0xFF5C5878;
	private static final int SWITCH_OFF = 0xFF34304A;

	private static final int SIDE_W = 96;
	private static final int CARD_H = 32;
	private static final int GAP = 6;

	private enum Category {
		HUD("HUD", "Elements on your screen", HUD_ICON),
		WORLD("World", "Sky, fog, weather and light", WORLD_ICON),
		GAME("Game", "PvP, zoom and performance", GAME_ICON);

		final String title;
		final String subtitle;
		final String[] icon;

		Category(String title, String subtitle, String[] icon) {
			this.title = title;
			this.subtitle = subtitle;
			this.icon = icon;
		}

		List<Option> options() {
			switch (this) {
				case WORLD: return WorldOptions.all();
				case GAME: return GameOptions.all();
				default: return HudOptions.all();
			}
		}
	}

	// 8x8 pixel icons: '#' is a pixel. Drawn 1:1 in GUI pixels, so they stay as blocky as the game.
	private static final String[] HUD_ICON = {
		"########",
		"#......#",
		"#.##.#.#",
		"#......#",
		"#.###..#",
		"#......#",
		"########",
		"..####..",
	};
	private static final String[] WORLD_ICON = {
		"..####..",
		".#.##.#.",
		"#..##..#",
		"########",
		"#..##..#",
		"#..##..#",
		".#.##.#.",
		"..####..",
	};
	private static final String[] GAME_ICON = {
		"......##",
		".....###",
		"....###.",
		"#..###..",
		".####...",
		"..##....",
		".#.##...",
		"#...#...",
	};
	private static final String[] SEARCH_ICON = {
		".###...",
		"#...#..",
		"#...#..",
		"#...#..",
		".###...",
		"....##.",
		".....##",
	};

	/** Remembered while the game runs, so reopening lands where you left off. */
	private static Category category = Category.HUD;
	private static final Map<Category, Integer> SCROLL = new EnumMap<>(Category.class);

	private final Host host;
	private final long openedAt = System.currentTimeMillis();
	private final List<Hit> hits = new ArrayList<>();
	/** Each switch's knob position (0 = off, 1 = on), eased towards its value. */
	private final Map<String, Float> knobs = new HashMap<>();
	private String query = "";
	private boolean searchFocused;
	private long lastFrame = System.nanoTime();
	private int maxScroll;

	public ClientMenu(Host host) {
		this.host = host;
	}

	private static final class Hit {
		final int x0;
		final int y0;
		final int x1;
		final int y1;
		final Runnable action;

		Hit(int x0, int y0, int x1, int y1, Runnable action) {
			this.x0 = x0;
			this.y0 = y0;
			this.x1 = x1;
			this.y1 = y1;
			this.action = action;
		}

		boolean contains(int x, int y) {
			return x >= x0 && x < x1 && y >= y0 && y < y1;
		}
	}

	// ---- Input -------------------------------------------------------------

	/** Left click at GUI coordinates; true if something took it. */
	public boolean click(int x, int y) {
		for (int i = hits.size() - 1; i >= 0; i--) {
			if (hits.get(i).contains(x, y)) {
				Safe.run("menu.click", hits.get(i).action);
				return true;
			}
		}
		return false;
	}

	/** Mouse wheel: positive scrolls down a row. */
	public void scroll(int rows) {
		int s = SCROLL.getOrDefault(category, 0) + rows;
		SCROLL.put(category, Math.max(0, Math.min(maxScroll, s)));
	}

	/** A printable character: goes to the search box (and focuses it). */
	public void typed(char c) {
		if (c < 32 || c == 127 || query.length() >= 24) {
			return;
		}
		searchFocused = true;
		query += c;
	}

	public void backspace() {
		if (!query.isEmpty()) {
			query = query.substring(0, query.length() - 1);
		}
	}

	/** Escape: clears the search first; returns false when the screen should close. */
	public boolean escape() {
		if (searchFocused || !query.isEmpty()) {
			query = "";
			searchFocused = false;
			return true;
		}
		return false;
	}

	// ---- Drawing -----------------------------------------------------------

	public void render(RenderBackend r, int mouseX, int mouseY) {
		hits.clear();
		long now = System.nanoTime();
		float dt = Math.min(0.1f, (now - lastFrame) / 1e9f);
		lastFrame = now;
		int sw = r.screenWidth();
		int sh = r.screenHeight();

		// Opening: a quick slide up into place.
		float t = Math.min(1f, (System.currentTimeMillis() - openedAt) / 180f);
		float ease = 1f - (1f - t) * (1f - t) * (1f - t);

		backdrop(r, sw, sh, ease);

		int pw = Math.min(460, sw - 16);
		int ph = Math.min(262, sh - 16);
		int px = (sw - pw) / 2;
		int py = (sh - ph) / 2 + Math.round((1f - ease) * 10);

		// Clicking outside the panel closes it.
		hits.add(new Hit(0, 0, sw, sh, host::close));
		hits.add(new Hit(px, py, px + pw, py + ph, () -> searchFocused = false));

		round(r, px - 1, py - 1, px + pw + 1, py + ph + 1, BORDER);
		round(r, px, py, px + pw, py + ph, PANEL);
		gradient(r, px + 2, py, px + pw - 2, py + 2, ACCENT, TEAL);

		sidebar(r, px, py, ph, mouseX, mouseY);
		content(r, px + SIDE_W + 12, py, pw - SIDE_W - 24, ph, mouseX, mouseY, dt);
	}

	/** Dimmed world with a few twinkling pixel stars and the square moon. */
	private static void backdrop(RenderBackend r, int sw, int sh, float ease) {
		r.fill(0, 0, sw, sh, ((int) (0xB0 * ease) << 24) | 0x05040C);
		long ms = System.currentTimeMillis();
		int seed = 0x5EED;
		for (int i = 0; i < 46; i++) {
			seed = seed * 1103515245 + 12345;
			int x = Math.floorMod(seed >> 8, Math.max(1, sw));
			seed = seed * 1103515245 + 12345;
			int y = Math.floorMod(seed >> 8, Math.max(1, sh));
			double twinkle = 0.45 + 0.4 * Math.sin(ms / 700.0 + i * 1.7);
			int alpha = (int) (twinkle * 200 * ease);
			r.fill(x, y, x + 1, y + 1, (alpha << 24) | 0xFFFFFF);
		}
		int mx = sw - 46;
		int my = 18;
		r.fill(mx - 6, my - 6, mx + 22, my + 22, ((int) (0x18 * ease) << 24) | 0x9D8CFF);
		r.fill(mx, my, mx + 16, my + 16, ((int) (0xE0 * ease) << 24) | 0xF4F1FF);
		r.fill(mx + 3, my + 4, mx + 7, my + 8, ((int) (0xE0 * ease) << 24) | 0xD9D3F2);
		r.fill(mx + 10, my + 9, mx + 13, my + 12, ((int) (0xE0 * ease) << 24) | 0xD9D3F2);
	}

	private void sidebar(RenderBackend r, int px, int py, int ph, int mouseX, int mouseY) {
		r.fill(px + 1, py + 2, px + SIDE_W, py + ph - 1, SIDEBAR);
		r.fill(px + SIDE_W, py + 2, px + SIDE_W + 1, py + ph - 1, BORDER);

		cube(r, px + 10, py + 12);
		wordmark(r, px + 28, py + 13);
		r.text("CLIENT", px + 28, py + 23, FAINT, false);

		int y = py + 44;
		for (Category c : Category.values()) {
			boolean on = c == category && query.isEmpty();
			boolean hot = inside(mouseX, mouseY, px + 6, y, px + SIDE_W - 6, y + 20);
			if (on) {
				round(r, px + 6, y, px + SIDE_W - 6, y + 20, 0x408B7CF6);
				r.fill(px + 6, y + 4, px + 8, y + 16, ACCENT);
			} else if (hot) {
				round(r, px + 6, y, px + SIDE_W - 6, y + 20, 0x14FFFFFF);
			}
			icon(r, c.icon, px + 14, y + 6, on ? TEXT : hot ? MUTED : FAINT);
			r.text(c.title, px + 28, y + 6, on ? TEXT : hot ? TEXT : MUTED, false);
			final Category pick = c;
			hits.add(new Hit(px + 6, y, px + SIDE_W - 6, y + 20, () -> {
				category = pick;
				query = "";
				searchFocused = false;
			}));
			y += 24;
		}

		// Who's playing, at the bottom of the sidebar.
		VersionAdapter a = Quartz.adapter();
		String name = Safe.call("menu.name", a::sessionName, "Player");
		boolean offline = Safe.call("menu.offline", a::sessionOffline, false);
		int ay = py + ph - 26;
		r.fill(px + 8, ay, px + SIDE_W - 8, ay + 1, BORDER);
		int avatar = 0xFF000000 | (Math.abs(name.hashCode()) & 0x7F7F7F) | 0x404040;
		r.fill(px + 10, ay + 7, px + 22, ay + 19, avatar);
		r.text(name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase(Locale.ROOT), px + 13, ay + 9, TEXT, true);
		r.text(trim(r, name, SIDE_W - 36), px + 27, ay + 6, TEXT, false);
		r.text(offline ? "Offline" : "Online", px + 27, ay + 15, offline ? 0xFFF5A524 : 0xFF3DD68C, false);
	}

	private void content(RenderBackend r, int x, int py, int w, int ph, int mouseX, int mouseY, float dt) {
		boolean searching = !query.isEmpty();
		List<Option> options = searching ? search() : category.options();

		// Header: the category in large type, then the search box.
		r.push();
		r.translate(x, py + 12);
		r.scale(1.5f);
		r.text(searching ? "Search" : category.title, 0, 0, TEXT, false);
		r.pop();
		String sub = searching ? options.size() + " matching \"" + query + "\"" : category.subtitle;
		r.text(trim(r, sub, w - 130), x, py + 26, FAINT, false);
		searchBox(r, x + w - 112, py + 12, mouseX, mouseY);

		// The grid, a row at a time so nothing spills past the panel.
		int top = py + 40;
		int bottom = py + ph - 30;
		int cols = w >= 300 ? 3 : 2;
		int cardW = (w - GAP * (cols - 1)) / cols;
		int rows = (options.size() + cols - 1) / cols;
		int visible = Math.max(1, (bottom - top + GAP) / (CARD_H + GAP));
		maxScroll = Math.max(0, rows - visible);
		int scroll = Math.min(SCROLL.getOrDefault(category, 0), maxScroll);
		SCROLL.put(category, scroll);

		if (options.isEmpty()) {
			r.centeredText(searching ? "Nothing matches that." : "Nothing here on this version.", x + w / 2, top + 30, FAINT, false);
		}
		for (int i = scroll * cols; i < options.size() && i < (scroll + visible) * cols; i++) {
			int col = i % cols;
			int row = i / cols - scroll;
			card(r, options.get(i), x + col * (cardW + GAP), top + row * (CARD_H + GAP), cardW, mouseX, mouseY, dt);
		}
		if (maxScroll > 0) {
			int track = bottom - top - GAP;
			int thumb = Math.max(12, track * visible / rows);
			int ty = top + (track - thumb) * scroll / maxScroll;
			r.fill(x + w + 4, top, x + w + 6, top + track, 0x20FFFFFF);
			r.fill(x + w + 4, ty, x + w + 6, ty + thumb, ACCENT);
		}

		// Footer.
		int fy = py + ph - 24;
		if (!searching && category == Category.HUD) {
			String label = "Edit HUD layout";
			int bw = r.textWidth(label) + 20;
			boolean hot = inside(mouseX, mouseY, x, fy, x + bw, fy + 16);
			round(r, x, fy, x + bw, fy + 16, hot ? 0xFF9D8FFF : ACCENT);
			r.text(label, x + 10, fy + 4, 0xFFFFFFFF, false);
			hits.add(new Hit(x, fy, x + bw, fy + 16, host::openHudEditor));
		}
		String hint = maxScroll > 0 ? "Scroll for more  ·  Right Shift to close" : "Right Shift to close";
		r.text(hint, x + w - r.textWidth(hint), fy + 4, FAINT, false);
	}

	private void searchBox(RenderBackend r, int x, int y, int mouseX, int mouseY) {
		int w = 112;
		boolean hot = inside(mouseX, mouseY, x, y, x + w, y + 16);
		round(r, x - 1, y - 1, x + w + 1, y + 17, searchFocused ? ACCENT : hot ? 0xFF3A3654 : BORDER);
		round(r, x, y, x + w, y + 16, CARD);
		icon(r, SEARCH_ICON, x + 6, y + 4, searchFocused ? ACCENT : FAINT);
		if (query.isEmpty() && !searchFocused) {
			r.text("Search modules", x + 17, y + 4, FAINT, false);
		} else {
			String shown = query;
			while (r.textWidth(shown) > w - 26 && !shown.isEmpty()) {
				shown = shown.substring(1);
			}
			int end = r.text(shown, x + 17, y + 4, TEXT, false);
			if (searchFocused && System.currentTimeMillis() / 500 % 2 == 0) {
				r.fill(end + 1, y + 3, end + 2, y + 13, TEXT);
			}
		}
		hits.add(new Hit(x, y, x + w, y + 16, () -> searchFocused = true));
	}

	private void card(RenderBackend r, Option o, int x, int y, int w, int mouseX, int mouseY, float dt) {
		boolean hot = inside(mouseX, mouseY, x, y, x + w, y + CARD_H);
		Boolean on = o.on();
		round(r, x, y, x + w, y + CARD_H, hot ? CARD_HOVER : CARD);
		if (Boolean.TRUE.equals(on)) {
			r.fill(x, y + 6, x + 2, y + CARD_H - 6, ACCENT);
		}
		r.text(trim(r, o.label, w - (on != null ? 38 : 14)), x + 8, y + 7, TEXT, false);

		if (on != null) {
			r.text(on ? "Enabled" : "Disabled", x + 8, y + 18, on ? 0xFFB9AEFF : FAINT, false);
			float knob = knobs.getOrDefault(o.label, on ? 1f : 0f);
			knob += ((on ? 1f : 0f) - knob) * Math.min(1f, dt * 18f);
			knobs.put(o.label, knob);
			int sx = x + w - 26;
			int sy = y + 12;
			round(r, sx, sy, sx + 18, sy + 9, mix(SWITCH_OFF, ACCENT, knob));
			int kx = sx + 1 + Math.round(knob * 9);
			r.fill(kx, sy + 1, kx + 7, sy + 8, 0xFFFFFFFF);
		} else {
			String value = o.value();
			r.text(trim(r, value, w - 24), x + 8, y + 18, 0xFFB9AEFF, false);
			r.text(">", x + w - 12, y + 12, hot ? TEXT : FAINT, false);
		}
		hits.add(new Hit(x, y, x + w, y + CARD_H, o::click));
	}

	/** Every option whose name contains the query, across all categories. */
	private List<Option> search() {
		String q = query.toLowerCase(Locale.ROOT).trim();
		List<Option> out = new ArrayList<>();
		for (Category c : Category.values()) {
			for (Option o : c.options()) {
				if (o.label.toLowerCase(Locale.ROOT).contains(q)) {
					out.add(o);
				}
			}
		}
		return out;
	}

	// ---- Small drawing helpers ------------------------------------------------

	private static boolean inside(int mx, int my, int x0, int y0, int x1, int y1) {
		return mx >= x0 && mx < x1 && my >= y0 && my < y1;
	}

	/** A rectangle with its four corner pixels cut: reads as rounded at GUI scale. */
	private static void round(RenderBackend r, int x0, int y0, int x1, int y1, int argb) {
		if ((argb >>> 24) == 0 || x1 - x0 < 3 || y1 - y0 < 3) {
			if ((argb >>> 24) != 0) {
				r.fill(x0, y0, x1, y1, argb);
			}
			return;
		}
		r.fill(x0 + 1, y0, x1 - 1, y0 + 1, argb);
		r.fill(x0, y0 + 1, x1, y1 - 1, argb);
		r.fill(x0 + 1, y1 - 1, x1 - 1, y1, argb);
	}

	/** A left-to-right colour ramp, in 2 px steps. */
	private static void gradient(RenderBackend r, int x0, int y0, int x1, int y1, int from, int to) {
		int w = Math.max(1, x1 - x0);
		for (int x = x0; x < x1; x += 2) {
			r.fill(x, y0, Math.min(x + 2, x1), y1, mix(from, to, (x - x0) / (float) w));
		}
	}

	private static int mix(int a, int b, float t) {
		t = Math.max(0f, Math.min(1f, t));
		int out = 0;
		for (int shift = 0; shift <= 24; shift += 8) {
			int ca = (a >>> shift) & 0xFF;
			int cb = (b >>> shift) & 0xFF;
			out |= Math.round(ca + (cb - ca) * t) << shift;
		}
		return out;
	}

	private static void icon(RenderBackend r, String[] rows, int x, int y, int color) {
		for (int row = 0; row < rows.length; row++) {
			String line = rows[row];
			int start = -1;
			for (int col = 0; col <= line.length(); col++) {
				boolean pixel = col < line.length() && line.charAt(col) == '#';
				if (pixel && start < 0) {
					start = col;
				} else if (!pixel && start >= 0) {
					r.fill(x + start, y + row, x + col, y + row + 1, color);
					start = -1;
				}
			}
		}
	}

	/** The Doohickey cube (14 x 14): two shaded sides, then the lit top face over them. */
	private static void cube(RenderBackend r, int x, int y) {
		int top = 0xFFC3BAFF;
		int left = 0xFF7263E6;
		int right = 0xFF4F42B8;
		r.fill(x, y + 3, x + 7, y + 11, left);
		r.fill(x + 7, y + 3, x + 14, y + 11, right);
		for (int row = 11; row < 14; row++) {
			int cut = 2 * (row - 10);
			r.fill(x + cut, y + row, x + 7, y + row + 1, left);
			r.fill(x + 7, y + row, x + 14 - cut, y + row + 1, right);
		}
		for (int row = 0; row < 7; row++) {
			int half = 1 + 2 * Math.min(row, 6 - row);
			r.fill(x + 7 - half, y + row, x + 7 + half, y + row + 1, top);
		}
	}

	/** "DOOHICKEY", each letter a step from violet to teal. */
	private static void wordmark(RenderBackend r, int x, int y) {
		String word = "DOOHICKEY";
		for (int i = 0; i < word.length(); i++) {
			String ch = word.substring(i, i + 1);
			x = r.text(ch, x, y, mix(0xFFC3BAFF, TEAL, i / (float) (word.length() - 1)), true) + 1;
		}
	}

	private static String trim(RenderBackend r, String s, int max) {
		if (r.textWidth(s) <= max) {
			return s;
		}
		while (!s.isEmpty() && r.textWidth(s + "..") > max) {
			s = s.substring(0, s.length() - 1);
		}
		return s + "..";
	}
}
