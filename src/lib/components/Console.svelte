<script lang="ts">
  import Icon from "./Icon.svelte";
  import VirtualList from "./VirtualList.svelte";
  import { api, type LogLine } from "../ipc";
  import { store } from "../store.svelte";

  /** The install whose game output to show. */
  let { instanceId }: { instanceId: string } = $props();

  let level = $state<"all" | "warn" | "error">("all");
  let query = $state("");
  let follow = $state(true);

  const running = $derived(store.isRunning(instanceId));
  const lines = $derived.by(() => {
    void store.logVersion[instanceId]; // re-run when the log grows
    const q = query.toLowerCase();
    return store.log(instanceId).filter((l) => {
      if (level === "warn" && l.level !== "warn" && l.level !== "error" && l.level !== "fatal") return false;
      if (level === "error" && l.level !== "error" && l.level !== "fatal") return false;
      return !q || l.text.toLowerCase().includes(q);
    });
  });

  const time = (l: LogLine) =>
    l.time ? new Date(l.time).toLocaleTimeString(undefined, { hour12: false }) : "";

  async function copy() {
    try {
      await navigator.clipboard.writeText(lines.map((l) => `[${time(l)}] [${l.level.toUpperCase()}] ${l.text}`).join("\n"));
      store.toast("success", `Copied ${lines.length} lines.`);
    } catch (e) {
      store.error(e);
    }
  }
</script>

<div class="console">
  <div class="toolbar">
    <div class="segmented">
      <button class:on={level === "all"} onclick={() => (level = "all")}>All</button>
      <button class:on={level === "warn"} onclick={() => (level = "warn")}>Warnings</button>
      <button class:on={level === "error"} onclick={() => (level = "error")}>Errors</button>
    </div>
    <div class="search">
      <Icon name="search" size={13} />
      <input bind:value={query} placeholder="Search log" />
    </div>
    <span class="spacer"></span>
    <label class="follow">
      <button class="switch" aria-pressed={follow} aria-label="Follow output" onclick={() => (follow = !follow)}></button>
      Follow
    </label>
    <button class="btn btn-ghost btn-sm" onclick={copy} disabled={!lines.length}><Icon name="copy" size={13} /> Copy</button>
    <button class="btn btn-ghost btn-sm" onclick={() => store.clearLog(instanceId)} disabled={!lines.length}>Clear</button>
    <button class="btn btn-ghost btn-sm" onclick={() => api.openFolder("logs", instanceId).catch((e) => store.error(e))}>
      <Icon name="folder" size={13} /> Logs
    </button>
    {#if running}
      <button class="btn btn-danger btn-sm" onclick={() => store.stop(instanceId)}><Icon name="stop" size={11} filled /> Stop</button>
    {/if}
  </div>

  {#if !store.log(instanceId).length}
    <div class="empty">
      <Icon name="terminal" size={22} />
      <span class="empty-title">{running ? "Waiting for output…" : "No output yet"}</span>
      <span>{running ? "Logs appear here as the game starts." : "Press Play to see the game's live output. Past sessions are in the Logs folder."}</span>
    </div>
  {:else}
    <VirtualList items={lines} rowHeight={20} {follow} class="log">
      {#snippet row(l)}
        <div class="line {l.level}" title={l.text}>
          <span class="t">{time(l)}</span>
          <span class="lvl">{l.level === "info" ? "" : l.level.toUpperCase()}</span>
          <span class="txt" data-selectable>{l.text}</span>
        </div>
      {/snippet}
    </VirtualList>
  {/if}
</div>

<style>
  .console {
    display: flex;
    flex-direction: column;
    height: 100%;
    padding: 0;
  }
  .toolbar {
    display: flex;
    align-items: center;
    gap: 8px;
    margin-bottom: 10px;
  }
  .search {
    display: flex;
    align-items: center;
    gap: 6px;
    width: 220px;
    height: 30px;
    padding: 0 10px;
    border: 1px solid var(--border);
    border-radius: var(--radius-sm);
    background: var(--bg-subtle);
    color: var(--text-faint);
  }
  .search input {
    flex: 1;
    min-width: 0;
    border: none;
    background: transparent;
    color: var(--text);
    font: inherit;
    font-size: 12.5px;
    outline: none;
  }
  .follow {
    display: flex;
    align-items: center;
    gap: 6px;
    color: var(--text-muted);
    font-size: 12.5px;
  }
  .console :global(.log) {
    flex: 1;
    min-height: 0;
    padding: 6px 0;
    border: 1px solid var(--border);
    border-radius: var(--radius-lg);
    background: color-mix(in srgb, var(--bg-solid) 70%, black);
    font-family: var(--font-mono);
    font-size: 12px;
  }
  :global([data-theme="light"]) .console :global(.log) {
    background: var(--bg-subtle);
  }
  .line {
    display: flex;
    gap: 10px;
    height: 20px;
    padding: 0 12px;
    align-items: center;
    white-space: pre;
    color: var(--text-muted);
  }
  .line:hover {
    background: var(--bg-hover);
  }
  .t {
    width: 62px;
    flex-shrink: 0;
    color: var(--text-faint);
  }
  .lvl {
    width: 42px;
    flex-shrink: 0;
    font-size: 10.5px;
    font-weight: 600;
  }
  .txt {
    overflow: hidden;
    text-overflow: ellipsis;
  }
  .warn {
    color: var(--warn);
  }
  .error,
  .fatal {
    color: var(--danger);
  }
  .debug,
  .trace {
    color: var(--text-faint);
  }
</style>
