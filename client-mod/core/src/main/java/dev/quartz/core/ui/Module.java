package dev.quartz.core.ui;

import dev.quartz.core.Feature;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * One card in the menu: a name, a one-line description, usually an on/off
 * switch, and any settings behind its gear icon. Built from the same
 * {@link Option}s the older menus use, so the two never disagree.
 */
public final class Module {
	public final String name;
	public final String description;
	public final Feature feature;
	/** Turns the module on or off; null for pure settings (e.g. Sky). */
	public final Option toggle;
	public final List<Option> settings;

	// Menu state, kept here so drawing allocates nothing per frame.
	float hover;
	float knob = -1;
	String fitName;
	String fitDescription;
	/** The description's second line, and whether it still had to be cut short. */
	String fitDescription2 = "";
	boolean cut;
	float fitWidth = -1;

	public Module(String name, String description, Feature feature, Option toggle, Option... settings) {
		this.name = name;
		this.description = description;
		this.feature = feature;
		this.toggle = toggle;
		this.settings = new ArrayList<>(Arrays.asList(settings));
	}

	/** A module that's just one setting (shown as its value on the card). */
	public static Module of(Option setting, String description) {
		return new Module(setting.label, description, setting.feature, null, setting);
	}

	public boolean on() {
		return toggle != null && Boolean.TRUE.equals(toggle.on());
	}

	/** Whether there's a settings page to open. */
	public boolean configurable() {
		return toggle == null ? settings.size() > 1 : !settings.isEmpty();
	}

	boolean matches(String query) {
		if (name.toLowerCase().contains(query) || description.toLowerCase().contains(query)) {
			return true;
		}
		for (Option o : settings) {
			if (o.label.toLowerCase().contains(query)) {
				return true;
			}
		}
		return false;
	}
}
