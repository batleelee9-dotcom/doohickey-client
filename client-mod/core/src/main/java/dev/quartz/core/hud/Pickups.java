package dev.quartz.core.hud;

import dev.quartz.core.Feature;
import dev.quartz.core.Quartz;
import dev.quartz.core.RenderBackend;
import dev.quartz.core.VersionAdapter;
import dev.quartz.core.config.ClientConfig;
import dev.quartz.core.ui.Smooth;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Item pickup feed: "+16 Iron Ingot" slides in at the right of the screen
 * whenever your inventory gains something, merging repeats. Counted by
 * item name across your whole inventory, so moving items around shows
 * nothing; while a screen is open (chests, crafting) it only keeps count.
 */
public final class Pickups {
	/** What a version reports for each stack in the player's inventory (main, armour, off hand). */
	public interface Sink {
		void stack(String name, int count, Object stack);
	}

	private static final class Count {
		int now;
		int before;
		Object icon;
	}

	private static final class Entry {
		String name;
		Object icon;
		int amount;
		String label;
		long createdMs;
		long shownMs;
	}

	private static final int SHOWN = 5;
	private static final long LIFE_MS = 3200;
	private static final long SLIDE_MS = 220;
	private static final Map<String, Count> COUNTS = new HashMap<>();
	private static final Entry[] FEED = new Entry[SHOWN];
	private static int feed;
	private static Object player;
	/** Ticks left before gains count again (joining, respawning: the server fills the inventory). */
	private static int settle;
	private static int ticks;
	private static final Sink COUNT = Pickups::count;

	private Pickups() {
	}

	public static boolean enabled() {
		return ClientConfig.get().pickupFeed && Quartz.available(Feature.PICKUP_FEED);
	}

	/** Every client tick; the inventory is counted five times a second. */
	public static void tick(VersionAdapter a) {
		if (!enabled()) {
			player = null;
			feed = 0;
			return;
		}
		if (++ticks % 4 != 0) {
			return;
		}
		for (Count c : COUNTS.values()) {
			c.now = 0;
		}
		Object p = a.inventory(COUNT);
		if (p != player) {
			player = p;
			settle = 5;
		}
		boolean counting = p != null && !a.screenOpen() && settle == 0;
		if (settle > 0) {
			settle--;
		}
		long now = System.currentTimeMillis();
		for (Iterator<Map.Entry<String, Count>> it = COUNTS.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<String, Count> e = it.next();
			Count c = e.getValue();
			if (counting && c.now > c.before) {
				gained(e.getKey(), c.now - c.before, c.icon, now);
			}
			c.before = c.now;
			if (c.now == 0) {
				it.remove();
			}
		}
	}

	private static void count(String name, int count, Object stack) {
		Count c = COUNTS.get(name);
		if (c == null) {
			COUNTS.put(name, c = new Count());
		}
		c.now += count;
		c.icon = stack;
	}

	/** Test hook: a gain as the tick would report it. */
	static void gained(String name, int amount, Object icon, long now) {
		for (int i = 0; i < feed; i++) {
			Entry e = FEED[i];
			if (e.name.equals(name) && now - e.shownMs < LIFE_MS - SLIDE_MS) {
				e.amount += amount;
				e.label = "+" + e.amount;
				e.icon = icon;
				e.shownMs = now;
				return;
			}
		}
		Entry e;
		if (feed < SHOWN) {
			e = FEED[feed] != null ? FEED[feed] : (FEED[feed] = new Entry());
			feed++;
		} else {
			// Full: the oldest makes way.
			e = FEED[0];
			System.arraycopy(FEED, 1, FEED, 0, SHOWN - 1);
			FEED[SHOWN - 1] = e;
		}
		e.name = name;
		e.icon = icon;
		e.amount = amount;
		e.label = "+" + amount;
		e.createdMs = now;
		e.shownMs = now;
	}

	public static boolean pending() {
		return feed > 0;
	}

	/** {@link #render} on the version's current HUD backend. */
	public static void renderHud() {
		render(Quartz.adapter().render(), System.currentTimeMillis());
	}

	/** Newest at the bottom, stacked upwards from just below the middle of the right edge. */
	public static void render(RenderBackend r, long now) {
		// Expired entries leave from the front.
		int expired = 0;
		while (expired < feed && now - FEED[expired].shownMs >= LIFE_MS) {
			expired++;
		}
		if (expired > 0) {
			for (int i = 0; i < expired; i++) {
				Entry e = FEED[0];
				System.arraycopy(FEED, 1, FEED, 0, SHOWN - 1);
				FEED[SHOWN - 1] = e;
			}
			feed -= expired;
		}
		float right = r.screenWidth() - 6;
		float bottom = Math.round(r.screenHeight() * 0.62f);
		float height = 14;
		for (int i = feed - 1, row = 0; i >= 0; i--, row++) {
			Entry e = FEED[i];
			long age = now - e.shownMs;
			// Slides in from the right, and back out at the end.
			float in = Math.min(1f, (now - e.createdMs) / (float) SLIDE_MS);
			float out = Math.min(1f, (LIFE_MS - age) / (float) SLIDE_MS);
			float shown = Math.min(ease(in), ease(out));
			float labelW = Smooth.width(r, e.label, 7f, true);
			float nameW = Smooth.width(r, e.name, 7f, false);
			float w = Math.round(6 + 10 + 4 + labelW + 3 + nameW + 7);
			float x = Math.round(right - w + (w + 8) * (1f - shown));
			float y = bottom - row * (height + 3);
			int alpha = (int) (Math.max(0.15f, shown) * 255);
			Smooth.roundRect(r, x, y, x + w, y + height, 5, ((int) (shown * 150)) << 24 | 0x0E0F14);
			r.push();
			r.translate(x + 5, y + 2);
			r.scale(10 / 16f);
			r.item(e.icon, 0, 0);
			r.pop();
			float ty = y + height / 2 - Smooth.lineHeight(r, 7f, true) / 2;
			float tx = Smooth.text(r, e.label, x + 20, ty, 7f, alpha << 24 | 0x5EEA8C, true);
			Smooth.text(r, e.name, tx + 3, ty, 7f, alpha << 24 | 0xF4F4F6, false);
		}
	}

	private static float ease(float t) {
		return 1f - (1f - t) * (1f - t) * (1f - t);
	}

	/** Forgets everything (tests). */
	static void reset() {
		COUNTS.clear();
		feed = 0;
		player = null;
	}
}
