<script lang="ts">
  import { open } from "@tauri-apps/plugin-dialog";
  import { fly } from "svelte/transition";
  import { accounts } from "../lib/accounts.svelte";
  import Icon from "../lib/components/Icon.svelte";
  import Motd from "../lib/components/Motd.svelte";
  import PlayButton from "../lib/components/PlayButton.svelte";
  import Spinner from "../lib/components/Spinner.svelte";
  import { buildLoaderLabel, isLanAddress, modName } from "../lib/format";
  import { api, type BuildView } from "../lib/ipc";
  import { dur } from "../lib/platform";
  import { pings } from "../lib/pings.svelte";
  import { router } from "../lib/router.svelte";
  import { store } from "../lib/store.svelte";

  const build = $derived(store.build);
  const loader = $derived(store.loader);
  const running = $derived(loader ? store.isRunning(loader.instanceId) : false);
  const favorites = $derived(store.servers.filter((s) => s.favorite).slice(0, 4));
  const offline = $derived(accounts.active?.kind === "offline");

  $effect(() => {
    void pings.pingAll(favorites.map((s) => s.address));
  });

  function pick(b: BuildView) {
    store.selectBuild(b.id);
  }

  // OptiFine: the player downloads it (its licence forbids launchers fetching
  // it); the launcher checks the file and installs it into this build.
  let optifineFile = $state<string | null>(null);
  let addingOptifine = $state(false);

  $effect(() => {
    const l = loader;
    optifineFile = null;
    if (!l?.optifine) return;
    api
      .listMods(l.instanceId)
      .then((mods) => {
        if (loader === l) optifineFile = mods.find((m) => /optifine/i.test(m.fileName))?.fileName ?? null;
      })
      .catch(() => {}); // Not installed yet.
  });

  async function chooseOptifine() {
    if (!build || !loader) return;
    const path = await open({ multiple: false, filters: [{ name: "OptiFine", extensions: ["jar"] }] });
    if (typeof path !== "string") return;
    addingOptifine = true;
    try {
      optifineFile = await api.addOptifine(build.id, loader.kind, path);
      store.toast("success", `OptiFine added to ${build.name}.`);
    } catch (e) {
      store.error(e);
    } finally {
      addingOptifine = false;
    }
  }
</script>

<div class="page">
  <div class="page-narrow">
    {#if !store.manifest}
      <div class="empty"><Spinner /></div>
    {:else if build && loader}
      <section class="hero card" in:fly={{ y: 6, duration: dur(200), opacity: 0 }}>
        <div class="hero-glow"></div>
        <div class="hero-text">
          <span class="eyebrow">{running ? "Now playing" : `Minecraft ${build.minecraft}`}</span>
          <h1 class="hero-name">{build.name}</h1>
          <p class="tagline">{build.tagline}</p>

          {#if build.loaders.length > 1}
            <div class="segmented loaders" role="radiogroup" aria-label="Mod loader">
              {#each build.loaders as l (l.kind)}
                <button role="radio" aria-checked={l.kind === loader.kind} class:on={l.kind === loader.kind} onclick={() => store.selectLoader(l.kind)}>
                  {buildLoaderLabel(l.kind)}
                </button>
              {/each}
            </div>
          {:else}
            <span class="single-loader">{buildLoaderLabel(loader.kind)}</span>
          {/if}

          <div class="included">
            {#if loader.quartzClient}
              <span class="chip accent" title="HUD, PvP, world controls and more — press Right Shift in game"><Icon name="zap" size={11} /> Doohickey Client</span>
            {/if}
            {#each loader.mods as m (m)}
              <span class="chip">{modName(m)}</span>
            {/each}
            {#if optifineFile}
              <span class="chip" title={optifineFile}>OptiFine</span>
            {/if}
            {#if !loader.quartzClient && !loader.mods.length}
              <span class="faint">Vanilla {buildLoaderLabel(loader.kind)}, nothing extra.</span>
            {/if}
          </div>
          {#if loader.optifine && !optifineFile}
            <p class="faint small optifine">
              {#if addingOptifine}
                <Spinner size={12} /> Adding OptiFine…
              {:else}
                Want OptiFine?
                <button class="link" onclick={() => api.openExternal("https://optifine.net/downloads")}>Download it for {build.minecraft}</button>,
                then <button class="link" onclick={chooseOptifine}>choose the file</button>.
              {/if}
            </p>
          {/if}
          {#if offline}
            <p class="faint small"><Icon name="alert" size={12} /> Offline account: singleplayer and LAN only.</p>
          {/if}
        </div>
        <div class="hero-actions">
          <PlayButton id={loader.instanceId} size="lg" onlaunch={() => store.launchBuild(build, loader)} />
          <button class="icon-btn" title="Game output" onclick={() => (store.consoleFor = loader.instanceId)}>
            <Icon name="terminal" size={15} />
          </button>
        </div>
      </section>

      <div class="section-title">Versions</div>
      <div class="grid">
        {#each store.manifest.builds as b (b.id)}
          {@const on = b.id === build.id}
          <button class="tile card" class:on onclick={() => pick(b)} aria-pressed={on}>
            <div class="tile-text">
              <span class="tile-name">{b.name}</span>
              <span class="faint">{b.loaders.map((l) => buildLoaderLabel(l.kind)).join(" · ")}</span>
            </div>
            {#if b.loaders.some((l) => store.isRunning(l.instanceId))}
              <span class="dot live" title="Running"></span>
            {:else if b.loaders.some((l) => l.quartzClient)}
              <span class="badge accent" title="Includes Doohickey Client">Doohickey</span>
            {/if}
          </button>
        {/each}
      </div>

      {#if store.manifest.problem}
        <p class="faint small source"><Icon name="alert" size={12} /> {store.manifest.problem} Using the {store.manifest.source === "cached" ? "last downloaded" : "built-in"} list.</p>
      {/if}

      {#if favorites.length}
        <div class="section-title row">
          Favorite servers
          <span class="spacer"></span>
          <button class="btn btn-ghost btn-sm" onclick={() => router.go({ name: "servers" })}>Manage</button>
        </div>
        <div class="list">
          {#each favorites as s (s.id)}
            {@const ping = pings.byAddress[s.address]}
            <div class="list-row">
              {#if ping?.state === "online" && ping.data.favicon}
                <img class="favicon" src={ping.data.favicon} alt="" />
              {:else}
                <span class="favicon placeholder"><Icon name="server" size={16} /></span>
              {/if}
              <div class="server-text">
                <span class="ellipsis"><strong>{s.name}</strong> <span class="faint mono">{s.address}</span></span>
                <span class="ellipsis motd">
                  {#if ping?.state === "online"}<Motd motd={ping.data.motd} />{:else if ping?.state === "offline"}<span class="faint">Offline</span>{:else}<span class="faint">Pinging…</span>{/if}
                </span>
              </div>
              {#if ping?.state === "online"}
                <span class="faint players">{ping.data.playersOnline.toLocaleString()} online</span>
              {/if}
              <button
                class="btn btn-secondary btn-sm"
                disabled={!!store.launches[loader.instanceId] || running || (offline && !isLanAddress(s.address))}
                title={offline && !isLanAddress(s.address) ? "Offline accounts are for singleplayer and LAN" : undefined}
                onclick={() => store.playSelected(s.address)}
              >
                <Icon name="play" size={11} filled /> Join
              </button>
            </div>
          {/each}
        </div>
      {/if}
    {/if}
  </div>
</div>

<style>
  .hero {
    position: relative;
    display: flex;
    align-items: flex-end;
    gap: 20px;
    padding: 28px;
    overflow: hidden;
  }
  .hero-glow {
    position: absolute;
    inset: -50% auto auto -10%;
    width: 70%;
    height: 200%;
    background: radial-gradient(closest-side, var(--accent-soft), transparent);
    pointer-events: none;
  }
  .hero-text {
    position: relative;
    display: flex;
    flex: 1;
    flex-direction: column;
    align-items: flex-start;
    min-width: 0;
  }
  .eyebrow {
    color: var(--text-faint);
    font-size: 11.5px;
    font-weight: 600;
    letter-spacing: 0.05em;
    text-transform: uppercase;
  }
  .hero-name {
    margin-top: 2px;
    font-size: 26px;
    font-weight: 650;
    letter-spacing: -0.02em;
  }
  .tagline {
    margin-top: 2px;
    color: var(--text-muted);
  }
  .loaders {
    margin-top: 16px;
  }
  .single-loader {
    margin-top: 16px;
    color: var(--text-muted);
    font-size: 12.5px;
  }
  .included {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
    margin-top: 14px;
  }
  .chip {
    display: inline-flex;
    align-items: center;
    gap: 4px;
    height: 22px;
    padding: 0 8px;
    border: 1px solid var(--border);
    border-radius: 999px;
    color: var(--text-muted);
    font-size: 11.5px;
  }
  .chip.accent {
    border-color: color-mix(in srgb, var(--accent) 45%, var(--border));
    color: var(--accent);
  }
  .hero-actions {
    position: relative;
    display: flex;
    align-items: center;
    gap: 6px;
  }
  .section-title {
    margin: 26px 0 10px;
  }
  .grid {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
    gap: 10px;
  }
  .tile {
    display: flex;
    align-items: center;
    gap: 10px;
    padding: 12px 14px;
    text-align: left;
    cursor: pointer;
    transition:
      border-color 0.15s,
      background-color 0.15s;
  }
  .tile:hover {
    border-color: var(--border-strong);
    background: var(--bg-hover);
  }
  .tile.on {
    border-color: color-mix(in srgb, var(--accent) 60%, var(--border));
    background: color-mix(in srgb, var(--accent) 7%, transparent);
  }
  .tile-text {
    display: flex;
    flex: 1;
    flex-direction: column;
    min-width: 0;
    line-height: 1.45;
  }
  .tile-name {
    font-weight: 550;
  }
  .optifine {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    gap: 4px;
    margin-top: 10px;
  }
  .link {
    padding: 0;
    border: 0;
    background: none;
    color: var(--accent);
    font: inherit;
    text-decoration: underline;
    text-underline-offset: 2px;
    cursor: pointer;
  }
  .source {
    display: flex;
    align-items: center;
    gap: 6px;
    margin-top: 10px;
  }
  .favicon {
    width: 32px;
    height: 32px;
    border-radius: 6px;
    image-rendering: pixelated;
  }
  .placeholder {
    display: grid;
    place-items: center;
    background: var(--bg-subtle);
    color: var(--text-faint);
  }
  .server-text {
    display: flex;
    flex: 1;
    flex-direction: column;
    min-width: 0;
    line-height: 1.4;
  }
  .motd {
    font-size: 12px;
  }
  .players {
    font-size: 12px;
    white-space: nowrap;
  }
</style>
