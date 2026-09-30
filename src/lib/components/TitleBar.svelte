<script lang="ts">
  import { accounts } from "../accounts.svelte";
  import { isMac, modKey } from "../platform";
  import type { PaletteMode } from "./CommandPalette.svelte";
  import Icon from "./Icon.svelte";
  import Logo from "./Logo.svelte";
  import SkinHead from "./SkinHead.svelte";
  import WindowControls from "./WindowControls.svelte";

  let { showActions, onOpenPalette }: { showActions: boolean; onOpenPalette: (mode: PaletteMode) => void } =
    $props();
</script>

<!-- Tauri only starts a window drag when the mousedown target itself carries
     data-tauri-drag-region, so decorative children get pointer-events: none
     and the click falls through to a region that has it. -->
<header class="titlebar" class:mac={isMac} class:bare={!showActions} data-tauri-drag-region>
  <div class="brand">
    <Logo size={16} />
    <span>Doohickey Client</span>
  </div>

  <div class="center" data-tauri-drag-region>
    {#if showActions}
      <button class="search" onclick={() => onOpenPalette("all")}>
        <Icon name="search" size={14} />
        <span>Search or run a command</span>
        <span class="keys"><kbd>{modKey}</kbd><kbd>K</kbd></span>
      </button>
    {/if}
  </div>

  <div class="right" data-tauri-drag-region>
    {#if showActions && accounts.active}
      <button class="account" title="Switch account" onclick={() => onOpenPalette("accounts")}>
        <SkinHead skinUrl={accounts.active.skinUrl} name={accounts.active.username} size={20} />
        <span>{accounts.active.username}</span>
        <Icon name="chevron" size={14} />
      </button>
    {/if}
    {#if !isMac}
      <WindowControls />
    {/if}
  </div>
</header>

<style>
  .titlebar {
    position: relative;
    z-index: 10;
    display: grid;
    grid-template-columns: 1fr minmax(0, 420px) 1fr;
    align-items: center;
    flex-shrink: 0;
    height: var(--titlebar-h);
    border-bottom: 1px solid var(--border);
    background: var(--bg);
  }
  .titlebar.bare {
    border-bottom-color: transparent;
    background: transparent;
  }
  .brand {
    display: flex;
    align-items: center;
    gap: 8px;
    padding-left: 14px;
    color: var(--text-muted);
    font-size: 12.5px;
    font-weight: 600;
    letter-spacing: 0.01em;
    pointer-events: none;
  }
  /* Room for the native traffic lights (titleBarStyle: Overlay). */
  .mac .brand {
    padding-left: 84px;
  }
  .center {
    display: flex;
    justify-content: center;
    height: 100%;
    align-items: center;
  }
  .search {
    display: flex;
    align-items: center;
    gap: 8px;
    width: 100%;
    height: 28px;
    padding: 0 6px 0 10px;
    border: 1px solid var(--border);
    border-radius: 7px;
    background: var(--bg-subtle);
    color: var(--text-faint);
    font-size: 12.5px;
    cursor: pointer;
    transition:
      border-color 0.15s,
      color 0.15s;
  }
  .search:hover {
    border-color: var(--border-strong);
    color: var(--text-muted);
  }
  .keys {
    display: flex;
    gap: 3px;
    margin-left: auto;
  }
  .right {
    display: flex;
    justify-content: flex-end;
    align-items: center;
    gap: 6px;
    height: 100%;
  }
  .mac .right {
    padding-right: 12px;
  }
  .account {
    display: flex;
    align-items: center;
    gap: 8px;
    height: 28px;
    max-width: 200px;
    padding: 0 6px 0 4px;
    border: none;
    border-radius: 7px;
    background: transparent;
    color: var(--text-muted);
    font-size: 12.5px;
    cursor: pointer;
    transition:
      background-color 0.15s,
      color 0.15s;
  }
  .account span {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  .account:hover {
    background: var(--bg-hover);
    color: var(--text);
  }
</style>
