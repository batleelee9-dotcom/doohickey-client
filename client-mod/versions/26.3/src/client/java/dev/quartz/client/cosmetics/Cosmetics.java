package dev.quartz.client.cosmetics;

import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Hats, bandanas and wings. Like capes they're original designs
 * (tools/make-textures.mjs) and render only on your own client — there is
 * no cosmetics server, so other players don't see them.
 */
public final class Cosmetics {
	public record Option(String id, String name) {
	}

	private static final Option NONE = new Option("none", "None");

	public static final List<Option> HATS = List.of(NONE, new Option("tophat", "Top hat"), new Option("crown", "Crown"), new Option("halo", "Halo"));
	public static final List<Option> BANDANAS = List.of(NONE, new Option("red", "Red"), new Option("black", "Black"), new Option("quartz", "Quartz"));
	public static final List<Option> WINGS = List.of(NONE, new Option("angel", "Angel"), new Option("dragon", "Dragon"), new Option("crystal", "Crystal"));

	private Cosmetics() {
	}

	/** Index of the selected option, or 0 ("None") for unknown ids. */
	public static int indexOf(List<Option> options, @Nullable String id) {
		for (int i = 0; i < options.size(); i++) {
			if (options.get(i).id().equals(id)) {
				return i;
			}
		}
		return 0;
	}

	/** The selected option's id, or null for "none" and unknown ids. */
	static @Nullable String selected(List<Option> options, @Nullable String id) {
		int i = indexOf(options, id);
		return i == 0 ? null : options.get(i).id();
	}

	static Identifier texture(String kind, String id) {
		return Identifier.fromNamespaceAndPath("quartz", "textures/cosmetic/" + kind + "_" + id + ".png");
	}
}
