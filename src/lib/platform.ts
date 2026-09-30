export const isMac = /Mac/i.test(navigator.userAgent);

/** Label for the primary shortcut modifier. */
export const modKey = isMac ? "⌘" : "Ctrl";

const reducedMotion = matchMedia("(prefers-reduced-motion: reduce)").matches;

/** Transition duration that collapses to 0 when the OS asks for reduced motion.
 *  (Svelte transitions run through the Web Animations API, which the CSS
 *  reduced-motion override in app.css doesn't reach.) */
export const dur = (ms: number) => (reducedMotion ? 0 : ms);
