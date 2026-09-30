<script lang="ts">
  import { fly } from "svelte/transition";
  import Icon from "../lib/components/Icon.svelte";
  import Menu from "../lib/components/Menu.svelte";
  import Modal from "../lib/components/Modal.svelte";
  import Motd from "../lib/components/Motd.svelte";
  import Spinner from "../lib/components/Spinner.svelte";
  import { accounts } from "../lib/accounts.svelte";
  import { isLanAddress } from "../lib/format";
  import { api, type Server } from "../lib/ipc";
  import { pings } from "../lib/pings.svelte";
  import { dur } from "../lib/platform";
  import { store } from "../lib/store.svelte";

  let editing = $state<Partial<Server> | null>(null);

  $effect(() => {
    void pings.pingAll(store.servers.map((s) => s.address));
  });

  async function save() {
    if (!editing?.address?.trim()) return;
    try {
      store.servers = await api.saveServer({
        id: editing.id,
        name: editing.name?.trim() ?? "",
        address: editing.address.trim(),
        favorite: editing.favorite ?? false,
        instanceId: null,
      });
      void pings.ping(editing.address.trim(), true);
      editing = null;
    } catch (e) {
      store.error(e);
    }
  }

  async function patch(server: Server, change: Partial<Server>) {
    try {
      store.servers = await api.saveServer({ ...server, ...change });
    } catch (e) {
      store.error(e);
    }
  }

  async function remove(server: Server) {
    try {
      store.servers = await api.removeServer(server.id);
    } catch (e) {
      store.error(e);
    }
  }

  /** Joins with the build selected on the Play screen. */
  function join(server: Server) {
    void store.playSelected(server.address);
  }

  const busy = $derived(!!store.loader && (!!store.launches[store.loader.instanceId] || store.isRunning(store.loader.instanceId)));
  // Offline accounts are for singleplayer and LAN; the launcher refuses public servers for them.
  const offline = $derived(accounts.active?.kind === "offline");

  /** 1–4 bars like the in-game server list. */
  const bars = (ms: number) => (ms < 80 ? 4 : ms < 150 ? 3 : ms < 300 ? 2 : 1);
</script>

<div class="page" in:fly={{ y: 6, duration: dur(220), opacity: 0 }}>
  <div class="page-narrow">
    <div class="page-head">
      <div>
        <h1 class="page-title">Servers</h1>
        <p class="page-sub">Live status and one-click join.</p>
      </div>
      <div class="row">
        <button class="btn btn-ghost" onclick={() => pings.pingAll(store.servers.map((s) => s.address), true)} disabled={!store.servers.length}>
          <Icon name="refresh" size={14} /> Refresh
        </button>
        <button class="btn btn-primary" onclick={() => (editing = { favorite: true })}><Icon name="plus" size={14} /> Add server</button>
      </div>
    </div>

    {#if !store.servers.length}
      <div class="empty card">
        <Icon name="server" size={22} />
        <span class="empty-title">No servers yet</span>
        <span>Add the servers you play on to see who's online and join them straight from the launcher.</span>
        <button class="btn btn-primary" onclick={() => (editing = { favorite: true })}>Add server</button>
      </div>
    {:else}
      <div class="list">
        {#each store.servers as s (s.id)}
          {@const ping = pings.byAddress[s.address]}
          <div class="list-row server">
            {#if ping?.state === "online" && ping.data.favicon}
              <img class="favicon" src={ping.data.favicon} alt="" />
            {:else}
              <span class="favicon placeholder"><Icon name="server" size={18} /></span>
            {/if}
            <div class="info">
              <div class="row name-row">
                <strong class="ellipsis">{s.name}</strong>
                <span class="faint mono ellipsis">{s.address}</span>
              </div>
              <div class="motd ellipsis">
                {#if ping?.state === "online"}
                  <Motd motd={ping.data.motd} />
                {:else if ping?.state === "offline"}
                  <span class="faint">{ping.error}</span>
                {:else}
                  <span class="faint row"><Spinner size={11} /> Pinging…</span>
                {/if}
              </div>
              <div class="faint small">
                {#if ping?.state === "online"}{ping.data.version} · {/if}Joins with {store.build?.name ?? "your selected build"}
              </div>
            </div>
            {#if ping?.state === "online"}
              <div class="status" title="{ping.data.latencyMs} ms">
                <span class="players">{ping.data.playersOnline.toLocaleString()}<span class="faint">/{ping.data.playersMax.toLocaleString()}</span></span>
                <span class="signal">
                  {#each [1, 2, 3, 4] as b (b)}<span class="bar" class:on={b <= bars(ping.data.latencyMs)} style:height="{3 + b * 3}px"></span>{/each}
                  <span class="faint ms">{ping.data.latencyMs} ms</span>
                </span>
              </div>
            {/if}
            <button class="icon-btn" class:fav={s.favorite} title={s.favorite ? "Unfavorite" : "Favorite"} onclick={() => patch(s, { favorite: !s.favorite })}>
              <Icon name="star" size={15} filled={s.favorite} />
            </button>
            <Menu
              items={[
                { label: "Edit", icon: "edit", run: () => (editing = { ...s }) },
                { label: "Copy address", icon: "copy", run: () => navigator.clipboard.writeText(s.address) },
                { label: "Remove", icon: "trash", danger: true, run: () => remove(s) },
              ]}
            />
            <button
              class="btn btn-accent btn-sm"
              onclick={() => join(s)}
              disabled={ping?.state === "offline" || busy || (offline && !isLanAddress(s.address))}
              title={offline && !isLanAddress(s.address) ? "Offline accounts are for singleplayer and LAN" : undefined}
            >
              <Icon name="play" size={11} filled /> Join
            </button>
          </div>
        {/each}
      </div>
    {/if}
  </div>
</div>

{#if editing}
  <Modal title={editing.id ? "Edit server" : "Add server"} width={440} onClose={() => (editing = null)}>
    <form class="form" onsubmit={(e) => { e.preventDefault(); void save(); }}>
      <label class="field">
        <span class="field-label">Address</span>
        <input class="input" bind:value={editing.address} placeholder="mc.hypixel.net" spellcheck="false" />
      </label>
      <label class="field">
        <span class="field-label">Name</span>
        <input class="input" bind:value={editing.name} placeholder={editing.address || "My server"} />
      </label>
      <button type="submit" hidden aria-hidden="true"></button>
    </form>
    {#snippet footer()}
      <button class="btn btn-ghost" onclick={() => (editing = null)}>Cancel</button>
      <button class="btn btn-primary" disabled={!editing?.address?.trim()} onclick={save}>Save</button>
    {/snippet}
  </Modal>
{/if}

<style>
  .server {
    min-height: 76px;
  }
  .favicon {
    width: 48px;
    height: 48px;
    flex-shrink: 0;
    border-radius: 8px;
    image-rendering: pixelated;
  }
  .placeholder {
    display: grid;
    place-items: center;
    background: var(--bg-active);
    color: var(--text-faint);
  }
  .info {
    display: flex;
    flex: 1;
    flex-direction: column;
    min-width: 0;
    line-height: 1.45;
  }
  .name-row {
    min-width: 0;
  }
  .motd {
    font-size: 12.5px;
  }
  .motd :global(.motd) {
    white-space: nowrap;
  }
  .small {
    font-size: 11.5px;
  }
  .status {
    display: flex;
    flex-direction: column;
    align-items: flex-end;
    gap: 4px;
    margin-right: 4px;
  }
  .players {
    font-weight: 600;
    font-size: 12.5px;
  }
  .signal {
    display: flex;
    align-items: flex-end;
    gap: 2px;
  }
  .bar {
    width: 3px;
    border-radius: 1px;
    background: var(--border-strong);
  }
  .bar.on {
    background: var(--success);
  }
  .ms {
    margin-left: 5px;
    font-size: 11px;
    line-height: 1;
  }
  .fav {
    color: var(--warn);
  }
  .form {
    display: flex;
    flex-direction: column;
    gap: 14px;
  }
</style>
