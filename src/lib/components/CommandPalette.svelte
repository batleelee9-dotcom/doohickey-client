<script lang="ts" module>
  export type PaletteMode = "all" | "accounts";
</script>

<script lang="ts">
  import { getCurrentWindow } from "@tauri-apps/api/window";
  import { fade, scale } from "svelte/transition";
  import { accounts } from "../accounts.svelte";
  import { accountKindLabel, buildLoaderLabel } from "../format";
  import { api, type Account } from "../ipc";
  import { dur, modKey } from "../platform";
  import { router } from "../router.svelte";
  import { store } from "../store.svelte";
  import Icon, { type IconName } from "./Icon.svelte";
  import SkinHead from "./SkinHead.svelte";

  type Command = {
    id: string;
    label: string;
    group: string;
    hint?: string;
    icon?: IconName;
    account?: Account;
    run: () => unknown;
  };

  let {
    mode,
    onClose,
    onAddAccount,
  }: {
    mode: PaletteMode;
    onClose: () => void;
    onAddAccount: () => void;
  } = $props();

  let query = $state("");
  let selected = $state(0);
  let input: HTMLInputElement;
  let list: HTMLElement;

  // Commands derive from live state, so they're always current.
  const commands = $derived.by(() => {
    const cmds: Command[] = [];
    const accountCmds: Command[] = [];
    for (const a of accounts.list) {
      if (a.id === accounts.activeId) continue;
      accountCmds.push({
        id: `switch:${a.id}`,
        label: `Switch to ${a.username}`,
        group: "Accounts",
        hint: accountKindLabel[a.kind],
        account: a,
        run: () => accounts.switchTo(a.id),
      });
    }
    accountCmds.push({ id: "add-account", label: "Add account…", group: "Accounts", icon: "plus", run: onAddAccount });
    const active = accounts.active;
    if (active) {
      accountCmds.push({ id: "sign-out", label: `Sign out of ${active.username}`, group: "Accounts", icon: "logout", run: () => accounts.remove(active.id) });
    }
    if (mode === "accounts") return accountCmds;

    for (const b of store.manifest?.builds ?? []) {
      for (const l of b.loaders) {
        const running = store.isRunning(l.instanceId);
        cmds.push({
          id: `play:${l.instanceId}`,
          label: `${running ? "Stop" : "Play"} ${b.name}`,
          group: "Play",
          hint: buildLoaderLabel(l.kind),
          icon: running ? "stop" : "play",
          run: () => {
            if (running) return store.stop(l.instanceId);
            store.selectBuild(b.id);
            store.selectLoader(l.kind);
            return store.launchBuild(b, l);
          },
        });
        cmds.push({
          id: `install:${l.instanceId}`,
          label: `Download ${b.name}`,
          group: "Play",
          hint: `${buildLoaderLabel(l.kind)} · without playing`,
          icon: "download",
          run: () => store.installBuild(b, l),
        });
      }
    }

    for (const s of store.servers.filter((s) => s.favorite)) {
      cmds.push({
        id: `join:${s.id}`,
        label: `Join ${s.name}`,
        group: "Servers",
        hint: s.address,
        icon: "server",
        run: () => store.playSelected(s.address),
      });
    }

    const nav: [string, IconName, () => void, string][] = [
      ["Play", "play", () => router.go({ name: "home" }), `${modKey} 1`],
      ["Servers", "server", () => router.go({ name: "servers" }), `${modKey} 2`],
      ["Skins", "shirt", () => router.go({ name: "skins" }), `${modKey} 3`],
      ["Settings", "settings", () => router.go({ name: "settings" }), `${modKey} ,`],
    ];
    for (const [label, icon, run, hint] of nav) cmds.push({ id: `go:${label}`, label: `Go to ${label}`, group: "Navigate", icon, hint, run });

    const dark = document.documentElement.dataset.theme === "dark";
    cmds.push(
      { id: "theme", label: `Switch to ${dark ? "light" : "dark"} theme`, group: "Launcher", icon: "palette", run: () => store.updateSettings({ theme: dark ? "light" : "dark" }) },
      { id: "updates", label: "Check for updates", group: "Launcher", icon: "download", run: () => router.go({ name: "settings", section: "updates" }) },
      { id: "data", label: "Open data folder", group: "Launcher", icon: "folder", run: () => api.openFolder("data") },
    );
    cmds.push(...accountCmds);
    const win = getCurrentWindow();
    cmds.push(
      { id: "win-min", label: "Minimize window", group: "Window", icon: "minimize", run: () => win.minimize() },
      { id: "win-max", label: "Toggle maximize", group: "Window", icon: "maximize", run: () => win.toggleMaximize() },
    );
    return cmds;
  });

  /** Subsequence match, so "plfab" finds "Play Fabric 26.3". */
  function score(label: string, q: string): number {
    const l = label.toLowerCase();
    if (l.startsWith(q)) return 3;
    if (l.includes(q)) return 2;
    let i = 0;
    for (const ch of q) {
      i = l.indexOf(ch, i);
      if (i === -1) return 0;
      i++;
    }
    return 1;
  }

  const results = $derived.by(() => {
    const q = query.toLowerCase().replace(/\s+/g, " ").trim();
    if (!q) return commands;
    return commands
      .map((c) => ({ c, s: score(c.label, q) || score(c.hint ?? "", q) * 0.5 }))
      .filter((x) => x.s > 0)
      .sort((a, b) => b.s - a.s)
      .map((x) => x.c);
  });

  $effect(() => {
    const previous = document.activeElement as HTMLElement | null;
    input.focus();
    return () => previous?.focus?.();
  });

  function move(delta: number) {
    if (!results.length) return;
    selected = (selected + delta + results.length) % results.length;
    list.querySelector(`[data-index="${selected}"]`)?.scrollIntoView({ block: "nearest" });
  }

  async function run(cmd: Command) {
    onClose();
    try {
      await cmd.run();
    } catch (e) {
      store.error(e);
    }
  }

  function onKeydown(e: KeyboardEvent) {
    switch (e.key) {
      case "ArrowDown":
        e.preventDefault();
        move(1);
        break;
      case "ArrowUp":
        e.preventDefault();
        move(-1);
        break;
      case "Enter":
        e.preventDefault();
        if (results[selected]) run(results[selected]);
        break;
      case "Escape":
        e.preventDefault();
        onClose();
        break;
      case "Tab":
        e.preventDefault();
        break;
    }
  }
</script>

<div class="backdrop" transition:fade={{ duration: dur(120) }}>
  <button class="dismiss" tabindex="-1" aria-label="Close command palette" onclick={onClose}></button>
  <div class="palette" role="dialog" aria-modal="true" aria-label="Command palette" transition:scale={{ start: 0.97, duration: dur(140) }}>
    <input
      bind:this={input}
      bind:value={query}
      oninput={() => (selected = 0)}
      onkeydown={onKeydown}
      placeholder={mode === "accounts" ? "Switch account…" : "Play a profile, join a server, or run a command…"}
      role="combobox"
      aria-expanded="true"
      aria-controls="palette-results"
      aria-activedescendant={results[selected] ? `cmd-${results[selected].id}` : undefined}
      autocomplete="off"
      spellcheck="false"
    />
    <div class="results" id="palette-results" role="listbox" bind:this={list}>
      {#each results as cmd, i (cmd.id)}
        {#if !query && (i === 0 || results[i - 1].group !== cmd.group)}
          <div class="group" role="presentation">{cmd.group}</div>
        {/if}
        <div
          id="cmd-{cmd.id}"
          class="item"
          class:selected={i === selected}
          role="option"
          aria-selected={i === selected}
          tabindex="-1"
          data-index={i}
          onmousemove={() => (selected = i)}
          onclick={() => run(cmd)}
          onkeydown={onKeydown}
        >
          {#if cmd.account}
            <SkinHead skinUrl={cmd.account.skinUrl} name={cmd.account.username} size={18} />
          {:else if cmd.icon}
            <span class="icon"><Icon name={cmd.icon} size={14} /></span>
          {/if}
          <span class="label">{cmd.label}</span>
          {#if cmd.hint}<span class="hint">{cmd.hint}</span>{/if}
        </div>
      {:else}
        <div class="empty">No matching commands</div>
      {/each}
    </div>
    <div class="footer">
      <span><kbd>↑</kbd><kbd>↓</kbd> navigate</span>
      <span><kbd>↵</kbd> run</span>
      <span><kbd>esc</kbd> close</span>
    </div>
  </div>
</div>

<style>
  .backdrop {
    position: fixed;
    inset: 0;
    z-index: 100;
    display: flex;
    justify-content: center;
    align-items: flex-start;
    padding-top: 13vh;
    background: var(--overlay);
  }
  .dismiss {
    position: absolute;
    inset: 0;
    border: none;
    background: transparent;
    cursor: default;
  }
  .palette {
    position: relative;
    display: flex;
    flex-direction: column;
    width: min(600px, calc(100vw - 32px));
    max-height: min(480px, 72vh);
    overflow: hidden;
    border-radius: var(--radius-lg);
    background: var(--bg-elevated);
    box-shadow: var(--shadow-lg);
  }
  input {
    flex-shrink: 0;
    height: 52px;
    padding: 0 18px;
    border: none;
    border-bottom: 1px solid var(--border);
    background: transparent;
    color: var(--text);
    font: inherit;
    font-size: 15px;
    outline: none;
  }
  input::placeholder {
    color: var(--text-faint);
  }
  .results {
    overflow-y: auto;
    padding: 6px;
    overscroll-behavior: contain;
  }
  .group {
    padding: 8px 10px 4px;
    color: var(--text-faint);
    font-size: 11.5px;
    font-weight: 500;
  }
  .item {
    display: flex;
    align-items: center;
    gap: 10px;
    height: 38px;
    padding: 0 10px;
    border-radius: 7px;
    color: var(--text-muted);
    cursor: pointer;
    outline: none;
  }
  .item.selected {
    background: var(--bg-active);
    color: var(--text);
  }
  .icon {
    display: grid;
    place-items: center;
    width: 18px;
  }
  .label {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  .hint {
    margin-left: auto;
    color: var(--text-faint);
    font-size: 12px;
    white-space: nowrap;
  }
  .empty {
    padding: 28px;
    text-align: center;
    color: var(--text-faint);
  }
  .footer {
    display: flex;
    gap: 16px;
    flex-shrink: 0;
    padding: 8px 14px;
    border-top: 1px solid var(--border);
    color: var(--text-faint);
    font-size: 11.5px;
  }
  .footer span {
    display: flex;
    align-items: center;
    gap: 4px;
  }
</style>
