<script lang="ts" module>
  import type { IconName } from "./Icon.svelte";
  export interface MenuItem {
    label: string;
    icon?: IconName;
    danger?: boolean;
    disabled?: boolean;
    run: () => unknown;
  }
</script>

<script lang="ts">
  import { fly } from "svelte/transition";
  import { dur } from "../platform";
  import Icon from "./Icon.svelte";

  let { items, label = "More actions" }: { items: MenuItem[]; label?: string } = $props();
  let open = $state(false);

  function run(item: MenuItem) {
    open = false;
    void item.run();
  }
</script>

<div class="menu">
  <button class="icon-btn" aria-label={label} title={label} aria-expanded={open} onclick={() => (open = !open)}>
    <Icon name="more" size={16} />
  </button>
  {#if open}
    <button class="outside" tabindex="-1" aria-label="Close menu" onclick={() => (open = false)}></button>
    <div class="list" role="menu" transition:fly={{ y: -4, duration: dur(120) }}>
      {#each items as item (item.label)}
        <button role="menuitem" class="entry" class:danger={item.danger} disabled={item.disabled} onclick={() => run(item)}>
          {#if item.icon}<Icon name={item.icon} size={14} />{/if}
          {item.label}
        </button>
      {/each}
    </div>
  {/if}
</div>

<style>
  .menu {
    position: relative;
  }
  .outside {
    position: fixed;
    inset: 0;
    z-index: 40;
    border: none;
    background: transparent;
    cursor: default;
  }
  .list {
    position: absolute;
    top: calc(100% + 4px);
    right: 0;
    z-index: 41;
    display: flex;
    flex-direction: column;
    min-width: 190px;
    padding: 4px;
    border-radius: var(--radius);
    background: var(--bg-elevated);
    box-shadow: var(--shadow-lg);
  }
  .entry {
    display: flex;
    align-items: center;
    gap: 10px;
    height: 32px;
    padding: 0 10px;
    border: none;
    border-radius: 6px;
    background: transparent;
    color: var(--text-muted);
    text-align: left;
    white-space: nowrap;
    cursor: pointer;
  }
  .entry:hover:not(:disabled) {
    background: var(--bg-hover);
    color: var(--text);
  }
  .entry.danger:hover:not(:disabled) {
    background: var(--danger-soft);
    color: var(--danger);
  }
  .entry:disabled {
    opacity: 0.45;
    cursor: not-allowed;
  }
</style>
