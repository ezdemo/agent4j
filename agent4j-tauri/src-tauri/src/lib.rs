#![cfg_attr(not(debug_assertions), windows_subsystem = "windows")]

use tauri::Manager;

// 从 Rust 端获取应用信息
#[tauri::command]
fn get_app_info() -> serde_json::Value {
    serde_json::json!({
        "name": "Agent4j",
        "version": "1.0.0",
        "description": "智能 AI 代码助手桌面端"
    })
}

// 从 Rust 端获取系统信息
#[tauri::command]
fn get_system_info() -> serde_json::Value {
    serde_json::json!({
        "os": std::env::consts::OS,
        "arch": std::env::consts::ARCH,
        "family": std::env::consts::FAMILY
    })
}

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    tauri::Builder::default()
        .invoke_handler(tauri::generate_handler![
            get_app_info,
            get_system_info
        ])
        .setup(|app| {
            // 窗口标题
            if let Some(window) = app.get_window("main") {
                window.set_title("Agent4j").ok();
            }
            Ok(())
        })
        .run(tauri::generate_context!())
        .expect("error while running tauri application");
}
