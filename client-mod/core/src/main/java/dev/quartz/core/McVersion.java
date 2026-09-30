package dev.quartz.core;

/**
 * The Minecraft versions Doohickey Client knows about. Only versions with
 * {@link #built} = true ship a jar today; the rest are listed so the
 * compatibility matrix and the launcher can speak about them honestly.
 */
public enum McVersion {
	V1_8_9("1.8.9", Loader.LEGACY_FABRIC, 8, RenderBackend.Kind.LEGACY_OPENGL, true),
	V1_12_2("1.12.2", Loader.LEGACY_FABRIC, 8, RenderBackend.Kind.LEGACY_OPENGL, false),
	V1_16_5("1.16.5", Loader.FABRIC, 8, RenderBackend.Kind.TESSELLATOR, false),
	V1_18_2("1.18.2", Loader.FABRIC, 17, RenderBackend.Kind.RENDER_SYSTEM, false),
	V1_20_1("1.20.1", Loader.FABRIC, 17, RenderBackend.Kind.PIPELINE, false),
	V1_21("1.21.x", Loader.FABRIC, 21, RenderBackend.Kind.PIPELINE, false),
	V26_3("26.3", Loader.FABRIC, 25, RenderBackend.Kind.PIPELINE, true);

	public enum Loader {
		/** Legacy Fabric (legacyfabric.net): Fabric Loader + Mixin for 1.3–1.13.2. */
		LEGACY_FABRIC,
		FABRIC
	}

	public final String id;
	public final Loader loader;
	public final int javaRelease;
	public final RenderBackend.Kind render;
	public final boolean built;

	McVersion(String id, Loader loader, int javaRelease, RenderBackend.Kind render, boolean built) {
		this.id = id;
		this.loader = loader;
		this.javaRelease = javaRelease;
		this.render = render;
		this.built = built;
	}

	/** Exact id first, then the "1.21.x"-style family. */
	public static McVersion fromId(String id) {
		for (McVersion v : values()) {
			if (v.id.equals(id)) {
				return v;
			}
		}
		for (McVersion v : values()) {
			if (v.id.endsWith(".x") && id.startsWith(v.id.substring(0, v.id.length() - 1))) {
				return v;
			}
		}
		throw new IllegalArgumentException("Unknown Minecraft version " + id);
	}
}
