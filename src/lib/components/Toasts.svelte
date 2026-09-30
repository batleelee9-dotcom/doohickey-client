<script lang="ts">
  import { flip } from "svelte/animate";
  import { fly } from "svelte/transition";
  import { dur } from "../platform";
  import { store } from "../store.svelte";
  import Icon from "./Icon.svelte";
</script>

<div class="toasts" aria-live="polite">
  {#each store.toasts as t (t.id)}
    <div class="toast {t.kind}" role={t.kind === "error" ? "alert" : "status"} animate:flip={{ duration: dur(180) }} transition:fly={{ y: 8, duration: dur(180) }}>
      <span class="icon"><Icon name={t.kind === "error" ? "alert" : t.kind === "success" ? "check" : "info"} size={15} /></span>
      <span class="msg" data-selectable>{t.message}</span>
      {#if t.action}
        {@const action = t.action}
        <button class="btn btn-secondary btn-sm" onclick={() => { action.run(); store.dismiss(t.id); }}>{action.label}</button>
      {/if}
      <button class="icon-btn" aria-label="Dismiss" onclick={() => store.dismiss(t.id)}><Icon name="x" size={13} /></button>
    </div>
  {/each}
</div>

<style>
  .toasts {
    position: fixed;
    right: 20px;
    bottom: 20px;
    z-index: 300;
    display: flex;
    flex-direction: column;
    align-items: flex-end;
    gap: 8px;
    pointer-events: none;
  }
  .toast {
    display: flex;
    align-items: center;
    gap: 10px;
    max-width: 440px;
    padding: 9px 8px 9px 13px;
    border-radius: var(--radius);
    background: var(--bg-elevated);
    box-shadow: var(--shadow-lg);
    pointer-events: auto;
  }
  .icon {
    display: grid;
    color: var(--text-muted);
  }
  .error .icon {
    color: var(--danger);
  }
  .success .icon {
    color: var(--success);
  }
  .msg {
    flex: 1;
    font-size: 13px;
  }
</style>
