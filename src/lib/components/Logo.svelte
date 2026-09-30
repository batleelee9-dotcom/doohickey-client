<script lang="ts">
  let { size = 20 }: { size?: number } = $props();
  // Several logos can be on screen at once; gradient ids must stay unique.
  const uid = $props.id();
</script>

<svg width={size} height={size} viewBox="0 0 32 32" aria-hidden="true">
  <defs>
    <linearGradient id="{uid}-top" x1="0" y1="0" x2="1" y2="1">
      <stop offset="0" stop-color="#c3baff" />
      <stop offset="1" stop-color="#8b7cf6" />
    </linearGradient>
  </defs>
  <path d="M16 3 27 9.4 16 15.8 5 9.4Z" fill="url(#{uid}-top)" />
  <path d="M5 9.4 16 15.8V29L5 22.6Z" fill="#7263e6" />
  <path d="M27 9.4 16 15.8V29l11-6.4Z" fill="#4f42b8" />
</svg>
