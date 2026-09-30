// Generates Doohickey's cosmetic textures — original designs, no Mojang
// artwork. Run: node tools/make-textures.mjs
//
// Cosmetic textures are 64×32 and use Minecraft's box UV layout. The box
// sizes and texture offsets here must match cosmetics/CosmeticModels.java.
import fs from "node:fs";
import path from "node:path";
import zlib from "node:zlib";

const ROOT = path.join(import.meta.dirname, "..", "versions/26.3/src/main/resources/assets/quartz/textures");

function crc32(buf) {
  let c, crc = 0xffffffff;
  for (let n = 0; n < buf.length; n++) {
    c = (crc ^ buf[n]) & 0xff;
    for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
    crc = (crc >>> 8) ^ c;
  }
  return (crc ^ 0xffffffff) >>> 0;
}

function chunk(type, data) {
  const len = Buffer.alloc(4);
  len.writeUInt32BE(data.length);
  const td = Buffer.concat([Buffer.from(type), data]);
  const crc = Buffer.alloc(4);
  crc.writeUInt32BE(crc32(td));
  return Buffer.concat([len, td, crc]);
}

function encode(img) {
  const { w, h, px } = img;
  const raw = Buffer.alloc((w * 4 + 1) * h);
  for (let y = 0; y < h; y++) {
    raw[y * (w * 4 + 1)] = 0;
    for (let x = 0; x < w; x++) raw.set(px[y * w + x], y * (w * 4 + 1) + 1 + x * 4);
  }
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(w, 0);
  ihdr.writeUInt32BE(h, 4);
  ihdr.set([8, 6, 0, 0, 0], 8);
  return Buffer.concat([
    Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
    chunk("IHDR", ihdr),
    chunk("IDAT", zlib.deflateSync(raw)),
    chunk("IEND", Buffer.alloc(0)),
  ]);
}

const image = (w = 64, h = 32) => ({ w, h, px: Array.from({ length: w * h }, () => [0, 0, 0, 0]) });
const set = (img, x, y, c) => {
  if (x >= 0 && y >= 0 && x < img.w && y < img.h) img.px[y * img.w + x] = c.length === 4 ? c : [...c, 255];
};
const mix = (a, b, t) => a.map((v, i) => Math.round(v + (b[i] - v) * t));
const hex = (s) => [1, 3, 5].map((i) => parseInt(s.slice(i, i + 2), 16));
/** Deterministic per-pixel noise in [0, 1). */
const noise = (x, y, seed = 0) => {
  let n = (x * 374761393 + y * 668265263 + seed * 2147483647) >>> 0;
  n = ((n ^ (n >>> 13)) * 1274126177) >>> 0;
  return ((n ^ (n >>> 16)) >>> 0) / 4294967296;
};

/**
 * Paints one box's six faces. `paint(face, fx, fy, fw, fh)` returns a colour;
 * fx/fy are the pixel inside the face, fy = 0 at the face's top (or, for the
 * top/bottom faces, its back edge).
 */
function box(img, u, v, w, h, d, paint) {
  const faces = [
    ["top", u + d, v, w, d],
    ["bottom", u + d + w, v, w, d],
    ["west", u, v + d, d, h],
    ["north", u + d, v + d, w, h],
    ["east", u + d + w, v + d, d, h],
    ["south", u + 2 * d + w, v + d, w, h],
  ];
  for (const [face, x0, y0, fw, fh] of faces)
    for (let fy = 0; fy < fh; fy++)
      for (let fx = 0; fx < fw; fx++) {
        const c = paint(face, fx, fy, fw, fh);
        if (c) set(img, x0 + fx, y0 + fy, c);
      }
}

/** Fills a whole region — for parts that share one material. */
function fill(img, x0, y0, w, h, paint) {
  for (let y = y0; y < y0 + h; y++) for (let x = x0; x < x0 + w; x++) set(img, x, y, paint(x, y));
}

// ---- Capes (64×32, vanilla cape layout) -------------------------------------

const capes = {
  quartz: (x, y) => {
    const base = mix([124, 108, 242], [63, 52, 150], y / 17);
    // A faceted crystal band across the back of the cape.
    const facet = (x + y) % 6 === 0 || (x - y + 64) % 6 === 0;
    return facet ? mix(base, [200, 190, 255], 0.35) : base;
  },
  aurora: (x, y) => {
    const t = (Math.sin(x / 3) + 1) / 2;
    return mix(mix([26, 180, 160], [120, 80, 230], t), [10, 16, 40], y / 20);
  },
  ember: (x, y) => {
    const flicker = (Math.sin(x * 1.7 + y * 0.9) + 1) / 2;
    return mix(mix([255, 170, 40], [200, 40, 30], y / 17), [255, 220, 120], flicker * 0.25);
  },
  void: (x, y) => {
    const star = (x * 7 + y * 13) % 29 === 0;
    return star ? [230, 220, 255] : mix([22, 18, 40], [6, 6, 12], y / 17);
  },
};

// ---- Hats ---------------------------------------------------------------------

function topHat() {
  const img = image();
  const felt = (x, y, k = 0) => mix(hex("#232329"), hex("#34343d"), noise(x, y, k) * 0.5);
  // Brim: 12×1×12 at (0, 0).
  box(img, 0, 0, 12, 1, 12, (f, x, y, w, h) => {
    const edge = x === 0 || y === 0 || x === w - 1 || y === h - 1;
    return edge && (f === "top" || f === "bottom") ? hex("#18181d") : felt(x, y, 1);
  });
  // Crown: 8×7×8 at (0, 13); a lilac band around its base.
  box(img, 0, 13, 8, 7, 8, (f, x, y, w, h) => {
    if (f === "top") return mix(felt(x, y, 2), hex("#3d3d47"), 0.3);
    if (f === "bottom") return felt(x, y, 3);
    if (y >= h - 2) return y === h - 2 ? hex("#9d90ff") : hex("#7563e8");
    return felt(x, y, 4);
  });
  return img;
}

function crown() {
  const img = image();
  const gold = (x, y) => {
    const n = noise(x, y, 7);
    return mix(hex("#f6c945"), n > 0.7 ? hex("#fff0a0") : hex("#d69a1c"), n > 0.7 ? 0.6 : n * 0.5);
  };
  fill(img, 0, 0, 48, 32, gold);
  // Gem material at (56, 24): ruby with a highlight.
  fill(img, 56, 24, 8, 8, (x, y) => ((x + y) % 5 === 0 ? hex("#ff9aa8") : mix(hex("#e0324a"), hex("#8e1024"), noise(x, y, 9))));
  // Sapphire at (48, 24).
  fill(img, 48, 24, 8, 8, (x, y) => ((x + y) % 5 === 0 ? hex("#a8c8ff") : mix(hex("#3060e0"), hex("#152f8a"), noise(x, y, 11))));
  return img;
}

function halo() {
  const img = image();
  fill(img, 0, 0, 32, 16, (x, y) => mix(hex("#fff7c8"), hex("#ffe27a"), noise(x, y, 5) * 0.6));
  return img;
}

// ---- Bandanas -------------------------------------------------------------------

function bandana(base, dot) {
  const img = image();
  // One cloth material everywhere: band (0,0), knot (0,10), tails (8,10).
  fill(img, 0, 0, 32, 16, (x, y) => {
    const n = noise(x, y, 3);
    // A small repeating paisley-ish motif: dots and tiny diamonds.
    const motif = (x % 4 === 1 && y % 4 === 1) || ((x + 2) % 4 === 1 && (y + 2) % 4 === 1 && n > 0.4);
    return motif ? dot : mix(base, [0, 0, 0], n * 0.12);
  });
  return img;
}

// ---- Wings ---------------------------------------------------------------------
// Each wing is a flat 14×20 plane at (0, 0): the front face spans x 0..13,
// the back face x 14..27 — mirrored, so both show the same silhouette.

const WING_W = 14;
const WING_H = 20;

function wings(shape) {
  const img = image();
  for (let fy = 0; fy < WING_H; fy++)
    for (let fx = 0; fx < WING_W; fx++) {
      const c = shape(fx, fy);
      if (!c) continue;
      set(img, fx, fy, c);
      set(img, WING_W + (WING_W - 1 - fx), fy, c);
    }
  return img;
}

/** Wing outline per column, fx = 0 at the root (by the spine) → 13 at the tip:
 *  a high leading edge, longest feathers past mid-wing, a pointed tip. */
const TOP = [6, 4, 3, 2, 1, 1, 0, 0, 0, 0, 1, 1, 2, 3];
const BOTTOM = [9, 11, 13, 14, 15, 16, 17, 18, 18, 17, 15, 12, 9, 5];
const wingTop = (fx) => TOP[fx];
const wingBottom = (fx) => BOTTOM[fx] - (fx % 3 === 2 && fx < 12 ? 1 : 0);

const angel = (fx, fy) => {
  const top = wingTop(fx);
  const bottom = wingBottom(fx);
  if (fy < top || fy > bottom) return null;
  if (fy === top) return hex("#c9ccd8");
  const feather = fx % 3 === 2 ? 0.14 : 0;
  const coverts = fy < top + 4 ? 0.06 : 0; // smaller feathers near the leading edge
  return mix(hex("#fbfbff"), hex("#b8bccb"), feather + coverts + noise(fx, fy, 21) * 0.08 + (fy / WING_H) * 0.12);
};

const dragon = (fx, fy) => {
  const top = wingTop(fx);
  // Scalloped membrane between the finger bones.
  const scallop = fx % 4 === 0 ? 0 : Math.round(2 * Math.sin(((fx % 4) / 4) * Math.PI));
  const bottom = BOTTOM[fx] - scallop;
  if (fy < top || fy > bottom) return null;
  if (fy === top || fx % 4 === 0) return hex("#1a1420");
  return mix(hex("#4a1f5e"), hex("#8a1f3a"), ((fy - top) / (bottom - top + 1)) * 0.7 + noise(fx, fy, 31) * 0.1);
};

const crystal = (fx, fy) => {
  const top = wingTop(fx);
  const bottom = BOTTOM[fx] - (fx % 2);
  if (fy < top || fy > bottom) return null;
  if (fy === top || fy === bottom) return hex("#e4ddff");
  if ((fx + fy) % 5 === 0 || (fx * 2 - fy + 40) % 7 === 0) return hex("#cfc6ff");
  return mix(hex("#8b7cf6"), hex("#4f3fc4"), ((fy - top) / (bottom - top + 1)) * 0.8 + noise(fx, fy, 41) * 0.1);
};

// ---- Write ---------------------------------------------------------------------

function write(dir, name, img) {
  fs.mkdirSync(path.join(ROOT, dir), { recursive: true });
  fs.writeFileSync(path.join(ROOT, dir, `${name}.png`), encode(img));
  console.log(`wrote ${dir}/${name}.png`);
}

for (const [name, paint] of Object.entries(capes)) {
  const img = image();
  fill(img, 0, 0, 64, 32, paint);
  write("cape", name, img);
}
write("cosmetic", "hat_tophat", topHat());
write("cosmetic", "hat_crown", crown());
write("cosmetic", "hat_halo", halo());
write("cosmetic", "bandana_red", bandana(hex("#c42a3c"), hex("#f4eee6")));
write("cosmetic", "bandana_black", bandana(hex("#1c1c22"), hex("#e8e8ee")));
write("cosmetic", "bandana_quartz", bandana(hex("#7563e8"), hex("#d8d2ff")));
write("cosmetic", "wings_angel", wings(angel));
write("cosmetic", "wings_dragon", wings(dragon));
write("cosmetic", "wings_crystal", wings(crystal));
