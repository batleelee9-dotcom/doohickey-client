<script lang="ts">
  import type { CrashAnalysis } from "../ipc";

  let { analysis }: { analysis: CrashAnalysis } = $props();
</script>

<div class="crash">
  <p class="summary" data-selectable>{analysis.summary}</p>

  {#if analysis.culprits.length}
    <div class="block">
      <div class="label">Likely cause</div>
      <div class="chips">
        {#each analysis.culprits as c (c)}<span class="badge danger">{c}</span>{/each}
      </div>
    </div>
  {/if}

  <div class="block">
    <div class="label">How to fix it</div>
    <ol class="fixes">
      {#each analysis.fixes as f, i (i)}<li>{f}</li>{/each}
    </ol>
  </div>

  {#if analysis.evidence.length}
    <details>
      <summary>From the logs</summary>
      <pre class="mono" data-selectable>{analysis.evidence.join("\n")}</pre>
    </details>
  {/if}
</div>

<style>
  .crash {
    display: flex;
    flex-direction: column;
    gap: 16px;
  }
  .summary {
    color: var(--text-muted);
  }
  .label {
    margin-bottom: 6px;
    color: var(--text-faint);
    font-size: 11.5px;
    font-weight: 600;
    text-transform: uppercase;
    letter-spacing: 0.04em;
  }
  .chips {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
  }
  .fixes {
    display: grid;
    gap: 6px;
    margin: 0;
    padding-left: 18px;
  }
  details summary {
    color: var(--text-muted);
    font-size: 12.5px;
    cursor: pointer;
  }
  pre {
    max-height: 160px;
    margin: 8px 0 0;
    padding: 10px 12px;
    overflow: auto;
    border: 1px solid var(--border);
    border-radius: var(--radius);
    background: var(--bg-subtle);
    white-space: pre-wrap;
    word-break: break-word;
  }
</style>
