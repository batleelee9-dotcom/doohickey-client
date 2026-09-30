<script lang="ts">
  import type { CrashRecord } from "../ipc";
  import { store } from "../store.svelte";
  import CrashDetails from "./CrashDetails.svelte";
  import Modal from "./Modal.svelte";

  let { crash }: { crash: CrashRecord } = $props();

  const close = () => store.markCrashSeen(crash.id);
  function viewConsole() {
    void close();
    store.consoleFor = crash.instanceId;
  }
</script>

<Modal title="{crash.instanceName} crashed" width={540} onClose={close}>
  <div class="head">
    <div class="icon">!</div>
    <h3>{crash.analysis.title}</h3>
  </div>
  <CrashDetails analysis={crash.analysis} />
  {#snippet footer()}
    {#if store.log(crash.instanceId).length}
      <button class="btn btn-ghost" onclick={viewConsole}>View console</button>
    {/if}
    <button class="btn btn-primary" data-autofocus onclick={close}>Got it</button>
  {/snippet}
</Modal>

<style>
  .head {
    display: flex;
    align-items: center;
    gap: 12px;
    margin-bottom: 14px;
  }
  .icon {
    display: grid;
    place-items: center;
    width: 30px;
    height: 30px;
    flex-shrink: 0;
    border-radius: 50%;
    background: var(--danger-soft);
    color: var(--danger);
    font-weight: 700;
  }
  h3 {
    font-size: 15px;
    font-weight: 600;
  }
</style>
