package dev.quartz.core.ui;

import dev.quartz.core.Feature;
import dev.quartz.core.config.ClientConfig;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * A setting described once, independent of any GUI toolkit. Each
 * version's menu turns these into its own buttons ("Label: Value", click
 * to change), so a new option appears on every version at once.
 */
public abstract class Option {
	public final Feature feature;
	public final String label;

	/** Ready-made click actions for menus, so a frame doesn't allocate one per option. */
	public final Runnable clicker = this::click;
	public final Runnable backer = this::clickBack;

	protected Option(Feature feature, String label) {
		this.feature = feature;
		this.label = label;
	}

	public abstract String value();

	/** For on/off options, whether it's on; null for options with more than two values. */
	public Boolean on() {
		return null;
	}

	/** Advances to the next value and saves. */
	public final void click() {
		advance();
		ClientConfig.get().save();
	}

	protected abstract void advance();

	/** Steps back one value (right-click); on/off options just flip. */
	protected void retreat() {
		advance();
	}

	public final void clickBack() {
		retreat();
		ClientConfig.get().save();
	}

	public String text() {
		return label + ": " + value();
	}

	public static Option toggle(Feature feature, String label, BooleanSupplier get, Consumer<Boolean> set) {
		return new Option(feature, label) {
			@Override
			public String value() {
				return get.getAsBoolean() ? "On" : "Off";
			}

			@Override
			public Boolean on() {
				return get.getAsBoolean();
			}

			@Override
			protected void advance() {
				set.accept(!get.getAsBoolean());
			}
		};
	}

	/** Cycles through {@code values}; an unknown current value shows as the first. */
	public static <T> Option choice(Feature feature, String label, List<T> values, List<String> names, Supplier<T> get, Consumer<T> set) {
		return new Option(feature, label) {
			private int index() {
				return Math.max(0, values.indexOf(get.get()));
			}

			@Override
			public String value() {
				return names.get(index());
			}

			@Override
			protected void advance() {
				set.accept(values.get((index() + 1) % values.size()));
			}

			@Override
			protected void retreat() {
				set.accept(values.get((index() + values.size() - 1) % values.size()));
			}
		};
	}
}
