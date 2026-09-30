import type { Settings } from "./ipc";

const media = matchMedia("(prefers-color-scheme: dark)");
let current: Settings | null = null;

/** Applies theme, accent and window material to the document. Components
 *  only use CSS variables, so this is the whole theme system. */
export function applyTheme(settings: Settings) {
  current = settings;
  const root = document.documentElement;
  const dark = settings.theme === "dark" || (settings.theme === "system" && media.matches);
  root.dataset.theme = dark ? "dark" : "light";
  root.style.setProperty("--accent", settings.accent);
  root.dataset.material = settings.material;
  // Translucent materials let the OS backdrop show through the page background.
  root.style.setProperty("--bg-alpha", settings.material === "solid" ? "100%" : `${Math.max(30, settings.opacity)}%`);
}

media.addEventListener("change", () => current && applyTheme(current));

export const ACCENTS = ["#8b7cf6", "#4f8ef7", "#2bb8a6", "#3dd68c", "#f5a524", "#f2555a", "#ec5fb7", "#a3a3ad"];
