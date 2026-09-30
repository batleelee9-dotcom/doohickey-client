<script lang="ts">
  import { router, type Route } from "../router.svelte";
  import { store } from "../store.svelte";
  import Icon, { type IconName } from "./Icon.svelte";

  const nav: { route: Route; label: string; icon: IconName; name: Route["name"] }[] = [
    { route: { name: "home" }, name: "home", label: "Play", icon: "play" },
    { route: { name: "servers" }, name: "servers", label: "Servers", icon: "server" },
    { route: { name: "skins" }, name: "skins", label: "Skins", icon: "shirt" },
  ];
</script>

<nav class="sidebar" aria-label="Main">
  <div class="group">
    {#each nav as item (item.name)}
      <button class="item" class:on={router.is(item.name)} onclick={() => router.go(item.route)}>
        <Icon name={item.icon} size={15} />
        {item.label}
      </button>
    {/each}
  </div>

  <div class="spacer"></div>

  {#each store.running as game (game.instanceId)}
    <div class="now-playing">
      <span class="dot live"></span>
      <div class="np-text">
        <span class="np-label">Playing</span>
        <span class="ellipsis">{game.instanceName}</span>
      </div>
      <button class="icon-btn" title="Console" onclick={() => (store.consoleFor = game.instanceId)}>
        <Icon name="terminal" size={14} />
      </button>
      <button class="icon-btn danger" title="Stop" onclick={() => store.stop(game.instanceId)}>
        <Icon name="stop" size={12} filled />
      </button>
    </div>
  {/each}

  <button class="item" class:on={router.is("settings")} onclick={() => router.go({ name: "settings" })}>
    <Icon name="settings" size={15} />
    Settings
  </button>
</nav>

<style>
  .sidebar {
    display: flex;
    flex-direction: column;
    gap: 2px;
    width: var(--sidebar-w);
    flex-shrink: 0;
    padding: 12px 10px;
    border-right: 1px solid var(--border);
    overflow-y: auto;
  }
  .group {
    display: flex;
    flex-direction: column;
    gap: 1px;
  }
  .item {
    display: flex;
    align-items: center;
    gap: 10px;
    height: 32px;
    padding: 0 10px;
    border: none;
    border-radius: 7px;
    background: transparent;
    color: var(--text-muted);
    font-size: 13px;
    font-weight: 500;
    text-align: left;
    cursor: pointer;
    transition:
      background-color 0.12s,
      color 0.12s;
  }
  .item:hover {
    background: var(--bg-hover);
    color: var(--text);
  }
  .item.on {
    background: var(--bg-active);
    color: var(--text);
  }
  .live {
    margin-left: auto;
    color: var(--success);
  }
  .now-playing {
    display: flex;
    align-items: center;
    gap: 8px;
    margin-bottom: 8px;
    padding: 8px 6px 8px 12px;
    border: 1px solid var(--border);
    border-radius: var(--radius);
    background: var(--bg-subtle);
    color: var(--success);
  }
  .np-text {
    display: flex;
    flex: 1;
    flex-direction: column;
    min-width: 0;
    color: var(--text);
    font-size: 12.5px;
    line-height: 1.3;
  }
  .np-label {
    color: var(--text-faint);
    font-size: 11px;
  }
</style>
