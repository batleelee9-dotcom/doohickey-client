<script lang="ts">
  import { getCurrentWindow } from "@tauri-apps/api/window";
  import { onMount, tick } from "svelte";
  import { accounts } from "./lib/accounts.svelte";
  import CommandPalette, { type PaletteMode } from "./lib/components/CommandPalette.svelte";
  import CrashDialog from "./lib/components/CrashDialog.svelte";
  import Icon from "./lib/components/Icon.svelte";
  import Sidebar from "./lib/components/Sidebar.svelte";
  import TitleBar from "./lib/components/TitleBar.svelte";
  import Toasts from "./lib/components/Toasts.svelte";
  import { toAppError, type AppError } from "./lib/ipc";
  import { router } from "./lib/router.svelte";
  import { store } from "./lib/store.svelte";
  import ConsoleModal from "./lib/components/ConsoleModal.svelte";
  import Login from "./screens/Login.svelte";
  import Play from "./screens/Play.svelte";
  import Servers from "./screens/Servers.svelte";
  import Settings from "./screens/Settings.svelte";
  import Skins from "./screens/Skins.svelte";

  let ready = $state(false);
  let fatal = $state<AppError | null>(null);
  /** Showing the sign-in screen while already signed in ("Add account"). */
  let addingAccount = $state(false);
  let palette = $state<PaletteMode | null>(null);

  const signedIn = $derived(ready && !!accounts.active && !addingAccount);

  onMount(async () => {
    try {
      await store.init();
      ready = true;
    } catch (e) {
      fatal = toAppError(e);
    }
    // The window is created hidden (tauri.conf.json) so nobody sees an empty
    // frame; reveal it once the first real screen has rendered.
    await tick();
    await getCurrentWindow().show();
  });

  function addAccount() {
    palette = null;
    addingAccount = true;
  }

  // Keyboard-first: every screen is one shortcut away.
  function onKeydown(e: KeyboardEvent) {
    if (!signedIn || !(e.ctrlKey || e.metaKey) || e.altKey) return;
    const key = e.key.toLowerCase();
    const routes: Record<string, () => void> = {
      k: () => (palette = palette ? null : "all"),
      "1": () => router.go({ name: "home" }),
      "2": () => router.go({ name: "servers" }),
      "3": () => router.go({ name: "skins" }),
      ",": () => router.go({ name: "settings" }),
      enter: () => store.playSelected(),
    };
    const action = routes[key];
    if (action) {
      e.preventDefault();
      action();
    }
  }
</script>

<svelte:window onkeydown={onKeydown} />

<div class="app">
  <TitleBar showActions={signedIn} onOpenPalette={(mode) => (palette = mode)} />

  {#if fatal}
    <div class="fatal">
      <Icon name="alert" size={22} />
      <h1>Doohickey couldn't start</h1>
      <p>{fatal.message}</p>
    </div>
  {:else if ready && !signedIn && store.info}
    <main class="login-main">
      <Login
        info={store.info}
        onBack={accounts.active ? () => (addingAccount = false) : undefined}
        onDone={() => (addingAccount = false)}
      />
    </main>
  {:else if signedIn}
    <div class="shell">
      <Sidebar />
      <main>
        {#key router.route.name}
          {#if router.route.name === "home"}
            <Play />
          {:else if router.route.name === "servers"}
            <Servers />
          {:else if router.route.name === "skins"}
            <Skins />
          {:else}
            <Settings section={router.route.section} onAddAccount={addAccount} />
          {/if}
        {/key}
      </main>
    </div>
  {/if}

  {#if palette && signedIn}
    <CommandPalette
      mode={palette}
      onClose={() => (palette = null)}
      onAddAccount={addAccount}
    />
  {/if}
  {#if store.consoleFor && signedIn}
    <ConsoleModal instanceId={store.consoleFor} onClose={() => (store.consoleFor = null)} />
  {/if}
  {#if signedIn && store.unseenCrash}
    <CrashDialog crash={store.unseenCrash} />
  {/if}
  <Toasts />
</div>

<style>
  .app {
    display: flex;
    flex-direction: column;
    height: 100vh;
  }
  .shell {
    display: flex;
    flex: 1;
    min-height: 0;
  }
  main {
    position: relative;
    flex: 1;
    min-width: 0;
    min-height: 0;
  }
  .login-main {
    flex: 1;
    min-height: 0;
  }
  .fatal {
    display: flex;
    flex: 1;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    gap: 8px;
    padding: 32px;
    color: var(--danger);
    text-align: center;
  }
  .fatal h1 {
    color: var(--text);
    font-size: 18px;
    font-weight: 600;
  }
  .fatal p {
    max-width: 440px;
    color: var(--text-muted);
    user-select: text;
  }
</style>
