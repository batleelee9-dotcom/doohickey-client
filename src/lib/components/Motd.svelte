<script lang="ts">
  import { parseMotd } from "../motd";

  let { motd }: { motd: unknown } = $props();
  const segments = $derived(parseMotd(motd));
</script>

<span class="motd">
  {#each segments as s, i (i)}<span
      style:color={s.color}
      style:font-weight={s.bold ? 700 : null}
      style:font-style={s.italic ? "italic" : null}
      style:text-decoration={[s.underline && "underline", s.strike && "line-through"].filter(Boolean).join(" ") || null}
      >{s.text}</span
    >{/each}
</span>

<style>
  .motd {
    white-space: pre-line;
  }
  /* MOTDs are designed for Minecraft's dark background; keep light colours
     (white, yellow) legible on the light theme. */
  :global([data-theme="light"]) .motd span {
    text-shadow: 0 0 1px rgb(0 0 0 / 0.55);
  }
</style>
