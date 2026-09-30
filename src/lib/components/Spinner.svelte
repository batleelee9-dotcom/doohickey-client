<script lang="ts">
  let { size = 14 }: { size?: number } = $props();
</script>

<span class="spinner" style:width="{size}px" style:height="{size}px" aria-hidden="true"></span>

<style>
  .spinner {
    display: inline-block;
    flex-shrink: 0;
    border: 1.5px solid currentColor;
    border-right-color: transparent;
    border-radius: 50%;
    opacity: 0.8;
    /* transform-only animation stays on the compositor thread */
    animation: spin 0.7s linear infinite;
  }
  @keyframes spin {
    to {
      transform: rotate(360deg);
    }
  }
</style>
