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
	private static final Map<Category, Scroll> SCROLL = new EnumMap<>(Category.class);

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
	private final Scroll settingsScroll = new Scroll();
	private final Scroll searchScroll = new Scroll();
	private Module hovered;
	private long hoveredSince;
	private long lastFrame = System.nanoTime();
	private float dt;
	/** The list being drawn this frame (wheel target) and its scrollbar, for dragging. */
	private Scroll active;
	private float wheelStep = CARD_H + GAP;
	private Scroll dragging;
	private float dragGrab;
	private float barTop;
	private float barTrack;
	private float barThumb;
	private float pressY;
	private float clipY0 = -1e9f;
	private float clipY1 = 1e9f;
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
	private final Runnable grabBar = this::grabBar;

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
		// Inside a scrolling list, only the visible part of a row is clickable.
		y0 = Math.max(y0, clipY0);
		y1 = Math.min(y1, clipY1);
		if (hits < HIT_LIMIT && y1 > y0) {
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
		pressY = y;
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

	/** Mouse wheel, in notches (fractions from trackpads are fine): positive scrolls down. */
	public void scroll(double notches) {
		if (active != null) {
			active.by((float) notches * wheelStep);
		}
	}

	/** Mouse moved with the left button held: drags the scrollbar if it was grabbed. */
	public boolean drag(int x, int y) {
		if (dragging == null) {
			return false;
		}
		float free = Math.max(1, barTrack - barThumb);
		dragging.target = Math.max(0, Math.min(dragging.max, (y - dragGrab - barTop) / free * dragging.max));
		dragging.pos = dragging.target;
		return true;
	}

	public void release() {
		dragging = null;
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
		searchScroll.reset();
	}

	// ---- Drawing -----------------------------------------------------------

	public void render(RenderBackend r, int mouseX, int mouseY) {
		hits = 0;
		active = null;
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
		float rowH = CARD_H + GAP;
		Scroll sc = searching ? searchScroll : SCROLL.computeIfAbsent(category, c -> new Scroll());
		float off = begin(r, sc, rowH, rows * rowH - GAP, x, top, w, bottom);

		hovered = null;
		if (list.isEmpty()) {
			String empty = searching ? "Nothing matches that." : "Nothing here on this version.";
			Smooth.text(r, empty, x + (w - Smooth.width(r, empty, 8, false)) / 2, top + 40, 8, FAINT, false);
		}
		// Only the rows that show, at pixel offsets so the list glides.
		int last = Math.min(rows - 1, (int) ((off + bottom - top) / rowH));
		for (int row = (int) (off / rowH); row <= last; row++) {
			for (int col = 0; col < cols && row * cols + col < list.size(); col++) {
				card(r, list.get(row * cols + col), x + col * (cardW + GAP), top + row * rowH - off, cardW, mouseX, mouseY);
			}
		}
		end(r, sc, rows * rowH - GAP, x, top, w, bottom, mouseX, mouseY);

		float fy = py + ph - 26;
		if (!searching && category == Category.HUD) {
			button(r, "Edit HUD layout", "edit", x, fy, mouseX, mouseY, 0, openEditor);
		}
		String hint = sc.max > 0 ? "Scroll for more  ·  Right Shift to close" : "Right Shift to close";
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
				settingsScroll.reset();
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
		float rowH = ROW_H + 5;
		float off = begin(r, settingsScroll, rowH, rows.size() * rowH - 5, x, top, w, bottom);
		int last = Math.min(rows.size() - 1, (int) ((off + bottom - top) / rowH));
		for (int i = (int) (off / rowH); i <= last; i++) {
			row(r, rows.get(i), m.toggle != null && i == 0, x, top + i * rowH - off, w, mouseX, mouseY);
		}
		end(r, settingsScroll, rows.size() * rowH - 5, x, top, w, bottom, mouseX, mouseY);
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

	/** Smooth pixel scrolling: input moves the target, the view eases towards it. */
	private static final class Scroll {
		float target;
		float pos;
		float max;

		void by(float px) {
			target = Math.max(0, Math.min(max, target + px));
		}

		void limit(float m) {
			max = m;
			target = Math.min(target, m);
			pos = Math.min(pos, m);
		}

		void reset() {
			target = 0;
			pos = 0;
		}
	}

	/**
	 * Starts a scrolling list: eases the offset, snaps it to whole device
	 * pixels (so text stays sharp mid-glide) and clips drawing to the list.
	 */
	private float begin(RenderBackend r, Scroll sc, float step, float content, float x, float top, float w, float bottom) {
		sc.limit(Math.max(0, content - (bottom - top)));
		if (sc != dragging) {
			sc.pos = approach(sc.pos, sc.target, 18);
		}
		active = sc;
		wheelStep = step;
		float s = Math.max(1, r.guiScale());
		// A little room above the first row, so its glow isn't cut while at the top.
		clipY0 = top - 5;
		clipY1 = bottom;
		r.clip((int) (x - 15), (int) clipY0, (int) Math.ceil(x + w + 4), (int) Math.ceil(bottom));
		return Math.round(sc.pos * s) / s;
	}

	/** Ends a scrolling list: soft edges where more is hidden, and a scrollbar you can drag. */
	private void end(RenderBackend r, Scroll sc, float content, float x, float top, float w, float bottom, int mouseX, int mouseY) {
		r.unclip();
		clipY0 = -1e9f;
		clipY1 = 1e9f;
		if (sc.max <= 0) {
			return;
		}
		if (sc.pos > 0.5f) {
			fade(r, x - 15, top - 5, x + w + 4, top + 8, true);
		}
		if (sc.pos < sc.max - 0.5f) {
			fade(r, x - 15, bottom - 12, x + w + 4, bottom, false);
		}
		float track = bottom - top;
		float thumb = Math.max(18, track * track / content);
		float ty = top + (track - thumb) * sc.pos / sc.max;
		float bx = x + w + 6;
		boolean hot = dragging == sc || inside(mouseX, mouseY, bx - 4, top, bx + 8, bottom);
		float h = misc[3] = approach(misc[3], hot ? 1f : 0f, 14);
		float bw = 3 + h;
		Smooth.roundRect(r, bx, top, bx + bw, bottom, bw / 2, 0x14FFFFFF);
		Smooth.roundRect(r, bx, ty, bx + bw, ty + thumb, bw / 2, Smooth.mix(ACCENT, ACCENT_LIGHT, h));
		barTop = top;
		barTrack = track;
		barThumb = thumb;
		hit(bx - 4, top, bx + 8, bottom, grabBar, null);
	}

	/** Clicked the scrollbar: grab the thumb where it was clicked, or jump it under the cursor. */
	private void grabBar() {
		Scroll sc = active;
		if (sc == null || sc.max <= 0) {
			return;
		}
		float ty = barTop + (barTrack - barThumb) * sc.pos / sc.max;
		dragGrab = pressY >= ty && pressY < ty + barThumb ? pressY - ty : barThumb / 2;
		dragging = sc;
		drag(0, (int) pressY);
	}

	/** A soft edge in the panel colour over a scrolling list; {@code down} fades out downwards. */
	private static void fade(RenderBackend r, float x0, float y0, float x1, float y1, boolean down) {
		int steps = 8;
		float h = (y1 - y0) / steps;
		for (int i = 0; i < steps; i++) {
			float a = down ? 1f - i / (float) steps : (i + 1) / (float) steps;
			Smooth.rect(r, x0, y0 + i * h, x1, y0 + (i + 1) * h, ((int) (0xF0 * a * a) << 24) | (PANEL & 0xFFFFFF));
		}
	}

	/** Moves {@code v} towards {@code target}, frame-rate independent. */
	private float approach(float v, float target, float speed) {
		v += (target - v) * Math.min(1f, dt * speed);
		return Math.abs(target - v) < 0.002f ? target : v;
	}

	/** Hover test; inside a scrolling list, the clipped-off part of a row doesn't count. */
	private boolean inside(int mx, int my, float x0, float y0, float x1, float y1) {
		return mx >= x0 && mx < x1 && my >= Math.max(y0, clipY0) && my < Math.min(y1, clipY1);
	}
}
