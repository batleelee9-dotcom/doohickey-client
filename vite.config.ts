import { defineConfig } from "vite";
import { svelte } from "@sveltejs/vite-plugin-svelte";

// Set by `tauri dev` when targeting a physical device on the network.
const host = process.env.TAURI_DEV_HOST;

export default defineConfig({
  plugins: [svelte()],
  // Keep Rust compiler output visible in the same terminal.
  clearScreen: false,
  server: {
    port: 1420,
    strictPort: true,
    host: host || false,
    hmr: host ? { protocol: "ws", host, port: 1421 } : undefined,
    watch: { ignored: ["**/src-tauri/**"] },
  },
  envPrefix: ["VITE_", "TAURI_ENV_"],
  build: {
    // Every supported webview (WebView2, WKWebView on macOS 12+, WebKitGTK 2.40+) runs ES2022.
    target: "es2022",
    sourcemap: !!process.env.TAURI_ENV_DEBUG,
    // The only large chunk is three.js for the 3D skin viewer, which is
    // lazy-loaded when the Skins page opens — never on startup.
    chunkSizeWarningLimit: 600,
  },
});
