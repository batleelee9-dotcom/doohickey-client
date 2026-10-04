package dev.quartz.core.fx;

/**
 * Paints the {@link Atmosphere} skies: for every texel of the six cube faces,
 * the colour of the sky in that direction. Runs once per sky on a background
 * thread (a fraction of a second), never per frame.
 */
final class SkyPainter {
	private SkyPainter() {
	}

	static int[] paint(String id) {
		int f = Atmosphere.FACE;
		int w = f * 3;
		int[] out = new int[w * f * 2];
		float[] rgb = new float[3];
		for (int face = 0; face < 6; face++) {
			float[] a = Atmosphere.FACES[face];
			int ox = (face % 3) * f;
			int oy = (face / 3) * f;
			for (int py = 0; py < f; py++) {
				float t = 1 - 2 * (py + 0.5f) / f;
				for (int px = 0; px < f; px++) {
					float s = 2 * (px + 0.5f) / f - 1;
					float x = a[0] + s * a[3] + t * a[6];
					float y = a[1] + s * a[4] + t * a[7];
					float z = a[2] + s * a[5] + t * a[8];
					float len = (float) Math.sqrt(x * x + y * y + z * z);
					shade(id, x / len, y / len, z / len, rgb);
					// A touch of dither so smooth gradients don't band.
					float d = (hash(px, py, face) - 0.5f) / 255f;
					out[(oy + py) * w + ox + px] = 0xFF000000 | channel(rgb[0] + d) << 16 | channel(rgb[1] + d) << 8 | channel(rgb[2] + d);
				}
			}
		}
		return out;
	}

	private static void shade(String id, float x, float y, float z, float[] o) {
		switch (id) {
			case "aurora":
				aurora(x, y, z, o);
				break;
			case "nebula":
				nebula(x, y, z, o);
				break;
			case "golden":
				golden(x, y, z, o);
				break;
			case "synthwave":
				synthwave(x, y, z, o);
				break;
			default:
				eclipse(x, y, z, o);
		}
	}

	// ---- Skies ------------------------------------------------------------

	/** A clear polar night: deep blue, a field of stars, and green-to-violet curtains rippling across the north. */
	private static void aurora(float x, float y, float z, float[] o) {
		if (y >= 0) {
			set(o, mix(0x0E2A4A, 0x03061A, smooth(0f, 0.65f, y)));
		} else {
			set(o, mix(0x0E2A4A, 0x04070F, smooth(0f, 0.3f, -y)));
		}
		add(o, 0xDCE8FF, stars(x, y, z, 95, 0.09f) * smooth(0.0f, 0.25f, y));
		if (y > 0.0f) {
			float az = (float) Math.atan2(z, x);
			float warp = fbm(x * 1.4f, y * 0.6f, z * 1.4f, 4);
			float wave = (float) Math.sin(az * 3 + warp * 5.5f);
			float band = (float) Math.exp(-wave * wave * 5);
			float base = 0.1f + 0.08f * noise(x * 2 + 9, 0, z * 2);
			float top = base + 0.38f;
			float rise = smooth(base - 0.04f, base + 0.015f, y) * (1 - smooth(base + 0.05f, top, y));
			float rays = 0.45f + 0.55f * noise(x * 28, y * 2, z * 28);
			float north = 0.35f + 0.65f * smooth(-0.3f, 0.8f, -z);
			float k = band * rise * rays * north * 1.5f;
			add(o, mixRgb(0x34FFB0, 0xA35CFF, smooth(base, top, y)), k);
			// Its light spilling onto the sky near the horizon.
			add(o, 0x1FB58A, band * north * 0.12f * (1 - smooth(0f, 0.35f, y)));
		}
	}

	/** Deep space: a tilted band of stars, glowing magenta-to-cyan clouds and dark dust lanes. */
	private static void nebula(float x, float y, float z, float[] o) {
		set(o, 0x05030C);
		float d = x * 0.31f + y * 0.78f + z * 0.54f;
		float band = (float) Math.exp(-d * d * 9);
		add(o, 0x241540, band * 0.6f);
		float n = fbm(x * 2.2f + 5, y * 2.2f, z * 2.2f, 5);
		float n2 = fbm(x * 3 + 11, y * 3, z * 3, 4);
		float cloud = smooth(0.42f, 0.85f, n) * (0.35f + 0.85f * band);
		add(o, mixRgb(0xC2389E, 0x3A4BD8, n2), cloud * 0.95f);
		add(o, 0x3FD0E0, cloud * smooth(0.58f, 0.85f, n2) * 0.6f);
		float dust = smooth(0.55f, 0.8f, fbm(x * 5 + 2, y * 5, z * 5, 3)) * band;
		scale(o, 1 - 0.55f * dust);
		add(o, 0xFFFFFF, stars(x, y, z, 110, 0.12f + band * 0.25f) * (0.7f + band));
		add(o, 0xCFE0FF, stars(x + 3, y, z, 32, 0.035f) * 1.4f);
	}

	/** A low sun in the west, warm gradient and wispy clouds lit pink and gold from the side. */
	private static void golden(float x, float y, float z, float[] o) {
		if (y < 0) {
			set(o, mix(0xE9A27C, 0x2B2236, smooth(0f, 0.25f, -y)));
		} else if (y < 0.12f) {
			set(o, mix(0xFFD39A, 0xF59A6A, y / 0.12f));
		} else if (y < 0.45f) {
			set(o, mix(0xF59A6A, 0x7E5A9E, (y - 0.12f) / 0.33f));
		} else {
			set(o, mix(0x7E5A9E, 0x24356E, smooth(0.45f, 1f, y)));
		}
		float sx = -0.86f;
		float sy = 0.12f;
		float sz = 0.49f;
		float c = x * sx + y * sy + z * sz;
		float sun = Math.max(0, c);
		add(o, 0xFFB070, (float) (Math.pow(sun, 64) * 0.7 + Math.pow(sun, 7) * 0.28));
		add(o, 0xFFF4D6, smooth(0.9984f, 0.9991f, c) * 1.5f);
		if (y > 0) {
			float h = y + 0.12f;
			float cl = fbm(x / h * 0.9f, 3.1f, z / h * 0.9f, 5);
			float cover = smooth(0.5f, 0.74f, cl) * smooth(0f, 0.07f, y) * (1 - smooth(0.45f, 0.85f, y));
			int lit = mix(0x7A4E7E, 0xFFC08A, Math.min(1f, (float) Math.pow(sun, 3) * 0.9f + 0.25f));
			blend(o, lit, cover * 0.88f);
		}
	}

	/** Retro neon: a striped sun sinking into a pink horizon, stars above and a glowing grid below. */
	private static void synthwave(float x, float y, float z, float[] o) {
		if (y >= 0) {
			if (y < 0.08f) {
				set(o, mix(0xFF9A5C, 0xFF4FA3, y / 0.08f));
			} else if (y < 0.32f) {
				set(o, mix(0xFF4FA3, 0x7A1FA2, (y - 0.08f) / 0.24f));
			} else {
				set(o, mix(0x7A1FA2, 0x160530, smooth(0.32f, 0.75f, y)));
			}
			add(o, 0xFFFFFF, stars(x, y, z, 80, 0.07f) * smooth(0.35f, 0.7f, y));
			// The sun, low in the south.
			float cx = 0;
			float cy = 0.16f;
			float cz = 0.987f;
			float c = x * cx + y * cy + z * cz;
			float radius = 0.24f;
			float ang = (float) Math.acos(Math.max(-1, Math.min(1, c)));
			add(o, 0xFF3C8E, (float) Math.exp(-Math.max(0, ang - radius) * 9) * 0.45f);
			if (ang < radius) {
				float v = (y - (cy - radius)) / (2 * radius);
				boolean cut = v < 0.5f && Math.sin(y * 140) > 1 - 2.2f * (0.5f - v);
				if (!cut) {
					set(o, mix(0xFF3C8E, 0xFFE45C, Math.max(0, Math.min(1, v))));
				}
			}
		} else {
			set(o, mix(0xC8407E, 0x12031F, smooth(0f, 0.12f, -y)));
			float k = -y;
			float gx = x / k * 0.3f;
			float gz = z / k * 0.3f;
			float lx = Math.abs(gx - (float) Math.floor(gx) - 0.5f);
			float lz = Math.abs(gz - (float) Math.floor(gz) - 0.5f);
			float width = 0.03f + 0.02f / Math.max(0.05f, k * 6);
			float grid = Math.max(smooth(0.5f - width, 0.5f, lx), smooth(0.5f - width, 0.5f, lz));
			add(o, 0xFF3CAC, grid * smooth(0.015f, 0.2f, k) * 0.95f);
		}
	}

	/** A blood-red sky of drifting smoke around a black sun with a burning corona. */
	private static void eclipse(float x, float y, float z, float[] o) {
		set(o, mix(0x4A0F12, 0x12030A, smooth(-0.1f, 0.7f, y)));
		float smoke = fbm(x * 1.8f + 4, y * 1.8f, z * 1.8f, 5);
		add(o, 0x7A1C1C, smooth(0.4f, 0.8f, smoke) * 0.55f);
		add(o, 0xFFC8A0, stars(x, y, z, 90, 0.04f) * 0.5f * smooth(0.1f, 0.5f, y));
		float c = x * 0.3f + y * 0.55f + z * -0.78f;
		float len = 1.0004f;
		float ang = (float) Math.acos(Math.max(-1, Math.min(1, c / len)));
		float r = 0.075f;
		float flicker = 0.75f + 0.5f * noise(x * 30, y * 30, z * 30);
		add(o, 0xFF5A2A, (float) Math.exp(-Math.max(0, ang - r) / 0.09f) * 0.55f * flicker);
		add(o, 0xFFE2B0, (float) Math.exp(-Math.pow((ang - r) / 0.012f, 2)) * 1.6f);
		if (ang < r) {
			set(o, 0x050102);
		}
	}

	// ---- Noise and colour --------------------------------------------------

	static float hash(int x, int y, int z) {
		int h = x * 374761393 + y * 668265263 + z * 1274126177;
		h = (h ^ (h >>> 13)) * 1103515245;
		h ^= h >>> 16;
		return (h & 0xFFFFFF) / (float) 0xFFFFFF;
	}

	static float noise(float x, float y, float z) {
		int xi = (int) Math.floor(x);
		int yi = (int) Math.floor(y);
		int zi = (int) Math.floor(z);
		float fx = x - xi;
		float fy = y - yi;
		float fz = z - zi;
		float u = fx * fx * (3 - 2 * fx);
		float v = fy * fy * (3 - 2 * fy);
		float w = fz * fz * (3 - 2 * fz);
		float x00 = lerp(hash(xi, yi, zi), hash(xi + 1, yi, zi), u);
		float x10 = lerp(hash(xi, yi + 1, zi), hash(xi + 1, yi + 1, zi), u);
		float x01 = lerp(hash(xi, yi, zi + 1), hash(xi + 1, yi, zi + 1), u);
		float x11 = lerp(hash(xi, yi + 1, zi + 1), hash(xi + 1, yi + 1, zi + 1), u);
		return lerp(lerp(x00, x10, v), lerp(x01, x11, v), w);
	}

	static float fbm(float x, float y, float z, int octaves) {
		float sum = 0;
		float amp = 0.5f;
		float norm = 0;
		for (int i = 0; i < octaves; i++) {
			sum += amp * noise(x, y, z);
			norm += amp;
			amp *= 0.5f;
			x *= 2.03f;
			y *= 2.03f;
			z *= 2.03f;
		}
		return sum / norm;
	}

	/** Point stars: one candidate per grid cell, kept with probability {@code density}. */
	static float stars(float x, float y, float z, float scale, float density) {
		float sx = x * scale;
		float sy = y * scale;
		float sz = z * scale;
		int cx = (int) Math.floor(sx);
		int cy = (int) Math.floor(sy);
		int cz = (int) Math.floor(sz);
		if (hash(cx, cy, cz) > density) {
			return 0;
		}
		float dx = sx - cx - (0.2f + 0.6f * hash(cx + 17, cy, cz));
		float dy = sy - cy - (0.2f + 0.6f * hash(cx, cy + 31, cz));
		float dz = sz - cz - (0.2f + 0.6f * hash(cx, cy, cz + 47));
		float size = 0.05f + 0.09f * hash(cx + 5, cy + 9, cz + 3);
		float d2 = dx * dx + dy * dy + dz * dz;
		return (float) Math.exp(-d2 / (size * size)) * (0.45f + 0.55f * hash(cx + 71, cy + 3, cz + 11));
	}

	private static float lerp(float a, float b, float t) {
		return a + (b - a) * t;
	}

	private static float smooth(float e0, float e1, float v) {
		float t = Math.max(0, Math.min(1, (v - e0) / (e1 - e0)));
		return t * t * (3 - 2 * t);
	}

	private static int mix(int a, int b, float t) {
		return mixRgb(a, b, t);
	}

	private static int mixRgb(int a, int b, float t) {
		t = Math.max(0, Math.min(1, t));
		int r = Math.round((a >> 16 & 255) * (1 - t) + (b >> 16 & 255) * t);
		int g = Math.round((a >> 8 & 255) * (1 - t) + (b >> 8 & 255) * t);
		int bl = Math.round((a & 255) * (1 - t) + (b & 255) * t);
		return r << 16 | g << 8 | bl;
	}

	private static void set(float[] o, int rgb) {
		o[0] = (rgb >> 16 & 255) / 255f;
		o[1] = (rgb >> 8 & 255) / 255f;
		o[2] = (rgb & 255) / 255f;
	}

	private static void add(float[] o, int rgb, float k) {
		o[0] += (rgb >> 16 & 255) / 255f * k;
		o[1] += (rgb >> 8 & 255) / 255f * k;
		o[2] += (rgb & 255) / 255f * k;
	}

	private static void blend(float[] o, int rgb, float k) {
		o[0] += ((rgb >> 16 & 255) / 255f - o[0]) * k;
		o[1] += ((rgb >> 8 & 255) / 255f - o[1]) * k;
		o[2] += ((rgb & 255) / 255f - o[2]) * k;
	}

	private static void scale(float[] o, float k) {
		o[0] *= k;
		o[1] *= k;
		o[2] *= k;
	}

	private static int channel(float v) {
		// Soft roll-off instead of a hard clip, so bright glows stay smooth.
		float c = v < 0.8f ? v : 0.8f + 0.2f * (1 - (float) Math.exp(-(v - 0.8f) * 5));
		return Math.max(0, Math.min(255, Math.round(c * 255)));
	}
}
