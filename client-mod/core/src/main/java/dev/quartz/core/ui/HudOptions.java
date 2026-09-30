package dev.quartz.core.ui;

import dev.quartz.core.hud.Hud;
import dev.quartz.core.hud.HudElement;

import java.util.ArrayList;
import java.util.List;

/** On/off switches for the shared HUD elements this version supports. */
public final class HudOptions {
	private HudOptions() {
	}

	public static List<Option> all() {
		List<Option> options = new ArrayList<>();
		for (HudElement e : Hud.available()) {
			options.add(Option.toggle(e.feature, e.name, () -> e.state().enabled, v -> e.state().enabled = v));
		}
		return options;
	}
}
