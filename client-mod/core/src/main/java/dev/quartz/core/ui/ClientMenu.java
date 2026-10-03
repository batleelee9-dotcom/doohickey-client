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
 * The Doohickey menu (Right Shift), drawn entirely through {@link Smooth} so
 * every Minecraft version gets the same smooth, anti-aliased look in the
 * client's own font. Each version's screen only forwards input:
 * {@link #render}, {@link #click}, {@link #scroll} and the key methods.
 * Immediate-mode: every frame draws the menu and records what's clickable,
 * and clicks are matched against the last frame.
 */
public final class ClientMenu {
	/** What the menu needs from the version's screen. */
	public interface Host {
		void close();

		void openHudEditor();
	}

	// Palette: the launcher's night sky, violet into teal.
	private static final int ACCENT = 0xFF8B7CF6;
	private static final int ACCENT_LIGHT = 0xFFB9AEFF;
	private static final int TEAL = 0xFF46E0D3;
	private static final int PANEL = 0xF50D0C14;
	private static final int SIDEBAR = 0xFF11101B;
	private static final int CARD = 0xFF17151F;
	private static final int CARD_HOVER = 0xFF1E1B2A;
	private static final int TEXT = 0xFFF2F0FA;
	private static final int MUTED = 0xFFA19CB8;
	private static final int FAINT = 0xFF67637F;
	private static final int SWITCH_OFF = 0xFF2E2B3D;

	private static final int SIDE_W = 104;
	private static final int CARD_H = 34;
	private static final int GAP = 7;

	private enum Category {
		HUD("HUD", "Everything drawn on your screen", "hud"),
		WORLD("World", "Sky, fog, weather and light", "world"),
		GAME("Game", "PvP, zoom and performance", "game");

		final String title;
		final String subtitle;
		final String icon;

		Category(String title, String subtitle, String icon) {
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

	/** Remembered while the game runs, so reopening lands where you left off. */
	private static Category category = Category.HUD;
	private static final Map<Category, Integer> SCROLL = new EnumMap<>(Category.class);

	private final Host host;
	private final long openedAt = System.currentTimeMillis();
	private final List<Hit> hits = new ArrayList<>();
	/** Eased 0..1 values per key: switch knobs and hover highlights. */
	private final Map<String, Float> anim = new HashMap<>();
	private String query = "";
	private boolean searchFocused;
	private long lastFrame = System.nanoTime();
	private float dt;
	private int maxScroll;

	public ClientMenu(Host host) {
		this.host = host;
	}

	private static final class Hit {
		final float x0;
		final float y0;
		final float x1;
		final float y1;
		final Runnable action;

		Hit(float x0, float y0, float x1, float y1, Runnable action) {
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
		dt = Math.min(0.1f, (now - lastFrame) / 1e9f);
		lastFrame = now;
		int sw = r.screenWidth();
		int sh = r.screenHeight();

		float t = Math.min(1f, (System.currentTimeMillis() - openedAt) / 220f);
		float ease = 1f - (1f - t) * (1f - t) * (1f - t);

		float pw = Math.min(470, sw - 20);
		float ph = Math.min(268, sh - 20);
		float px = Math.round((sw - pw) / 2);
		float py = Math.round((sh - ph) / 2 + (1f - ease) * 12);

		backdrop(r, sw, sh, px, py, pw, ph, ease);

		// Clicking outside the panel closes it; clicking the panel itself drops search focus.
		hits.add(new Hit(0, 0, sw, sh, host::close));
		hits.add(new Hit(px, py, px + pw, py + ph, () -> searchFocused = false));

		Smooth.shadow(r, px, py, px + pw, py + ph, 14, 0xC0000000);
		Smooth.roundBox(r, px, py, px + pw, py + ph, 12, PANEL, 0x26FFFFFF);
		Smooth.gradient(r, px + 24, py + 1, px + pw - 24, py + 2, 0x008B7CF6 | 0xC0000000, 0xC046E0D3);

		sidebar(r, px, py, ph, mouseX, mouseY);
		content(r, px + SIDE_W + 16, py, pw - SIDE_W - 30, ph, mouseX, mouseY);
	}

	/** The world, dimmed, under two slow aurora glows in the launcher's colours. */
	private static void backdrop(RenderBackend r, int sw, int sh, float px, float py, float pw, float ph, float ease) {
		Smooth.rect(r, 0, 0, sw, sh, ((int) (0xA8 * ease) << 24) | 0x06050D);
		double ms = System.currentTimeMillis() / 1000.0;
		float drift = (float) Math.sin(ms * 0.35) * 18;
		int a = (int) (0x70 * ease) << 24;
		Smooth.glow(r, px + 20 + drift, py + 10, 190, a | 0x8B7CF6);
		Smooth.glow(r, px + pw - 30 - drift, py + ph - 10, 170, a | 0x2BB8B0);
		Smooth.glow(r, px + pw * 0.55f, py - 30 + drift * 0.5f, 120, ((int) (0x38 * ease) << 24) | 0xC3BAFF);
	}

	private void sidebar(RenderBackend r, float px, float py, float ph, int mouseX, int mouseY) {
		Smooth.roundRect(r, px + 1, py + 1, px + SIDE_W, py + ph - 1, 11, SIDEBAR);
		Smooth.rect(r, px + SIDE_W - 12, py + 1, px + SIDE_W, py + ph - 1, SIDEBAR);
		Smooth.rect(r, px + SIDE_W, py + 12, px + SIDE_W + 1, py + ph - 12, 0x12FFFFFF);

		Smooth.logo(r, px + 12, py + 13, 18);
		Smooth.text(r, "Doohickey", px + 35, py + 12, 10, TEXT, true);
		Smooth.text(r, "CLIENT", px + 35, py + 24, 6.5f, FAINT, true);

		float y = py + 50;
		for (Category c : Category.values()) {
			boolean on = c == category && query.isEmpty();
			boolean hot = inside(mouseX, mouseY, px + 8, y, px + SIDE_W - 8, y + 24);
			float h = ease("nav:" + c, hot ? 1f : 0f, 16);
			if (on) {
				Smooth.roundRect(r, px + 8, y, px + SIDE_W - 8, y + 24, 8, 0x2E8B7CF6);
				Smooth.glow(r, px + 22, y + 12, 16, 0x508B7CF6);
			} else if (h > 0.01f) {
				Smooth.roundRect(r, px + 8, y, px + SIDE_W - 8, y + 24, 8, ((int) (0x10 * h) << 24) | 0xFFFFFF);
			}
			Smooth.icon(r, c.icon, px + 16, y + 6, 12, on ? ACCENT_LIGHT : Smooth.mix(FAINT, MUTED, h));
			Smooth.text(r, c.title, px + 34, y + 12 - Smooth.lineHeight(r, 8.5f, on) / 2, 8.5f, on ? TEXT : Smooth.mix(MUTED, TEXT, h), on);
			final Category pick = c;
			hits.add(new Hit(px + 8, y, px + SIDE_W - 8, y + 24, () -> {
				category = pick;
				query = "";
				searchFocused = false;
			}));
			y += 28;
		}

		// Who's playing.
		VersionAdapter a = Quartz.adapter();
		String name = Safe.call("menu.name", a::sessionName, "Player");
		boolean offline = Safe.call("menu.offline", a::sessionOffline, false);
		float ay = py + ph - 38;
		Smooth.roundRect(r, px + 8, ay, px + SIDE_W - 8, ay + 30, 9, 0x0CFFFFFF);
		int avatar = 0xFF000000 | Smooth.mix(0xFF4F42B8, 0xFF2BB8B0, (Math.abs(name.hashCode()) % 100) / 100f);
		Smooth.circle(r, px + 23, ay + 15, 8, avatar);
		String initial = name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase(Locale.ROOT);
		float iw = Smooth.width(r, initial, 8, true);
		Smooth.text(r, initial, px + 23 - iw / 2, ay + 15 - Smooth.lineHeight(r, 8, true) / 2, 8, TEXT, true);
		Smooth.text(r, Smooth.fit(r, name, 7.5f, true, SIDE_W - 52), px + 36, ay + 6, 7.5f, TEXT, true);
		Smooth.circle(r, px + 39, ay + 21, 2, offline ? 0xFFF5A524 : 0xFF3DD68C);
		Smooth.text(r, offline ? "Offline" : "Online", px + 44, ay + 17, 6.5f, MUTED, false);
	}

	private void content(RenderBackend r, float x, float py, float w, float ph, int mouseX, int mouseY) {
		boolean searching = !query.isEmpty();
		List<Option> options = searching ? search() : category.options();

		Smooth.text(r, searching ? "Search" : category.title, x, py + 13, 14, TEXT, true);
		String sub = searching ? options.size() + (options.size() == 1 ? " result" : " results") + " for “" + query + "”" : category.subtitle;
		Smooth.text(r, Smooth.fit(r, sub, 7.5f, false, w - 140), x, py + 32, 7.5f, FAINT, false);
		searchBox(r, x + w - 124, py + 14, 124, mouseX, mouseY);

		// The grid, a row at a time so nothing spills past the panel.
		float top = py + 50;
		float bottom = py + ph - 34;
		int cols = w >= 300 ? 3 : 2;
		float cardW = (w - GAP * (cols - 1)) / cols;
		int rows = (options.size() + cols - 1) / cols;
		int visible = Math.max(1, (int) ((bottom - top + GAP) / (CARD_H + GAP)));
		maxScroll = Math.max(0, rows - visible);
		int scroll = Math.min(SCROLL.getOrDefault(category, 0), maxScroll);
		SCROLL.put(category, scroll);

		if (options.isEmpty()) {
			String empty = searching ? "Nothing matches that." : "Nothing here on this version.";
			Smooth.text(r, empty, x + (w - Smooth.width(r, empty, 8, false)) / 2, top + 40, 8, FAINT, false);
		}
		for (int i = scroll * cols; i < options.size() && i < (scroll + visible) * cols; i++) {
			int col = i % cols;
			int row = i / cols - scroll;
			card(r, options.get(i), x + col * (cardW + GAP), top + row * (CARD_H + GAP), cardW, mouseX, mouseY);
		}
		if (maxScroll > 0) {
			float track = bottom - top - GAP;
			float thumb = Math.max(16, track * visible / rows);
			float ty = top + (track - thumb) * scroll / maxScroll;
			Smooth.roundRect(r, x + w + 6, top, x + w + 9, top + track, 1.5f, 0x14FFFFFF);
			Smooth.roundRect(r, x + w + 6, ty, x + w + 9, ty + thumb, 1.5f, ACCENT);
		}

		// Footer.
		float fy = py + ph - 26;
		if (!searching && category == Category.HUD) {
			String label = "Edit HUD layout";
			float bw = Smooth.width(r, label, 8, true) + 34;
			boolean hot = inside(mouseX, mouseY, x, fy, x + bw, fy + 18);
			float h = ease("edit", hot ? 1f : 0f, 14);
			if (h > 0.01f) {
				Smooth.glow(r, x + bw / 2, fy + 9, bw * 0.7f, ((int) (0x50 * h) << 24) | 0x8B7CF6);
			}
			Smooth.roundRect(r, x, fy, x + bw, fy + 18, 9, Smooth.mix(ACCENT, 0xFF9F92FF, h));
			Smooth.icon(r, "edit", x + 9, fy + 4, 10, 0xFFFFFFFF);
			Smooth.text(r, label, x + 23, fy + 9 - Smooth.lineHeight(r, 8, true) / 2, 8, 0xFFFFFFFF, true);
			hits.add(new Hit(x, fy, x + bw, fy + 18, host::openHudEditor));
		}
		String hint = maxScroll > 0 ? "Scroll for more  ·  Right Shift to close" : "Right Shift to close";
		Smooth.text(r, hint, x + w - Smooth.width(r, hint, 7, false), fy + 5, 7, FAINT, false);
	}

	private void searchBox(RenderBackend r, float x, float y, float w, int mouseX, int mouseY) {
		boolean hot = inside(mouseX, mouseY, x, y, x + w, y + 18);
		float f = ease("search", searchFocused ? 1f : hot ? 0.5f : 0f, 14);
		Smooth.roundBox(r, x, y, x + w, y + 18, 9, 0xFF14121D, Smooth.mix(0x1EFFFFFF, ACCENT, f));
		Smooth.icon(r, "search", x + 7, y + 4, 10, searchFocused ? ACCENT_LIGHT : FAINT);
		float ty = y + 9 - Smooth.lineHeight(r, 7.5f, false) / 2;
		if (query.isEmpty() && !searchFocused) {
			Smooth.text(r, "Search modules", x + 21, ty, 7.5f, FAINT, false);
		} else {
			String shown = query;
			while (!shown.isEmpty() && Smooth.width(r, shown, 7.5f, false) > w - 30) {
				shown = shown.substring(1);
			}
			float end = Smooth.text(r, shown, x + 21, ty, 7.5f, TEXT, false);
			if (searchFocused && System.currentTimeMillis() / 500 % 2 == 0) {
				Smooth.rect(r, end + 1, y + 4, end + 1.6f, y + 14, ACCENT_LIGHT);
			}
		}
		hits.add(new Hit(x, y, x + w, y + 18, () -> searchFocused = true));
	}

	private void card(RenderBackend r, Option o, float x, float y, float w, int mouseX, int mouseY) {
		boolean hot = inside(mouseX, mouseY, x, y, x + w, y + CARD_H);
		Boolean on = o.on();
		float h = ease("card:" + o.label, hot ? 1f : 0f, 18);
		boolean enabled = Boolean.TRUE.equals(on);
		int border = enabled ? Smooth.mix(0x448B7CF6, 0x888B7CF6, h) : Smooth.mix(0x10FFFFFF, 0x24FFFFFF, h);
		Smooth.roundBox(r, x, y, x + w, y + CARD_H, 8, Smooth.mix(CARD, CARD_HOVER, h), border);
		if (enabled) {
			Smooth.glow(r, x + 10, y + CARD_H / 2f, 26, 0x308B7CF6);
		}

		float right = on != null ? 34 : 14;
		Smooth.text(r, Smooth.fit(r, o.label, 8.5f, true, w - right - 10), x + 10, y + 7, 8.5f, TEXT, true);

		if (on != null) {
			Smooth.text(r, on ? "Enabled" : "Disabled", x + 10, y + 19, 7, on ? ACCENT_LIGHT : FAINT, false);
			float knob = ease("knob:" + o.label, on ? 1f : 0f, 16);
			float sx = x + w - 30;
			float sy = y + CARD_H / 2f - 5.5f;
			Smooth.roundRect(r, sx, sy, sx + 21, sy + 11, 5.5f, Smooth.mix(SWITCH_OFF, ACCENT, knob));
			float kx = sx + 5.5f + knob * 10;
			Smooth.circle(r, kx, sy + 5.5f + 0.6f, 4.4f, 0x40000000);
			Smooth.circle(r, kx, sy + 5.5f, 4.2f, 0xFFFFFFFF);
		} else {
			String value = o.value();
			float vw = Smooth.width(r, value, 7, true) + 12;
			float vx = x + 10;
			Smooth.roundRect(r, vx, y + 18, vx + vw, y + 28, 5, 0x2A8B7CF6);
			Smooth.text(r, value, vx + 6, y + 23 - Smooth.lineHeight(r, 7, true) / 2, 7, ACCENT_LIGHT, true);
			Smooth.text(r, "→", x + w - 16, y + CARD_H / 2f - Smooth.lineHeight(r, 8, false) / 2, 8, Smooth.mix(FAINT, TEXT, h), false);
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

	/** Moves the value stored under {@code key} towards {@code target}, frame-rate independent. */
	private float ease(String key, float target, float speed) {
		float v = anim.containsKey(key) ? anim.get(key) : target;
		v += (target - v) * Math.min(1f, dt * speed);
		if (Math.abs(target - v) < 0.002f) {
			v = target;
		}
		anim.put(key, v);
		return v;
	}

	private static boolean inside(int mx, int my, float x0, float y0, float x1, float y1) {
		return mx >= x0 && mx < x1 && my >= y0 && my < y1;
	}
}
