<script lang="ts">
  import { open } from "@tauri-apps/plugin-dialog";
  import { fly } from "svelte/transition";
  import { accounts } from "../lib/accounts.svelte";
  import CrashDetails from "../lib/components/CrashDetails.svelte";
  import Icon from "../lib/components/Icon.svelte";
  import Modal from "../lib/components/Modal.svelte";
  import SkinHead from "../lib/components/SkinHead.svelte";
  import Spinner from "../lib/components/Spinner.svelte";
  import { accountKindLabel, bytes } from "../lib/format";
  import { api, type CrashAnalysis, type InstalledRuntime, type Material, type UpdateInfo } from "../lib/ipc";
  import { dur } from "../lib/platform";
  import { store } from "../lib/store.svelte";
  import { ACCENTS } from "../lib/theme";
  import { PRESETS } from "../lib/presets";

  let { section, onAddAccount }: { section?: string; onAddAccount: () => void } = $props();

  const s = $derived(store.settings!);
  const info = $derived(store.info!);
  let runtimes = $state<InstalledRuntime[]>([]);
  let cfKey = $state("");
  let update = $state<UpdateInfo | null | undefined>(undefined);
  let checking = $state(false);
  let installing = $state<string | null>(null);
  let analysis = $state<CrashAnalysis | null>(null);
  // svelte-ignore state_referenced_locally
  let memory = $state(store.settings?.defaultMemoryMb ?? 4096);
  let opacity = $state(85);
  // svelte-ignore state_referenced_locally
  let manifestUrl = $state(store.settings?.manifestUrl ?? "");
  let savingManifest = $state(false);

  const maxMemory = $derived(Math.max(2048, Math.min(32768, Math.floor((info.totalMemoryMb - 1536) / 512) * 512)));
  const materials = $derived<{ id: Material; label: string }[]>(
    info.platform === "macos"
      ? [{ id: "solid", label: "Solid" }, { id: "vibrancy", label: "Vibrancy" }]
      : info.platform === "windows"
        ? [{ id: "solid", label: "Solid" }, { id: "mica", label: "Mica" }, { id: "acrylic", label: "Acrylic" }]
        : [{ id: "solid", label: "Solid" }],
  );

  $effect(() => {
    opacity = store.settings?.opacity ?? 85;
    api.listJavaRuntimes().then((r) => (runtimes = r)).catch(() => {});
  });

  $effect(() => {
    if (section) document.getElementById(`s-${section}`)?.scrollIntoView({ block: "start" });
  });

  async function saveManifestUrl() {
    savingManifest = true;
    try {
      await store.updateSettings({ manifestUrl: manifestUrl.trim() || null });
      await store.loadManifest();
      if (store.manifest?.source === "remote") store.toast("success", `Loaded ${store.manifest.builds.length} builds.`);
    } finally {
      savingManifest = false;
    }
  }

  async function saveCurseforgeKey() {
    try {
      const ok = await api.setCurseforgeKey(cfKey.trim() || null);
      store.info = await api.appInfo();
      cfKey = "";
      store.toast("success", ok ? "CurseForge key saved to your system keychain." : "CurseForge key removed.");
    } catch (e) {
      store.error(e);
    }
  }

  async function checkUpdates() {
    checking = true;
    try {
      update = await api.checkUpdate();
    } catch (e) {
      store.error(e);
    } finally {
      checking = false;
    }
  }

  async function installUpdate() {
    installing = "Downloading";
    try {
      await api.installUpdate((p) => {
        installing = p.total ? `Downloading ${Math.round((100 * p.downloaded) / p.total)}%` : `Downloading ${bytes(p.downloaded)}`;
      });
    } catch (e) {
      store.error(e);
      installing = null;
    }
  }

  async function removeRuntime(r: InstalledRuntime) {
    try {
      await api.removeJavaRuntime(r.component);
      runtimes = runtimes.filter((x) => x.component !== r.component);
      store.toast("success", `Removed ${r.component}. It downloads again when a version needs it.`);
    } catch (e) {
      store.error(e);
    }
  }

  async function analyzeFile() {
    const picked = await open({ multiple: false, filters: [{ name: "Logs", extensions: ["log", "txt", "gz"] }] });
    if (typeof picked !== "string") return;
    try {
      analysis = await api.analyzeLogFile(picked);
    } catch (e) {
      store.error(e);
    }
  }

  const gb = (mb: number) => `${(mb / 1024).toFixed(mb % 1024 ? 1 : 0)} GB`;
</script>

<div class="page" in:fly={{ y: 6, duration: dur(220), opacity: 0 }}>
  <div class="page-narrow">
    <h1 class="page-title">Settings</h1>

    <!-- Accounts -->
    <div class="section-title" id="s-accounts">Accounts</div>
    <div class="list">
      {#each accounts.list as a (a.id)}
        {@const active = a.id === accounts.activeId}
        <div class="list-row">
          <SkinHead skinUrl={a.skinUrl} name={a.username} size={30} />
          <div class="grow">
            <div class="strong">{a.username}</div>
            <div class="faint small">{accountKindLabel[a.kind]} account</div>
          </div>
          {#if active}
            <span class="badge success"><span class="dot"></span> Active</span>
          {:else}
            <button class="btn btn-ghost btn-sm" onclick={() => accounts.switchTo(a.id).catch((e) => store.error(e))}>Switch</button>
          {/if}
          <button class="icon-btn danger" title="Sign out" onclick={() => accounts.remove(a.id).catch((e) => store.error(e))}>
            <Icon name="logout" size={14} />
          </button>
        </div>
      {/each}
      <button class="list-row add" onclick={onAddAccount}><Icon name="plus" size={14} /> Add account</button>
    </div>

    <!-- Appearance -->
    <div class="section-title" id="s-appearance">Appearance</div>
    <div class="card">
      <div class="setting">
        <div class="setting-text"><div class="setting-title">Theme</div></div>
        <div class="segmented">
          <button class:on={s.theme === "system"} onclick={() => store.updateSettings({ theme: "system" })}>System</button>
          <button class:on={s.theme === "dark"} onclick={() => store.updateSettings({ theme: "dark" })}>Dark</button>
          <button class:on={s.theme === "light"} onclick={() => store.updateSettings({ theme: "light" })}>Light</button>
        </div>
      </div>
      <div class="setting">
        <div class="setting-text"><div class="setting-title">Accent colour</div></div>
        <div class="swatches">
          {#each ACCENTS as c (c)}
            <button class="swatch" class:on={s.accent === c} style:--c={c} aria-label="Accent {c}" onclick={() => store.updateSettings({ accent: c })}></button>
          {/each}
          <label class="swatch custom" style:--c={s.accent} title="Custom colour">
            <input type="color" value={s.accent} onchange={(e) => store.updateSettings({ accent: e.currentTarget.value })} />
          </label>
        </div>
      </div>
      <div class="setting">
        <div class="setting-text">
          <div class="setting-title">Window material</div>
          <div class="setting-desc">Translucent backdrops blur your desktop behind the launcher.{info.platform === "windows" ? " Mica needs Windows 11." : ""}</div>
        </div>
        <div class="segmented">
          {#each materials as m (m.id)}
            <button class:on={s.material === m.id} onclick={() => store.updateSettings({ material: m.id })}>{m.label}</button>
          {/each}
        </div>
      </div>
      {#if s.material !== "solid"}
        <div class="setting">
          <div class="setting-text"><div class="setting-title">Background opacity</div></div>
          <input class="range" type="range" min="30" max="100" bind:value={opacity} onchange={() => store.updateSettings({ opacity })} />
          <span class="mono val">{opacity}%</span>
        </div>
      {/if}
    </div>

    <!-- Launching -->
    <div class="section-title" id="s-launching">Launching</div>
    <div class="card">
      <div class="setting">
        <div class="setting-text">
          <div class="setting-title">When a game starts</div>
          <div class="setting-desc">"Close" frees the launcher's memory for the game; it returns from the tray when you quit.</div>
        </div>
        <select class="select narrow" value={s.onLaunch} onchange={(e) => store.updateSettings({ onLaunch: e.currentTarget.value as typeof s.onLaunch })}>
          <option value="close">Close to tray</option>
          <option value="minimize">Minimize</option>
          <option value="keep">Keep open</option>
        </select>
      </div>
      <div class="setting">
        <div class="setting-text">
          <div class="setting-title">Memory</div>
          <div class="setting-desc">{gb(info.totalMemoryMb)} installed, {gb(info.recommendedMemoryMb)} recommended</div>
        </div>
        <input class="range" type="range" min="1024" max={maxMemory} step="512" bind:value={memory} onchange={() => store.updateSettings({ defaultMemoryMb: memory })} />
        <span class="mono val">{gb(memory)}</span>
      </div>
      <div class="setting">
        <div class="setting-text">
          <div class="setting-title">JVM preset</div>
          <div class="setting-desc">{PRESETS.find((p) => p.id === s.defaultJvmPreset)?.desc}</div>
        </div>
        <select class="select narrow" value={s.defaultJvmPreset} onchange={(e) => store.updateSettings({ defaultJvmPreset: e.currentTarget.value as typeof s.defaultJvmPreset })}>
          {#each PRESETS as p (p.id)}<option value={p.id}>{p.label}</option>{/each}
        </select>
      </div>
    </div>

    <!-- Java -->
    <div class="section-title" id="s-java">Java runtimes</div>
    {#if runtimes.length}
      <div class="list">
        {#each runtimes as r (r.component)}
          <div class="list-row">
            <Icon name="cpu" size={16} />
            <div class="grow">
              <div class="strong">Java {r.version.match(/^\d+/)?.[0]} <span class="faint small">{r.version}</span></div>
              <div class="faint small mono ellipsis">{r.component} · {bytes(r.sizeBytes)}</div>
            </div>
            <button class="icon-btn danger" title="Remove" onclick={() => removeRuntime(r)}><Icon name="trash" size={14} /></button>
          </div>
        {/each}
      </div>
    {:else}
      <p class="faint">None yet — Doohickey downloads the right Java automatically the first time a version needs it.</p>
    {/if}

    <!-- Integrations -->
    <div class="section-title" id="s-integrations">Integrations</div>
    <div class="card">
      <div class="setting">
        <div class="setting-text">
          <div class="setting-title">Discord Rich Presence</div>
          <div class="setting-desc">
            {info.discordConfigured ? "Show what you're playing on your Discord profile." : "This build has no Discord application id (QUARTZ_DISCORD_APP_ID)."}
          </div>
        </div>
        <button class="switch" aria-pressed={s.discordRpc && info.discordConfigured} aria-label="Discord Rich Presence" disabled={!info.discordConfigured} onclick={() => store.updateSettings({ discordRpc: !s.discordRpc })}></button>
      </div>
      <div class="setting">
        <div class="setting-text">
          <div class="setting-title">CurseForge API key</div>
          <div class="setting-desc">
            {info.curseforgeConfigured ? "Saved in your system keychain." : "Needed to browse CurseForge. Get one at console.curseforge.com."}
          </div>
        </div>
        <input class="input narrow" type="password" bind:value={cfKey} placeholder={info.curseforgeConfigured ? "••••••••" : "Paste key"} autocomplete="off" />
        <button class="btn btn-secondary btn-sm" disabled={!cfKey.trim() && !info.curseforgeConfigured} onclick={saveCurseforgeKey}>
          {cfKey.trim() || !info.curseforgeConfigured ? "Save" : "Remove"}
        </button>
      </div>
    </div>

    <!-- Builds -->
    <div class="section-title" id="s-builds">Builds</div>
    <div class="card">
      <form class="setting" onsubmit={(e) => (e.preventDefault(), void saveManifestUrl())}>
        <div class="setting-text">
          <div class="setting-title">Build list</div>
          <div class="setting-desc">
            {#if store.manifest?.source === "remote"}
              Loaded from your URL{store.manifest.updated ? ` · updated ${store.manifest.updated}` : ""}.
            {:else if store.manifest?.problem}
              {store.manifest.problem}
            {:else}
              Using the list built into Doohickey. Point this at a JSON manifest to change builds without updating the launcher.
            {/if}
          </div>
        </div>
        <input class="input manifest-url" bind:value={manifestUrl} placeholder="https://…/builds.json" spellcheck="false" />
        <button class="btn btn-secondary btn-sm" disabled={savingManifest}>
          {#if savingManifest}<Spinner size={12} />{/if} {manifestUrl.trim() ? "Load" : "Use built-in"}
        </button>
      </form>
    </div>

    <!-- Updates -->
    <div class="section-title" id="s-updates">Updates</div>
    <div class="card">
      <div class="setting">
        <div class="setting-text">
          <div class="setting-title">Doohickey Client {info.version}</div>
          <div class="setting-desc">
            {#if !info.updaterConfigured}
              This build has no update server configured.
            {:else if update === undefined}
              Updates are signed and verified before installing.
            {:else if update === null}
              You're on the latest version.
            {:else}
              Version {update.version} is available.
            {/if}
          </div>
        </div>
        {#if update}
          <button class="btn btn-accent btn-sm" disabled={!!installing} onclick={installUpdate}>
            {#if installing}<Spinner size={12} /> {installing}{:else}Install and restart{/if}
          </button>
        {:else}
          <button class="btn btn-secondary btn-sm" disabled={!info.updaterConfigured || checking} onclick={checkUpdates}>
            {#if checking}<Spinner size={12} />{/if} Check for updates
          </button>
        {/if}
      </div>
    </div>

    <!-- Tools -->
    <div class="section-title" id="s-tools">Tools & storage</div>
    <div class="card">
      <div class="setting">
        <div class="setting-text">
          <div class="setting-title">Crash log analyzer</div>
          <div class="setting-desc">Explain any Minecraft log or crash report — even from another launcher.</div>
        </div>
        <button class="btn btn-secondary btn-sm" onclick={analyzeFile}><Icon name="bug" size={13} /> Analyze a file…</button>
      </div>
      <div class="setting">
        <div class="setting-text">
          <div class="setting-title">Data folder</div>
          <div class="setting-desc mono ellipsis" data-selectable>{info.dataDir}</div>
        </div>
        <button class="btn btn-secondary btn-sm" onclick={() => api.openFolder("data").catch((e) => store.error(e))}><Icon name="folder" size={13} /> Open</button>
      </div>
    </div>

    <p class="legal faint">
      Doohickey Client {info.version}. Not an official Minecraft product. Not approved by or associated with Mojang or Microsoft.
    </p>
  </div>
</div>

{#if analysis}
  <Modal title={analysis.title} width={540} onClose={() => (analysis = null)}>
    <CrashDetails {analysis} />
    {#snippet footer()}<button class="btn btn-primary" onclick={() => (analysis = null)}>Done</button>{/snippet}
  </Modal>
{/if}

<style>
  .grow {
    flex: 1;
    min-width: 0;
    line-height: 1.35;
  }
  .strong {
    font-weight: 500;
  }
  .small {
    font-size: 12px;
  }
  .add {
    width: 100%;
    border: none;
    background: transparent;
    color: var(--text-muted);
    font-weight: 500;
    cursor: pointer;
  }
  .swatches {
    display: flex;
    align-items: center;
    gap: 6px;
  }
  .swatch {
    position: relative;
    width: 22px;
    height: 22px;
    border: 2px solid transparent;
    border-radius: 50%;
    background: var(--c);
    box-shadow: inset 0 0 0 2px var(--bg-subtle);
    cursor: pointer;
  }
  .swatch.on {
    border-color: var(--c);
  }
  .custom {
    overflow: hidden;
    background: conic-gradient(red, yellow, lime, aqua, blue, magenta, red);
  }
  .custom input {
    position: absolute;
    inset: 0;
    opacity: 0;
    cursor: pointer;
  }
  .range {
    width: 200px;
  }
  .val {
    width: 52px;
    text-align: right;
  }
  .manifest-url {
    width: 260px;
  }
  .narrow {
    width: 180px;
  }
  .legal {
    margin-top: 32px;
    font-size: 12px;
  }
</style>
