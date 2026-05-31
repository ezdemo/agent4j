#![cfg_attr(not(debug_assertions), windows_subsystem = "windows")]

use std::fs;
use std::net::TcpListener;
use std::path::{Path, PathBuf};
use std::process::{Child, Command};
use std::sync::Mutex;
use tauri::Manager;

// agent4j-web 进程管理器
struct Agent4jWebManager {
    child: Mutex<Option<Child>>,
    port: Mutex<u16>,
}

impl Agent4jWebManager {
    fn new() -> Self {
        Self {
            child: Mutex::new(None),
            port: Mutex::new(0),
        }
    }

    // 获取当前端口
    fn get_port(&self) -> u16 {
        *self.port.lock().unwrap()
    }

    // 获取安装目录
    fn get_install_dir(&self) -> PathBuf {
        let home = dirs::home_dir().unwrap_or_else(|| PathBuf::from("."));
        home.join(".agent4j")
    }

    // 检查是否已安装
    fn is_installed(&self) -> bool {
        let install_dir = self.get_install_dir();
        let jar_path = install_dir.join("bin").join("agent4j-web.jar");
        jar_path.exists()
    }

    // 从资源中安装 agent4j-web
    fn install_from_resource(&self, resource_dir: &Path) -> Result<(), String> {
        let install_dir = self.get_install_dir();
        let bin_dir = install_dir.join("bin");

        // 创建目录
        fs::create_dir_all(&bin_dir)
            .map_err(|e| format!("Failed to create directory: {}", e))?;

        // 根据平台选择解压方式
        #[cfg(target_os = "windows")]
        let archive_name = "agent4j-web-dist.zip";
        #[cfg(not(target_os = "windows"))]
        let archive_name = "agent4j-web-dist.tar.gz";

        let archive_path = resource_dir.join(archive_name);

        if !archive_path.exists() {
            return Err(format!("Resource not found: {:?}", archive_path));
        }

        // 解压
        #[cfg(target_os = "windows")]
        self.extract_zip(&archive_path, &install_dir)?;

        #[cfg(not(target_os = "windows"))]
        self.extract_tar_gz(&archive_path, &install_dir)?;

        // 设置执行权限（Linux/macOS）
        #[cfg(not(target_os = "windows"))]
        {
            let launcher = bin_dir.join("agent4j-web");
            if launcher.exists() {
                use std::os::unix::fs::PermissionsExt;
                fs::set_permissions(&launcher, fs::Permissions::from_mode(0o755))
                    .map_err(|e| format!("Failed to set permissions: {}", e))?;
            }
        }

        Ok(())
    }

    // 解压 tar.gz
    #[cfg(not(target_os = "windows"))]
    fn extract_tar_gz(&self, archive_path: &Path, dest_dir: &Path) -> Result<(), String> {
        use flate2::read::GzDecoder;
        use tar::Archive;

        let file = fs::File::open(archive_path)
            .map_err(|e| format!("Failed to open archive: {}", e))?;
        let gz = GzDecoder::new(file);
        let mut archive = Archive::new(gz);

        archive.unpack(dest_dir)
            .map_err(|e| format!("Failed to extract archive: {}", e))?;

        Ok(())
    }

    // 解压 zip
    #[cfg(target_os = "windows")]
    fn extract_zip(&self, archive_path: &Path, dest_dir: &Path) -> Result<(), String> {
        let file = fs::File::open(archive_path)
            .map_err(|e| format!("Failed to open archive: {}", e))?;
        let mut archive = zip::ZipArchive::new(file)
            .map_err(|e| format!("Failed to read zip: {}", e))?;

        for i in 0..archive.len() {
            let mut file = archive.by_index(i)
                .map_err(|e| format!("Failed to read zip entry: {}", e))?;

            let outpath = dest_dir.join(file.mangled_name());

            if file.name().ends_with('/') {
                fs::create_dir_all(&outpath)
                    .map_err(|e| format!("Failed to create directory: {}", e))?;
            } else {
                if let Some(p) = outpath.parent() {
                    if !p.exists() {
                        fs::create_dir_all(p)
                            .map_err(|e| format!("Failed to create directory: {}", e))?;
                    }
                }
                let mut outfile = fs::File::create(&outpath)
                    .map_err(|e| format!("Failed to create file: {}", e))?;
                std::io::copy(&mut file, &mut outfile)
                    .map_err(|e| format!("Failed to write file: {}", e))?;
            }
        }

        Ok(())
    }

    // 启动 agent4j-web 服务
    fn start(&self) -> Result<u32, String> {
        // Dev 模式：优先使用 Maven target 目录的最新 jar
        let dev_jar = PathBuf::from(env!("CARGO_MANIFEST_DIR"))
            .join("../../agent4j-web/target/agent4j-web.jar");
        let jar_path = if dev_jar.exists() {
            println!("Using dev jar: {:?}", dev_jar);
            dev_jar
        } else {
            let install_dir = self.get_install_dir();
            let bin_dir = install_dir.join("bin");
            let fallback = bin_dir.join("agent4j-web.jar");
            if !fallback.exists() {
                return Err("agent4j-web.jar not found".to_string());
            }
            println!("Using installed jar: {:?}", fallback);
            fallback
        };

        // 找一个可用的端口
        let listener = TcpListener::bind("127.0.0.1:0")
            .map_err(|e| format!("Failed to bind port: {}", e))?;
        let port = listener.local_addr()
            .map_err(|e| format!("Failed to get port: {}", e))?
            .port();
        // 释放端口，Java 进程会重新绑定
        drop(listener);

        // 保存端口
        let mut port_lock = self.port.lock().unwrap();
        *port_lock = port;

        // 构建启动命令（传 --server.port 覆盖 app.yml 中的 server.port）
        let mut cmd = Command::new("java");
        cmd.args(&[
            "-Dfile.encoding=UTF-8",
            "-jar",
            jar_path.to_str().unwrap(),
            &format!("--server.port={}", port),
        ]);

        // Windows: 隐藏控制台窗口
        #[cfg(target_os = "windows")]
        {
            use std::os::windows::process::CommandExt;
            cmd.creation_flags(0x08000000); // CREATE_NO_WINDOW
        }

        // 启动进程
        let child = cmd
            .spawn()
            .map_err(|e| format!("Failed to start agent4j-web: {}", e))?;

        let pid = child.id();

        // 保存进程引用
        let mut child_lock = self.child.lock().unwrap();
        *child_lock = Some(child);
        drop(child_lock);

        // 等 Java 进程就绪（最长 15 秒，逐秒尝试连接端口）
        let start = std::time::Instant::now();
        let timeout = std::time::Duration::from_secs(15);
        let mut ready = false;

        while start.elapsed() < timeout {
            // 先检查进程是否还活着
            {
                let mut cl = self.child.lock().unwrap();
                if let Some(ref mut ch) = *cl {
                    if let Ok(Some(_)) = ch.try_wait() {
                        // 进程已退出
                        *cl = None;
                        return Err(format!("Java process (PID {}) exited prematurely", pid));
                    }
                }
            }

            // 尝试连接端口
            if std::net::TcpStream::connect_timeout(
                &format!("127.0.0.1:{}", port).parse().unwrap(),
                std::time::Duration::from_millis(500),
            )
            .is_ok()
            {
                ready = true;
                break;
            }

            std::thread::sleep(std::time::Duration::from_secs(1));
        }

        if !ready {
            return Err(format!(
                "Java process (PID {}) started but did not listen on port {} within 15s",
                pid, port
            ));
        }

        println!("Agent4j Web is ready on 127.0.0.1:{} (PID {})", port, pid);

        Ok(port as u32)
    }

    // 停止 agent4j-web 服务
    fn stop(&self) -> Result<(), String> {
        let mut child_lock = self.child.lock().unwrap();
        
        if let Some(ref mut child) = *child_lock {
            child.kill()
                .map_err(|e| format!("Failed to kill process: {}", e))?;
            child.wait()
                .map_err(|e| format!("Failed to wait for process: {}", e))?;
        }

        *child_lock = None;
        Ok(())
    }

    // 检查是否正在运行
    fn is_running(&self) -> bool {
        let mut child_lock = self.child.lock().unwrap();
        
        if let Some(ref mut child) = *child_lock {
            match child.try_wait() {
                Ok(Some(_)) => {
                    // 进程已退出
                    *child_lock = None;
                    false
                }
                Ok(None) => true, // 进程仍在运行
                Err(_) => {
                    *child_lock = None;
                    false
                }
            }
        } else {
            false
        }
    }
}

// Tauri 命令：获取 agent4j-web 状态
#[tauri::command]
fn get_agent4j_web_status(state: tauri::State<'_, Agent4jWebManager>) -> serde_json::Value {
    serde_json::json!({
        "installed": state.is_installed(),
        "running": state.is_running(),
        "install_dir": state.get_install_dir().to_string_lossy()
    })
}

// Tauri 命令：启动 agent4j-web（返回端口号）
#[tauri::command]
fn start_agent4j_web(state: tauri::State<'_, Agent4jWebManager>) -> Result<u32, String> {
    state.start()
}

// Tauri 命令：获取当前端口号
#[tauri::command]
fn get_agent4j_web_port(state: tauri::State<'_, Agent4jWebManager>) -> u16 {
    state.get_port()
}

// Tauri 命令：停止 agent4j-web
#[tauri::command]
fn stop_agent4j_web(state: tauri::State<'_, Agent4jWebManager>) -> Result<(), String> {
    state.stop()
}

// 从 Rust 端获取应用信息
#[tauri::command]
fn get_app_info() -> serde_json::Value {
    serde_json::json!({
        "name": "Agent4j",
        "version": "2.0.0",
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
    let manager = Agent4jWebManager::new();

    tauri::Builder::default()
        .plugin(tauri_plugin_shell::init())
        .manage(manager)
        .invoke_handler(tauri::generate_handler![
            get_app_info,
            get_system_info,
            get_agent4j_web_status,
            start_agent4j_web,
            stop_agent4j_web,
            get_agent4j_web_port
        ])
        .setup(|app| {
            // 窗口标题
            if let Some(window) = app.get_webview_window("main") {
                window.set_title("Agent4j").ok();
            }

            // 获取资源目录
            let resource_dir = app.path().resource_dir()
                .unwrap_or_else(|_| PathBuf::from("resources"))
                .join("resources");

            // 获取管理器
            let manager = app.state::<Agent4jWebManager>();

            // 检查是否已安装，如果没有则安装
            if !manager.is_installed() {
                println!("Agent4j Web not installed, installing from resources...");
                match manager.install_from_resource(&resource_dir) {
                    Ok(_) => println!("Agent4j Web installed successfully"),
                    Err(e) => eprintln!("Failed to install Agent4j Web: {}", e),
                }
            }

            // 启动 agent4j-web 服务（start 内部会等待就绪）
            if let Err(e) = manager.start() {
                eprintln!("Failed to start Agent4j Web: {}", e);
            }

            Ok(())
        })
        .on_window_event(|window, event| {
            // 窗口关闭时停止 agent4j-web
            if let tauri::WindowEvent::Destroyed = event {
                let manager = window.state::<Agent4jWebManager>();
                if let Err(e) = manager.stop() {
                    eprintln!("Failed to stop Agent4j Web: {}", e);
                }
            }
        })
        .run(tauri::generate_context!())
        .expect("error while running tauri application");
}
