package dev.quartz.core;

/**
 * 2D drawing for HUD and overlays. Feature code draws only through this —
 * never through GL, GlStateManager, RenderSystem or GuiGraphics directly —
 * so one feature implementation serves every rendering pipeline.
 *
 * Coordinates are GUI-scaled pixels, origin top-left. Colours are ARGB.
 */
public interface RenderBackend {
	/** The four rendering eras a backend implementation targets. */
	enum Kind {
		/** 1.8.9–1.12.2: fixed-function GL11 via GlStateManager, Gui.drawRect, FontRenderer. */
		LEGACY_OPENGL,
		/** 1.13–1.16: Tessellator/BufferBuilder with MatrixStack arriving in 1.15. */
		TESSELLATOR,
		/** 1.17–1.19: core-profile shaders through RenderSystem. */
		RENDER_SYSTEM,
		/** 1.20+: GuiGraphics / render-state extraction (26.x: GuiGraphicsExtractor). */
		PIPELINE
	}

	Kind kind();

	int screenWidth();

	int screenHeight();

	void fill(int x0, int y0, int x1, int y1, int argb);

	/** Draws a string; returns the x just after it. */
	int text(String text, int x, int y, int argb, boolean shadow);

	int textWidth(String text);

	int fontHeight();

	/** Saves the current transform. Every push must be matched by a pop. */
	void push();

	void pop();

	void translate(float x, float y);

	void scale(float factor);

	/**
	 * Draws a 16x16 item icon. {@code stack} is the version's own item
	 * stack, passed through from the adapter untouched.
	 */
	void item(Object stack, int x, int y);

	/** Screen pixels per GUI pixel (the GUI scale), so smooth drawing can rasterize at native resolution. */
	default float guiScale() {
		return 1f;
	}

	/**
	 * Uploads an ARGB image once under {@code key} (later calls with the same
	 * key return the same handle without calling {@code pixels}). Returns -1
	 * when this backend can't draw images; smooth drawing then falls back to
	 * plain fills and the game font.
	 */
	default int image(String key, int width, int height, java.util.function.Supplier<int[]> pixels) {
		return -1;
	}

	/**
	 * Draws {@code count} quads from an uploaded image, tinted by {@code argb}
	 * (opaque white = as uploaded). Each quad is 8 floats in {@code quads}:
	 * x0, y0, x1, y1 in GUI coordinates, then u0, v0, u1, v1 in 0..1.
	 */
	default void drawImage(int handle, float[] quads, int count, int argb) {
	}

	/** Clips drawing to this rectangle (GUI coordinates) until {@link #unclip}. Not nestable; a no-op where unsupported. */
	default void clip(int x0, int y0, int x1, int y1) {
	}

	default void unclip() {
	}

	/** A 1-pixel rectangle outline. */
	default void outline(int x, int y, int w, int h, int argb) {
		fill(x, y, x + w, y + 1, argb);
		fill(x, y + h - 1, x + w, y + h, argb);
		fill(x, y + 1, x + 1, y + h - 1, argb);
		fill(x + w - 1, y + 1, x + w, y + h - 1, argb);
	}

	default void centeredText(String text, int centerX, int y, int argb, boolean shadow) {
		text(text, centerX - textWidth(text) / 2, y, argb, shadow);
	}
}
