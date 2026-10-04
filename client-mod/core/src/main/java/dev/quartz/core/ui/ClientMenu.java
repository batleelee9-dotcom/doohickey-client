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

	// Palette: neutral graphite, with the launcher's violet only where it means something.
	private static final int ACCENT = 0xFF8B7CF6;
	private static final int ACCENT_LIGHT = 0xFFB9AEFF;
	private static final int PANEL = 0xFA121317;
	private static final int SIDEBAR = 0xFF0E0F12;
	private static final int CARD = 0xFF18191E;
	private static final int CARD_HOVER = 0xFF1E1F25;
	private static final int BORDER = 0x14FFFFFF;
	private static final int TEXT = 0xFFEDEEF2;
	private static final int MUTED = 0xFF9C9EAA;
	private static final int FAINT = 0xFF62646F;
	private static final int SWITCH_OFF = 0xFF2C2E36;
	private static final String[] COUNTS = new String[100];

	static {
		for (int i = 0; i < COUNTS.length; i++) {
			COUNTS[i] = Integer.toString(i);
		}
	}

	private static final int SIDE_W = 112;
	private static final int CARD_H = 38;
	private static final int ROW_H = 28;
	private static final int GAP = 7;
	private static final int HIT_LIMIT = 256;

	/** Remembered while the game runs, so reopening lands where you left off. */
	private static Category category = Category.HUD;
	private static final Map<Category, Scroll> SCROLL = new EnumMap<>(Category.class);

	private final Host host;
	private final long openedAt = System.currentTimeMillis();
	private final Map<Category, List<Module>> modules = new EnumMap<>(Category.class);
	private final float[] navHover = new float[Category.values().length];
	private final float[] misc = new float[6];
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

		float t = Math.min(1f, (System.currentTimeMillis() - openedAt) / 180f);
		float ease = 1f - (1f - t) * (1f - t) * (1f - t);

		// Grows with the screen, up to a comfortable size, so text has room.
		float pw = Math.round(Math.min(sw - 20, Math.max(460, Math.min(640, sw * 0.72f))));
		float ph = Math.round(Math.min(sh - 20, Math.max(260, Math.min(380, sh * 0.72f))));
		float px = Math.round((sw - pw) / 2);
		float py = Math.round((sh - ph) / 2 + (1f - ease) * 8);

		// A plain dim over the world: the panel is the only thing asking for attention.
		Smooth.rect(r, 0, 0, sw, sh, ((int) (0x90 * ease) << 24) | 0x050608);

		// Clicking outside the panel closes it; clicking the panel itself drops search focus.
		hit(0, 0, sw, sh, close, null);
		hit(px, py, px + pw, py + ph, unfocus, null);

		Smooth.shadow(r, px, py, px + pw, py + ph, 18, 0xB4000000);
		Smooth.roundBox(r, px, py, px + pw, py + ph, 10, PANEL, BORDER);

		sidebar(r, px, py, ph, mouseX, mouseY);
		closeButton(r, px + pw - 27, py + 11, mouseX, mouseY);
		float cx = px + SIDE_W + 18;
		float cw = pw - SIDE_W - 32;
		if (open != null) {
			settings(r, open, cx, py, cw, ph, mouseX, mouseY);
		} else {
			grid(r, cx, py, cw, ph, mouseX, mouseY);
		}
		tooltip(r, mouseX, mouseY, sw);
	}

	private void sidebar(RenderBackend r, float px, float py, float ph, int mouseX, int mouseY) {
		// The left column: square on its inner edge, a hairline between.
		Smooth.roundRect(r, px + 1, py + 1, px + SIDE_W, py + ph - 1, 9, SIDEBAR);
		Smooth.rect(r, px + SIDE_W - 10, py + 1, px + SIDE_W, py + ph - 1, SIDEBAR);
		Smooth.rect(r, px + SIDE_W, py + 1, px + SIDE_W + 1, py + ph - 1, BORDER);

		Smooth.logo(r, px + 14, py + 14, 16);
		Smooth.text(r, "Doohickey", px + 36, py + 13, 9.5f, TEXT, true);
		Smooth.text(r, "Client", px + 36, py + 24.5f, 6.5f, FAINT, false);

		Smooth.text(r, "MODULES", px + 14, py + 47, 6, FAINT, true);
		float y = py + 57;
		if (modules(category).isEmpty()) {
			category = Category.HUD;
		}
		Category[] cats = Category.values();
		for (int i = 0; i < cats.length; i++) {
			Category c = cats[i];
			List<Module> list = modules(c);
			if (list.isEmpty()) {
				continue;
			}
			boolean on = c == category && query.isEmpty();
			boolean hot = inside(mouseX, mouseY, px + 8, y, px + SIDE_W - 8, y + 21);
			float h = navHover[i] = approach(navHover[i], hot ? 1f : 0f, 16);
			if (on) {
				Smooth.roundRect(r, px + 8, y, px + SIDE_W - 8, y + 21, 5, 0x12FFFFFF);
				Smooth.roundRect(r, px + 8, y + 5, px + 10, y + 16, 1, ACCENT);
			} else if (h > 0.01f) {
				Smooth.roundRect(r, px + 8, y, px + SIDE_W - 8, y + 21, 5, ((int) (0x0A * h) << 24) | 0xFFFFFF);
			}
			Smooth.icon(r, c.icon, px + 16, y + 5, 11, on ? TEXT : Smooth.mix(FAINT, MUTED, h));
			Smooth.text(r, c.title, px + 33, y + 10.5f - Smooth.lineHeight(r, 8, on) / 2, 8, on ? TEXT : Smooth.mix(MUTED, TEXT, h), on);
			// How many of its switches are on, at a glance.
			int count = 0;
			for (int k = 0; k < list.size(); k++) {
				if (list.get(k).on()) {
					count++;
				}
			}
			if (count > 0) {
				String n = COUNTS[Math.min(count, COUNTS.length - 1)];
				float nw = Smooth.width(r, n, 6, true) + 8;
				float nx = px + SIDE_W - 13 - nw;
				Smooth.roundRect(r, nx, y + 5.5f, nx + nw, y + 15.5f, 5, on ? 0xFF2E2950 : 0xFF1C1D22);
				Smooth.text(r, n, nx + 4, y + 10.5f - Smooth.lineHeight(r, 6, true) / 2, 6, on ? ACCENT_LIGHT : FAINT, true);
			}
			hit(px + 8, y, px + SIDE_W - 8, y + 21, nav(c), null);
			y += 24;
		}

		// Who's playing (read once per open: it can only change from the title screen).
		if (playerName == null) {
			VersionAdapter a = Quartz.adapter();
			playerName = Safe.call("menu.name", a::sessionName, "Player");
			playerOffline = Safe.test("menu.offline", a::sessionOffline, false);
		}
		String name = playerName;
		boolean offline = playerOffline;
		float ay = py + ph - 36;
		Smooth.rect(r, px + 12, ay - 6, px + SIDE_W - 12, ay - 5, BORDER);
		int avatar = 0xFF000000 | Smooth.mix(0xFF4F42B8, 0xFF2BB8B0, (Math.abs(name.hashCode()) % 100) / 100f);
		Smooth.circle(r, px + 22, ay + 13, 8, avatar);
		String initial = name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase(Locale.ROOT);
		Smooth.text(r, initial, px + 22 - Smooth.width(r, initial, 8, true) / 2, ay + 13 - Smooth.lineHeight(r, 8, true) / 2, 8, TEXT, true);
		Smooth.text(r, Smooth.fit(r, name, 7.5f, true, SIDE_W - 50), px + 35, ay + 4.5f, 7.5f, TEXT, true);
		Smooth.circle(r, px + 37.5f, ay + 19.5f, 2, offline ? 0xFFF5A524 : 0xFF3DD68C);
		Smooth.text(r, offline ? "Offline" : "Online", px + 42, ay + 15.5f, 6.5f, MUTED, false);
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

	private void closeButton(RenderBackend r, float x, float y, int mouseX, int mouseY) {
		boolean hot = inside(mouseX, mouseY, x, y, x + 16, y + 16);
		float h = misc[4] = approach(misc[4], hot ? 1f : 0f, 16);
		if (h > 0.01f) {
			Smooth.roundRect(r, x, y, x + 16, y + 16, 4, ((int) (0x16 * h) << 24) | 0xFFFFFF);
		}
		Smooth.icon(r, "close", x + 3, y + 3, 10, Smooth.mix(FAINT, TEXT, h));
		hit(x, y, x + 16, y + 16, close, null);
	}

	private void header(RenderBackend r, String title, String sub, float x, float py, float w, int mouseX, int mouseY) {
		Smooth.text(r, title, x, py + 13, 13, TEXT, true);
		Smooth.text(r, Smooth.fit(r, sub, 7, false, w - 175), x, py + 30, 7, FAINT, false);
		searchBox(r, x + w - 152, py + 12, 132, mouseX, mouseY);
		Smooth.rect(r, x, py + 45, x + w, py + 46, BORDER);
	}

	private void grid(RenderBackend r, float x, float py, float w, float ph, int mouseX, int mouseY) {
		boolean searching = !query.isEmpty();
		List<Module> list = searching ? results : modules(category);
		String sub = searching ? list.size() + (list.size() == 1 ? " module" : " modules") + " for “" + query + "”" : category.subtitle;
		header(r, searching ? "Search" : category.title, sub, x, py, w, mouseX, mouseY);

		float top = py + 55;
		float bottom = py + ph - 32;
		// Three columns only when each card still fits its name; short lists get two wide ones.
		int cols = list.size() > 6 && (w - GAP * 2) / 3 >= 125 ? 3 : 2;
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

		float fy = py + ph - 25;
		if (!searching && category == Category.HUD) {
			button(r, "Edit HUD layout", "edit", x, fy, mouseX, mouseY, 0, openEditor);
		}
		float kx = keyHint(r, x + w, fy + 4, "Right Shift", "Close");
		if (sc.max > 0) {
			keyHint(r, kx - 10, fy + 4, "Scroll", "More");
		}
	}

	/** A key cap and what it does, right-aligned at {@code right}; returns where it starts. */
	private static float keyHint(RenderBackend r, float right, float y, String key, String action) {
		float aw = Smooth.width(r, action, 6.5f, false);
		float kw = Smooth.width(r, key, 6, true) + 8;
		float ax = right - aw;
		float kx = ax - 5 - kw;
		Smooth.roundBox(r, kx, y, kx + kw, y + 11, 3, 0xFF1E1F24, 0xFF2D2E35);
		Smooth.text(r, key, kx + 4, y + 5.5f - Smooth.lineHeight(r, 6, true) / 2, 6, MUTED, true);
		Smooth.text(r, action, ax, y + 5.5f - Smooth.lineHeight(r, 6.5f, false) / 2, 6.5f, FAINT, false);
		return kx;
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
		int border = enabled ? Smooth.mix(0x508B7CF6, 0x808B7CF6, h) : Smooth.mix(0x0EFFFFFF, 0x20FFFFFF, h);
		Smooth.roundBox(r, x, y, x + w, y + CARD_H, 6, Smooth.mix(CARD, CARD_HOVER, h), border);

		// Right side: switch (+ gear) for modules with a switch, a value for single settings.
		boolean single = m.toggle == null && m.settings.size() == 1;
		float right;
		if (m.toggle != null) {
			right = 30 + (m.configurable() ? 17 : 0);
		} else if (single) {
			right = Math.min(w * 0.45f, Smooth.width(r, m.settings.get(0).value(), 7, true) + 22);
		} else {
			right = 20;
		}
		if (m.fitWidth != w) {
			m.fitName = Smooth.fit(r, m.name, 8.5f, true, w - right - 14);
			m.fitDescription = Smooth.fit(r, m.description, 6.5f, false, w - right - 14);
			m.fitWidth = w;
		}
		Smooth.text(r, m.fitName, x + 10, y + 8.5f, 8.5f, TEXT, true);
		Smooth.text(r, m.fitDescription, x + 10, y + 21.5f, 6.5f, FAINT, false);

		float mid = y + CARD_H / 2f;
		if (m.toggle != null) {
			float k = m.knob = m.knob < 0 ? (enabled ? 1f : 0f) : approach(m.knob, enabled ? 1f : 0f, 16);
			toggle(r, x + w - 30, mid - 5.5f, k);
			hit(x, y, x + w, y + CARD_H, m.toggle.clicker, null);
			if (m.configurable()) {
				float gx = x + w - 47;
				float gy = mid - 6;
				boolean gHot = inside(mouseX, mouseY, gx - 2, gy - 2, gx + 14, gy + 14);
				if (gHot) {
					Smooth.roundRect(r, gx - 3, gy - 3, gx + 15, gy + 15, 4, 0x14FFFFFF);
				}
				Smooth.icon(r, "gear", gx, gy, 12, gHot ? TEXT : Smooth.mix(FAINT, MUTED, h));
				hit(gx - 3, gy - 3, gx + 15, gy + 15, opener(m), null);
			}
		} else if (single) {
			Option o = m.settings.get(0);
			String value = o.value();
			float vw = Math.min(right - 8, Smooth.width(r, value, 7, true) + 12);
			float vx = x + w - vw - 9;
			Smooth.roundRect(r, vx, mid - 6.5f, vx + vw, mid + 6.5f, 4, Smooth.mix(0xFF2A2B31, 0xFF33343B, h));
			Smooth.text(r, Smooth.fit(r, value, 7, true, vw - 10), vx + 6, mid - Smooth.lineHeight(r, 7, true) / 2, 7, TEXT, true);
			hit(x, y, x + w, y + CARD_H, o.clicker, o.backer);
		} else {
			Smooth.icon(r, "right", x + w - 18, mid - 5, 10, Smooth.mix(FAINT, TEXT, h));
			hit(x, y, x + w, y + CARD_H, opener(m), null);
		}
	}

	/** A switch: track and knob, {@code k} from 0 (off) to 1 (on). */
	private static void toggle(RenderBackend r, float sx, float sy, float k) {
		Smooth.roundRect(r, sx, sy, sx + 21, sy + 11, 5.5f, Smooth.mix(SWITCH_OFF, ACCENT, k));
		float kx = sx + 5.5f + k * 10;
		Smooth.circle(r, kx, sy + 6f, 4.3f, 0x38000000);
		Smooth.circle(r, kx, sy + 5.5f, 4.1f, 0xFFFFFFFF);
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

	/** A module's settings: a way back, its name, then one grouped list (its switch first). */
	private void settings(RenderBackend r, Module m, float x, float py, float w, float ph, int mouseX, int mouseY) {
		backLink(r, query.isEmpty() ? category.title : "Search", x, py + 11, mouseX, mouseY);
		Smooth.text(r, m.name, x, py + 24, 13, TEXT, true);
		Smooth.text(r, Smooth.fit(r, m.description, 7, false, w - 30), x, py + 41, 7, FAINT, false);
		Smooth.rect(r, x, py + 53, x + w, py + 54, BORDER);

		List<Option> rows = new ArrayList<>(m.settings.size() + 1);
		if (m.toggle != null) {
			rows.add(m.toggle);
		}
		rows.addAll(m.settings);
		float top = py + 62;
		float bottom = py + ph - 32;
		float content = rows.size() * ROW_H;
		float off = begin(r, settingsScroll, ROW_H, content, x, top, w, bottom);
		// One card holding every row, divided by hairlines.
		Smooth.roundBox(r, x, top - off, x + w, top - off + content, 7, CARD, BORDER);
		int last = Math.min(rows.size() - 1, (int) ((off + bottom - top) / ROW_H));
		for (int i = (int) (off / ROW_H); i <= last; i++) {
			row(r, rows.get(i), m.toggle != null && i == 0, i == rows.size() - 1, x, top + i * ROW_H - off, w, mouseX, mouseY);
		}
		end(r, settingsScroll, content, x, top, w, bottom, mouseX, mouseY);
		float kx = keyHint(r, x + w, py + ph - 21, "Esc", "Back");
		keyHint(r, kx - 10, py + ph - 21, "Right-click", "Step back");
	}

	private void row(RenderBackend r, Option o, boolean main, boolean lastRow, float x, float y, float w, int mouseX, int mouseY) {
		boolean hot = inside(mouseX, mouseY, x, y, x + w, y + ROW_H);
		if (hot) {
			Smooth.rect(r, x + 1, y, x + w - 1, y + ROW_H, 0x08FFFFFF);
		}
		if (!lastRow) {
			Smooth.rect(r, x + 10, y + ROW_H - 1, x + w - 10, y + ROW_H, 0x0DFFFFFF);
		}
		Boolean on = o.on();
		String label = main ? "Enabled" : o.label;
		float mid = y + ROW_H / 2f;
		Smooth.text(r, label, x + 11, mid - Smooth.lineHeight(r, 8, false) / 2, 8, TEXT, false);
		if (on != null) {
			toggle(r, x + w - 32, mid - 5.5f, on ? 1f : 0f);
			hit(x, y, x + w, y + ROW_H, o.clicker, null);
		} else {
			// ‹ value ›: the arrows step, clicking the row steps forward.
			String value = o.value();
			float vw = Math.max(36, Smooth.width(r, value, 7.5f, true) + 10);
			float rx = x + w - 25;
			float lx = rx - vw - 16;
			Smooth.text(r, value, lx + 16 + (vw - Smooth.width(r, value, 7.5f, true)) / 2, mid - Smooth.lineHeight(r, 7.5f, true) / 2, 7.5f, hot ? TEXT : MUTED, true);
			stepButton(r, "left", lx, mid - 7, mouseX, mouseY);
			stepButton(r, "right", rx, mid - 7, mouseX, mouseY);
			hit(x, y, x + w, y + ROW_H, o.clicker, o.backer);
			hit(lx, y, lx + 15, y + ROW_H, o.backer, o.backer);
			hit(rx, y, rx + 15, y + ROW_H, o.clicker, o.backer);
		}
	}

	private void stepButton(RenderBackend r, String icon, float x, float y, int mouseX, int mouseY) {
		boolean hot = inside(mouseX, mouseY, x, y, x + 14, y + 14);
		Smooth.roundBox(r, x, y, x + 14, y + 14, 4, hot ? 0xFF2D2E35 : 0xFF222328, 0xFF2D2E35);
		Smooth.icon(r, icon, x + 3, y + 3, 8, hot ? TEXT : MUTED);
	}

	/** "‹ Visual": back to the grid. */
	private void backLink(RenderBackend r, String label, float x, float y, int mouseX, int mouseY) {
		float w = Smooth.width(r, label, 7, false) + 12;
		boolean hot = inside(mouseX, mouseY, x - 2, y - 2, x + w + 2, y + 11);
		float h = misc[5] = approach(misc[5], hot ? 1f : 0f, 16);
		int colour = Smooth.mix(FAINT, TEXT, h);
		Smooth.icon(r, "left", x - 1, y + 0.5f, 8, colour);
		Smooth.text(r, label, x + 10, y + 4.5f - Smooth.lineHeight(r, 7, false) / 2, 7, colour, false);
		hit(x - 2, y - 2, x + w + 2, y + 11, back, null);
	}

	/** A small button; returns its width. {@code slot} keeps its hover animation; slot 0 is the accent one. */
	private float button(RenderBackend r, String label, String icon, float x, float y, int mouseX, int mouseY, int slot, Runnable action) {
		float bw = Smooth.width(r, label, 7.5f, true) + 28;
		boolean hot = inside(mouseX, mouseY, x, y, x + bw, y + 17);
		float h = misc[slot] = approach(misc[slot], hot ? 1f : 0f, 14);
		boolean primary = slot == 0;
		int fill = primary ? Smooth.mix(ACCENT, 0xFF9C8FFA, h) : Smooth.mix(0x10FFFFFF, 0x1CFFFFFF, h);
		Smooth.roundRect(r, x, y, x + bw, y + 17, 5, fill);
		Smooth.icon(r, icon, x + 8, y + 4, 9, 0xFFFFFFFF);
		Smooth.text(r, label, x + 20, y + 8.5f - Smooth.lineHeight(r, 7.5f, true) / 2, 7.5f, 0xFFFFFFFF, true);
		hit(x, y, x + bw, y + 17, action, null);
		return bw;
	}

	private void searchBox(RenderBackend r, float x, float y, float w, int mouseX, int mouseY) {
		boolean hot = inside(mouseX, mouseY, x, y, x + w, y + 18);
		float f = misc[2] = approach(misc[2], searchFocused ? 1f : hot ? 0.4f : 0f, 14);
		Smooth.roundBox(r, x, y, x + w, y + 18, 5, 0xFF0D0E11, Smooth.mix(0x18FFFFFF, ACCENT, f));
		Smooth.icon(r, "search", x + 7, y + 4, 10, searchFocused ? TEXT : FAINT);
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
		Smooth.roundBox(r, x, y, x + w, y + 16, 5, 0xF81E2026, 0x24FFFFFF);
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
		Smooth.roundRect(r, bx, top, bx + bw, bottom, bw / 2, 0x0AFFFFFF);
		Smooth.roundRect(r, bx, ty, bx + bw, ty + thumb, bw / 2, Smooth.mix(0x40FFFFFF, 0x80FFFFFF, h));
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
