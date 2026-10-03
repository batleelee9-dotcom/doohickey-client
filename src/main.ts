import { mount } from "svelte";
import App from "./App.svelte";
import "./app.css";

// Release builds should feel like a native app, not a web page: no browser
// context menu except where it's useful (text fields, for copy/paste).
if (import.meta.env.PROD) {
  document.addEventListener("contextmenu", (e) => {
    if (!(e.target instanceof HTMLInputElement || e.target instanceof HTMLTextAreaElement)) {
      e.preventDefault();
    }
  });
}

// `npm run dev` in a plain browser: fake the Rust backend (never in release builds).
if (import.meta.env.DEV && !("__TAURI_INTERNALS__" in window)) {
  await import("./dev/preview");
}

export default mount(App, { target: document.getElementById("app")! });
