<script lang="ts">
  import { getCurrentWindow } from "@tauri-apps/api/window";

  const win = getCurrentWindow();
  let maximized = $state(false);

  $effect(() => {
    let disposed = false;
    let unlisten: (() => void) | undefined;
    const sync = async () => {
      maximized = await win.isMaximized();
    };
    void sync();
    win.onResized(sync).then((fn) => (disposed ? fn() : (unlisten = fn)));
    return () => {
      disposed = true;
      unlisten?.();
    };
  });
</script>

<!-- Windows 11 caption-button proportions (46×40) so the window feels native.
     tabindex=-1: native caption buttons aren't in the tab order either. -->
<div class="controls">
  <button tabindex="-1" aria-label="Minimize" onclick={() => win.minimize()}>
    <svg width="10" height="10" viewBox="0 0 10 10"><path d="M0 5.5h10" /></svg>
  </button>
  <button tabindex="-1" aria-label={maximized ? "Restore" : "Maximize"} onclick={() => win.toggleMaximize()}>
    {#if maximized}
      <svg width="10" height="10" viewBox="0 0 10 10"><path d="M2.5 2.5V.5h7v7h-2M.5 2.5h7v7h-7z" /></svg>
    {:else}
      <svg width="10" height="10" viewBox="0 0 10 10"><path d="M.5.5h9v9h-9z" /></svg>
    {/if}
  </button>
  <button tabindex="-1" class="close" aria-label="Close" onclick={() => win.close()}>
    <svg width="10" height="10" viewBox="0 0 10 10"><path d="M.5.5l9 9M9.5.5l-9 9" /></svg>
  </button>
</div>

<style>
  .controls {
    display: flex;
    height: 100%;
  }
  button {
    display: grid;
    place-items: center;
    width: 46px;
    height: 100%;
    border: none;
    background: transparent;
    color: var(--text-muted);
    transition: background-color 0.1s;
  }
  button:hover {
    background: var(--bg-hover);
    color: var(--text);
  }
  button:active {
    background: var(--bg-active);
  }
  .close:hover {
    background: #c42b1c;
    color: #fff;
  }
  .close:active {
    background: #b0281a;
  }
  svg {
    fill: none;
    stroke: currentColor;
    stroke-width: 1;
  }
</style>
