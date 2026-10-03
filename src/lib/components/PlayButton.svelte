<script lang="ts">
  import { store } from "../store.svelte";
  import Icon from "./Icon.svelte";
  import Spinner from "./Spinner.svelte";

  let {
    id,
    onlaunch,
    size = "md",
    label = "Play",
  }: {
    /** The install whose progress and running state this button shows. */
    id: string;
    onlaunch: () => void;
    size?: "sm" | "md" | "lg" | "xl";
    label?: string;
  } = $props();

  const launch = $derived(store.launches[id]);
  const running = $derived(store.isRunning(id));
  const pct = $derived.by(() => {
    const p = launch?.progress;
    if (!p || !p.totalBytes) return null;
    return Math.min(100, (p.doneBytes / p.totalBytes) * 100);
  });

  function onclick(e: MouseEvent) {
    e.stopPropagation();
    if (launch || running) void store.stop(id);
    else onlaunch();
  }
</script>

<button
  class="play {size}"
  class:busy={!!launch}
  class:running
  {onclick}
  title={launch ? `${launch.message} — click to cancel` : running ? "Stop the game" : undefined}
>
  {#if launch}
    {#if pct !== null}<span class="fill" style:width="{pct}%"></span>{/if}
    <Spinner size={size === "lg" || size === "xl" ? 16 : 13} />
    <span class="text">{size === "sm" ? (pct !== null ? `${Math.round(pct)}%` : "…") : launch.message}{#if pct !== null && size !== "sm"}&nbsp;· {Math.round(pct)}%{/if}</span>
  {:else if running}
    <Icon name="stop" size={size === "lg" || size === "xl" ? 15 : 12} filled />
    <span class="text">Stop</span>
  {:else}
    <Icon name="play" size={size === "lg" || size === "xl" ? 15 : 12} filled />
    <span class="text">{label}</span>
  {/if}
</button>

<style>
  .play {
    position: relative;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    gap: 8px;
    overflow: hidden;
    border: none;
    border-radius: var(--radius);
    background: var(--accent);
    color: white;
    font-weight: 600;
    cursor: pointer;
    white-space: nowrap;
    transition:
      background-color 0.15s,
      transform 0.1s var(--ease-out),
      box-shadow 0.15s;
  }
  .play:hover {
    background: color-mix(in srgb, var(--accent) 88%, white);
  }
  .play:active {
    transform: scale(0.985);
  }
  .sm {
    height: 28px;
    min-width: 72px;
    padding: 0 12px;
    font-size: 12.5px;
  }
  .md {
    height: 34px;
    min-width: 96px;
    padding: 0 16px;
  }
  .lg {
    height: 44px;
    min-width: 160px;
    padding: 0 22px;
    font-size: 14.5px;
    box-shadow: 0 6px 20px color-mix(in srgb, var(--accent) 35%, transparent);
  }
  /* The hero's launch button: the one thing on the home screen meant to be pressed. */
  .xl {
    height: 54px;
    min-width: 210px;
    padding: 0 30px;
    border-radius: 12px;
    background: linear-gradient(135deg, color-mix(in srgb, var(--accent) 80%, white), var(--accent) 45%, color-mix(in srgb, var(--accent) 70%, black));
    font-size: 15px;
    font-weight: 750;
    letter-spacing: 0.08em;
    text-transform: uppercase;
    box-shadow:
      0 10px 30px color-mix(in srgb, var(--accent) 45%, transparent),
      inset 0 1px 0 rgb(255 255 255 / 0.25);
  }
  .xl:hover {
    background: linear-gradient(135deg, color-mix(in srgb, var(--accent) 70%, white), color-mix(in srgb, var(--accent) 90%, white) 45%, var(--accent));
    box-shadow:
      0 12px 38px color-mix(in srgb, var(--accent) 60%, transparent),
      inset 0 1px 0 rgb(255 255 255 / 0.3);
  }
  .xl.busy,
  .xl.running {
    background: rgb(255 255 255 / 0.1);
    backdrop-filter: blur(12px);
    letter-spacing: 0.02em;
    text-transform: none;
    font-weight: 600;
  }
  .busy {
    background: var(--bg-active);
    color: var(--text);
    box-shadow: none;
  }
  .busy:hover {
    background: var(--bg-active);
  }
  .running {
    background: var(--bg-active);
    color: var(--text);
    box-shadow: none;
  }
  .running:hover {
    background: var(--danger-soft);
    color: var(--danger);
  }
  .fill {
    position: absolute;
    inset: 0 auto 0 0;
    background: var(--accent-soft);
    transition: width 0.2s linear;
  }
  .text {
    position: relative;
    max-width: 260px;
    overflow: hidden;
    text-overflow: ellipsis;
  }
</style>
