<script lang="ts">
  import { open } from "@tauri-apps/plugin-dialog";
  import type { SkinViewer } from "skinview3d";
  import { fly } from "svelte/transition";
  import { accounts } from "../lib/accounts.svelte";
  import Icon from "../lib/components/Icon.svelte";
  import SkinHead from "../lib/components/SkinHead.svelte";
  import Spinner from "../lib/components/Spinner.svelte";
  import { api, type ProfileTextures, type Skin, type SkinVariant } from "../lib/ipc";
  import { dur } from "../lib/platform";
  import { store } from "../lib/store.svelte";

  let canvas: HTMLCanvasElement;
  let stage: HTMLElement;
  let viewer: SkinViewer | null = null;
  let skinview: typeof import("skinview3d") | null = null;

  let library = $state<Skin[]>([]);
  let thumbs = $state<Record<string, string>>({});
  let profile = $state<ProfileTextures | null>(null);
  let profileError = $state<string | null>(null);
  let loadingProfile = $state(false);
  let previewId = $state<string | "current">("current");
  let variant = $state<SkinVariant>("classic");
  let capeId = $state<string | null>(null);
  let animation = $state<"idle" | "walk" | "run">("walk");
  let applying = $state(false);

  const microsoft = $derived(accounts.active?.kind === "microsoft");
  const previewSkin = $derived(previewId === "current" ? null : library.find((s) => s.id === previewId) ?? null);

  // three.js (≈600 KB) loads only when this page opens, never at startup.
  $effect(() => {
    let disposed = false;
    import("skinview3d").then((mod) => {
      if (disposed) return;
      skinview = mod;
      viewer = new mod.SkinViewer({ canvas, width: stage.clientWidth, height: stage.clientHeight, zoom: 0.72 });
      viewer.autoRotate = false;
      viewer.animation = new mod.WalkingAnimation();
      void refreshPreview();
    });
    const resize = new ResizeObserver(() => viewer?.setSize(stage.clientWidth, stage.clientHeight));
    resize.observe(stage);
    return () => {
      disposed = true;
      resize.disconnect();
      viewer?.dispose();
      viewer = null;
    };
  });

  $effect(() => {
    api.listSkins().then((l) => (library = l)).catch((e) => store.error(e));
  });

  $effect(() => {
    for (const s of library) {
      if (!thumbs[s.id]) api.skinData(s.id).then((d) => (thumbs[s.id] = d)).catch(() => {});
    }
  });

  // The account's current skin and owned capes (Microsoft accounts only).
  $effect(() => {
    void accounts.activeId;
    profile = null;
    profileError = null;
    if (!microsoft) return;
    loadingProfile = true;
    api
      .profileTextures()
      .then((p) => {
        profile = p;
        variant = p.variant;
        capeId = p.capes.find((c) => c.active)?.id ?? null;
      })
      .catch((e) => (profileError = e?.message ?? String(e)))
      .finally(() => (loadingProfile = false));
  });

  $effect(() => {
    if (!skinview || !viewer) return;
    viewer.animation =
      animation === "idle" ? new skinview.IdleAnimation() : animation === "run" ? new skinview.RunningAnimation() : new skinview.WalkingAnimation();
  });

  $effect(() => {
    void [previewId, variant, capeId, profile, thumbs[previewId]];
    void refreshPreview();
  });

  /** A neutral mannequin texture for accounts without a skin. Drawn here
   *  rather than shipping Mojang's default skins, which can't be redistributed. */
  function mannequin(): HTMLCanvasElement {
    const c = document.createElement("canvas");
    c.width = c.height = 64;
    const g = c.getContext("2d")!;
    // Base-layer regions of the 64×64 skin layout: [x, y, w, h, colour].
    const parts: [number, number, number, number, string][] = [
      [0, 0, 32, 16, "#9a9aa6"], // head
      [16, 16, 24, 16, "#6f6f7c"], // body
      [40, 16, 16, 16, "#838390"], // right arm
      [32, 48, 16, 16, "#838390"], // left arm
      [0, 16, 16, 16, "#55555f"], // right leg
      [16, 48, 16, 16, "#55555f"], // left leg
    ];
    for (const [x, y, w, h, colour] of parts) {
      g.fillStyle = colour;
      g.fillRect(x, y, w, h);
    }
    return c;
  }

  async function refreshPreview() {
    if (!viewer) return;
    const model = variant === "slim" ? "slim" : "default";
    try {
      if (previewSkin) {
        const data = thumbs[previewSkin.id] ?? (await api.skinData(previewSkin.id));
        await viewer.loadSkin(data, { model });
      } else if (profile?.skinUrl) {
        await viewer.loadSkin(await api.textureData(profile.skinUrl), { model });
      } else if (accounts.active?.skinUrl) {
        await viewer.loadSkin(await api.textureData(accounts.active.skinUrl), { model });
      } else {
        viewer.loadSkin(mannequin(), { model });
      }
      const cape = profile?.capes.find((c) => c.id === capeId);
      if (cape) await viewer.loadCape(await api.textureData(cape.url));
      else viewer.loadCape(null);
    } catch {
      // A texture that fails to load just leaves the model blank.
    }
  }

  async function addSkin() {
    const picked = await open({ multiple: false, filters: [{ name: "Skin", extensions: ["png"] }] });
    if (typeof picked !== "string") return;
    const name = picked.split(/[\\/]/).pop()?.replace(/\.png$/i, "") ?? "Skin";
    try {
      const skin = await api.importSkin(picked, name, variant);
      library = [skin, ...library];
      previewId = skin.id;
      variant = skin.variant;
    } catch (e) {
      store.error(e);
    }
  }

  async function select(skin: Skin) {
    previewId = skin.id;
    variant = skin.variant;
  }

  async function setVariant(v: SkinVariant) {
    variant = v;
    if (previewSkin) {
      try {
        library = await api.updateSkin(previewSkin.id, null, v);
      } catch (e) {
        store.error(e);
      }
    }
  }

  async function remove(skin: Skin) {
    try {
      library = await api.deleteSkin(skin.id);
      if (previewId === skin.id) previewId = "current";
    } catch (e) {
      store.error(e);
    }
  }

  async function apply() {
    if (!previewSkin) return;
    applying = true;
    try {
      profile = await api.applySkin(previewSkin.id);
      await accounts.load();
      previewId = "current";
      store.toast("success", "Skin updated. Other players will see it after rejoining.");
    } catch (e) {
      store.error(e);
    } finally {
      applying = false;
    }
  }

  async function chooseCape(id: string | null) {
    const previous = capeId;
    capeId = id;
    try {
      profile = await api.setCape(id);
    } catch (e) {
      capeId = previous;
      store.error(e);
    }
  }
</script>

<div class="skins" in:fly={{ y: 6, duration: dur(220), opacity: 0 }}>
  <section class="viewer card" bind:this={stage}>
    <canvas bind:this={canvas}></canvas>
    <div class="viewer-top">
      <div class="who">
        {#if accounts.active}<SkinHead skinUrl={accounts.active.skinUrl} name={accounts.active.username} size={22} />{accounts.active.username}{/if}
      </div>
      <span class="badge">{previewSkin ? `Previewing “${previewSkin.name}”` : "Current skin"}</span>
    </div>
    <div class="viewer-bottom">
      <div class="segmented">
        <button class:on={animation === "idle"} onclick={() => (animation = "idle")}>Idle</button>
        <button class:on={animation === "walk"} onclick={() => (animation = "walk")}>Walk</button>
        <button class:on={animation === "run"} onclick={() => (animation = "run")}>Run</button>
      </div>
      <div class="segmented">
        <button class:on={variant === "classic"} onclick={() => setVariant("classic")}>Classic arms</button>
        <button class:on={variant === "slim"} onclick={() => setVariant("slim")}>Slim arms</button>
      </div>
    </div>
  </section>

  <aside class="side">
    <div class="row head">
      <h1 class="page-title">Skins</h1>
      <span class="spacer"></span>
      <button class="btn btn-secondary btn-sm" onclick={addSkin}><Icon name="upload" size={13} /> Add skin</button>
    </div>

    {#if !microsoft}
      <div class="notice">
        <Icon name="info" size={15} />
        <span>Offline accounts have no Minecraft profile, so skins can be previewed here but not applied. Sign in with Microsoft to change your skin.</span>
      </div>
    {:else if profileError}
      <div class="notice warn"><Icon name="alert" size={15} /> {profileError}</div>
    {/if}

    <div class="section-title">Library</div>
    <div class="library">
      <button class="skin-card" class:on={previewId === "current"} onclick={() => (previewId = "current")}>
        {#if accounts.active}<SkinHead skinUrl={accounts.active.skinUrl} name={accounts.active.username} size={40} />{/if}
        <span class="ellipsis">Current</span>
      </button>
      {#each library as s (s.id)}
        <div class="skin-card" class:on={previewId === s.id} role="button" tabindex="0" onclick={() => select(s)} onkeydown={(e) => e.key === "Enter" && select(s)}>
          <SkinHead skinUrl={thumbs[s.id] ?? null} name={s.name} size={40} />
          <span class="ellipsis">{s.name}</span>
          <button class="icon-btn danger remove" title="Remove from library" onclick={(e) => { e.stopPropagation(); void remove(s); }}>
            <Icon name="x" size={12} />
          </button>
        </div>
      {/each}
    </div>
    {#if previewSkin}
      <button class="btn btn-primary btn-block apply" disabled={!microsoft || applying} onclick={apply}>
        {#if applying}<Spinner size={13} />{/if} Apply “{previewSkin.name}” to {accounts.active?.username}
      </button>
    {/if}

    {#if microsoft}
      <div class="section-title">Capes</div>
      {#if loadingProfile}
        <div class="faint row"><Spinner size={12} /> Loading capes…</div>
      {:else if profile && profile.capes.length}
        <div class="capes">
          <button class="cape" class:on={capeId === null} onclick={() => chooseCape(null)}>
            <span class="cape-none"><Icon name="x" size={14} /></span>No cape
          </button>
          {#each profile.capes as c (c.id)}
            <button class="cape" class:on={capeId === c.id} onclick={() => chooseCape(c.id)}>
              <span class="cape-art" style:background-image="url({c.url})"></span>{c.alias}
            </button>
          {/each}
        </div>
      {:else}
        <p class="faint">This account doesn't own any capes.</p>
      {/if}
    {/if}
  </aside>
</div>

<style>
  .skins {
    display: grid;
    grid-template-columns: minmax(320px, 1fr) 360px;
    gap: 16px;
    height: 100%;
    padding: 20px;
  }
  .viewer {
    position: relative;
    min-height: 0;
    overflow: hidden;
    background: radial-gradient(ellipse at 50% 35%, color-mix(in srgb, var(--accent) 10%, var(--bg-subtle)), var(--bg-subtle) 70%);
  }
  canvas {
    display: block;
    cursor: grab;
  }
  canvas:active {
    cursor: grabbing;
  }
  .viewer-top,
  .viewer-bottom {
    position: absolute;
    right: 14px;
    left: 14px;
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 8px;
    pointer-events: none;
  }
  .viewer-top {
    top: 14px;
  }
  .viewer-bottom {
    bottom: 14px;
  }
  .viewer-bottom > * {
    pointer-events: auto;
  }
  .who {
    display: flex;
    align-items: center;
    gap: 8px;
    font-weight: 600;
  }
  .side {
    overflow-y: auto;
    padding: 8px 4px 8px 8px;
  }
  .head {
    margin-bottom: 16px;
  }
  .library {
    display: grid;
    grid-template-columns: repeat(3, 1fr);
    gap: 8px;
  }
  .skin-card {
    position: relative;
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 8px;
    padding: 14px 8px 10px;
    border: 1px solid var(--border);
    border-radius: var(--radius);
    background: var(--bg-subtle);
    font-size: 12px;
    cursor: pointer;
    min-width: 0;
  }
  .skin-card > .ellipsis {
    max-width: 100%;
  }
  .skin-card:hover {
    border-color: var(--border-strong);
  }
  .skin-card.on {
    border-color: var(--accent);
    background: var(--accent-soft);
  }
  .remove {
    position: absolute;
    top: 2px;
    right: 2px;
    width: 22px;
    height: 22px;
    opacity: 0;
  }
  .skin-card:hover .remove {
    opacity: 1;
  }
  .apply {
    margin-top: 12px;
  }
  .capes {
    display: grid;
    grid-template-columns: repeat(2, 1fr);
    gap: 8px;
  }
  .cape {
    display: flex;
    align-items: center;
    gap: 10px;
    padding: 8px 10px;
    border: 1px solid var(--border);
    border-radius: var(--radius);
    background: var(--bg-subtle);
    font-size: 12.5px;
    text-align: left;
    cursor: pointer;
  }
  .cape.on {
    border-color: var(--accent);
    background: var(--accent-soft);
  }
  /* The cape's front face is the 10×16 region at (1,1) of a 64×32 texture. */
  .cape-art,
  .cape-none {
    display: grid;
    place-items: center;
    width: 20px;
    height: 32px;
    flex-shrink: 0;
    border-radius: 3px;
    background-size: 128px 64px;
    background-position: -2px -2px;
    image-rendering: pixelated;
    background-color: var(--bg-active);
    color: var(--text-faint);
  }
</style>
