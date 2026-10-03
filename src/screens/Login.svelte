<script lang="ts">
  import { fade, fly } from "svelte/transition";
  import { accounts } from "../lib/accounts.svelte";
  import Icon from "../lib/components/Icon.svelte";
  import HeroArt from "../lib/components/HeroArt.svelte";
  import Logo from "../lib/components/Logo.svelte";
  import Spinner from "../lib/components/Spinner.svelte";
  import { api, toAppError, type AppError, type AppInfo, type LoginStep } from "../lib/ipc";
  import { dur } from "../lib/platform";
  import { store } from "../lib/store.svelte";

  let { info, onBack, onDone }: { info: AppInfo; onBack?: () => void; onDone: () => void } = $props();

  type Phase = "choose" | "offline" | "microsoft" | "failed";
  let phase = $state<Phase>("choose");
  let error = $state<AppError | null>(null);

  // ---- Microsoft (sign-in window) ---------------------------------------

  const STEPS: { id: "signin" | LoginStep; label: string }[] = [
    { id: "signin", label: "Sign in with Microsoft" },
    { id: "xbox", label: "Authenticate with Xbox Live" },
    { id: "minecraft", label: "Log in to Minecraft" },
    { id: "profile", label: "Load your profile" },
  ];
  let stepIndex = $state(0);

  // The Azure app's client ID, pasted here; blank uses the one built into this copy.
  let clientId = $state(store.settings?.msClientId ?? "");
  let clientIdError = $state<string | null>(null);
  const canSignIn = $derived(info.msConfigured || clientId.trim() !== "");

  async function startMicrosoft() {
    clientIdError = null;
    if (clientId.trim() !== (store.settings?.msClientId ?? "")) {
      try {
        store.settings = await api.updateSettings({ msClientId: clientId.trim() || null });
        clientId = store.settings.msClientId ?? "";
      } catch (e) {
        clientIdError = toAppError(e).message;
        return;
      }
    }
    error = null;
    stepIndex = 0;
    phase = "microsoft";
    try {
      const snapshot = await api.msLogin((e) => {
        stepIndex = STEPS.findIndex((s) => s.id === e.data.step);
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
  <div class="backdrop"><HeroArt seed="doohickey" tone={0} /></div>
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
            <label class="client-id">
              <span>Azure client ID</span>
              <input
                class="input mono"
                bind:value={clientId}
                placeholder={info.msConfigured ? "Built in. Paste one to use your own" : "xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"}
                spellcheck="false"
                autocomplete="off"
              />
            </label>
            {#if clientIdError}
              <p class="note error">{clientIdError}</p>
            {/if}
            <button class="btn btn-primary btn-lg btn-block" onclick={startMicrosoft} disabled={!canSignIn}>
              <svg width="15" height="15" viewBox="0 0 21 21" aria-hidden="true">
                <path fill="#f25022" d="M0 0h10v10H0z" />
                <path fill="#7fba00" d="M11 0h10v10H11z" />
                <path fill="#00a4ef" d="M0 11h10v10H0z" />
                <path fill="#ffb900" d="M11 11h10v10H11z" />
              </svg>
              Continue with Microsoft
            </button>
            {#if !canSignIn}
              <p class="note">Paste the Application (client) ID from your Azure app's Overview page.</p>
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
          <p class="sub">Finish signing in in the Microsoft window. It closes by itself when you're done.</p>
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
  /* The same night landscape as the home screen, dimmed so the card reads clearly. */
  .backdrop {
    position: absolute;
    inset: 0;
    overflow: hidden;
  }
  .backdrop::after {
    content: "";
    position: absolute;
    inset: 0;
    background: radial-gradient(70% 80% at 50% 45%, rgb(5 4 12 / 0.35), rgb(5 4 12 / 0.8));
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
    max-width: 400px;
    padding: 36px 26px 28px;
    border: 1px solid rgb(255 255 255 / 0.1);
    border-radius: 22px;
    background: color-mix(in srgb, var(--bg-solid) 80%, transparent);
    backdrop-filter: blur(18px);
    box-shadow: 0 30px 80px rgb(0 0 0 / 0.5);
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
  .client-id {
    display: flex;
    flex-direction: column;
    gap: 6px;
    text-align: left;
    font-size: 12px;
    color: var(--text-faint);
  }
  .client-id .input {
    width: 100%;
    font-size: 12px;
  }
  .note.error {
    color: var(--danger);
  }
  .note {
    color: var(--text-faint);
    font-size: 12px;
    line-height: 1.45;
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
