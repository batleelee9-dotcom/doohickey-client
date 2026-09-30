package dev.quartz.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import dev.quartz.client.cosmetics.Capes;
import dev.quartz.client.cosmetics.Cosmetics;
import dev.quartz.client.hud.HudModule;
import dev.quartz.client.hud.HudRenderer;
import dev.quartz.client.map.Minimap;
import dev.quartz.client.map.Waypoints;
import dev.quartz.client.pvp.Crosshair;
import dev.quartz.client.pvp.HitColor;
import dev.quartz.core.Feature;
import dev.quartz.core.config.ClientConfig;
import dev.quartz.core.ui.GameOptions;
import dev.quartz.core.ui.Option;
import dev.quartz.core.ui.WorldOptions;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.function.Consumer;

/**
 * The Doohickey menu (Right Shift). Opens as a HUD editor — drag modules to
 * move them, scroll over one to resize — with a Settings panel for
 * everything else. The game keeps running underneath.
 */
public final class QuartzScreen extends Screen {
	private enum Tab { HUD, PVP, WORLD, MAP, COSMETICS }

	private static final int[] PARTICLES = {100, 75, 50, 25, 0};
	private static final int PREVIEW_W = 100;
	private static final int[] TEXT_COLORS = {0xFFFFFFFF, 0xFFB8ADFF, 0xFF55FFFF, 0xFF55FF55, 0xFFFFD166};
	private static final String[] TEXT_COLOR_NAMES = {"White", "Lilac", "Cyan", "Green", "Gold"};

	private boolean settingsOpen;
	private Tab tab = Tab.HUD;
	private @Nullable HudModule dragging;
	private double grabX;
	private double grabY;

	public QuartzScreen() {
		super(Component.literal("Doohickey Client"));
	}

	@Override
	protected void init() {
		ClientConfig c = ClientConfig.get();
		if (!settingsOpen) {
			int x = this.width / 2 - 102;
			add(x, 6, 100, "Settings", () -> settingsOpen = true);
			add(x + 104, 6, 100, "Done", this::onClose);
			return;
		}

		int panelW = 340;
		int left = (this.width - panelW) / 2;
		int top = Math.max(24, this.height / 2 - 120);
		int tabW = (panelW - 2 * (Tab.values().length - 1)) / Tab.values().length;
		Tab[] tabs = Tab.values();
		String[] tabNames = {"HUD", "PvP", "World", "Map", "Cosmetics"};
		for (int i = 0; i < tabs.length; i++) {
			Tab t = tabs[i];
			Button b = add(left + i * (tabW + 2), top, tabW, (t == tab ? "» " : "") + tabNames[i], () -> tab = t);
			b.active = t != tab;
		}

		// Cosmetics leaves room on the right for a live preview of your player.
		Grid grid = tab == Tab.COSMETICS ? new Grid(left, top + 28, panelW - PREVIEW_W - 8, 1) : new Grid(left, top + 28, panelW, 2);
		switch (tab) {
			case HUD -> {
				for (HudModule m : HudRenderer.modules()) {
					grid.toggle(m.name, m.state().enabled, () -> m.state().enabled = !m.state().enabled);
				}
				grid.toggle("Backgrounds", c.moduleBackground, () -> c.moduleBackground = !c.moduleBackground);
				grid.toggle("Text shadow", c.textShadow, () -> c.textShadow = !c.textShadow);
				grid.cycle("Text colour", TEXT_COLOR_NAMES, indexOf(TEXT_COLORS, c.textColor), i -> c.textColor = TEXT_COLORS[i]);
				grid.action("Reset layout", () -> c.modules.clear());
			}
			case PVP -> {
				grid.toggle("Custom crosshair", c.customCrosshair, () -> c.customCrosshair = !c.customCrosshair);
				grid.cycle("Style", Crosshair.STYLES, c.crosshairStyle, i -> c.crosshairStyle = i);
				grid.cycle("Colour", Crosshair.COLOR_NAMES, indexOf(Crosshair.COLORS, c.crosshairColor), i -> c.crosshairColor = Crosshair.COLORS[i]);
				grid.cycle("Size", new String[] {"2", "3", "4", "5", "6", "8", "10"}, List.of(2, 3, 4, 5, 6, 8, 10).indexOf(c.crosshairSize),
					i -> c.crosshairSize = new int[] {2, 3, 4, 5, 6, 8, 10}[i]);
				grid.cycle("Gap", new String[] {"0", "1", "2", "3", "4", "6"}, List.of(0, 1, 2, 3, 4, 6).indexOf(c.crosshairGap),
					i -> c.crosshairGap = new int[] {0, 1, 2, 3, 4, 6}[i]);
				grid.toggle("Outline", c.crosshairOutline, () -> c.crosshairOutline = !c.crosshairOutline);
				String[] hitNames = new String[HitColor.PRESET_NAMES.length + 1];
				hitNames[0] = "Vanilla";
				System.arraycopy(HitColor.PRESET_NAMES, 0, hitNames, 1, HitColor.PRESET_NAMES.length);
				grid.cycle("Hit colour", hitNames, c.hitColorEnabled ? indexOf(HitColor.PRESETS, c.hitColor) + 1 : 0, i -> {
					c.hitColorEnabled = i > 0;
					if (i > 0) {
						c.hitColor = HitColor.PRESETS[i - 1];
					}
					HitColor.apply();
				});
				grid.toggle("Damage tint", c.damageTint, () -> c.damageTint = !c.damageTint);
				grid.cycle("Particles", new String[] {"100%", "75%", "50%", "25%", "Off"}, indexOf(PARTICLES, c.particlePercent),
					i -> c.particlePercent = PARTICLES[i]);
			}
			case WORLD -> {
				// Shared with every Minecraft version; options this version lacks are left out.
				for (Option o : WorldOptions.all()) {
					grid.action(o.text(), o::click);
				}
				for (Option o : GameOptions.of(Feature.DYNAMIC_RENDER_DISTANCE)) {
					grid.action(o.text(), o::click);
				}
			}
			case MAP -> {
				grid.toggle("Minimap", Minimap.MODULE.state().enabled, () -> Minimap.MODULE.state().enabled = !Minimap.MODULE.state().enabled);
				grid.cycle("Zoom", new String[] {"1x", "2x", "4x"}, List.of(1, 2, 4).indexOf(Minimap.zoom()), i -> c.minimapZoom = Minimap.ZOOMS[i]);
				grid.toggle("Waypoint labels", c.waypointsInWorld, () -> c.waypointsInWorld = !c.waypointsInWorld);
				grid.toggle("Death waypoints", c.deathWaypoints, () -> c.deathWaypoints = !c.deathWaypoints);
				grid.action("Add waypoint here", () -> Waypoints.addHere(this.minecraft, null));
				int count = Waypoints.current(this.minecraft).size();
				grid.action("Clear " + count + " waypoints", () -> Waypoints.clear(this.minecraft)).active = count > 0;
			}
			case COSMETICS -> {
				String[] capes = Capes.NAMES.toArray(String[]::new);
				grid.cycle("Cape", capes, Math.max(0, Capes.IDS.indexOf(c.cape)), i -> c.cape = Capes.IDS.get(i));
				cosmetic(grid, "Hat", Cosmetics.HATS, c.hat, id -> c.hat = id);
				cosmetic(grid, "Bandana", Cosmetics.BANDANAS, c.bandana, id -> c.bandana = id);
				cosmetic(grid, "Wings", Cosmetics.WINGS, c.wings, id -> c.wings = id);
			}
		}
		add(left + panelW / 2 - 60, grid.bottom() + 10, 120, "Edit HUD layout", () -> settingsOpen = false);
	}

	private Button add(int x, int y, int w, String label, Runnable action) {
		return this.addRenderableWidget(Button.builder(Component.literal(label), b -> {
			action.run();
			ClientConfig.get().save();
			this.rebuildWidgets();
		}).bounds(x, y, w, 20).build());
	}

	private void cosmetic(Grid grid, String label, List<Cosmetics.Option> options, String current, Consumer<String> set) {
		String[] names = options.stream().map(Cosmetics.Option::name).toArray(String[]::new);
		grid.cycle(label, names, Cosmetics.indexOf(options, current), i -> set.accept(options.get(i).id()));
	}

	/** Option buttons laid out in columns. */
	private final class Grid {
		private final int left;
		private final int top;
		private final int columns;
		private final int colW;
		private int index;

		Grid(int left, int top, int width, int columns) {
			this.left = left;
			this.top = top;
			this.columns = columns;
			this.colW = (width - 4 * (columns - 1)) / columns;
		}

		private int x() {
			return left + (index % columns) * (colW + 4);
		}

		private int y() {
			return top + (index / columns) * 22;
		}

		Button toggle(String label, boolean on, Runnable flip) {
			Button b = add(x(), y(), colW, label + ": " + (on ? "On" : "Off"), flip);
			index++;
			return b;
		}

		Button cycle(String label, String[] options, int current, java.util.function.IntConsumer set) {
			int cur = Math.max(0, current);
			Button b = add(x(), y(), colW, label + ": " + options[cur], () -> set.accept((cur + 1) % options.length));
			index++;
			return b;
		}

		Button action(String label, Runnable run) {
			Button b = add(x(), y(), colW, label, run);
			index++;
			return b;
		}

		int bottom() {
			return top + ((index + columns - 1) / columns) * 22;
		}
	}

	private static int indexOf(int[] values, int value) {
		for (int i = 0; i < values.length; i++) {
			if (values[i] == value) {
				return i;
			}
		}
		return 0;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		// A light dim instead of the menu blur: the HUD being arranged stays visible.
		g.fill(0, 0, this.width, this.height, settingsOpen ? 0x90000000 : 0x40000000);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
		if (!settingsOpen) {
			for (HudModule m : HudRenderer.modules()) {
				if (!m.state().enabled) {
					continue;
				}
				HudRenderer.draw(g, m, true);
				HudRenderer.Placement p = HudRenderer.place(m, this.width, this.height);
				boolean hot = m == dragging || p.contains(mouseX, mouseY);
				g.outline(p.x() - 1, p.y() - 1, p.w() + 2, p.h() + 2, hot ? 0xFFB8ADFF : 0x80FFFFFF);
			}
			g.centeredText(this.font, "Drag to move · Scroll to resize · Right Shift to close", this.width / 2, 32, 0xFFCCCCCC);
		} else {
			int panelW = 340;
			int left = (this.width - panelW) / 2;
			int top = Math.max(24, this.height / 2 - 120);
			g.fill(left - 8, top - 22, left + panelW + 8, top + 250, 0xE0101014);
			g.outline(left - 8, top - 22, panelW + 16, 272, 0xFF2A2A30);
			g.text(this.font, "Doohickey Client", left, top - 14, 0xFFB8ADFF, false);
			if (tab == Tab.COSMETICS && this.minecraft.player != null) {
				int x1 = left + panelW;
				int x0 = x1 - PREVIEW_W;
				int y0 = top + 28;
				int y1 = y0 + 130;
				g.fill(x0, y0, x1, y1, 0x30FFFFFF);
				InventoryScreen.extractEntityInInventoryFollowsMouse(g, x0, y0, x1, y1, 52, 0.0625f, mouseX, mouseY, this.minecraft.player);
				g.centeredText(this.font, "Only you see these", (x0 + x1) / 2, y1 + 4, 0xFF9A9AA6);
			}
		}
		super.extractRenderState(g, mouseX, mouseY, a);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (super.mouseClicked(event, doubleClick) || settingsOpen) {
			return true;
		}
		List<HudModule> modules = HudRenderer.modules();
		for (int i = modules.size() - 1; i >= 0; i--) {
			HudModule m = modules.get(i);
			if (!m.state().enabled) {
				continue;
			}
			HudRenderer.Placement p = HudRenderer.place(m, this.width, this.height);
			if (p.contains(event.x(), event.y())) {
				dragging = m;
				grabX = event.x() - p.x();
				grabY = event.y() - p.y();
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (dragging == null) {
			return super.mouseDragged(event, dx, dy);
		}
		HudRenderer.Placement p = HudRenderer.place(dragging, this.width, this.height);
		int freeX = Math.max(1, this.width - p.w());
		int freeY = Math.max(1, this.height - p.h());
		dragging.state().x = (float) Math.clamp((event.x() - grabX) / freeX, 0.0, 1.0);
		dragging.state().y = (float) Math.clamp((event.y() - grabY) / freeY, 0.0, 1.0);
		return true;
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (dragging != null) {
			dragging = null;
			ClientConfig.get().save();
			return true;
		}
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		if (!settingsOpen) {
			for (HudModule m : HudRenderer.modules()) {
				if (m.state().enabled && HudRenderer.place(m, this.width, this.height).contains(x, y)) {
					m.state().scale = (float) Math.clamp(m.state().scale + scrollY * 0.1, 0.5, 2.5);
					ClientConfig.get().save();
					return true;
				}
			}
		}
		return super.mouseScrolled(x, y, scrollX, scrollY);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		// The key that opened the menu closes it too.
		if (event.key() == InputConstants.KEY_RSHIFT) {
			this.onClose();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void onClose() {
		ClientConfig.get().save();
		super.onClose();
	}
}
