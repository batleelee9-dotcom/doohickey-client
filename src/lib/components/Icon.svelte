<script lang="ts" module>
  // 16×16 stroke icons. Inline paths instead of an icon font or library keep
  // the bundle tiny and let icons inherit `currentColor`.
  const paths = {
    alert: "M8 5.5V8.5M8 11h.01M7.13 2.5 1.6 12a1 1 0 0 0 .87 1.5h11.06a1 1 0 0 0 .87-1.5L8.87 2.5a1 1 0 0 0-1.74 0Z",
    arrowLeft: "M13 8H3M7 4 3 8l4 4",
    box: "M8 1.5 14 4.75v6.5L8 14.5 2 11.25v-6.5L8 1.5ZM2 4.75l6 3.25 6-3.25M8 8v6.5",
    bug: "M5.5 5.5h5a2 2 0 0 1 2 2V10a4.5 4.5 0 0 1-9 0V7.5a2 2 0 0 1 2-2ZM6 5.5V4.5a2 2 0 0 1 4 0v1M8 8v6M1.5 8.5h2M12.5 8.5h2M2.5 5l1.5 1.5M13.5 5 12 6.5M2.5 13 4 11.5M13.5 13 12 11.5",
    check: "M3.5 8.5 6.5 11.5 12.5 4.5",
    chevron: "M5 6.5 8 9.5 11 6.5",
    chevronRight: "M6.5 4 10 8l-3.5 4",
    copy: "M5.5 5.5V3a1 1 0 0 1 1-1H13a1 1 0 0 1 1 1v6.5a1 1 0 0 1-1 1h-2.5M3 5.5h6.5a1 1 0 0 1 1 1V13a1 1 0 0 1-1 1H3a1 1 0 0 1-1-1V6.5a1 1 0 0 1 1-1Z",
    cpu: "M5 3.5h6a1.5 1.5 0 0 1 1.5 1.5v6a1.5 1.5 0 0 1-1.5 1.5H5A1.5 1.5 0 0 1 3.5 11V5A1.5 1.5 0 0 1 5 3.5ZM6.5 6.5h3v3h-3zM6 1.5v2M10 1.5v2M6 12.5v2M10 12.5v2M1.5 6h2M1.5 10h2M12.5 6h2M12.5 10h2",
    download: "M8 2v8.5M4.5 7 8 10.5 11.5 7M2.5 13.5h11",
    edit: "M10.5 2.5l3 3L6 13H3v-3l7.5-7.5Z",
    external: "M9.5 2.5h4v4M13.5 2.5 8 8M12 9.5v3a1 1 0 0 1-1 1H3.5a1 1 0 0 1-1-1V5a1 1 0 0 1 1-1h3",
    file: "M9 1.5H4.5a1 1 0 0 0-1 1v11a1 1 0 0 0 1 1h7a1 1 0 0 0 1-1V5L9 1.5ZM9 1.5V5h3.5",
    film: "M2.5 2.5h11v11h-11zM5 2.5v11M11 2.5v11M2.5 5.5H5M2.5 8H5M2.5 10.5H5M11 5.5h2.5M11 8h2.5M11 10.5h2.5",
    folder: "M2 4.5a1 1 0 0 1 1-1h3l1.5 1.5H13a1 1 0 0 1 1 1V12a1 1 0 0 1-1 1H3a1 1 0 0 1-1-1V4.5Z",
    globe: "M8 14.5A6.5 6.5 0 1 0 8 1.5a6.5 6.5 0 0 0 0 13ZM1.5 8h13M8 1.5c1.7 1.8 2.5 4 2.5 6.5S9.7 12.7 8 14.5C6.3 12.7 5.5 10.5 5.5 8S6.3 3.3 8 1.5Z",
    grid: "M2.5 2.5h4.5v4.5H2.5zM9 2.5h4.5v4.5H9zM2.5 9h4.5v4.5H2.5zM9 9h4.5v4.5H9z",
    home: "M2.5 7 8 2.5 13.5 7v6a.5.5 0 0 1-.5.5H10v-4H6v4H3a.5.5 0 0 1-.5-.5V7Z",
    info: "M8 14.5A6.5 6.5 0 1 0 8 1.5a6.5 6.5 0 0 0 0 13ZM8 7.5V11M8 5h.01",
    logout: "M6 2.5H3.5a1 1 0 0 0-1 1v9a1 1 0 0 0 1 1H6M10.5 11 13.5 8l-3-3M13.5 8h-7",
    map: "M1.5 3.5 5.5 2l5 1.5 4-1.5v10.5l-4 1.5-5-1.5-4 1.5V3.5ZM5.5 2v10.5M10.5 3.5V14",
    maximize: "M3.5 3.5h9v9h-9Z",
    minimize: "M3.5 8h9",
    more: "M3.5 8h.01M8 8h.01M12.5 8h.01",
    palette: "M8 1.5a6.5 6.5 0 0 0 0 13c.8 0 1.2-.6 1.2-1.2 0-.6-.4-.9-.4-1.5 0-.7.5-1.3 1.3-1.3h1.4a3 3 0 0 0 3-3C14.5 4.3 11.6 1.5 8 1.5ZM4.75 8.5h.01M6 5.25h.01M9.5 4.5h.01M11.75 7h.01",
    play: "M4.5 2.75v10.5a.5.5 0 0 0 .77.42l8.07-5.25a.5.5 0 0 0 0-.84L5.27 2.33a.5.5 0 0 0-.77.42Z",
    plus: "M8 3v10M3 8h10",
    puzzle: "M6.5 2.5a1.5 1.5 0 0 1 3 0v1h3v3h-1a1.5 1.5 0 0 0 0 3h1v3h-3v-1a1.5 1.5 0 0 0-3 0v1h-3v-3h1a1.5 1.5 0 0 0 0-3h-1v-3h3v-1Z",
    refresh: "M13.5 8a5.5 5.5 0 1 1-1.6-3.9M13.5 2.5v3h-3",
    search: "M7 12A5 5 0 1 0 7 2a5 5 0 0 0 0 10ZM14 14l-3.5-3.5",
    server: "M2.5 3.5h11v4h-11zM2.5 8.5h11v4h-11zM5 5.5h.01M5 10.5h.01",
    settings: "M8 10a2 2 0 1 0 0-4 2 2 0 0 0 0 4ZM13 8.8V7.2l-1.5-.4a4 4 0 0 0-.4-1l.8-1.3-1.2-1.2-1.3.8a4 4 0 0 0-1-.4L8.8 2.2H7.2l-.4 1.5a4 4 0 0 0-1 .4l-1.3-.8-1.2 1.2.8 1.3a4 4 0 0 0-.4 1L2.2 7.2v1.6l1.5.4c.1.4.2.7.4 1l-.8 1.3 1.2 1.2 1.3-.8c.3.2.6.3 1 .4l.4 1.5h1.6l.4-1.5c.4-.1.7-.2 1-.4l1.3.8 1.2-1.2-.8-1.3c.2-.3.3-.6.4-1l1.5-.4Z",
    shirt: "M5.5 2 2 4l1.5 3L5 6.25V14h6V6.25L12.5 7 14 4l-3.5-2a2.5 2.5 0 0 1-5 0Z",
    sliders: "M3 4.5h6M12 4.5h1M3 11.5h1M7 11.5h6M10.5 3v3M5.5 10v3",
    star: "M8 1.8l1.9 3.9 4.3.6-3.1 3 .7 4.3L8 11.6l-3.8 2 .7-4.3-3.1-3 4.3-.6L8 1.8Z",
    stop: "M4 4h8v8H4z",
    switch: "M3 5.5h9.5M10 3l2.5 2.5L10 8M13 10.5H3.5M6 8l-2.5 2.5L6 13",
    terminal: "M2.5 3h11a1 1 0 0 1 1 1v8a1 1 0 0 1-1 1h-11a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1ZM4.5 6.5 6.5 8l-2 1.5M8 10h3",
    trash: "M2.5 4h11M6 4V2.5h4V4M4 4l.7 9.5h6.6L12 4M6.75 6.5v4.5M9.25 6.5v4.5",
    upload: "M8 10.5V2M4.5 5.5 8 2l3.5 3.5M2.5 13.5h11",
    user: "M8 8a3 3 0 1 0 0-6 3 3 0 0 0 0 6ZM2.5 14.5c.6-2.6 2.8-4.5 5.5-4.5s4.9 1.9 5.5 4.5",
    x: "M4 4l8 8M12 4l-8 8",
    zap: "M9 1.5 3 9h4.5L7 14.5 13 7H8.5L9 1.5Z",
  } as const;

  export type IconName = keyof typeof paths;
</script>

<script lang="ts">
  let { name, size = 16, filled = false }: { name: IconName; size?: number; filled?: boolean } = $props();
</script>

<svg
  width={size}
  height={size}
  viewBox="0 0 16 16"
  fill={filled ? "currentColor" : "none"}
  stroke="currentColor"
  stroke-width="1.5"
  stroke-linecap="round"
  stroke-linejoin="round"
  aria-hidden="true"
>
  <path d={paths[name]} />
</svg>

<style>
  svg {
    flex-shrink: 0;
  }
</style>
