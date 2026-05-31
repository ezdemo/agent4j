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

    // 检查 Java 是否可用且版本 >= 17
    fn check_java() -> Result<String, String> {
        let output = Command::new("java")
            .args(&["-version"])
            .output()
            .map_err(|_| "Java not found, please install JDK 17+ from https://adoptium.net/".to_string())?;

        let ver_str = String::from_utf8_lossy(&output.stderr);
        // 解析 "openjdk version \"17.0.1\" 2021-10-19" 或 "\"17\""
        let major = ver_str.split('"').nth(1)
            .and_then(|v| v.split('.').next())
            .and_then(|v| v.parse::<i32>().ok())
            .unwrap_or(0);
        if major < 17 {
            return Err(format!("Java 17+ required, found version {}", major));
        }
        Ok(ver_str.lines().next().unwrap_or("unknown").to_string())
    }

    // 配置 PATH 环境变量（Windows: User PATH；Unix: shell rc）
    fn setup_path(bin_dir: &Path) {
        let bin_str = bin_dir.to_string_lossy().to_string();

        #[cfg(target_os = "windows")]
        {
            use std::os::windows::process::CommandExt;
            // 通过注册表设置用户 PATH
            let key = winreg::RegKey::predef(winreg::enums::HKEY_CURRENT_USER)
                .open_subkey_with_flags("Environment", winreg::enums::KEY_READ | winreg::enums::KEY_WRITE)
                .ok();
            if let Some(key) = key {
                let current: String = key.get_value("Path").unwrap_or_default();
                if !current.contains(&bin_str) {
                    let new_path = if current.is_empty() { bin_str.clone() } else { format!("{};{}", current, bin_str) };
                    let _ = key.set_value("Path", &new_path);
                    // 通知系统环境变量已更改
                    let _ = Command::new("powershell")
                        .args(&["-NoProfile", "-Command", "[Environment]::SetEnvironmentVariable('Path', $env:Path, 'User')"])
                        .creation_flags(0x08000000)
                        .output();
                }
            }
        }

        #[cfg(not(target_os = "windows"))]
        {
            // 写入 ~/.profile（最通用的 shell rc）
            let profile = dirs::home_dir().map(|h| h.join(".profile"));
            if let Some(path) = profile {
                if let Ok(content) = fs::read_to_string(&path) {
                    if !content.contains(&bin_str) {
                        let _ = fs::write(&path, format!(
                            "{}\n# Agent4j Web\nexport PATH=\"$PATH:{}\"\n", content, bin_str
                        ));
                    }
                } else {
                    let _ = fs::write(&path, format!("# Agent4j Web\nexport PATH=\"$PATH:{}\"\n", bin_str));
                }
            }
        }
    }

    // 创建启动脚本（agent4j-web.bat / agent4j-web）
    fn create_launcher(bin_dir: &Path) {
        let jar_path = bin_dir.join("agent4j-web.jar");

        #[cfg(target_os = "windows")]
        {
            // agent4j-web.bat
            let bat = bin_dir.join("agent4j-web.bat");
            if !bat.exists() {
                let _ = fs::write(&bat, format!(
                    "@echo off\r\nset \"JAVA_OPTS=-Dfile.encoding=UTF-8\"\r\njava %JAVA_OPTS% -jar \"{}\" %*\r\n",
                    jar_path.to_string_lossy()
                ));
            }
        }

        #[cfg(not(target_os = "windows"))]
        {
            // agent4j-web shell script
            let launcher = bin_dir.join("agent4j-web");
            if !launcher.exists() {
                let _ = fs::write(&launcher, format!(
                    "#!/bin/bash\nJAVA_OPTS=\"-Dfile.encoding=UTF-8\"\njava $JAVA_OPTS -jar \"{}\" \"$@\"\n",
                    jar_path.to_string_lossy()
                ));
                let _ = std::process::Command::new("chmod").args(&["+x", &launcher.to_string_lossy()]).output();
            }
        }
    }

    // 计算文件 SHA256 十六进制字符串
    fn sha256(path: &Path) -> Result<String, String> {
        use sha2::{Digest, Sha256};
        let mut file = fs::File::open(path)
            .map_err(|e| format!("Failed to open {}: {}", path.display(), e))?;
        let mut hasher = Sha256::new();
        std::io::copy(&mut file, &mut hasher)
            .map_err(|e| format!("Failed to read {}: {}", path.display(), e))?;
        Ok(format!("{:x}", hasher.finalize()))
    }

    // 计算 tar.gz 内 bin/agent4j-web.jar 的 SHA256
    // 使用 ends_with 匹配，兼容 tar 条目可能带 ./ 前缀等情况
    fn jar_hash_in_archive(archive_path: &Path) -> Result<String, String> {
        use sha2::{Digest, Sha256};
        let file = fs::File::open(archive_path)
            .map_err(|e| format!("Failed to open archive: {}", e))?;
        let gz = flate2::read::GzDecoder::new(file);
        let mut archive = tar::Archive::new(gz);

        for entry in archive.entries().map_err(|e| format!("Failed to read archive: {}", e))? {
            let mut entry = entry.map_err(|e| format!("Failed to read entry: {}", e))?;
            let path = entry.path().map_err(|_| "Failed to get path".to_string())?;
            if path.ends_with("bin/agent4j-web.jar") {
                let mut hasher = Sha256::new();
                std::io::copy(&mut entry, &mut hasher)
                    .map_err(|e| format!("Failed to read jar from archive: {}", e))?;
                return Ok(format!("{:x}", hasher.finalize()));
            }
        }
        Err("bin/agent4j-web.jar not found in archive".to_string())
    }

    // 从资源中安装 agent4j-web
    fn install_from_resource(&self, resource_dir: &Path) -> Result<(), String> {
        // 0) 检查 Java
        let java_ver = Self::check_java()?;
        println!("Java: {}", java_ver);

        let install_dir = self.get_install_dir();
        let archive_path = resource_dir.join("agent4j-web-dist.tar.gz");

        if !archive_path.exists() {
            return Err(format!("Resource not found: {:?}", archive_path));
        }

        // 1) 解压到临时目录
        let temp_dir = install_dir.join(".tmp-install");
        if temp_dir.exists() {
            let _ = fs::remove_dir_all(&temp_dir);
        }
        fs::create_dir_all(&temp_dir)
            .map_err(|e| format!("Failed to create temp dir: {}", e))?;
        self.extract_tar_gz(&archive_path, &temp_dir)?;

        // 2) 复制 bin/ 下所有文件到安装目录
        let target_bin = install_dir.join("bin");
        fs::create_dir_all(&target_bin)
            .map_err(|e| format!("Failed to create bin dir: {}", e))?;

        let src_bin = temp_dir.join("bin");
        if src_bin.exists() {
            for entry in fs::read_dir(&src_bin).map_err(|e| format!("Failed to read bin: {}", e))? {
                let entry = entry.map_err(|e| format!("Failed to read entry: {}", e))?;
                let fname = entry.file_name();
                let target = target_bin.join(&fname);
                let _ = fs::remove_file(&target);
                fs::copy(&entry.path(), &target)
                    .map_err(|e| format!("Failed to copy {}: {}", fname.to_string_lossy(), e))?;
            }
        }

        // 3) 复制 agent4j.md（保留已有的，不覆盖）
        //     config.json 由 Agent4jConfig.load() 自动创建，不从归档复制
        for name in &["agent4j.md"] {
            let src = temp_dir.join(name);
            let target = install_dir.join(name);
            if src.exists() && !target.exists() {
                let _ = fs::copy(&src, &target);
            }
        }

        // 4) 创建启动脚本 + 配置 PATH
        Self::create_launcher(&target_bin);
        Self::setup_path(&target_bin);

        // 5) 清理临时目录
        let _ = fs::remove_dir_all(&temp_dir);

        Ok(())
    }

    // 解压 tar.gz
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

    // 停止本实例之前启动的子进程（self.child 句柄追踪，不会误杀其他实例的进程）
    fn cleanup_stale(&self) {
        let _ = self.stop();
    }

    // 启动 agent4j-web 服务
    fn start(&self) -> Result<u32, String> {
        // 先清理残留进程
        self.cleanup_stale();

        // 始终使用已安装目录下的 jar（由 setup 保证是最新版）
        let install_dir = self.get_install_dir();
        let bin_dir = install_dir.join("bin");
        let jar_path = bin_dir.join("agent4j-web.jar");
        if !jar_path.exists() {
            return Err("agent4j-web.jar not found".to_string());
        }

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

    // 停止 agent4j-web 服务（仅通过 self.child 句柄杀进程，不会误杀其他实例）
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

            // 检查压缩包中 jar 的 hash 与已安装的是否一致
            let archive_path = resource_dir.join("agent4j-web-dist.tar.gz");
            let installed_jar = manager.get_install_dir().join("bin").join("agent4j-web.jar");

            let needs_install = if !manager.is_installed() {
                true
            } else if let (Ok(archive_hash), Ok(installed_hash)) = (
                Agent4jWebManager::jar_hash_in_archive(&archive_path),
                Agent4jWebManager::sha256(&installed_jar),
            ) {
                if archive_hash != installed_hash {
                    println!("Jar hash mismatch, reinstalling...");
                    true
                } else {
                    println!("Jar hash matches, skipping install.");
                    false
                }
            } else {
                true  // 无法计算 hash 时保守地重新安装
            };

            if needs_install {
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
            // 窗口关闭时停止 agent4j-web（CloseRequested 比 Destroyed 先触发）
            if let tauri::WindowEvent::CloseRequested { .. } = event {
                let manager = window.state::<Agent4jWebManager>();
                if let Err(e) = manager.stop() {
                    eprintln!("Failed to stop Agent4j Web: {}", e);
                }
            }
        })
        .run(tauri::generate_context!())
        .expect("error while running tauri application");
}
