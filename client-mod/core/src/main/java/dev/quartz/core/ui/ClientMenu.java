package dev.quartz.core.ui;

import dev.quartz.core.Quartz;
import dev.quartz.core.RenderBackend;
import dev.quartz.core.Safe;
import dev.quartz.core.VersionAdapter;
import dev.quartz.core.ui.Modules.Category;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The Doohickey menu (Right Shift), drawn entirely through {@link Smooth} so
 * every Minecraft version gets the same smooth look in the client's own font.
 * Each version's screen only forwards input: {@link #render}, {@link #click},
 * {@link #rightClick}, {@link #scroll} and the key methods.
 *
 * Immediate-mode: every frame draws the menu and records what's clickable.
 * Module lists are built once per open and animation state lives on the
 * modules themselves, so a frame allocates next to nothing.
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
	private static final int PANEL = 0xF50D0C14;
	private static final int SIDEBAR = 0xFF11101B;
	private static final int CARD = 0xFF17151F;
	private static final int CARD_HOVER = 0xFF1E1B2A;
	private static final int TEXT = 0xFFF2F0FA;
	private static final int MUTED = 0xFFA19CB8;
	private static final int FAINT = 0xFF67637F;
	private static final int SWITCH_OFF = 0xFF2E2B3D;

	private static final int SIDE_W = 104;
	private static final int CARD_H = 36;
	private static final int ROW_H = 26;
	private static final int GAP = 7;
	private static final int HIT_LIMIT = 256;

	/** Remembered while the game runs, so reopening lands where you left off. */
	private static Category category = Category.HUD;
	private static final Map<Category, Integer> SCROLL = new EnumMap<>(Category.class);

	private final Host host;
	private final long openedAt = System.currentTimeMillis();
	private final Map<Category, List<Module>> modules = new EnumMap<>(Category.class);
	private final float[] navHover = new float[Category.values().length];
	private final float[] misc = new float[4];
	private String query = "";
	private List<Module> results = new ArrayList<>();
	private boolean searchFocused;
	/** The module whose settings page is open, or null for the grid. */
	private Module open;
	private int settingsScroll;
	private Module hovered;
	private long hoveredSince;
	private long lastFrame = System.nanoTime();
	private float dt;
	private int maxScroll;
	private String playerName;
	private boolean playerOffline;

	// Clickable regions from the last frame: parallel arrays, reused every frame.
	private final float[] hx0 = new float[HIT_LIMIT];
	private final float[] hy0 = new float[HIT_LIMIT];
	private final float[] hx1 = new float[HIT_LIMIT];
	private final float[] hy1 = new float[HIT_LIMIT];
	private final Runnable[] hLeft = new Runnable[HIT_LIMIT];
	private final Runnable[] hRight = new Runnable[HIT_LIMIT];
	private int hits;

	private final Runnable close;
	private final Runnable unfocus;
	private final Runnable focusSearch;
	private final Runnable back;
	private final Runnable openEditor;

	public ClientMenu(Host host) {
		this.host = host;
		this.close = host::close;
		this.unfocus = () -> searchFocused = false;
		this.focusSearch = () -> searchFocused = true;
		this.back = () -> open = null;
		this.openEditor = host::openHudEditor;
	}

	private List<Module> modules(Category c) {
		List<Module> list = modules.get(c);
		if (list == null) {
			list = Modules.of(c);
			modules.put(c, list);
		}
		return list;
	}

	private void hit(float x0, float y0, float x1, float y1, Runnable left, Runnable right) {
		if (hits < HIT_LIMIT) {
			hx0[hits] = x0;
			hy0[hits] = y0;
			hx1[hits] = x1;
			hy1[hits] = y1;
			hLeft[hits] = left;
			hRight[hits] = right;
			hits++;
		}
	}

	// ---- Input -------------------------------------------------------------

	/** Left click at GUI coordinates; true if something took it. */
	public boolean click(int x, int y) {
		return press(x, y, false);
	}

	/** Right click: steps a value backwards. */
	public boolean rightClick(int x, int y) {
		return press(x, y, true);
	}

	private boolean press(int x, int y, boolean right) {
		for (int i = hits - 1; i >= 0; i--) {
			if (x >= hx0[i] && x < hx1[i] && y >= hy0[i] && y < hy1[i]) {
				Runnable action = right && hRight[i] != null ? hRight[i] : hLeft[i];
				if (action != null) {
					Safe.run("menu.click", action);
				}
				return true;
			}
		}
		return false;
	}

	/** Mouse wheel: positive scrolls down a row. */
	public void scroll(int rows) {
		if (open != null) {
			settingsScroll = Math.max(0, settingsScroll + rows);
			return;
		}
		int s = SCROLL.getOrDefault(category, 0) + rows;
		SCROLL.put(category, Math.max(0, Math.min(maxScroll, s)));
	}

	/** A printable character: goes to the search box (and focuses it). */
	public void typed(char c) {
		if (c < 32 || c == 127 || query.length() >= 24) {
			return;
		}
		searchFocused = true;
		open = null;
		query += c;
		search();
	}

	public void backspace() {
		if (!query.isEmpty()) {
			query = query.substring(0, query.length() - 1);
			search();
		}
	}

	/** Escape: closes a settings page, then clears the search; false when the screen should close. */
	public boolean escape() {
		if (open != null) {
			open = null;
			return true;
		}
		if (searchFocused || !query.isEmpty()) {
			query = "";
			searchFocused = false;
			return true;
		}
		return false;
	}

	private void search() {
		String q = query.toLowerCase(Locale.ROOT).trim();
		List<Module> out = new ArrayList<>();
		for (Category c : Category.values()) {
			for (Module m : modules(c)) {
				if (m.matches(q)) {
					out.add(m);
				}
			}
		}
		results = out;
	}

	// ---- Drawing -----------------------------------------------------------

	public void render(RenderBackend r, int mouseX, int mouseY) {
		hits = 0;
		if (open != null) {
			hovered = null;
		}
		long now = System.nanoTime();
		dt = Math.min(0.1f, (now - lastFrame) / 1e9f);
		lastFrame = now;
		int sw = r.screenWidth();
		int sh = r.screenHeight();

		float t = Math.min(1f, (System.currentTimeMillis() - openedAt) / 220f);
		float ease = 1f - (1f - t) * (1f - t) * (1f - t);

		float pw = Math.min(480, sw - 20);
		float ph = Math.min(268, sh - 20);
		float px = Math.round((sw - pw) / 2);
		float py = Math.round((sh - ph) / 2 + (1f - ease) * 12);

		backdrop(r, sw, sh, px, py, pw, ph, ease);

		// Clicking outside the panel closes it; clicking the panel itself drops search focus.
		hit(0, 0, sw, sh, close, null);
		hit(px, py, px + pw, py + ph, unfocus, null);

		Smooth.shadow(r, px, py, px + pw, py + ph, 14, 0xC0000000);
		Smooth.roundBox(r, px, py, px + pw, py + ph, 12, PANEL, 0x26FFFFFF);
		Smooth.gradient(r, px + 24, py + 1, px + pw - 24, py + 2, 0xC08B7CF6, 0xC046E0D3);

		sidebar(r, px, py, ph, mouseX, mouseY);
		float cx = px + SIDE_W + 16;
		float cw = pw - SIDE_W - 30;
		if (open != null) {
			settings(r, open, cx, py, cw, ph, mouseX, mouseY);
		} else {
			grid(r, cx, py, cw, ph, mouseX, mouseY);
		}
		tooltip(r, mouseX, mouseY, sw);
	}

	/** The world, dimmed, under two slow aurora glows in the launcher's colours. */
	private static void backdrop(RenderBackend r, int sw, int sh, float px, float py, float pw, float ph, float ease) {
		Smooth.rect(r, 0, 0, sw, sh, ((int) (0xA8 * ease) << 24) | 0x06050D);
		double s = System.currentTimeMillis() / 1000.0;
		float drift = (float) Math.sin(s * 0.35) * 18;
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

		float y = py + 46;
		if (modules(category).isEmpty()) {
			category = Category.HUD;
		}
		Category[] cats = Category.values();
		for (int i = 0; i < cats.length; i++) {
			Category c = cats[i];
			if (modules(c).isEmpty()) {
				continue;
			}
			boolean on = c == category && query.isEmpty();
			boolean hot = inside(mouseX, mouseY, px + 8, y, px + SIDE_W - 8, y + 22);
			float h = navHover[i] = approach(navHover[i], hot ? 1f : 0f, 16);
			if (on) {
				Smooth.roundRect(r, px + 8, y, px + SIDE_W - 8, y + 22, 8, 0x2E8B7CF6);
				Smooth.glow(r, px + 21, y + 11, 15, 0x508B7CF6);
			} else if (h > 0.01f) {
				Smooth.roundRect(r, px + 8, y, px + SIDE_W - 8, y + 22, 8, ((int) (0x10 * h) << 24) | 0xFFFFFF);
			}
			Smooth.icon(r, c.icon, px + 15, y + 5, 12, on ? ACCENT_LIGHT : Smooth.mix(FAINT, MUTED, h));
			Smooth.text(r, c.title, px + 33, y + 11 - Smooth.lineHeight(r, 8, on) / 2, 8, on ? TEXT : Smooth.mix(MUTED, TEXT, h), on);
			hit(px + 8, y, px + SIDE_W - 8, y + 22, nav(c), null);
			y += 25;
		}

		// Who's playing (read once per open: it can only change from the title screen).
		if (playerName == null) {
			VersionAdapter a = Quartz.adapter();
			playerName = Safe.call("menu.name", a::sessionName, "Player");
			playerOffline = Safe.test("menu.offline", a::sessionOffline, false);
		}
		String name = playerName;
		boolean offline = playerOffline;
		float ay = py + ph - 38;
		Smooth.roundRect(r, px + 8, ay, px + SIDE_W - 8, ay + 30, 9, 0x0CFFFFFF);
		int avatar = 0xFF000000 | Smooth.mix(0xFF4F42B8, 0xFF2BB8B0, (Math.abs(name.hashCode()) % 100) / 100f);
		Smooth.circle(r, px + 23, ay + 15, 8, avatar);
		String initial = name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase(Locale.ROOT);
		Smooth.text(r, initial, px + 23 - Smooth.width(r, initial, 8, true) / 2, ay + 15 - Smooth.lineHeight(r, 8, true) / 2, 8, TEXT, true);
		Smooth.text(r, Smooth.fit(r, name, 7.5f, true, SIDE_W - 52), px + 36, ay + 6, 7.5f, TEXT, true);
		Smooth.circle(r, px + 39, ay + 21, 2, offline ? 0xFFF5A524 : 0xFF3DD68C);
		Smooth.text(r, offline ? "Offline" : "Online", px + 44, ay + 17, 6.5f, MUTED, false);
	}

	private final Map<Category, Runnable> navActions = new EnumMap<>(Category.class);

	private Runnable nav(Category c) {
		Runnable action = navActions.get(c);
		if (action == null) {
			action = () -> {
				category = c;
				query = "";
				searchFocused = false;
				open = null;
			};
			navActions.put(c, action);
		}
		return action;
	}

	private void header(RenderBackend r, String title, String sub, float x, float py, float w, int mouseX, int mouseY) {
		Smooth.text(r, title, x, py + 13, 14, TEXT, true);
		Smooth.text(r, Smooth.fit(r, sub, 7.5f, false, w - 140), x, py + 32, 7.5f, FAINT, false);
		searchBox(r, x + w - 124, py + 14, 124, mouseX, mouseY);
	}

	private void grid(RenderBackend r, float x, float py, float w, float ph, int mouseX, int mouseY) {
		boolean searching = !query.isEmpty();
		List<Module> list = searching ? results : modules(category);
		String sub = searching ? list.size() + (list.size() == 1 ? " module" : " modules") + " for “" + query + "”" : category.subtitle;
		header(r, searching ? "Search" : category.title, sub, x, py, w, mouseX, mouseY);

		float top = py + 50;
		float bottom = py + ph - 34;
		// Short lists get two wide columns, so names and descriptions fit.
		int cols = w >= 300 && list.size() > 6 ? 3 : 2;
		float cardW = (w - GAP * (cols - 1)) / cols;
		int rows = (list.size() + cols - 1) / cols;
		int visible = Math.max(1, (int) ((bottom - top + GAP) / (CARD_H + GAP)));
		maxScroll = Math.max(0, rows - visible);
		int scroll = Math.min(SCROLL.getOrDefault(category, 0), maxScroll);
		SCROLL.put(category, scroll);

		hovered = null;
		if (list.isEmpty()) {
			String empty = searching ? "Nothing matches that." : "Nothing here on this version.";
			Smooth.text(r, empty, x + (w - Smooth.width(r, empty, 8, false)) / 2, top + 40, 8, FAINT, false);
		}
		for (int i = scroll * cols; i < list.size() && i < (scroll + visible) * cols; i++) {
			int col = i % cols;
			int row = i / cols - scroll;
			card(r, list.get(i), x + col * (cardW + GAP), top + row * (CARD_H + GAP), cardW, mouseX, mouseY);
		}
		if (maxScroll > 0) {
			float track = bottom - top - GAP;
			float thumb = Math.max(16, track * visible / rows);
			float ty = top + (track - thumb) * scroll / maxScroll;
			Smooth.roundRect(r, x + w + 6, top, x + w + 9, top + track, 1.5f, 0x14FFFFFF);
			Smooth.roundRect(r, x + w + 6, ty, x + w + 9, ty + thumb, 1.5f, ACCENT);
		}

		float fy = py + ph - 26;
		if (!searching && category == Category.HUD) {
			button(r, "Edit HUD layout", "edit", x, fy, mouseX, mouseY, 0, openEditor);
		}
		String hint = maxScroll > 0 ? "Scroll for more  ·  Right Shift to close" : "Right Shift to close";
		Smooth.text(r, hint, x + w - Smooth.width(r, hint, 7, false), fy + 5, 7, FAINT, false);
	}

	private void card(RenderBackend r, Module m, float x, float y, float w, int mouseX, int mouseY) {
		boolean hot = inside(mouseX, mouseY, x, y, x + w, y + CARD_H);
		if (hot) {
			if (hovered != m) {
				hoveredSince = System.currentTimeMillis();
			}
			hovered = m;
		}
		float h = m.hover = approach(m.hover, hot ? 1f : 0f, 18);
		boolean enabled = m.on();
		int border = enabled ? Smooth.mix(0x448B7CF6, 0x888B7CF6, h) : Smooth.mix(0x10FFFFFF, 0x24FFFFFF, h);
		Smooth.roundBox(r, x, y, x + w, y + CARD_H, 8, Smooth.mix(CARD, CARD_HOVER, h), border);
		if (enabled) {
			Smooth.glow(r, x + 10, y + CARD_H / 2f, 26, 0x308B7CF6);
		}

		// Right side: switch (+ gear) for modules with a switch, a value pill for single settings.
		boolean single = m.toggle == null && m.settings.size() == 1;
		float right;
		if (m.toggle != null) {
			right = 32 + (m.configurable() ? 16 : 0);
		} else if (single) {
			right = Math.min(w * 0.45f, Smooth.width(r, m.settings.get(0).value(), 7, true) + 22);
		} else {
			right = 20;
		}
		if (m.fitWidth != w) {
			m.fitName = Smooth.fit(r, m.name, 8.5f, true, w - right - 12);
			m.fitDescription = Smooth.fit(r, m.description, 6.5f, false, w - right - 12);
			m.fitWidth = w;
		}
		Smooth.text(r, m.fitName, x + 10, y + 8, 8.5f, TEXT, true);
		Smooth.text(r, m.fitDescription, x + 10, y + 20, 6.5f, FAINT, false);

		if (m.toggle != null) {
			float k = m.knob = m.knob < 0 ? (enabled ? 1f : 0f) : approach(m.knob, enabled ? 1f : 0f, 16);
			float sx = x + w - 30;
			float sy = y + CARD_H / 2f - 5.5f;
			Smooth.roundRect(r, sx, sy, sx + 21, sy + 11, 5.5f, Smooth.mix(SWITCH_OFF, ACCENT, k));
			float kx = sx + 5.5f + k * 10;
			Smooth.circle(r, kx, sy + 6.1f, 4.4f, 0x40000000);
			Smooth.circle(r, kx, sy + 5.5f, 4.2f, 0xFFFFFFFF);
			hit(x, y, x + w, y + CARD_H, m.toggle.clicker, null);
			if (m.configurable()) {
				float gx = sx - 16;
				float gy = y + CARD_H / 2f - 6;
				boolean gHot = inside(mouseX, mouseY, gx - 2, gy - 2, gx + 14, gy + 14);
				if (gHot) {
					Smooth.circle(r, gx + 6, gy + 6, 8, 0x16FFFFFF);
				}
				Smooth.icon(r, "gear", gx, gy, 12, gHot ? TEXT : Smooth.mix(FAINT, MUTED, h));
				hit(gx - 3, gy - 3, gx + 15, gy + 15, opener(m), null);
			}
		} else if (single) {
			Option o = m.settings.get(0);
			String value = o.value();
			float vw = Math.min(right - 8, Smooth.width(r, value, 7, true) + 12);
			float vx = x + w - vw - 9;
			Smooth.roundRect(r, vx, y + CARD_H / 2f - 6, vx + vw, y + CARD_H / 2f + 6, 6, 0x2A8B7CF6);
			Smooth.text(r, Smooth.fit(r, value, 7, true, vw - 10), vx + 6, y + CARD_H / 2f - Smooth.lineHeight(r, 7, true) / 2, 7, ACCENT_LIGHT, true);
			hit(x, y, x + w, y + CARD_H, o.clicker, o.backer);
		} else {
			Smooth.text(r, "→", x + w - 16, y + CARD_H / 2f - Smooth.lineHeight(r, 8, false) / 2, 8, Smooth.mix(FAINT, TEXT, h), false);
			hit(x, y, x + w, y + CARD_H, opener(m), null);
		}
	}

	private final java.util.IdentityHashMap<Module, Runnable> openers = new java.util.IdentityHashMap<>();

	private Runnable opener(Module m) {
		Runnable action = openers.get(m);
		if (action == null) {
			action = () -> {
				open = m;
				settingsScroll = 0;
			};
			openers.put(m, action);
		}
		return action;
	}

	/** A module's settings: its switch first, then each setting as a full-width row. */
	private void settings(RenderBackend r, Module m, float x, float py, float w, float ph, int mouseX, int mouseY) {
		// Back button, then the module's name.
		float bw = button(r, "Back", "back", x, py + 12, mouseX, mouseY, 1, back);
		Smooth.text(r, m.name, x + bw + 10, py + 13, 14, TEXT, true);
		Smooth.text(r, Smooth.fit(r, m.description, 7.5f, false, w - 10), x, py + 34, 7.5f, FAINT, false);

		List<Option> rows = new ArrayList<>(m.settings.size() + 1);
		if (m.toggle != null) {
			rows.add(m.toggle);
		}
		rows.addAll(m.settings);
		float top = py + 52;
		float bottom = py + ph - 30;
		int visible = Math.max(1, (int) ((bottom - top + 5) / (ROW_H + 5)));
		settingsScroll = Math.min(settingsScroll, Math.max(0, rows.size() - visible));
		for (int i = settingsScroll; i < rows.size() && i < settingsScroll + visible; i++) {
			row(r, rows.get(i), m.toggle != null && i == 0, x, top + (i - settingsScroll) * (ROW_H + 5), w, mouseX, mouseY);
		}
		String hint = "Right-click a value to go back one  ·  Esc to return";
		Smooth.text(r, hint, x + w - Smooth.width(r, hint, 7, false), py + ph - 21, 7, FAINT, false);
	}

	private void row(RenderBackend r, Option o, boolean main, float x, float y, float w, int mouseX, int mouseY) {
		boolean hot = inside(mouseX, mouseY, x, y, x + w, y + ROW_H);
		Boolean on = o.on();
		Smooth.roundBox(r, x, y, x + w, y + ROW_H, 7, hot ? CARD_HOVER : CARD, hot ? 0x24FFFFFF : 0x10FFFFFF);
		String label = main ? "Enabled" : o.label;
		Smooth.text(r, label, x + 10, y + ROW_H / 2f - Smooth.lineHeight(r, 8, true) / 2, 8, TEXT, true);
		if (on != null) {
			float sx = x + w - 31;
			float sy = y + ROW_H / 2f - 5.5f;
			Smooth.roundRect(r, sx, sy, sx + 21, sy + 11, 5.5f, on ? ACCENT : SWITCH_OFF);
			float kx = sx + 5.5f + (on ? 10 : 0);
			Smooth.circle(r, kx, sy + 5.5f, 4.2f, 0xFFFFFFFF);
			hit(x, y, x + w, y + ROW_H, o.clicker, null);
		} else {
			String value = o.value();
			float vw = Smooth.width(r, value, 7.5f, true);
			float right = x + w - 10;
			Smooth.text(r, "→", right - 8, y + ROW_H / 2f - Smooth.lineHeight(r, 8, false) / 2, 8, hot ? TEXT : FAINT, false);
			Smooth.text(r, value, right - 14 - vw, y + ROW_H / 2f - Smooth.lineHeight(r, 7.5f, true) / 2, 7.5f, ACCENT_LIGHT, true);
			Smooth.text(r, "←", right - 30 - vw, y + ROW_H / 2f - Smooth.lineHeight(r, 8, false) / 2, 8, hot ? TEXT : FAINT, false);
			// Left arrow steps back, anywhere else steps forward.
			hit(x, y, x + w, y + ROW_H, o.clicker, o.backer);
			hit(right - 36 - vw, y, right - 18 - vw, y + ROW_H, o.backer, o.backer);
		}
	}

	/** A pill button; returns its width. {@code slot} keeps its hover animation. */
	private float button(RenderBackend r, String label, String icon, float x, float y, int mouseX, int mouseY, int slot, Runnable action) {
		float bw = Smooth.width(r, label, 8, true) + 32;
		boolean hot = inside(mouseX, mouseY, x, y, x + bw, y + 18);
		float h = misc[slot] = approach(misc[slot], hot ? 1f : 0f, 14);
		boolean primary = slot == 0;
		if (primary && h > 0.01f) {
			Smooth.glow(r, x + bw / 2, y + 9, bw * 0.7f, ((int) (0x50 * h) << 24) | 0x8B7CF6);
		}
		int fill = primary ? Smooth.mix(ACCENT, 0xFF9F92FF, h) : Smooth.mix(0x14FFFFFF, 0x26FFFFFF, h);
		Smooth.roundRect(r, x, y, x + bw, y + 18, 9, fill);
		Smooth.icon(r, icon, x + 9, y + 4, 10, 0xFFFFFFFF);
		Smooth.text(r, label, x + 22, y + 9 - Smooth.lineHeight(r, 8, true) / 2, 8, 0xFFFFFFFF, true);
		hit(x, y, x + bw, y + 18, action, null);
		return bw;
	}

	private void searchBox(RenderBackend r, float x, float y, float w, int mouseX, int mouseY) {
		boolean hot = inside(mouseX, mouseY, x, y, x + w, y + 18);
		float f = misc[2] = approach(misc[2], searchFocused ? 1f : hot ? 0.5f : 0f, 14);
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
		hit(x, y, x + w, y + 18, focusSearch, null);
	}

	/** The full description of a card whose text was cut short, after a short hover. */
	private void tooltip(RenderBackend r, int mouseX, int mouseY, int sw) {
		Module m = hovered;
		if (m == null || m.description.equals(m.fitDescription) || System.currentTimeMillis() - hoveredSince < 450) {
			return;
		}
		float w = Smooth.width(r, m.description, 7, false) + 14;
		float x = Math.min(mouseX + 10, sw - w - 6);
		float y = mouseY - 22;
		Smooth.roundBox(r, x, y, x + w, y + 16, 6, 0xF51C1A28, 0x30FFFFFF);
		Smooth.text(r, m.description, x + 7, y + 8 - Smooth.lineHeight(r, 7, false) / 2, 7, TEXT, false);
	}

	/** Moves {@code v} towards {@code target}, frame-rate independent. */
	private float approach(float v, float target, float speed) {
		v += (target - v) * Math.min(1f, dt * speed);
		return Math.abs(target - v) < 0.002f ? target : v;
	}

	private static boolean inside(int mx, int my, float x0, float y0, float x1, float y1) {
		return mx >= x0 && mx < x1 && my >= y0 && my < y1;
	}
}
