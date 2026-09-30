package dev.quartz.client.cosmetics;

import dev.quartz.core.config.ClientConfig;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Doohickey's own cape designs (assets/quartz/textures/cape). They're original
 * artwork — nothing imitating Mojang or MINECON capes — and render only on
 * your own client.
 */
public final class Capes {
	public static final List<String> IDS = List.of("none", "quartz", "aurora", "ember", "void");
	public static final List<String> NAMES = List.of("None", "Quartz", "Aurora", "Ember", "Void");

	private Capes() {
	}

	public static ClientAsset.@Nullable Texture selectedTexture() {
		String id = ClientConfig.get().cape;
		if (id == null || id.equals("none") || !IDS.contains(id)) {
			return null;
		}
		return new ClientAsset.ResourceTexture(Identifier.fromNamespaceAndPath("quartz", "cape/" + id));
	}
}
