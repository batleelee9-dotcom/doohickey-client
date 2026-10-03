<script lang="ts" module>
  // Original artwork, drawn in code: a blocky night landscape with a square
  // moon. Each build gets its own palette and terrain from its id, so every
  // version looks different without shipping any Mojang assets.
  const PALETTES = [
    { sky: ["#0d0a24", "#2a1b5e"], glow: "#9d8cff", hills: ["#2b2266", "#1a1446", "#0d0a26"] },
    { sky: ["#04161d", "#0e3e4b"], glow: "#46e0d3", hills: ["#105061", "#0a3340", "#051c24"] },
    { sky: ["#1c0b08", "#5c2614"], glow: "#ffa057", hills: ["#62301c", "#3f1d11", "#200e08"] },
    { sky: ["#06150d", "#14432a"], glow: "#6ff09a", hills: ["#185237", "#103722", "#081e12"] },
    { sky: ["#060e20", "#132f63"], glow: "#62a8ff", hills: ["#1a3a75", "#11264f", "#08142d"] },
    { sky: ["#1a0716", "#53163f"], glow: "#ff6fb4", hills: ["#5a1c47", "#3a122e", "#1d0918"] },
  ];

  function hash(s: string) {
    let h = 2166136261;
    for (let i = 0; i < s.length; i++) h = Math.imul(h ^ s.charCodeAt(i), 16777619);
    return h >>> 0;
  }

  /** mulberry32: a tiny seeded RNG, so the same build always draws the same scene. */
  function rng(seed: number) {
    return () => {
      seed = (seed + 0x6d2b79f5) | 0;
      let t = Math.imul(seed ^ (seed >>> 15), 1 | seed);
      t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
      return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
    };
  }

  const W = 1200;
  const H = 400;

  /** A stepped skyline: flat runs of blocks that climb and fall a block at a time. */
  function terrain(r: () => number, base: number, amp: number, block: number) {
    let y = base;
    let d = `M0 ${H} V${y}`;
    for (let x = 0; x < W; x += block) {
      const step = r();
      if (step < 0.3) y -= block;
      else if (step > 0.7) y += block;
      y = Math.max(base - amp, Math.min(base + amp, y));
      d += ` H${x} V${y}`;
    }
    return `${d} H${W} V${H} Z`;
  }

  let uid = 0;
</script>

<script lang="ts">
  /** `tone` picks the colour scheme directly (e.g. a build's position), so neighbours never match. */
  let { seed, tone, stars = true }: { seed: string; tone?: number; stars?: boolean } = $props();

  const id = `art${uid++}`;
  const scene = $derived.by(() => {
    const h = hash(seed);
    const r = rng(h);
    const palette = PALETTES[(tone ?? h) % PALETTES.length];
    return {
      palette,
      moon: { x: 760 + r() * 280, y: 50 + r() * 50 },
      stars: Array.from({ length: 70 }, () => ({ x: r() * W, y: r() * 210, s: r() < 0.15 ? 4 : 2, o: 0.25 + r() * 0.7 })),
      clouds: Array.from({ length: 3 }, () => ({ x: r() * 1000, y: 90 + r() * 90, w: 3 + Math.floor(r() * 4) })),
      hills: [terrain(r, 230, 48, 24), terrain(r, 285, 40, 32), terrain(r, 340, 32, 40)],
    };
  });
</script>

<svg class="art" viewBox="0 0 {W} {H}" preserveAspectRatio="xMidYMid slice" aria-hidden="true">
  <defs>
    <linearGradient id="{id}-sky" x1="0" y1="0" x2="0" y2="1">
      <stop offset="0" stop-color={scene.palette.sky[0]} />
      <stop offset="1" stop-color={scene.palette.sky[1]} />
    </linearGradient>
    <radialGradient id="{id}-glow">
      <stop offset="0" stop-color={scene.palette.glow} stop-opacity="0.55" />
      <stop offset="1" stop-color={scene.palette.glow} stop-opacity="0" />
    </radialGradient>
  </defs>
  <rect width={W} height={H} fill="url(#{id}-sky)" />
  <circle cx={scene.moon.x + 22} cy={scene.moon.y + 22} r="170" fill="url(#{id}-glow)" />
  {#if stars}
    {#each scene.stars as s, i (i)}
      <rect x={s.x} y={s.y} width={s.s} height={s.s} fill="#fff" opacity={s.o} />
    {/each}
  {/if}
  <!-- The moon is square, as it should be. -->
  <rect x={scene.moon.x} y={scene.moon.y} width="44" height="44" fill="#f4f1ff" />
  <rect x={scene.moon.x + 8} y={scene.moon.y + 10} width="10" height="10" fill="#d9d3f2" />
  <rect x={scene.moon.x + 26} y={scene.moon.y + 24} width="8" height="8" fill="#d9d3f2" />
  {#each scene.clouds as c, i (i)}
    <g opacity="0.07" fill="#fff">
      <rect x={c.x} y={c.y} width={c.w * 24} height="12" />
      <rect x={c.x + 24} y={c.y - 12} width={(c.w - 2) * 24} height="12" />
    </g>
  {/each}
  {#each scene.hills as d, i (i)}
    <path {d} fill={scene.palette.hills[i]} />
  {/each}
</svg>

<style>
  .art {
    position: absolute;
    inset: 0;
    width: 100%;
    height: 100%;
    display: block;
  }
</style>
