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

<!-- A slim icon rail, like the game clients this sits next to. -->
<nav class="rail" aria-label="Main">
  {#each nav as item (item.name)}
    <button class="item" class:on={router.is(item.name)} aria-current={router.is(item.name) ? "page" : undefined} onclick={() => router.go(item.route)}>
      <span class="icon"><Icon name={item.icon} size={18} filled={item.icon === "play" && router.is(item.name)} /></span>
      <span class="label">{item.label}</span>
    </button>
  {/each}

  <div class="spacer"></div>

  {#each store.running as game (game.instanceId)}
    <div class="playing" title="Playing {game.instanceName}">
      <span class="pulse"></span>
      <button class="mini" title="Game output" onclick={() => (store.consoleFor = game.instanceId)}>
        <Icon name="terminal" size={14} />
      </button>
      <button class="mini stop" title="Stop {game.instanceName}" onclick={() => store.stop(game.instanceId)}>
        <Icon name="stop" size={12} filled />
      </button>
    </div>
  {/each}

  <button class="item" class:on={router.is("settings")} aria-current={router.is("settings") ? "page" : undefined} onclick={() => router.go({ name: "settings" })}>
    <span class="icon"><Icon name="settings" size={18} /></span>
    <span class="label">Settings</span>
  </button>
</nav>

<style>
  .rail {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 6px;
    width: var(--sidebar-w);
    flex-shrink: 0;
    padding: 14px 0;
    border-right: 1px solid var(--border);
    overflow-y: auto;
  }
  .item {
    position: relative;
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 4px;
    width: 64px;
    padding: 6px 0;
    border: none;
    background: transparent;
    color: var(--text-faint);
    cursor: pointer;
  }
  .icon {
    display: grid;
    place-items: center;
    width: 44px;
    height: 36px;
    border-radius: 11px;
    transition:
      background-color 0.15s,
      color 0.15s,
      box-shadow 0.15s;
  }
  .label {
    font-size: 10.5px;
    font-weight: 600;
    letter-spacing: 0.01em;
    transition: color 0.15s;
  }
  .item:hover {
    color: var(--text);
  }
  .item:hover .icon {
    background: var(--bg-hover);
  }
  .item.on {
    color: var(--text);
  }
  .item.on .icon {
    background: var(--accent);
    color: #fff;
    box-shadow: 0 6px 18px color-mix(in srgb, var(--accent) 40%, transparent);
  }
  /* The active page's marker on the rail's edge. */
  .item.on::before {
    content: "";
    position: absolute;
    left: -6px;
    top: 14px;
    width: 3px;
    height: 20px;
    border-radius: 0 3px 3px 0;
    background: var(--accent);
  }
  .playing {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 4px;
    margin-bottom: 6px;
    padding: 8px 6px;
    border: 1px solid color-mix(in srgb, var(--success) 35%, var(--border));
    border-radius: 12px;
    background: color-mix(in srgb, var(--success) 8%, transparent);
  }
  .pulse {
    width: 8px;
    height: 8px;
    margin-bottom: 2px;
    border-radius: 50%;
    background: var(--success);
    box-shadow: 0 0 10px var(--success);
  }
  .mini {
    display: grid;
    place-items: center;
    width: 30px;
    height: 26px;
    border: none;
    border-radius: 7px;
    background: transparent;
    color: var(--text-muted);
    cursor: pointer;
  }
  .mini:hover {
    background: var(--bg-hover);
    color: var(--text);
  }
  .mini.stop:hover {
    background: var(--danger-soft);
    color: var(--danger);
  }
</style>
