<script lang="ts">
  let { skinUrl, name, size = 32 }: { skinUrl: string | null; name: string; size?: number } = $props();

  let failed = $state(false);

  // A skin is a 64×64 (or legacy 64×32) texture. The face is the 8×8 square at
  // (8, 8) and the hat overlay the one at (40, 8). Scaling the whole texture by
  // size/8 lets background-position crop them — no canvas, no pixel reads.
  const k = $derived(size / 8);

  // Offline accounts (or a skin that fails to load) get a letter tile instead
  // of a Mojang default-skin asset. FNV-1a spreads similar names apart, and a
  // curated hue set avoids muddy olive/brown tiles.
  const HUES = [262, 214, 188, 158, 32, 338, 8, 286];
  const hue = $derived.by(() => {
    let h = 0x811c9dc5;
    for (const c of name) h = Math.imul(h ^ c.charCodeAt(0), 0x01000193);
    return HUES[(h >>> 0) % HUES.length];
  });
</script>

{#if skinUrl && !failed}
  <span class="head" role="img" aria-label={name} style:width="{size}px" style:height="{size}px">
    {#each [8, 40] as x (x)}
      <span
        class="layer"
        style:background-image={`url("${skinUrl}")`}
        style:background-size="{64 * k}px auto"
        style:background-position="{-x * k}px {-8 * k}px"
      ></span>
    {/each}
    <!-- Invisible probe: CSS backgrounds fail silently, <img> tells us. -->
    <img src={skinUrl} alt="" hidden onerror={() => (failed = true)} />
  </span>
{:else}
  <span
    class="head letter"
    role="img"
    aria-label={name}
    style:width="{size}px"
    style:height="{size}px"
    style:font-size="{size * 0.46}px"
    style:--hue={hue}
  >
    {name.charAt(0).toUpperCase()}
  </span>
{/if}

<style>
  .head {
    position: relative;
    display: inline-grid;
    flex-shrink: 0;
    border-radius: 22%;
    overflow: hidden;
  }
  .layer {
    position: absolute;
    inset: 0;
    background-repeat: no-repeat;
    image-rendering: pixelated;
  }
  .letter {
    place-items: center;
    font-weight: 600;
    color: hsl(var(--hue) 75% 86%);
    background: hsl(var(--hue) 42% 30%);
  }
  :global([data-theme="light"]) .letter {
    color: hsl(var(--hue) 45% 30%);
    background: hsl(var(--hue) 55% 88%);
  }
</style>
