<script lang="ts">
  import type { Snippet } from "svelte";
  import { fade, scale } from "svelte/transition";
  import { dur } from "../platform";
  import Icon from "./Icon.svelte";

  let {
    title,
    width = 480,
    onClose,
    children,
    footer,
  }: {
    title: string;
    width?: number;
    onClose: () => void;
    children: Snippet;
    footer?: Snippet;
  } = $props();

  let dialog: HTMLDivElement;

  $effect(() => {
    const previous = document.activeElement as HTMLElement | null;
    // Focus the dialog's default action if it marks one, else its first field.
    (
      dialog.querySelector<HTMLElement>("[data-autofocus]") ??
      dialog.querySelector<HTMLElement>("input, select, textarea") ??
      dialog
    ).focus();
    return () => previous?.focus?.();
  });

  function onkeydown(e: KeyboardEvent) {
    if (e.key === "Escape") {
      e.stopPropagation();
      onClose();
    }
  }
</script>

<div class="backdrop" transition:fade={{ duration: dur(120) }}>
  <button class="dismiss" tabindex="-1" aria-label="Close" onclick={onClose}></button>
  <div
    class="modal"
    role="dialog"
    aria-modal="true"
    aria-label={title}
    tabindex="-1"
    bind:this={dialog}
    style:width="min({width}px, calc(100vw - 32px))"
    {onkeydown}
    transition:scale={{ start: 0.97, duration: dur(150) }}
  >
    <header>
      <h2>{title}</h2>
      <button class="icon-btn close" aria-label="Close" onclick={onClose}><Icon name="x" size={14} /></button>
    </header>
    <div class="body">{@render children()}</div>
    {#if footer}<footer>{@render footer()}</footer>{/if}
  </div>
</div>

<style>
  .backdrop {
    position: fixed;
    inset: 0;
    z-index: 150;
    display: grid;
    place-items: center;
    padding: 24px;
    background: var(--overlay);
  }
  .dismiss {
    position: absolute;
    inset: 0;
    border: none;
    background: transparent;
    cursor: default;
  }
  .modal {
    position: relative;
    display: flex;
    flex-direction: column;
    max-height: calc(100vh - 64px);
    border-radius: var(--radius-lg);
    background: var(--bg-elevated);
    box-shadow: var(--shadow-lg);
    outline: none;
    overflow: hidden;
  }
  header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 14px 14px 10px 20px;
  }
  h2 {
    font-size: 15px;
    font-weight: 600;
  }
  .body {
    padding: 4px 20px 20px;
    overflow-y: auto;
  }
  footer {
    display: flex;
    justify-content: flex-end;
    gap: 8px;
    padding: 12px 16px;
    border-top: 1px solid var(--border);
  }
</style>
