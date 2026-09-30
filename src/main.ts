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

export default mount(App, { target: document.getElementById("app")! });
