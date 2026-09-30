<script lang="ts">
  import { store } from "../store.svelte";
  import Console from "./Console.svelte";
  import Modal from "./Modal.svelte";

  let { instanceId, onClose }: { instanceId: string; onClose: () => void } = $props();

  const name = $derived(
    store.running.find((g) => g.instanceId === instanceId)?.instanceName ??
      store.buildFor(instanceId)?.name ??
      "Minecraft",
  );
</script>

<Modal title="Game output · {name}" width={920} {onClose}>
  <div class="body">
    <Console {instanceId} />
  </div>
</Modal>

<style>
  .body {
    height: min(62vh, 560px);
  }
</style>
