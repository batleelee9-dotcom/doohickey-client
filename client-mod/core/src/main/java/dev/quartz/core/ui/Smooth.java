package dev.quartz.core.ui;

import dev.quartz.core.RenderBackend;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.Polygon;
import java.awt.RadialGradientPaint;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Smooth, anti-aliased UI drawing for the Doohickey menu: the client's own
 * font, rounded corners, glows and vector icons. Everything is rasterized by
 * Java2D at the screen's real resolution (so it's sharp, not scaled) and drawn
 * through {@link RenderBackend#image}/{@link RenderBackend#drawImage}. On a
 * backend without image support it falls back to plain fills and the game font.
 */
public final class Smooth {
	/** The first of these installed is the client's UI font. */
	private static final String[] FAMILIES = {
		"Segoe UI Variable Text", "Segoe UI", "SF Pro Text", "Helvetica Neue", "Inter", "Ubuntu", "Cantarell", "Noto Sans", "DejaVu Sans", "Arial",
	};
	private static final String CHARSET;

	static {
		StringBuilder sb = new StringBuilder();
		for (char c = 32; c < 127; c++) {
			sb.append(c);
		}
		for (char c = 160; c < 256; c++) {
			sb.append(c);
		}
		// Ellipsis, bullet, dashes, curly quotes, arrows and the middle dot.
		sb.append("…•–—‘’“”←→↑↓·");
		CHARSET = sb.toString();
	}

	private static String family;
	// Handle caches, so drawing never builds a lookup string. They belong to one
	// backend (there is one per game); a different backend starts them afresh.
	private static RenderBackend owner;
	private static int whiteHandle;
	private static int glowHandle;
	private static final int[] CIRCLES = new int[513];
	private static final Atlas[][] ATLASES = new Atlas[2][257];
	private static final Map<String, int[]> ICONS = new HashMap<>();

	/** Per-size handles for one icon (-2 = not uploaded yet). */
	private static int[] slot(RenderBackend r, String name) {
		own(r);
		int[] slot = ICONS.get(name);
		if (slot == null) {
			slot = new int[257];
			Arrays.fill(slot, -2);
			ICONS.put(name, slot);
		}
		return slot;
	}

	private static void own(RenderBackend r) {
		if (r != owner) {
			owner = r;
			whiteHandle = -2;
			glowHandle = -2;
			Arrays.fill(CIRCLES, -2);
			for (Atlas[] row : ATLASES) {
				Arrays.fill(row, null);
			}
			ICONS.clear();
		}
	}
	private static final float[] QUADS = new float[8 * 512];

	private Smooth() {
	}

	// ---- Shapes --------------------------------------------------------------

	/** Whether this backend draws images (otherwise everything falls back to fills). */
	public static boolean supported(RenderBackend r) {
		return white(r) >= 0;
	}

	public static void rect(RenderBackend r, float x0, float y0, float x1, float y1, int argb) {
		int white = white(r);
		if (white < 0) {
			r.fill(Math.round(x0), Math.round(y0), Math.round(x1), Math.round(y1), argb);
			return;
		}
		quad(0, x0, y0, x1, y1, 0, 0, 1, 1);
		r.drawImage(white, QUADS, 1, argb);
	}

	/** An anti-aliased rounded rectangle; corners are true quarter circles at screen resolution. */
	public static void roundRect(RenderBackend r, float x0, float y0, float x1, float y1, float radius, int argb) {
		if ((argb >>> 24) == 0 || x1 <= x0 || y1 <= y0) {
			return;
		}
		float s = r.guiScale();
		radius = Math.min(radius, Math.min(x1 - x0, y1 - y0) / 2f);
		int px = Math.max(1, Math.round(radius * s));
		int circle = circle(r, px);
		int white = white(r);
		if (circle < 0 || white < 0) {
			r.fill(Math.round(x0), Math.round(y0), Math.round(x1), Math.round(y1), argb);
			return;
		}
		float rad = px / s;
		quad(0, x0 + rad, y0, x1 - rad, y1, 0, 0, 1, 1);
		quad(1, x0, y0 + rad, x0 + rad, y1 - rad, 0, 0, 1, 1);
		quad(2, x1 - rad, y0 + rad, x1, y1 - rad, 0, 0, 1, 1);
		r.drawImage(white, QUADS, 3, argb);
		quad(0, x0, y0, x0 + rad, y0 + rad, 0, 0, 0.5f, 0.5f);
		quad(1, x1 - rad, y0, x1, y0 + rad, 0.5f, 0, 1, 0.5f);
		quad(2, x0, y1 - rad, x0 + rad, y1, 0, 0.5f, 0.5f, 1);
		quad(3, x1 - rad, y1 - rad, x1, y1, 0.5f, 0.5f, 1, 1);
		r.drawImage(circle, QUADS, 4, argb);
	}

	/** A rounded outline: the border colour, then the fill inset by one GUI pixel. */
	public static void roundBox(RenderBackend r, float x0, float y0, float x1, float y1, float radius, int fill, int border) {
		roundRect(r, x0, y0, x1, y1, radius, border);
		roundRect(r, x0 + 1, y0 + 1, x1 - 1, y1 - 1, Math.max(0, radius - 1), fill);
	}

	public static void circle(RenderBackend r, float cx, float cy, float radius, int argb) {
		roundRect(r, cx - radius, cy - radius, cx + radius, cy + radius, radius, argb);
	}

	/** A soft radial glow: full colour in the middle, fading to nothing at {@code radius}. */
	public static void glow(RenderBackend r, float cx, float cy, float radius, int argb) {
		own(r);
		if (glowHandle == -2) {
			glowHandle = r.image("smooth:glow", 128, 128, () -> raster(128, 128, g -> {
			g.setPaint(new RadialGradientPaint(new Point2D.Float(64, 64), 64, new float[] {0f, 0.45f, 1f},
				new Color[] {new Color(255, 255, 255, 255), new Color(255, 255, 255, 90), new Color(255, 255, 255, 0)}));
			g.fillRect(0, 0, 128, 128);
			}));
		}
		int h = glowHandle;
		if (h < 0) {
			return;
		}
		quad(0, cx - radius, cy - radius, cx + radius, cy + radius, 0, 0, 1, 1);
		r.drawImage(h, QUADS, 1, argb);
	}

	/** A soft drop shadow under a rounded panel. */
	public static void shadow(RenderBackend r, float x0, float y0, float x1, float y1, float spread, int argb) {
		for (int i = 1; i <= 6; i++) {
			float k = spread * i / 6f;
			int a = Math.max(1, ((argb >>> 24) * (7 - i)) / 24);
			roundRect(r, x0 - k, y0 - k + spread / 3f, x1 + k, y1 + k + spread / 3f, 8 + k, (a << 24) | (argb & 0xFFFFFF));
		}
	}

	/** A left-to-right two-colour bar (a few dozen slices: smooth enough at any width). */
	public static void gradient(RenderBackend r, float x0, float y0, float x1, float y1, int from, int to) {
		int white = white(r);
		int steps = 48;
		for (int i = 0; i < steps; i++) {
			float a = x0 + (x1 - x0) * i / steps;
			float b = x0 + (x1 - x0) * (i + 1) / steps;
			int color = mix(from, to, i / (float) (steps - 1));
			if (white < 0) {
				r.fill(Math.round(a), Math.round(y0), Math.round(b), Math.round(y1), color);
			} else {
				quad(0, a, y0, b, y1, 0, 0, 1, 1);
				r.drawImage(white, QUADS, 1, color);
			}
		}
	}

	// ---- Text --------------------------------------------------------------------

	/** Draws text in the client font with its top at {@code y}; returns the x just after it. */
	public static float text(RenderBackend r, String s, float x, float y, float size, int argb, boolean bold) {
		Atlas a = atlas(r, size, bold);
		if (a == null) {
			return r.text(s, Math.round(x), Math.round(y + (size - 8) / 2f), argb, false);
		}
		float scale = r.guiScale();
		float pen = Math.round(x * scale);
		float baseline = Math.round(y * scale + a.ascent);
		int n = 0;
		for (int i = 0; i < s.length(); i++) {
			float[] gl = a.glyph(s.charAt(i));
			if (gl[6] > 0 && gl[7] > 0) {
				float gx = pen + gl[4];
				float gy = baseline + gl[5];
				quad(n++, gx / scale, gy / scale, (gx + gl[6]) / scale, (gy + gl[7]) / scale, gl[0], gl[1], gl[2], gl[3]);
				if (n == QUADS.length / 8) {
					r.drawImage(a.handle, QUADS, n, argb);
					n = 0;
				}
			}
			pen += gl[8];
		}
		if (n > 0) {
			r.drawImage(a.handle, QUADS, n, argb);
		}
		return pen / scale;
	}

	public static float width(RenderBackend r, String s, float size, boolean bold) {
		Atlas a = atlas(r, size, bold);
		if (a == null) {
			return r.textWidth(s);
		}
		float w = 0;
		for (int i = 0; i < s.length(); i++) {
			w += a.glyph(s.charAt(i))[8];
		}
		return w / r.guiScale();
	}

	/** Height of a line of text at this size, for vertical centring. */
	public static float lineHeight(RenderBackend r, float size, boolean bold) {
		Atlas a = atlas(r, size, bold);
		return a == null ? 8 : (a.ascent + a.descent) / r.guiScale();
	}

	/** Shortens {@code s} with "…" until it fits {@code max}. */
	public static String fit(RenderBackend r, String s, float size, boolean bold, float max) {
		if (width(r, s, size, bold) <= max) {
			return s;
		}
		while (!s.isEmpty() && width(r, s + "…", size, bold) > max) {
			s = s.substring(0, s.length() - 1);
		}
		return s.trim() + "…";
	}

	// ---- Icons -------------------------------------------------------------------

	/** A named vector icon ("hud", "world", "game", "search", "user"), {@code size} GUI pixels square. */
	public static void icon(RenderBackend r, String name, float x, float y, float size, int argb) {
		int px = Math.min(256, Math.max(4, Math.round(size * r.guiScale())));
		int[] slot = slot(r, name);
		if (slot[px] == -2) {
			slot[px] = r.image("smooth:icon:" + name + ":" + px, px, px, () -> raster(px, px, g -> {
				float k = px / 24f;
				g.scale(k, k);
				g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
				drawIcon(g, name);
			}));
		}
		int h = slot[px];
		if (h < 0) {
			return;
		}
		quad(0, x, y, x + size, y + size, 0, 0, 1, 1);
		r.drawImage(h, QUADS, 1, argb);
	}

	/**
	 * A particle sprite (an icon named "p.…") centred on {@code cx, cy},
	 * {@code w}×{@code h} GUI units. Sizes snap to a few device-pixel steps so
	 * shrinking particles reuse a handful of textures.
	 */
	public static void sprite(RenderBackend r, String name, float cx, float cy, float w, float h, int argb) {
		int px = Math.round(Math.max(w, h) * r.guiScale());
		if (px < 2) {
			return;
		}
		px = px <= 16 ? Math.max(4, px) : px <= 48 ? (px + 3) & ~3 : Math.min(256, (px + 7) & ~7);
		int[] slot = slot(r, name);
		if (slot[px] == -2) {
			int size = px;
			slot[px] = r.image("smooth:icon:" + name + ":" + px, px, px, () -> raster(size, size, g -> {
				float k = size / 24f;
				g.scale(k, k);
				g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
				drawIcon(g, name);
			}));
		}
		int handle = slot[px];
		if (handle >= 0) {
			quad(0, cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2, 0, 0, 1, 1);
			r.drawImage(handle, QUADS, 1, argb);
		}
	}

	private static java.awt.geom.Path2D.Float chevron(float x0, float y0, float x1, float y1, float x2, float y2) {
		java.awt.geom.Path2D.Float p = new java.awt.geom.Path2D.Float();
		p.moveTo(x0, y0);
		p.lineTo(x1, y1);
		p.lineTo(x2, y2);
		return p;
	}

	/** A star with {@code points} tips, outer and inner radius, in icon units. */
	private static java.awt.geom.Path2D.Float star(int points, float cx, float cy, float outer, float inner) {
		java.awt.geom.Path2D.Float p = new java.awt.geom.Path2D.Float();
		for (int i = 0; i < points * 2; i++) {
			double a = Math.PI * i / points - Math.PI / 2;
			float rad = i % 2 == 0 ? outer : inner;
			float x = cx + (float) Math.cos(a) * rad;
			float y = cy + (float) Math.sin(a) * rad;
			if (i == 0) {
				p.moveTo(x, y);
			} else {
				p.lineTo(x, y);
			}
		}
		p.closePath();
		return p;
	}

	private static void drawIcon(java.awt.Graphics2D g, String name) {
		switch (name) {
			case "hud":
				g.draw(new RoundRectangle2D.Float(3, 4, 18, 13, 4, 4));
				g.fill(new RoundRectangle2D.Float(6, 7, 5, 2.4f, 2, 2));
				g.fill(new RoundRectangle2D.Float(6, 11, 9, 2.4f, 2, 2));
				g.draw(new Line2D.Float(9, 20.5f, 15, 20.5f));
				break;
			case "world":
				g.draw(new Ellipse2D.Float(3, 3, 18, 18));
				g.draw(new Ellipse2D.Float(8, 3, 8, 18));
				g.draw(new Line2D.Float(3.5f, 12, 20.5f, 12));
				break;
			case "game":
				g.draw(new Ellipse2D.Float(5, 5, 14, 14));
				g.draw(new Line2D.Float(12, 2, 12, 7));
				g.draw(new Line2D.Float(12, 17, 12, 22));
				g.draw(new Line2D.Float(2, 12, 7, 12));
				g.draw(new Line2D.Float(17, 12, 22, 12));
				g.fill(new Ellipse2D.Float(10.5f, 10.5f, 3, 3));
				break;
			case "search":
				g.draw(new Ellipse2D.Float(4, 4, 12, 12));
				g.draw(new Line2D.Float(14.5f, 14.5f, 20, 20));
				break;
			case "edit":
				g.draw(new RoundRectangle2D.Float(4, 4, 16, 16, 4, 4));
				g.draw(new Line2D.Float(9, 9, 15, 15));
				g.draw(new Line2D.Float(15, 9, 15, 15));
				g.draw(new Line2D.Float(9, 15, 15, 15));
				break;
			case "gear": {
				// Eight teeth around a ring.
				for (int i = 0; i < 8; i++) {
					double a = i * Math.PI / 4;
					g.draw(new Line2D.Double(12 + Math.cos(a) * 6.5, 12 + Math.sin(a) * 6.5, 12 + Math.cos(a) * 9.5, 12 + Math.sin(a) * 9.5));
				}
				g.draw(new Ellipse2D.Float(6, 6, 12, 12));
				g.draw(new Ellipse2D.Float(9.5f, 9.5f, 5, 5));
				break;
			}
			case "back":
				g.draw(new Line2D.Float(15, 5, 8, 12));
				g.draw(new Line2D.Float(8, 12, 15, 19));
				break;
			case "sparkle":
				g.fill(poly(12, 2, 14.2f, 9.8f, 22, 12, 14.2f, 14.2f, 12, 22, 9.8f, 14.2f, 2, 12, 9.8f, 9.8f));
				g.fill(poly(19, 2, 19.8f, 4.2f, 22, 5, 19.8f, 5.8f, 19, 8, 18.2f, 5.8f, 16, 5, 18.2f, 4.2f));
				break;
			case "sound":
				g.fill(poly(3, 9, 7, 9, 12, 4.5f, 12, 19.5f, 7, 15, 3, 15));
				g.draw(new java.awt.geom.Arc2D.Float(10, 7, 8, 10, -55, 110, java.awt.geom.Arc2D.OPEN));
				g.draw(new java.awt.geom.Arc2D.Float(9, 3.5f, 13, 17, -55, 110, java.awt.geom.Arc2D.OPEN));
				break;
			case "shirt":
				g.draw(poly(8, 3.5f, 4, 6, 2.5f, 10.5f, 6, 12, 6, 20.5f, 18, 20.5f, 18, 12, 21.5f, 10.5f, 20, 6, 16, 3.5f, 14, 5.5f, 10, 5.5f));
				break;
			case "bolt":
				g.fill(poly(13.5f, 2, 5, 13.5f, 11, 13.5f, 9.5f, 22, 19, 9.5f, 13, 9.5f));
				break;
			case "heart": {
				java.awt.geom.Path2D.Float p = new java.awt.geom.Path2D.Float();
				p.moveTo(12, 21);
				p.curveTo(4, 15, 1.5f, 11, 1.5f, 7.5f);
				p.curveTo(1.5f, 4, 4.2f, 2, 7, 2);
				p.curveTo(9.2f, 2, 11, 3.3f, 12, 5);
				p.curveTo(13, 3.3f, 14.8f, 2, 17, 2);
				p.curveTo(19.8f, 2, 22.5f, 4, 22.5f, 7.5f);
				p.curveTo(22.5f, 11, 20, 15, 12, 21);
				p.closePath();
				g.fill(p);
				break;
			}
			case "more":
				g.fill(new Ellipse2D.Float(10, 3, 4, 4));
				g.fill(new Ellipse2D.Float(10, 10, 4, 4));
				g.fill(new Ellipse2D.Float(10, 17, 4, 4));
				break;
			case "close":
				g.draw(new Line2D.Float(6, 6, 18, 18));
				g.draw(new Line2D.Float(18, 6, 6, 18));
				break;
			case "left":
				g.draw(chevron(15, 5, 8, 12, 15, 19));
				break;
			case "right":
				g.draw(chevron(9, 5, 16, 12, 9, 19));
				break;
			// Particle sprites (fx.Sprites): white, tinted when drawn.
			case "p.snow":
				g.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
				for (int i = 0; i < 6; i++) {
					java.awt.geom.AffineTransform t = g.getTransform();
					g.rotate(Math.PI / 3 * i, 12, 12);
					g.draw(new Line2D.Float(12, 12, 12, 2.5f));
					g.draw(new Line2D.Float(12, 6.5f, 9, 4));
					g.draw(new Line2D.Float(12, 6.5f, 15, 4));
					g.setTransform(t);
				}
				break;
			case "p.star":
				g.fill(star(5, 12, 12.6f, 11, 4.6f));
				break;
			case "p.sparkle":
				g.fill(star(4, 12, 12, 11.5f, 2.6f));
				break;
			case "p.petal":
				g.rotate(Math.toRadians(35), 12, 12);
				g.fill(new Ellipse2D.Float(7, 2, 10, 20));
				break;
			case "p.bubble":
				g.draw(new Ellipse2D.Float(3, 3, 18, 18));
				g.fill(new Ellipse2D.Float(7, 6.5f, 4, 4));
				break;
			case "p.dot":
				g.setPaint(new RadialGradientPaint(12, 12, 11, new float[] {0f, 0.35f, 1f},
					new Color[] {Color.WHITE, new Color(255, 255, 255, 150), new Color(255, 255, 255, 0)}));
				g.fill(new Ellipse2D.Float(1, 1, 22, 22));
				break;
			case "hitmarker":
				// Four short strokes round the crosshair, like a shooter's hit marker.
				g.setStroke(new BasicStroke(2.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
				for (int i = 0; i < 4; i++) {
					java.awt.geom.AffineTransform t = g.getTransform();
					g.rotate(Math.PI / 4 + Math.PI / 2 * i, 12, 12);
					g.draw(new Line2D.Float(12, 5.5f, 12, 1.8f));
					g.setTransform(t);
				}
				break;
			case "p.confetti":
				g.fill(new RoundRectangle2D.Float(1, 1, 22, 22, 5, 5));
				break;
			default:
				g.fill(new Ellipse2D.Float(6, 6, 12, 12));
		}
	}

	/** The Doohickey cube in its own colours (not tinted). */
	public static void logo(RenderBackend r, float x, float y, float size) {
		int px = Math.min(256, Math.max(8, Math.round(size * r.guiScale())));
		int[] slot = slot(r, "logo");
		if (slot[px] == -2) {
			slot[px] = r.image("smooth:logo:" + px, px, px, () -> raster(px, px, g -> {
				float k = px / 32f;
				g.scale(k, k);
				g.setColor(new Color(0xC3BAFF));
				g.fill(poly(16, 3, 27, 9.4f, 16, 15.8f, 5, 9.4f));
				g.setColor(new Color(0x7263E6));
				g.fill(poly(5, 9.4f, 16, 15.8f, 16, 29, 5, 22.6f));
				g.setColor(new Color(0x4F42B8));
				g.fill(poly(27, 9.4f, 16, 15.8f, 16, 29, 27, 22.6f));
			}));
		}
		int h = slot[px];
		if (h < 0) {
			return;
		}
		quad(0, x, y, x + size, y + size, 0, 0, 1, 1);
		r.drawImage(h, QUADS, 1, 0xFFFFFFFF);
	}

	private static java.awt.geom.Path2D poly(float... xy) {
		java.awt.geom.Path2D.Float p = new java.awt.geom.Path2D.Float();
		p.moveTo(xy[0], xy[1]);
		for (int i = 2; i < xy.length; i += 2) {
			p.lineTo(xy[i], xy[i + 1]);
		}
		p.closePath();
		return p;
	}

	// ---- Plumbing --------------------------------------------------------------

	public static int mix(int a, int b, float t) {
		t = Math.max(0f, Math.min(1f, t));
		int out = 0;
		for (int shift = 0; shift <= 24; shift += 8) {
			int ca = (a >>> shift) & 0xFF;
			int cb = (b >>> shift) & 0xFF;
			out |= Math.round(ca + (cb - ca) * t) << shift;
		}
		return out;
	}

	private static void quad(int i, float x0, float y0, float x1, float y1, float u0, float v0, float u1, float v1) {
		int o = i * 8;
		QUADS[o] = x0;
		QUADS[o + 1] = y0;
		QUADS[o + 2] = x1;
		QUADS[o + 3] = y1;
		QUADS[o + 4] = u0;
		QUADS[o + 5] = v0;
		QUADS[o + 6] = u1;
		QUADS[o + 7] = v1;
	}

	private static int white(RenderBackend r) {
		own(r);
		if (whiteHandle == -2) {
			whiteHandle = r.image("smooth:white", 4, 4, () -> {
				int[] p = new int[16];
				Arrays.fill(p, 0xFFFFFFFF);
				return p;
			});
		}
		return whiteHandle;
	}

	/** A filled circle {@code 2 * radiusPx} wide; its quarters are the rounded corners. */
	private static int circle(RenderBackend r, int radiusPx) {
		own(r);
		int i = Math.min(radiusPx, CIRCLES.length - 1);
		if (CIRCLES[i] == -2) {
			int d = i * 2;
			CIRCLES[i] = r.image("smooth:circle:" + i, d, d, () -> raster(d, d, g -> g.fill(new Ellipse2D.Float(0, 0, d, d))));
		}
		return CIRCLES[i];
	}

	private interface Painter {
		void paint(java.awt.Graphics2D g);
	}

	/** Draws white shapes onto a transparent image and returns its ARGB pixels. */
	private static int[] raster(int w, int h, Painter painter) {
		BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		java.awt.Graphics2D g = img.createGraphics();
		hints(g);
		g.setColor(Color.WHITE);
		painter.paint(g);
		g.dispose();
		return img.getRGB(0, 0, w, h, null, 0, w);
	}

	private static void hints(java.awt.Graphics2D g) {
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
		g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
		g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
	}

	private static String family() {
		if (family == null) {
			Set<String> installed = new HashSet<>();
			try {
				installed.addAll(Arrays.asList(GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()));
			} catch (Throwable ignored) {
				// No font list (unusual headless setups): the logical font below still works.
			}
			family = Font.SANS_SERIF;
			for (String f : FAMILIES) {
				if (installed.contains(f)) {
					family = f;
					break;
				}
			}
		}
		return family;
	}

	/** One rasterized font size: every glyph packed into a single texture, sized for this screen. */
	private static final class Atlas {
		final int handle;
		final float ascent;
		final float descent;
		final Map<Character, float[]> glyphs = new HashMap<>();
		final float[] fallback;

		Atlas(RenderBackend r, int px, boolean bold) {
			Font font = new Font(family(), bold ? Font.BOLD : Font.PLAIN, px);
			BufferedImage probe = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
			java.awt.Graphics2D pg = probe.createGraphics();
			hints(pg);
			FontRenderContext frc = pg.getFontRenderContext();
			java.awt.font.LineMetrics lm = font.getLineMetrics("Hg", frc);
			ascent = lm.getAscent();
			descent = lm.getDescent();

			int pad = 2;
			int width = px > 36 ? 1024 : 512;
			int x = 0;
			int y = 0;
			int rowH = 0;
			Map<Character, Rectangle> cells = new HashMap<>();
			Map<Character, GlyphVector> vectors = new HashMap<>();
			Map<Character, Rectangle> bounds = new HashMap<>();
			for (int i = 0; i < CHARSET.length(); i++) {
				char c = CHARSET.charAt(i);
				GlyphVector gv = font.createGlyphVector(frc, String.valueOf(c));
				Rectangle b = gv.getPixelBounds(frc, 0, 0);
				int cw = b.width + pad * 2;
				int ch = b.height + pad * 2;
				if (x + cw > width) {
					x = 0;
					y += rowH;
					rowH = 0;
				}
				cells.put(c, new Rectangle(x, y, cw, ch));
				vectors.put(c, gv);
				bounds.put(c, b);
				x += cw;
				rowH = Math.max(rowH, ch);
			}
			int height = 1;
			while (height < y + rowH) {
				height <<= 1;
			}
			pg.dispose();

			final int w = width;
			final int h = height;
			for (Map.Entry<Character, Rectangle> e : cells.entrySet()) {
				Rectangle cell = e.getValue();
				Rectangle b = bounds.get(e.getKey());
				float advance = (float) vectors.get(e.getKey()).getGlyphMetrics(0).getAdvanceX();
				boolean empty = b.width == 0 || b.height == 0;
				glyphs.put(e.getKey(), new float[] {
					cell.x / (float) w, cell.y / (float) h, (cell.x + cell.width) / (float) w, (cell.y + cell.height) / (float) h,
					b.x - pad, b.y - pad, empty ? 0 : cell.width, empty ? 0 : cell.height, advance,
				});
			}
			fallback = glyphs.get('?');
			handle = r.image("smooth:font:" + family() + ":" + bold + ":" + px, w, h, () -> raster(w, h, g -> {
				for (Map.Entry<Character, Rectangle> e : cells.entrySet()) {
					Rectangle cell = e.getValue();
					Rectangle b = bounds.get(e.getKey());
					g.drawGlyphVector(vectors.get(e.getKey()), cell.x + pad - b.x, cell.y + pad - b.y);
				}
			}));
		}

		float[] glyph(char c) {
			float[] g = glyphs.get(c);
			return g != null ? g : fallback;
		}
	}

	private static Atlas atlas(RenderBackend r, float size, boolean bold) {
		if (!supported(r)) {
			return null;
		}
		int px = Math.min(ATLASES[0].length - 1, Math.max(6, Math.round(size * r.guiScale())));
		Atlas a = ATLASES[bold ? 1 : 0][px];
		if (a == null) {
			a = new Atlas(r, px, bold);
			ATLASES[bold ? 1 : 0][px] = a;
		}
		return a.handle < 0 ? null : a;
	}
}
