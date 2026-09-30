<script lang="ts" generics="T">
  import type { Snippet } from "svelte";

  // Fixed-height virtualization: only the rows in view (plus a small overscan)
  // exist in the DOM, so a 5000-line console or a 900-version list scrolls at
  // full frame rate.
  let {
    items,
    rowHeight,
    row,
    key = (_item: T, i: number) => i,
    overscan = 8,
    follow = false,
    onEndReached,
    class: cls = "",
  }: {
    items: T[];
    rowHeight: number;
    row: Snippet<[T, number]>;
    key?: (item: T, index: number) => unknown;
    overscan?: number;
    /** Keep the view pinned to the bottom while the user hasn't scrolled up. */
    follow?: boolean;
    onEndReached?: () => void;
    class?: string;
  } = $props();

  let viewport: HTMLDivElement;
  let scrollTop = $state(0);
  let height = $state(0);
  let pinned = true;

  const start = $derived(Math.max(0, Math.floor(scrollTop / rowHeight) - overscan));
  const end = $derived(Math.min(items.length, Math.ceil((scrollTop + height) / rowHeight) + overscan));
  const visible = $derived(items.slice(start, end));

  function onscroll() {
    scrollTop = viewport.scrollTop;
    pinned = viewport.scrollTop + viewport.clientHeight >= viewport.scrollHeight - rowHeight;
    if (onEndReached && scrollTop + height >= items.length * rowHeight - rowHeight * 6) onEndReached();
  }

  $effect(() => {
    void items.length;
    if (follow && pinned && viewport) {
      viewport.scrollTop = viewport.scrollHeight;
      scrollTop = viewport.scrollTop;
    }
  });

  export function scrollToEnd() {
    pinned = true;
    viewport.scrollTop = viewport.scrollHeight;
  }
</script>

<div class="viewport {cls}" bind:this={viewport} bind:clientHeight={height} {onscroll}>
  <div class="spacer" style:height="{items.length * rowHeight}px">
    <div class="window" style:transform="translateY({start * rowHeight}px)">
      {#each visible as item, i (key(item, start + i))}
        <div class="vrow" style:height="{rowHeight}px">{@render row(item, start + i)}</div>
      {/each}
    </div>
  </div>
</div>

<style>
  .viewport {
    position: relative;
    overflow-y: auto;
    contain: strict;
  }
  .spacer {
    position: relative;
  }
  .window {
    position: absolute;
    inset: 0 0 auto 0;
    will-change: transform;
  }
  .vrow {
    overflow: hidden;
  }
</style>
