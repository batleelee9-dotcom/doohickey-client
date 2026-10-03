<script lang="ts">
  import { fly } from "svelte/transition";
  import { accounts } from "../lib/accounts.svelte";
  import HeroArt from "../lib/components/HeroArt.svelte";
  import Icon, { type IconName } from "../lib/components/Icon.svelte";
  import Motd from "../lib/components/Motd.svelte";
  import PlayButton from "../lib/components/PlayButton.svelte";
  import Spinner from "../lib/components/Spinner.svelte";
  import { buildLoaderLabel, isLanAddress, modName } from "../lib/format";
  import type { BuildView } from "../lib/ipc";
  import { dur } from "../lib/platform";
  import { pings } from "../lib/pings.svelte";
  import { router } from "../lib/router.svelte";
  import { store } from "../lib/store.svelte";

  const build = $derived(store.build);
  const loader = $derived(store.loader);
  const running = $derived(loader ? store.isRunning(loader.instanceId) : false);
  const favorites = $derived(store.servers.filter((s) => s.favorite).slice(0, 4));
  const offline = $derived(accounts.active?.kind === "offline");

  const FEATURES: { icon: IconName; title: string; text: string }[] = [
    { icon: "grid", title: "HUD editor", text: "FPS, CPS, keystrokes, armor, potions and more. Drag to place, scroll to resize." },
    { icon: "globe", title: "World", text: "Sky colour, time, fog, weather and fullbright, all client-side." },
    { icon: "cpu", title: "Performance", text: "Particle limiter and a render distance that adapts to your FPS." },
    { icon: "switch", title: "Account switcher", text: "Swap accounts from the pause menu without restarting." },
  ];

  $effect(() => {
    void pings.pingAll(favorites.map((s) => s.address));
  });

  function pick(b: BuildView) {
    store.selectBuild(b.id);
  }
</script>

<div class="page play">
  {#if !store.manifest}
    <div class="empty"><Spinner /></div>
  {:else if build && loader}
    {#key build.id}
      <section class="hero" in:fly={{ y: 6, duration: dur(260), opacity: 0 }}>
        <HeroArt seed={build.id} tone={store.manifest.builds.findIndex((b) => b.id === build.id)} />
        <div class="shade"></div>
        <div class="hero-body">
          <div class="pills">
            <span class="pill">Minecraft {build.minecraft}</span>
            {#if running}
              <span class="pill live"><span class="dot"></span> Now playing</span>
            {:else if loader.quartzClient}
              <span class="pill accent"><Icon name="zap" size={11} /> Doohickey Client</span>
            {/if}
          </div>
          <h1 class="hero-name">{build.name}</h1>
          <p class="tagline">{build.tagline}</p>

          <div class="actions">
            <PlayButton id={loader.instanceId} size="xl" onlaunch={() => store.launchBuild(build, loader)} />
            {#if build.loaders.length > 1}
              <div class="glass-seg" role="radiogroup" aria-label="Mod loader">
                {#each build.loaders as l (l.kind)}
                  <button role="radio" aria-checked={l.kind === loader.kind} class:on={l.kind === loader.kind} onclick={() => store.selectLoader(l.kind)}>
                    {buildLoaderLabel(l.kind)}
                  </button>
                {/each}
              </div>
            {:else}
              <span class="glass-label">{buildLoaderLabel(loader.kind)}</span>
            {/if}
            <button class="glass-icon" title="Game output" onclick={() => (store.consoleFor = loader.instanceId)}>
              <Icon name="terminal" size={16} />
            </button>
          </div>

          <div class="included">
            {#each loader.mods as m (m)}
              <span class="mod">{modName(m)}</span>
            {/each}
            {#if !loader.quartzClient && !loader.mods.length}
              <span class="mod">Vanilla {buildLoaderLabel(loader.kind)}</span>
            {/if}
          </div>
          {#if offline}
            <p class="offline"><Icon name="alert" size={12} /> Offline account: singleplayer and LAN only.</p>
          {/if}
        </div>
      </section>
    {/key}

    <div class="section-head">
      <h2>Versions</h2>
      {#if store.manifest.problem}
        <span class="faint small source" title={store.manifest.problem}>
          <Icon name="alert" size={12} /> Using the {store.manifest.source === "cached" ? "last downloaded" : "built-in"} list
        </span>
      {/if}
    </div>
    <div class="versions">
      {#each store.manifest.builds as b, i (b.id)}
        {@const on = b.id === build.id}
        <button class="version" class:on onclick={() => pick(b)} aria-pressed={on}>
          <div class="version-art">
            <HeroArt seed={b.id} tone={i} stars={false} />
            {#if b.loaders.some((l) => store.isRunning(l.instanceId))}
              <span class="tag live"><span class="dot"></span> Playing</span>
            {:else if b.loaders.some((l) => l.quartzClient)}
              <span class="tag"><Icon name="zap" size={10} /> Client</span>
            {/if}
            <span class="version-number">{b.minecraft}</span>
          </div>
          <div class="version-body">
            <span class="version-name">{b.name}</span>
            <span class="version-sub">{b.loaders.map((l) => buildLoaderLabel(l.kind)).join(" · ")}</span>
          </div>
        </button>
      {/each}
    </div>

    <div class="columns">
      <section class="panel">
        <div class="panel-head">
          <h2>Servers</h2>
          <button class="btn btn-ghost btn-sm" onclick={() => router.go({ name: "servers" })}>
            {favorites.length ? "Manage" : "Add a server"}
          </button>
        </div>
        {#if favorites.length}
          <div class="servers">
            {#each favorites as s (s.id)}
              {@const ping = pings.byAddress[s.address]}
              {@const blocked = offline && !isLanAddress(s.address)}
              <div class="server">
                {#if ping?.state === "online" && ping.data.favicon}
                  <img class="favicon" src={ping.data.favicon} alt="" />
                {:else}
                  <span class="favicon placeholder"><Icon name="server" size={16} /></span>
                {/if}
                <div class="server-text">
                  <span class="ellipsis server-name">{s.name}</span>
                  <span class="ellipsis motd">
                    {#if ping?.state === "online"}<Motd motd={ping.data.motd} />{:else if ping?.state === "offline"}<span class="faint">Offline</span>{:else}<span class="faint">Pinging…</span>{/if}
                  </span>
                </div>
                {#if ping?.state === "online"}
                  <span class="players"><span class="dot"></span>{ping.data.playersOnline.toLocaleString()}</span>
                {/if}
                <button
                  class="join"
                  disabled={!!store.launches[loader.instanceId] || running || blocked}
                  title={blocked ? "Offline accounts are for singleplayer and LAN" : `Join with ${build.name}`}
                  onclick={() => store.playSelected(s.address)}
                >
                  <Icon name="play" size={11} filled /> Join
                </button>
              </div>
            {/each}
          </div>
        {:else}
          <p class="faint empty-note">Favorite a server and it shows up here, one click from joining.</p>
        {/if}
      </section>

      <section class="panel client">
        <div class="panel-head">
          <h2>Doohickey Client</h2>
          <span class="keycap">Right Shift</span>
        </div>
        {#if loader.quartzClient}
          <ul class="features">
            {#each FEATURES as f (f.title)}
              <li>
                <span class="feature-icon"><Icon name={f.icon} size={15} /></span>
                <div>
                  <strong>{f.title}</strong>
                  <p>{f.text}</p>
                </div>
              </li>
            {/each}
          </ul>
        {:else}
          <p class="faint empty-note">
            {build.name} on {buildLoaderLabel(loader.kind)} runs the optimization mods only. The in-game client comes with 1.8.9 and 26.3 on Fabric.
          </p>
        {/if}
      </section>
    </div>
  {/if}
</div>

<style>
  .play {
    padding: 24px 28px 40px;
  }

  /* ---- Hero ---------------------------------------------------------------- */
  .hero {
    position: relative;
    min-height: 340px;
    overflow: hidden;
    border-radius: 18px;
    box-shadow:
      0 20px 50px rgb(0 0 0 / 0.45),
      0 0 0 1px rgb(255 255 255 / 0.06);
    isolation: isolate;
  }
  .shade {
    position: absolute;
    inset: 0;
    background:
      linear-gradient(90deg, rgb(5 4 12 / 0.82) 0%, rgb(5 4 12 / 0.45) 45%, transparent 75%),
      linear-gradient(0deg, rgb(5 4 12 / 0.75) 0%, transparent 55%);
  }
  .hero-body {
    position: relative;
    display: flex;
    flex-direction: column;
    align-items: flex-start;
    justify-content: flex-end;
    min-height: 340px;
    padding: 32px 36px;
    color: #fff;
  }
  .pills {
    display: flex;
    gap: 6px;
  }
  .pill {
    display: inline-flex;
    align-items: center;
    gap: 5px;
    height: 24px;
    padding: 0 10px;
    border: 1px solid rgb(255 255 255 / 0.14);
    border-radius: 999px;
    background: rgb(255 255 255 / 0.08);
    backdrop-filter: blur(10px);
    color: rgb(255 255 255 / 0.85);
    font-size: 11px;
    font-weight: 650;
    letter-spacing: 0.06em;
    text-transform: uppercase;
  }
  .pill.accent {
    border-color: color-mix(in srgb, var(--accent) 55%, transparent);
    background: color-mix(in srgb, var(--accent) 22%, transparent);
    color: #fff;
  }
  .pill.live {
    border-color: color-mix(in srgb, var(--success) 50%, transparent);
    color: var(--success);
  }
  .dot {
    width: 7px;
    height: 7px;
    border-radius: 50%;
    background: currentColor;
    box-shadow: 0 0 8px currentColor;
  }
  .hero-name {
    margin-top: 12px;
    font-family: "Segoe UI Variable Display", var(--font-sans);
    font-size: 44px;
    font-weight: 800;
    line-height: 1.05;
    letter-spacing: -0.03em;
    text-shadow: 0 2px 24px rgb(0 0 0 / 0.4);
  }
  .tagline {
    margin-top: 8px;
    max-width: 460px;
    color: rgb(255 255 255 / 0.72);
    font-size: 14.5px;
  }
  .actions {
    display: flex;
    align-items: center;
    gap: 10px;
    margin-top: 26px;
  }
  .glass-seg {
    display: inline-flex;
    padding: 4px;
    border: 1px solid rgb(255 255 255 / 0.14);
    border-radius: 12px;
    background: rgb(255 255 255 / 0.07);
    backdrop-filter: blur(12px);
  }
  .glass-seg button {
    height: 38px;
    padding: 0 14px;
    border: none;
    border-radius: 9px;
    background: transparent;
    color: rgb(255 255 255 / 0.7);
    font-size: 13px;
    font-weight: 600;
    cursor: pointer;
    transition:
      background-color 0.15s,
      color 0.15s;
  }
  .glass-seg button:hover {
    color: #fff;
  }
  .glass-seg button.on {
    background: rgb(255 255 255 / 0.16);
    color: #fff;
  }
  .glass-label {
    display: inline-flex;
    align-items: center;
    height: 54px;
    padding: 0 18px;
    border: 1px solid rgb(255 255 255 / 0.14);
    border-radius: 12px;
    background: rgb(255 255 255 / 0.07);
    backdrop-filter: blur(12px);
    color: rgb(255 255 255 / 0.85);
    font-weight: 600;
  }
  .glass-icon {
    display: grid;
    place-items: center;
    width: 54px;
    height: 54px;
    border: 1px solid rgb(255 255 255 / 0.14);
    border-radius: 12px;
    background: rgb(255 255 255 / 0.07);
    backdrop-filter: blur(12px);
    color: rgb(255 255 255 / 0.8);
    cursor: pointer;
    transition: background-color 0.15s;
  }
  .glass-icon:hover {
    background: rgb(255 255 255 / 0.15);
    color: #fff;
  }
  .included {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
    margin-top: 18px;
  }
  .mod {
    height: 22px;
    padding: 0 9px;
    border-radius: 6px;
    background: rgb(0 0 0 / 0.35);
    color: rgb(255 255 255 / 0.68);
    font-size: 11.5px;
    line-height: 22px;
  }
  .offline {
    display: flex;
    align-items: center;
    gap: 6px;
    margin-top: 12px;
    color: var(--warn);
    font-size: 12px;
  }

  /* ---- Versions ------------------------------------------------------------ */
  .section-head {
    display: flex;
    align-items: center;
    gap: 12px;
    margin: 30px 2px 12px;
  }
  h2 {
    font-size: 15px;
    font-weight: 700;
    letter-spacing: -0.01em;
  }
  .source {
    display: inline-flex;
    align-items: center;
    gap: 5px;
  }
  .versions {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(176px, 1fr));
    gap: 12px;
  }
  .version {
    display: flex;
    flex-direction: column;
    padding: 0;
    overflow: hidden;
    border: 1px solid var(--border);
    border-radius: 14px;
    background: var(--bg-subtle);
    text-align: left;
    cursor: pointer;
    transition:
      transform 0.18s var(--ease-out),
      border-color 0.15s,
      box-shadow 0.18s;
  }
  .version:hover {
    transform: translateY(-2px);
    border-color: var(--border-strong);
    box-shadow: 0 12px 28px rgb(0 0 0 / 0.35);
  }
  .version.on {
    border-color: var(--accent);
    box-shadow:
      0 0 0 1px var(--accent),
      0 12px 32px color-mix(in srgb, var(--accent) 25%, transparent);
  }
  .version-art {
    position: relative;
    height: 92px;
    overflow: hidden;
  }
  .version-number {
    position: absolute;
    left: 14px;
    bottom: 8px;
    color: #fff;
    font-family: "Segoe UI Variable Display", var(--font-sans);
    font-size: 24px;
    font-weight: 800;
    letter-spacing: -0.02em;
    text-shadow: 0 2px 12px rgb(0 0 0 / 0.6);
  }
  .tag {
    position: absolute;
    top: 8px;
    right: 8px;
    display: inline-flex;
    align-items: center;
    gap: 4px;
    height: 20px;
    padding: 0 7px;
    border-radius: 999px;
    background: rgb(0 0 0 / 0.45);
    backdrop-filter: blur(8px);
    color: #fff;
    font-size: 10.5px;
    font-weight: 650;
  }
  .tag.live {
    color: var(--success);
  }
  .version-body {
    display: flex;
    flex-direction: column;
    gap: 1px;
    padding: 10px 14px 12px;
  }
  .version-name {
    font-weight: 650;
  }
  .version-sub {
    color: var(--text-faint);
    font-size: 12px;
  }

  /* ---- Panels -------------------------------------------------------------- */
  .columns {
    display: grid;
    grid-template-columns: 1.25fr 1fr;
    gap: 14px;
    margin-top: 30px;
  }
  @media (max-width: 980px) {
    .columns {
      grid-template-columns: 1fr;
    }
  }
  .panel {
    padding: 16px;
    border: 1px solid var(--border);
    border-radius: 16px;
    background: var(--bg-subtle);
  }
  .panel-head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 12px;
  }
  .servers {
    display: flex;
    flex-direction: column;
    gap: 6px;
  }
  .server {
    display: flex;
    align-items: center;
    gap: 12px;
    padding: 8px 8px 8px 10px;
    border-radius: 11px;
    background: var(--bg-hover);
    transition: background-color 0.15s;
  }
  .server:hover {
    background: var(--bg-active);
  }
  .favicon {
    width: 36px;
    height: 36px;
    flex-shrink: 0;
    border-radius: 8px;
    image-rendering: pixelated;
  }
  .placeholder {
    display: grid;
    place-items: center;
    background: var(--bg-elevated);
    color: var(--text-faint);
  }
  .server-text {
    display: flex;
    flex: 1;
    flex-direction: column;
    min-width: 0;
    line-height: 1.35;
  }
  .server-name {
    font-weight: 650;
  }
  .motd {
    font-size: 12px;
  }
  .players {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    color: var(--text-muted);
    font-size: 12px;
    font-variant-numeric: tabular-nums;
  }
  .players .dot {
    width: 6px;
    height: 6px;
    color: var(--success);
  }
  .join {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    height: 32px;
    padding: 0 14px;
    border: none;
    border-radius: 9px;
    background: var(--accent);
    color: #fff;
    font-size: 12.5px;
    font-weight: 650;
    cursor: pointer;
    transition: filter 0.15s;
  }
  .join:hover {
    filter: brightness(1.12);
  }
  .join:disabled {
    background: var(--bg-active);
    color: var(--text-faint);
    cursor: not-allowed;
    filter: none;
  }
  .empty-note {
    line-height: 1.5;
  }
  .client {
    background:
      radial-gradient(120% 90% at 100% 0%, color-mix(in srgb, var(--accent) 16%, transparent), transparent 60%),
      var(--bg-subtle);
  }
  .keycap {
    padding: 3px 8px;
    border: 1px solid var(--border-strong);
    border-bottom-width: 2px;
    border-radius: 6px;
    background: var(--bg-elevated);
    color: var(--text-muted);
    font-size: 11px;
    font-weight: 600;
  }
  .features {
    display: flex;
    flex-direction: column;
    gap: 12px;
    margin: 0;
    padding: 0;
    list-style: none;
  }
  .features li {
    display: flex;
    gap: 12px;
  }
  .feature-icon {
    display: grid;
    flex-shrink: 0;
    place-items: center;
    width: 32px;
    height: 32px;
    border-radius: 9px;
    background: var(--accent-soft);
    color: var(--accent);
  }
  .features strong {
    font-size: 13px;
    font-weight: 650;
  }
  .features p {
    color: var(--text-muted);
    font-size: 12px;
    line-height: 1.45;
  }
</style>
