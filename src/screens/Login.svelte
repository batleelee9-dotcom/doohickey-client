<script lang="ts">
  import { openUrl } from "@tauri-apps/plugin-opener";
  import { fade, fly } from "svelte/transition";
  import { accounts } from "../lib/accounts.svelte";
  import Icon from "../lib/components/Icon.svelte";
  import Logo from "../lib/components/Logo.svelte";
  import Spinner from "../lib/components/Spinner.svelte";
  import { api, toAppError, type AppError, type AppInfo, type LoginStep } from "../lib/ipc";
  import { dur } from "../lib/platform";

  let { info, onBack, onDone }: { info: AppInfo; onBack?: () => void; onDone: () => void } = $props();

  type Phase = "choose" | "offline" | "microsoft" | "failed";
  let phase = $state<Phase>("choose");
  let error = $state<AppError | null>(null);

  // ---- Microsoft (device-code flow) -------------------------------------

  const STEPS: { id: "signin" | LoginStep; label: string }[] = [
    { id: "signin", label: "Sign in with Microsoft" },
    { id: "xbox", label: "Authenticate with Xbox Live" },
    { id: "minecraft", label: "Log in to Minecraft" },
    { id: "profile", label: "Load your profile" },
  ];
  let code = $state<{ userCode: string; verificationUri: string } | null>(null);
  let stepIndex = $state(0);
  let copied = $state(false);

  async function startMicrosoft() {
    error = null;
    code = null;
    stepIndex = 0;
    phase = "microsoft";
    try {
      const snapshot = await api.msLogin((e) => {
        if (e.event === "deviceCode") {
          code = e.data;
          void copy(e.data.userCode);
        } else {
          stepIndex = STEPS.findIndex((s) => s.id === e.data.step);
        }
      });
      accounts.apply(snapshot);
      onDone();
    } catch (e) {
      const err = toAppError(e);
      if (err.kind === "cancelled") {
        phase = "choose";
      } else {
        // A dedicated screen with Retry; sign-in never falls through to anything else.
        error = err;
        phase = "failed";
      }
    }
  }

  async function copy(text: string) {
    try {
      await navigator.clipboard.writeText(text);
      copied = true;
    } catch {
      // Clipboard can be unavailable (e.g. window not focused); the code is on screen anyway.
    }
  }

  /** microsoft.com/link pre-fills the code when given as ?otc=, saving a paste. */
  function linkUrl(c: { userCode: string; verificationUri: string }) {
    try {
      const url = new URL(c.verificationUri);
      if (url.pathname === "/link") url.searchParams.set("otc", c.userCode);
      return url.toString();
    } catch {
      return c.verificationUri;
    }
  }

  // ---- Offline ------------------------------------------------------------

  let username = $state("");
  let submitting = $state(false);
  const hasMicrosoft = $derived(accounts.list.some((a) => a.kind === "microsoft"));
  const offlineLocked = $derived(info.offlineRequiresMicrosoft && !hasMicrosoft);

  // Same rules the Rust side enforces; checked here too for instant feedback.
  const usernameError = $derived.by(() => {
    const n = username.trim();
    if (!n) return null;
    if (!/^[A-Za-z0-9_]+$/.test(n)) return "Only letters, numbers and underscores";
    if (n.length < 3) return "At least 3 characters";
    return null;
  });

  async function submitOffline(e: SubmitEvent) {
    e.preventDefault();
    if (submitting || usernameError || !username.trim()) return;
    submitting = true;
    error = null;
    try {
      accounts.apply(await api.addOfflineAccount(username.trim()));
      onDone();
    } catch (err) {
      error = toAppError(err);
    } finally {
      submitting = false;
    }
  }

  function autofocus(node: HTMLElement) {
    node.focus();
  }

  function show(next: Phase) {
    error = null;
    phase = next;
  }
</script>

<div class="login">
  {#if onBack && phase === "choose"}
    <button class="back btn btn-ghost btn-sm" onclick={onBack} transition:fade={{ duration: dur(120) }}>
      ← Back
    </button>
  {/if}

  <div class="panel">
    <div class="mark"><Logo size={30} /></div>

    {#key phase}
      <div class="stage" in:fly={{ y: 8, duration: dur(260), opacity: 0 }}>
        {#if phase === "choose"}
          <h1>{accounts.list.length ? "Add an account" : "Sign in to Doohickey"}</h1>
          <p class="sub">Use the Microsoft account that owns Minecraft: Java Edition.</p>

          <div class="actions">
            <button class="btn btn-primary btn-lg btn-block" onclick={startMicrosoft} disabled={!info.msConfigured}>
              <svg width="15" height="15" viewBox="0 0 21 21" aria-hidden="true">
                <path fill="#f25022" d="M0 0h10v10H0z" />
                <path fill="#7fba00" d="M11 0h10v10H11z" />
                <path fill="#00a4ef" d="M0 11h10v10H0z" />
                <path fill="#ffb900" d="M11 11h10v10H11z" />
              </svg>
              Continue with Microsoft
            </button>
            {#if !info.msConfigured}
              <p class="note">Microsoft sign-in needs an Azure client ID. Set <code>QUARTZ_MS_CLIENT_ID</code> — see README.</p>
            {/if}

            <div class="divider"><span>or</span></div>

            <button class="btn btn-secondary btn-lg btn-block" onclick={() => show("offline")} disabled={offlineLocked}>
              Play offline
            </button>
            {#if offlineLocked}
              <p class="note">Offline play unlocks after you add a Microsoft account that owns Minecraft.</p>
            {/if}
          </div>
        {:else if phase === "failed"}
          <h1>Couldn't sign in</h1>
          <p class="sub">{error?.message ?? "Something went wrong while signing in."}</p>
          <div class="actions">
            <button class="btn btn-primary btn-lg btn-block" data-autofocus onclick={startMicrosoft}>
              <Icon name="refresh" size={14} /> Retry
            </button>
            <button class="btn btn-ghost btn-block" onclick={() => show("choose")}>Back</button>
          </div>
        {:else if phase === "offline"}
          <h1>Play offline</h1>
          <p class="sub">Works in singleplayer and on servers with online-mode off.</p>

          <form class="actions" onsubmit={submitOffline}>
            <label class="field">
              <span class="field-label">Username</span>
              <input
                class="input input-lg"
                bind:value={username}
                use:autofocus
                maxlength="16"
                placeholder="Steve"
                autocomplete="off"
                spellcheck="false"
                aria-invalid={!!usernameError}
                aria-describedby="username-help"
              />
              <span id="username-help" class="field-help" class:invalid={usernameError}>
                {usernameError ?? "3–16 letters, numbers or underscores"}
              </span>
            </label>
            <button
              class="btn btn-primary btn-lg btn-block"
              type="submit"
              disabled={submitting || !!usernameError || username.trim().length < 3}
            >
              {#if submitting}<Spinner />{/if}
              Continue
            </button>
            <button type="button" class="btn btn-ghost btn-block" onclick={() => show("choose")}>
              Use a different method
            </button>
            {#if !info.offlineRequiresMicrosoft && !hasMicrosoft}
              <p class="note">Development build: the game-ownership check is skipped.</p>
            {/if}
          </form>
        {:else}
          <h1>Sign in with Microsoft</h1>
          {#if code}
            {@const c = code}
            <p class="sub">Enter this code on the Microsoft page. It's already on your clipboard.</p>
            <button class="code" title="Copy code" onclick={() => copy(c.userCode)}>
              <span data-selectable>{c.userCode}</span>
              <span class="code-hint">{copied ? "Copied" : "Click to copy"}</span>
            </button>
            <div class="actions">
              <button class="btn btn-primary btn-lg btn-block" onclick={() => openUrl(linkUrl(c))}>
                Open microsoft.com/link
                <Icon name="external" size={14} />
              </button>
            </div>
            <ol class="steps">
              {#each STEPS as s, i (s.id)}
                <li class:done={i < stepIndex} class:current={i === stepIndex}>
                  <span class="dot">
                    {#if i < stepIndex}
                      <Icon name="check" size={10} />
                    {:else if i === stepIndex}
                      <Spinner size={12} />
                    {/if}
                  </span>
                  {i === 0 && stepIndex === 0 ? "Waiting for you to sign in…" : s.label}
                </li>
              {/each}
            </ol>
          {:else}
            <p class="sub waiting"><Spinner /> Requesting a sign-in code…</p>
          {/if}
          <button class="btn btn-ghost btn-block" onclick={() => api.msLoginCancel()}>Cancel</button>
        {/if}
      </div>
    {/key}

    {#if error && phase !== "failed"}
      <div class="error" role="alert" in:fly={{ y: 4, duration: dur(200) }}>
        <Icon name="alert" size={16} />
        <p>{error.message}</p>
      </div>
    {/if}
  </div>

  <!-- Required wording from Mojang's usage guidelines for third-party software. -->
  <footer class="legal">Not an official Minecraft product. Not approved by or associated with Mojang or Microsoft.</footer>
</div>

<style>
  .login {
    position: relative;
    display: grid;
    place-items: center;
    height: 100%;
    padding: 32px 32px 56px;
    overflow: auto;
  }
  /* A faint accent glow behind the panel — depth without decoration. */
  .login::before {
    content: "";
    position: absolute;
    inset: 0;
    background: radial-gradient(640px circle at 50% 28%, var(--accent-soft), transparent 70%);
    opacity: 0.55;
    pointer-events: none;
  }
  .back {
    position: absolute;
    top: 12px;
    left: 16px;
  }
  .panel {
    position: relative;
    display: flex;
    flex-direction: column;
    align-items: center;
    width: 100%;
    max-width: 348px;
    text-align: center;
  }
  .mark {
    display: grid;
    place-items: center;
    width: 56px;
    height: 56px;
    margin-bottom: 28px;
    border: 1px solid var(--border);
    border-radius: 15px;
    background: var(--bg-subtle);
    box-shadow: 0 8px 24px rgb(0 0 0 / 0.25);
  }
  .stage {
    display: flex;
    flex-direction: column;
    align-items: center;
    width: 100%;
  }
  h1 {
    margin-bottom: 8px;
    font-size: 20px;
    font-weight: 600;
    letter-spacing: -0.015em;
  }
  .sub {
    margin-bottom: 28px;
    color: var(--text-muted);
    text-wrap: balance;
  }
  .waiting {
    display: flex;
    align-items: center;
    gap: 10px;
  }
  .actions {
    display: flex;
    flex-direction: column;
    gap: 10px;
    width: 100%;
  }
  .divider {
    display: flex;
    align-items: center;
    gap: 12px;
    margin: 6px 0;
    color: var(--text-faint);
    font-size: 12px;
  }
  .divider::before,
  .divider::after {
    content: "";
    flex: 1;
    height: 1px;
    background: var(--border);
  }
  .note {
    color: var(--text-faint);
    font-size: 12px;
    line-height: 1.45;
  }
  code {
    font-family: var(--font-mono);
    font-size: 11.5px;
    color: var(--text-muted);
  }
  .field {
    display: flex;
    flex-direction: column;
    gap: 6px;
    text-align: left;
    margin-bottom: 6px;
  }
  .field-label {
    color: var(--text-muted);
    font-size: 12.5px;
    font-weight: 500;
  }
  .field-help {
    color: var(--text-faint);
    font-size: 12px;
  }
  .field-help.invalid {
    color: var(--danger);
  }
  .code {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 2px;
    width: 100%;
    margin-bottom: 14px;
    padding: 16px;
    border: 1px dashed var(--border-strong);
    border-radius: var(--radius-lg);
    background: var(--bg-subtle);
    cursor: pointer;
    transition: border-color 0.15s;
  }
  .code:hover {
    border-color: var(--accent);
  }
  .code span:first-child {
    font-family: var(--font-mono);
    font-size: 28px;
    font-weight: 600;
    letter-spacing: 0.16em;
    color: var(--text);
  }
  .code-hint {
    color: var(--text-faint);
    font-size: 11.5px;
  }
  .steps {
    display: grid;
    gap: 10px;
    width: 100%;
    margin: 24px 0 14px;
    padding: 0;
    list-style: none;
    text-align: left;
    color: var(--text-faint);
  }
  .steps li {
    display: flex;
    align-items: center;
    gap: 10px;
    transition: color 0.2s;
  }
  .steps li.current {
    color: var(--text);
  }
  .steps li.done {
    color: var(--text-muted);
  }
  .dot {
    display: grid;
    place-items: center;
    width: 16px;
    height: 16px;
    border: 1px solid var(--border-strong);
    border-radius: 50%;
  }
  .current .dot {
    border-color: transparent;
    color: var(--accent);
  }
  .done .dot {
    border-color: var(--success);
    background: var(--success);
    color: var(--bg);
  }
  .error {
    display: flex;
    gap: 10px;
    width: 100%;
    margin-top: 20px;
    padding: 12px 14px;
    border: 1px solid color-mix(in srgb, var(--danger) 28%, transparent);
    border-radius: var(--radius);
    background: var(--danger-soft);
    color: var(--danger);
    text-align: left;
    font-size: 13px;
  }
  .error :global(svg) {
    margin-top: 2px;
  }
  .error p {
    color: var(--text);
    user-select: text;
  }
  .legal {
    position: absolute;
    right: 0;
    bottom: 16px;
    left: 0;
    color: var(--text-faint);
    font-size: 11px;
    text-align: center;
    pointer-events: none;
  }
</style>
