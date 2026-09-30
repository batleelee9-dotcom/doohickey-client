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
