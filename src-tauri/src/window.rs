//! The main window's lifecycle: stepping aside while a game runs (optionally
//! destroying the webview to free its memory), coming back afterwards, and
//! the translucent window materials.

use tauri::{
    window::{Effect, EffectsBuilder},
    AppHandle, Manager, WebviewWindow, WebviewWindowBuilder,
};

use crate::settings::{Material, OnLaunch};

pub const MAIN: &str = "main";

/// Shows the main window, recreating it if it was closed to the tray. A
/// recreated window starts hidden and the UI reveals it after first render.
pub fn show_main(app: &AppHandle) {
    if let Some(window) = app.get_webview_window(MAIN) {
        let _ = window.unminimize();
        let _ = window.show();
        let _ = window.set_focus();
        return;
    }
    let Some(config) = app.config().app.windows.iter().find(|w| w.label == MAIN).cloned() else { return };
    match WebviewWindowBuilder::from_config(app, &config).and_then(|b| b.build()) {
        Ok(window) => {
            apply_material(&window, app.state::<crate::state::AppState>().settings.get().material);
        }
        Err(e) => eprintln!("couldn't recreate the main window: {e}"),
    }
}

/// Called once the game's own window is up.
pub fn step_aside(app: &AppHandle, on_launch: OnLaunch) {
    let Some(window) = app.get_webview_window(MAIN) else { return };
    match on_launch {
        OnLaunch::Keep => {}
        OnLaunch::Minimize => {
            let _ = window.minimize();
        }
        // Destroying (not hiding) the webview releases its renderer, GPU and
        // utility processes; the tray icon keeps the launcher reachable.
        OnLaunch::Close => {
            let _ = window.destroy();
        }
    }
}

/// Called when the last running game exits. The window always returns if it
/// was closed (by us or the player), so a crash report is never missed.
pub fn come_back(app: &AppHandle, on_launch: OnLaunch) {
    if on_launch != OnLaunch::Keep || app.get_webview_window(MAIN).is_none() {
        show_main(app);
    }
}

pub fn apply_material(window: &WebviewWindow, material: Material) {
    let effect = match material {
        Material::Solid => None,
        Material::Mica => Some(if cfg!(target_os = "macos") { Effect::UnderWindowBackground } else { Effect::Mica }),
        Material::Acrylic => Some(if cfg!(target_os = "macos") { Effect::HudWindow } else { Effect::Acrylic }),
        Material::Vibrancy => Some(if cfg!(target_os = "macos") { Effect::Sidebar } else { Effect::Mica }),
    };
    // Unsupported effects (e.g. Mica on Windows 10) fail quietly; the UI then
    // simply keeps its solid background.
    let _ = window.set_effects(effect.map(|e| EffectsBuilder::new().effect(e).build()));
}
