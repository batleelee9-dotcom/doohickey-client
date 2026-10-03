mod accounts;
mod bridge;
mod builds;
mod client_mod;
mod commands;
mod crash;
mod discord;
mod download;
mod error;
mod fsutil;
mod install;
mod instances;
mod java;
mod launch;
mod manifest;
mod meta;
mod mods;
mod paths;
mod replays;
mod servers;
mod settings;
mod skins;
mod state;
mod system;
mod window;
mod worlds;

use std::time::Duration;

use tauri::{
    menu::{Menu, MenuItem},
    tray::{MouseButton, MouseButtonState, TrayIconBuilder, TrayIconEvent},
    Manager, RunEvent,
};
use tauri_plugin_window_state::StateFlags;

pub fn run() {
    let app = tauri::Builder::default()
        // Must be registered first. A second launch focuses the existing window
        // instead of starting another launcher that would race this one on
        // shared files (accounts, libraries, running games).
        .plugin(tauri_plugin_single_instance::init(|app, _args, _cwd| window::show_main(app)))
        .plugin(
            tauri_plugin_window_state::Builder::new()
                // Visibility belongs to the frontend: the window starts hidden and
                // the UI reveals it after its first real render (no blank flash).
                .with_state_flags(StateFlags::all().difference(StateFlags::VISIBLE))
                .build(),
        )
        .plugin(tauri_plugin_opener::init())
        .plugin(tauri_plugin_dialog::init())
        .plugin(tauri_plugin_updater::Builder::new().build())
        .setup(|app| {
            let paths = paths::Paths::resolve(app.path().app_data_dir()?)?;
            let state = state::AppState::new(paths)?;
            let material = state.settings.get().material;
            // Without the bridge only in-game account switching is lost.
            match bridge::Bridge::start(app.handle().clone()) {
                Ok(bridge) => {
                    let _ = state.bridge.set(bridge);
                }
                Err(e) => eprintln!("in-game account switching unavailable: {e}"),
            }
            app.manage(state);

            if let Some(main) = app.get_webview_window(window::MAIN) {
                window::apply_material(&main, material);
                // Safety net: if the UI fails to load it never calls show(),
                // which would leave an invisible process running.
                tauri::async_runtime::spawn(async move {
                    tokio::time::sleep(Duration::from_secs(3)).await;
                    if !main.is_visible().unwrap_or(true) {
                        let _ = main.show();
                    }
                });
            }

            // The tray icon keeps the launcher reachable while its window is
            // closed during play (Settings → "When a game starts").
            let open = MenuItem::with_id(app, "open", "Open Doohickey Client", true, None::<&str>)?;
            let quit = MenuItem::with_id(app, "quit", "Quit Doohickey Client", true, None::<&str>)?;
            let menu = Menu::with_items(app, &[&open, &quit])?;
            let mut tray = TrayIconBuilder::with_id("quartz")
                .tooltip("Doohickey Client")
                .menu(&menu)
                .show_menu_on_left_click(false)
                .on_menu_event(|app, event| match event.id().as_ref() {
                    "open" => window::show_main(app),
                    "quit" => app.exit(0),
                    _ => {}
                })
                .on_tray_icon_event(|tray, event| {
                    if let TrayIconEvent::Click { button: MouseButton::Left, button_state: MouseButtonState::Up, .. } = event {
                        window::show_main(tray.app_handle());
                    }
                });
            if let Some(icon) = app.default_window_icon() {
                tray = tray.icon(icon.clone());
            }
            tray.build(app)?;
            Ok(())
        })
        .invoke_handler(tauri::generate_handler![
            commands::app_info,
            commands::get_settings,
            commands::update_settings,
            commands::set_curseforge_key,
            commands::open_folder,
            commands::open_path,
            commands::open_external,
            commands::list_game_versions,
            commands::list_loader_versions,
            commands::list_java_runtimes,
            commands::remove_java_runtime,
            commands::list_crashes,
            commands::mark_crash_seen,
            commands::clear_crashes,
            commands::analyze_log_file,
            commands::accounts::list_accounts,
            commands::accounts::add_offline_account,
            commands::accounts::switch_account,
            commands::accounts::remove_account,
            commands::accounts::ms_login,
            commands::accounts::ms_login_cancel,
            commands::builds::get_manifest,
            commands::builds::launch_build,
            commands::builds::install_build,
            commands::instances::list_instances,
            commands::instances::create_instance,
            commands::instances::update_instance,
            commands::instances::delete_instance,
            commands::instances::duplicate_instance,
            commands::instances::select_instance,
            commands::instances::launch_instance,
            commands::instances::cancel_launch,
            commands::instances::kill_game,
            commands::instances::running_games,
            commands::instances::game_log,
            commands::instances::repair_instance,
            commands::instances::list_worlds,
            commands::instances::delete_world,
            commands::instances::list_replays,
            commands::instances::delete_replay,
            commands::instances::rename_replay,
            commands::instances::replaymod_status,
            commands::instances::install_replaymod,
            commands::mods::list_mods,
            commands::mods::mod_icon,
            commands::mods::set_mod_enabled,
            commands::mods::remove_mod,
            commands::mods::import_mods,
            commands::mods::search_mods,
            commands::mods::install_mod,
            commands::mods::check_mod_updates,
            commands::mods::update_mod,
            commands::mods::install_performance_pack,
            commands::servers::list_servers,
            commands::servers::save_server,
            commands::servers::remove_server,
            commands::servers::ping_server,
            commands::skins::list_skins,
            commands::skins::import_skin,
            commands::skins::update_skin,
            commands::skins::delete_skin,
            commands::skins::skin_data,
            commands::skins::texture_data,
            commands::skins::profile_textures,
            commands::skins::apply_skin,
            commands::skins::set_cape,
            commands::updates::check_update,
            commands::updates::install_update,
        ])
        .build(tauri::generate_context!())
        .expect("error while building Doohickey");

    app.run(|app, event| {
        // With the window closed (to save memory, or by the player) while a
        // game runs, stay alive in the tray; the window returns when it exits.
        if let RunEvent::ExitRequested { api, code: None, .. } = event {
            if app.state::<state::AppState>().games.any_running() {
                api.prevent_exit();
            }
        }
    });
}
