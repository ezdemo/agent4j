// windows_subsystem 属性已在 main.rs 中设置（binary crate专属）

use std::fs;
use std::io::{Read, Write};
use std::net::TcpListener;
use std::path::{Path, PathBuf};
use std::process::{Child, Command};
use std::sync::Mutex;
use tauri::Emitter;
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

    // ── Java 版本解析（从 java -version 输出的 stderr 中提取） ──
    fn parse_java_version(stderr: &str) -> Result<(i32, String), String> {
        let major = stderr.split('"').nth(1)
            .and_then(|v| v.split('.').next())
            .and_then(|v| v.parse::<i32>().ok())
            .unwrap_or(0);
        let ver_line = stderr.lines().next().unwrap_or("unknown").to_string();
        if major < 17 {
            Err(format!("Java 17+ required, found version {}", major))
        } else {
            Ok((major, ver_line))
        }
    }

    // 检查指定路径的 Java
    fn check_java_at(path: &Path) -> Result<String, String> {
        let output = Command::new(path)
            .args(&["-version"])
            .output()
            .map_err(|_| format!("Java not found at {}", path.display()))?;
        let stderr = String::from_utf8_lossy(&output.stderr);
        let (_, ver) = Self::parse_java_version(&stderr)?;
        Ok(ver)
    }

    // ── Java 自动安装相关 ──

    // 获取 ~/.agent4j/jre25/bin/java 路径
    fn get_bundled_java_path(install_dir: &Path) -> PathBuf {
        let mut p = install_dir.join("jre25").join("bin").join("java");
        if cfg!(windows) {
            p.set_extension("exe");
        }
        p
    }

    // 确保 Java 可用：仅检查 ~/.agent4j/jre25/bin/java
    fn ensure_java(&self) -> Result<String, String> {
        let install_dir = self.get_install_dir();
        let bundled_path = Self::get_bundled_java_path(&install_dir);
        if bundled_path.exists() {
            if let Ok(ver) = Self::check_java_at(&bundled_path) {
                return Ok(format!("{} (bundled)", ver));
            }
        }
        let ver = self.download_and_install_jdk()?;
        Ok(format!("{} (auto-installed)", ver))
    }

    // ── 从清华镜像下载 JDK 25（通过 Adoptium API 获取最新版本号） ──
    fn adoptium_os() -> &'static str {
        match std::env::consts::OS {
            "macos" => "mac",
            "windows" => "windows",
            _ => "linux",
        }
    }
    fn adoptium_arch() -> &'static str {
        match std::env::consts::ARCH {
            "x86_64" => "x64",
            "aarch64" => "aarch64",
            "x86" => "x86",
            _ => "x64",
        }
    }

    // 从清华镜像下载 JDK 25（通过 Adoptium API 获取文件名后构造镜像 URL）
    fn get_jdk_download_url() -> Result<String, String> {
        let os = Self::adoptium_os();
        let arch = Self::adoptium_arch();

        // 从 Adoptium API 获取最新 JDK 的包名
        let package_name = Self::fetch_jdk_package_name().unwrap_or_else(|_| {
            // API 不可用时使用兜底文件名
            format!("OpenJDK25U-jre_{}_{}_hotspot_25.0.3_9.{}", arch, os,
                if os == "windows" { "zip" } else { "tar.gz" })
        });

        let url = format!(
            "https://mirrors.tuna.tsinghua.edu.cn/Adoptium/25/jre/{}/{}/{}",
            arch, os, package_name
        );
        Ok(url)
    }

    // 从 Adoptium API 获取最新 JDK 25 的包文件名
    fn fetch_jdk_package_name() -> Result<String, String> {
        let os = Self::adoptium_os();
        let arch = Self::adoptium_arch();
        let api_url = format!(
            "https://api.adoptium.net/v3/assets/feature_releases/25/ga?architecture={}&image_type=jre&os={}&page_size=1",
            arch, os
        );
        let resp = ureq::get(&api_url)
            .call()
            .map_err(|e| format!("查询 Adoptium 失败: {}", e))?;
        let reader = resp.into_reader();
        let json: serde_json::Value = serde_json::from_reader(reader)
            .map_err(|e| format!("解析响应失败: {}", e))?;

        json[0]["binaries"][0]["package"]["name"]
            .as_str()
            .map(|s: &str| s.to_string())
            .ok_or("API 返回中未找到包名".to_string())
    }

    // 下载并安装 JRE 25 到 ~/.agent4j/jre25/
    fn download_and_install_jdk(&self) -> Result<String, String> {
        let install_dir = self.get_install_dir();
        let jdk_dir = install_dir.join("jre25");
        let tmp_dir = install_dir.join(".tmp-jdk");

        // 清理残留
        if tmp_dir.exists() {
            fs::remove_dir_all(&tmp_dir).ok();
        }
        fs::create_dir_all(&tmp_dir)
            .map_err(|e| format!("创建临时目录失败: {}", e))?;

        // 获取下载地址
        let url = Self::get_jdk_download_url()?;
        let filename = url.rsplit('/').next().ok_or("无效的下载地址")?.to_string();
        let archive_path = tmp_dir.join(&filename);

        // 下载
        let resp = ureq::get(&url)
            .call()
            .map_err(|e| format!("下载 JDK 失败: {}", e))?;
        let mut reader = resp.into_reader();
        let mut file = fs::File::create(&archive_path)
            .map_err(|e| format!("创建文件失败: {}", e))?;
        std::io::copy(&mut reader, &mut file)
            .map_err(|e| format!("写入下载文件失败: {}", e))?;
        drop(file);

        // 解压
        let extract_dir = tmp_dir.join("extract");
        fs::create_dir_all(&extract_dir)
            .map_err(|e| format!("创建解压目录失败: {}", e))?;

        if filename.ends_with(".tar.gz") || filename.ends_with(".tgz") {
            let gz = flate2::read::GzDecoder::new(
                fs::File::open(&archive_path)
                    .map_err(|e| format!("打开压缩包失败: {}", e))?
            );
            let mut archive = tar::Archive::new(gz);
            archive.unpack(&extract_dir)
                .map_err(|e| format!("解压 tar.gz 失败: {}", e))?;
        } else if cfg!(windows) && filename.ends_with(".zip") {
            #[cfg(windows)]
            {
                let zf = fs::File::open(&archive_path)
                    .map_err(|e| format!("打开 zip 失败: {}", e))?;
                let mut archive = zip::ZipArchive::new(zf)
                    .map_err(|e| format!("读取 zip 失败: {}", e))?;
                archive.extract(&extract_dir)
                    .map_err(|e| format!("解压 zip 失败: {}", e))?;
            }
            #[cfg(not(windows))]
            return Err("ZIP 解压仅在 Windows 下支持".to_string());
        } else {
            return Err(format!("不支持的压缩格式: {}", filename));
        }

        // 找到 JDK 顶层目录（解压后唯一的子目录）
        let entries: Vec<_> = fs::read_dir(&extract_dir)
            .map_err(|e| e.to_string())?
            .filter_map(|e| e.ok())
            .filter(|e| e.file_type().map(|t| t.is_dir()).unwrap_or(false))
            .collect();
        let jdk_subdir = entries.into_iter().next()
            .ok_or("解压后未找到 JDK 目录")?;

        // 移动到最终位置
        if jdk_dir.exists() {
            fs::remove_dir_all(&jdk_dir)
                .map_err(|e| format!("清理旧 JDK 失败: {}", e))?;
        }
        fs::rename(jdk_subdir.path(), &jdk_dir)
            .map_err(|e| format!("移动 JDK 目录失败: {}", e))?;

        // 清理临时文件
        fs::remove_dir_all(&tmp_dir).ok();

        // 验证
        let java_bin = Self::get_bundled_java_path(&install_dir);
        let ver = Self::check_java_at(&java_bin)?;

        Ok(ver)
    }

    // ── 带进度事件的 JDK 下载（在后台线程调用，通过 Tauri 事件通知前端） ──
    fn download_java_with_progress(install_dir: &Path, app: &tauri::AppHandle) -> Result<String, String> {
        let jdk_dir = install_dir.join("jre25");
        let tmp_dir = install_dir.join(".tmp-jre");

        // 清理残留
        if tmp_dir.exists() {
            fs::remove_dir_all(&tmp_dir).ok();
        }
        fs::create_dir_all(&tmp_dir)
            .map_err(|e| format!("创建临时目录失败: {}", e))?;

        // 获取下载地址
        let _ = app.emit("java-download-progress", serde_json::json!({
            "phase": "resolving", "downloaded": 0, "total": 0, "percent": 0,
            "message": "正在获取 JDK 下载地址..."
        }));
        let url = Self::get_jdk_download_url()?;
        let filename = url.rsplit('/').next().ok_or("无效的下载地址")?.to_string();
        let archive_path = tmp_dir.join(&filename);

        // 下载
        let _ = app.emit("java-download-progress", serde_json::json!({
            "phase": "downloading", "downloaded": 0, "total": 0, "percent": 0,
            "message": "正在连接下载服务器..."
        }));
        let resp = ureq::get(&url)
            .call()
            .map_err(|e| format!("下载 JDK 失败: {}", e))?;

        let total_size: u64 = resp.header("Content-Length")
            .and_then(|v| v.parse().ok())
            .unwrap_or(0);

        let mut reader = resp.into_reader();
        let mut file = fs::File::create(&archive_path)
            .map_err(|e| format!("创建文件失败: {}", e))?;

        let mut downloaded: u64 = 0;
        let mut buffer = [0u8; 65536]; // 64KB 分块
        let start_time = std::time::Instant::now();
        let mut last_emit = std::time::Instant::now();

        loop {
            let n = reader.read(&mut buffer)
                .map_err(|e| format!("读取下载流失败: {}", e))?;
            if n == 0 {
                break;
            }
            file.write_all(&buffer[..n])
                .map_err(|e| format!("写入文件失败: {}", e))?;
            downloaded += n as u64;

            // 每 200ms 或最后一块时发射进度事件
            if last_emit.elapsed().as_millis() >= 200 || n < buffer.len() {
                let elapsed = start_time.elapsed().as_secs().max(1);
                let speed = downloaded / elapsed;
                let percent = if total_size > 0 {
                    (downloaded as f64 / total_size as f64 * 100.0) as u32
                } else {
                    0
                };
                let _ = app.emit("java-download-progress", serde_json::json!({
                    "phase": "downloading",
                    "downloaded": downloaded,
                    "total": total_size,
                    "percent": percent.min(99),
                    "speed": speed,
                    "message": format!("正在下载 JDK ({}/s)", Self::format_file_size(speed))
                }));
                last_emit = std::time::Instant::now();
            }
        }
        drop(file);

        // 解压
        let _ = app.emit("java-download-progress", serde_json::json!({
            "phase": "extracting", "downloaded": 0, "total": 0, "percent": 0,
            "message": "正在解压 JDK 压缩包..."
        }));
        let extract_dir = tmp_dir.join("extract");
        fs::create_dir_all(&extract_dir)
            .map_err(|e| format!("创建解压目录失败: {}", e))?;

        if filename.ends_with(".tar.gz") || filename.ends_with(".tgz") {
            let gz = flate2::read::GzDecoder::new(
                fs::File::open(&archive_path)
                    .map_err(|e| format!("打开压缩包失败: {}", e))?
            );
            let mut archive = tar::Archive::new(gz);
            archive.unpack(&extract_dir)
                .map_err(|e| format!("解压 tar.gz 失败: {}", e))?;
        } else if cfg!(windows) && filename.ends_with(".zip") {
            #[cfg(windows)]
            {
                let zf = fs::File::open(&archive_path)
                    .map_err(|e| format!("打开 zip 失败: {}", e))?;
                let mut archive = zip::ZipArchive::new(zf)
                    .map_err(|e| format!("读取 zip 失败: {}", e))?;
                archive.extract(&extract_dir)
                    .map_err(|e| format!("解压 zip 失败: {}", e))?;
            }
            #[cfg(not(windows))]
            return Err("ZIP 解压仅在 Windows 下支持".to_string());
        } else {
            return Err(format!("不支持的压缩格式: {}", filename));
        }

        // 找到 JDK 顶层目录
        let entries: Vec<_> = fs::read_dir(&extract_dir)
            .map_err(|e| e.to_string())?
            .filter_map(|e| e.ok())
            .filter(|e| e.file_type().map(|t| t.is_dir()).unwrap_or(false))
            .collect();
        let jdk_subdir = entries.into_iter().next()
            .ok_or("解压后未找到 JDK 目录")?;

        // 移动到最终位置
        let _ = app.emit("java-download-progress", serde_json::json!({
            "phase": "installing", "downloaded": 0, "total": 0, "percent": 0,
            "message": "正在安装 JDK..."
        }));
        if jdk_dir.exists() {
            fs::remove_dir_all(&jdk_dir)
                .map_err(|e| format!("清理旧 JDK 失败: {}", e))?;
        }
        fs::rename(jdk_subdir.path(), &jdk_dir)
            .map_err(|e| format!("移动 JDK 目录失败: {}", e))?;

        // 清理临时文件
        fs::remove_dir_all(&tmp_dir).ok();

        // 验证
        let java_bin = Self::get_bundled_java_path(install_dir);
        let ver = Self::check_java_at(&java_bin)?;

        Ok(ver)
    }

    // 格式化文件大小（如 "12.5 MB"）
    fn format_file_size(bytes: u64) -> String {
        const KB: u64 = 1024;
        const MB: u64 = KB * 1024;
        const GB: u64 = MB * 1024;
        if bytes >= GB {
            format!("{:.1} GB", bytes as f64 / GB as f64)
        } else if bytes >= MB {
            format!("{:.1} MB", bytes as f64 / MB as f64)
        } else if bytes >= KB {
            format!("{:.0} KB", bytes as f64 / KB as f64)
        } else {
            format!("{} B", bytes)
        }
    }

    // ── JPID 文件管理（持久化 PID，即使 Tauri 崩溃也能在下次启动时清理） ──

    // ~/.agent4j/bin/.jpid 路径
    fn get_jpid_path(&self) -> PathBuf {
        self.get_install_dir().join("bin").join(".jpid")
    }

    // 读取 JPID 文件中的 PID（文件不存在或格式错误时返回 None）
    fn read_jpid(&self) -> Option<u32> {
        let path = self.get_jpid_path();
        if !path.exists() {
            return None;
        }
        let content = fs::read_to_string(path).ok()?;
        let pid: u32 = content.trim().parse().ok()?;
        if pid < 2 {
            return None;
        }
        Some(pid)
    }

    // 保存 PID 到 JPID 文件
    fn save_jpid(&self, pid: u32) {
        let path = self.get_jpid_path();
        if let Some(parent) = path.parent() {
            fs::create_dir_all(parent).ok();
        }
        fs::write(&path, pid.to_string()).ok();
        println!("JPID saved: {} -> {}", path.display(), pid);
    }

    // 删除 JPID 文件
    fn remove_jpid(&self) {
        let path = self.get_jpid_path();
        if path.exists() {
            fs::remove_file(&path).ok();
            println!("JPID file removed: {}", path.display());
        }
    }

    // 根据 PID 杀死整个进程组（跨平台）
    // 返回 true 表示进程已死或已被杀死，false 表示杀失败
    fn kill_pid(pid: u32) -> bool {
        #[cfg(unix)]
        {
            unsafe {
                // 先检查进程是否存在（kill 0 不发送信号，只检查存在性）
                let exists = libc::kill(pid as i32, 0);
                if exists != 0 {
                    // ESRCH = 进程不存在，算成功
                    return true;
                }
                // 用负 PID 杀整个进程组（与 start() 中的 setsid() 配套）
                println!("Killing process group -{} with SIGTERM", pid);
                libc::kill(-(pid as i32), libc::SIGTERM);
                std::thread::sleep(std::time::Duration::from_millis(500));
                println!("Killing process group -{} with SIGKILL", pid);
                libc::kill(-(pid as i32), libc::SIGKILL);
                return true;
            }
        }

        #[cfg(windows)]
        {
            println!("Killing PID {} with taskkill /T", pid);
            let output = Command::new("taskkill")
                .args(&["/F", "/T", "/PID", &pid.to_string()])
                .output();
            match output {
                Ok(o) => {
                    if o.status.success() {
                        true
                    } else {
                        let stderr = String::from_utf8_lossy(&o.stderr);
                        // 进程已不存在也算成功
                        stderr.contains("not found") || stderr.contains("不存在")
                    }
                }
                Err(_) => false,
            }
        }
    }

    // 创建启动脚本
    fn create_launcher(bin_dir: &Path) {
        let jar_path = bin_dir.join("agent4j-web.jar");

        #[cfg(target_os = "windows")]
        {
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

    // 计算文件 SHA256
    fn sha256(path: &Path) -> Result<String, String> {
        use sha2::{Digest, Sha256};
        let mut file = fs::File::open(path)
            .map_err(|e| format!("Failed to open {}: {}", path.display(), e))?;
        let mut hasher = Sha256::new();
        std::io::copy(&mut file, &mut hasher)
            .map_err(|e| format!("Failed to read {}: {}", path.display(), e))?;
        Ok(format!("{:x}", hasher.finalize()))
    }

    // 计算 tar.gz 内 jar 的 SHA256
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

    // 步骤1：确保 Java 可用（系统 → 已捆绑 → 自动下载）
    fn install_step1_check_java(&self, resource_dir: &Path) -> Result<String, String> {
        // 确保 Java 可用（自动下载安装到 ~/.agent4j/jre25/）
        let java_ver = self.ensure_java()?;

        // 验证安装包存在
        let archive_path = resource_dir.join("agent4j-web-dist.tar.gz");
        if !archive_path.exists() {
            return Err(format!("Resource not found: {:?}", archive_path));
        }

        Ok(java_ver)
    }

    // 步骤2：解压安装包
    fn install_step2_extract(&self, resource_dir: &Path) -> Result<String, String> {
        let install_dir = self.get_install_dir();
        let archive_path = resource_dir.join("agent4j-web-dist.tar.gz");
        let temp_dir = install_dir.join(".tmp-install");

        // 清理可能的残留
        if temp_dir.exists() {
            fs::remove_dir_all(&temp_dir)
                .map_err(|e| format!("Failed to clean temp dir: {}", e))?;
        }
        fs::create_dir_all(&temp_dir)
            .map_err(|e| format!("Failed to create temp dir: {}", e))?;

        self.extract_tar_gz(&archive_path, &temp_dir)?;

        Ok("解压完成".to_string())
    }

    // 递归复制目录（支持覆盖已有文件）
    fn copy_dir_recursive(src: &Path, dst: &Path) -> Result<(), String> {
        if !src.exists() {
            return Ok(());
        }
        fs::create_dir_all(dst)
            .map_err(|e| format!("Failed to create dir {}: {}", dst.display(), e))?;

        for entry in fs::read_dir(src).map_err(|e| format!("Failed to read dir {}: {}", src.display(), e))? {
            let entry = entry.map_err(|e| format!("Failed to read entry: {}", e))?;
            let file_type = entry.file_type().map_err(|e| format!("Failed to get file type: {}", e))?;
            let src_path = entry.path();
            let dst_path = dst.join(entry.file_name());

            if file_type.is_dir() {
                Self::copy_dir_recursive(&src_path, &dst_path)?;
            } else {
                let _ = fs::remove_file(&dst_path);
                fs::copy(&src_path, &dst_path)
                    .map_err(|e| format!("Failed to copy {}: {}", entry.file_name().to_string_lossy(), e))?;
            }
        }
        Ok(())
    }

    // 步骤3：复制文件到安装目录
    fn install_step3_copy_files(&self, _resource_dir: &Path) -> Result<String, String> {
        let install_dir = self.get_install_dir();
        let temp_dir = install_dir.join(".tmp-install");

        if !temp_dir.exists() {
            return Err("请先执行解压步骤".to_string());
        }

        // 复制 bin/
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

        // 复制 plugin/（递归，支持覆盖）
        let src_plugin = temp_dir.join("plugin");
        if src_plugin.exists() {
            let target_plugin = install_dir.join("plugin");
            Self::copy_dir_recursive(&src_plugin, &target_plugin)?;
        }

        // 复制 agent4j.md（不覆盖已有的）
        for name in &["agent4j.md"] {
            let src = temp_dir.join(name);
            let target = install_dir.join(name);
            if src.exists() && !target.exists() {
                let _ = fs::copy(&src, &target);
            }
        }

        // 创建启动脚本
        Self::create_launcher(&target_bin);

        // 清理临时目录
        if temp_dir.exists() {
            let _ = fs::remove_dir_all(&temp_dir);
        }

        Ok("安装完成".to_string())
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

    // 清理残留进程
    fn cleanup_stale(&self) {
        let _ = self.stop();
    }

    // 启动 agent4j-web 服务（随机端口）
    fn start(&self, app: Option<&tauri::AppHandle>) -> Result<u32, String> {
        // 先读 JPID 文件，杀死旧进程（应对 Tauri 崩溃后重启的场景）
        if let Some(old_pid) = self.read_jpid() {
            println!("Found stale JPID {}, cleaning up...", old_pid);
            Self::kill_pid(old_pid);
            self.remove_jpid();
        }

        self.cleanup_stale();

        let install_dir = self.get_install_dir();
        let bin_dir = install_dir.join("bin");
        let jar_path = bin_dir.join("agent4j-web.jar");
        if !jar_path.exists() {
            return Err("agent4j-web.jar not found".to_string());
        }

        let listener = TcpListener::bind("127.0.0.1:0")
            .map_err(|e| format!("Failed to bind port: {}", e))?;
        let port = listener.local_addr()
            .map_err(|e| format!("Failed to get port: {}", e))?
            .port();
        drop(listener);

        let mut port_lock = self.port.lock().unwrap();
        *port_lock = port;

        // 自动确保 JRE 可用：不存在则异步下载（带进度）
        let jre_java = Agent4jWebManager::get_bundled_java_path(&install_dir);
        if !jre_java.exists() {
            println!("JRE not found, downloading...");
            match app {
                Some(handle) => Agent4jWebManager::download_java_with_progress(&install_dir, handle)?,
                None => self.download_and_install_jdk()?,
            };
        }
        let mut cmd = Command::new(jre_java);
        cmd.args(&[
            "-Dfile.encoding=UTF-8",
            &format!("-Dserver.port={}", port),
            "-jar",
            &jar_path.to_string_lossy(),
        ]);

        // Unix 下创建新进程组，方便 kill 时清理整个子树
        #[cfg(unix)]
        {
            use std::os::unix::process::CommandExt;
            unsafe { cmd.pre_exec(|| {
                libc::setsid();
                Ok(())
            })};
        }

        // Windows 下隐藏控制台窗口
        #[cfg(target_os = "windows")]
        {
            use std::os::windows::process::CommandExt;
            cmd.creation_flags(0x08000000);
        }

        let child = cmd
            .spawn()
            .map_err(|e| format!("Failed to start agent4j-web: {}", e))?;

        let pid = child.id();

        let mut child_lock = self.child.lock().unwrap();
        *child_lock = Some(child);
        drop(child_lock);

        // 保存 PID 到 JPID 文件（持久化，关闭时或下次启动时可恢复）
        self.save_jpid(pid);

        // 等待就绪（最长 15 秒）
        let start = std::time::Instant::now();
        let timeout = std::time::Duration::from_secs(15);
        let mut ready = false;

        while start.elapsed() < timeout {
            {
                let mut cl = self.child.lock().unwrap();
                if let Some(ref mut ch) = *cl {
                    if let Ok(Some(_)) = ch.try_wait() {
                        *cl = None;
                        return Err(format!("Java process (PID {}) exited prematurely", pid));
                    }
                }
            }

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

    // 停止服务（递归杀死进程树）
    fn stop(&self) -> Result<(), String> {
        let mut child_lock = self.child.lock().unwrap();
        let mut killed = false;

        // 方式一：通过 Child 句柄杀（进程组方式，最干净）
        if let Some(ref mut child) = *child_lock {
            let pid = child.id();

            // Windows: taskkill /T 递归杀进程树
            #[cfg(target_os = "windows")]
            {
                let _ = Command::new("taskkill")
                    .args(&["/F", "/T", "/PID", &pid.to_string()])
                    .output();
            }

            // Unix: kill 负 PGID 杀整个进程组
            #[cfg(unix)]
            {
                unsafe { libc::kill(-(pid as i32), libc::SIGTERM); }
                std::thread::sleep(std::time::Duration::from_millis(200));
                unsafe { libc::kill(-(pid as i32), libc::SIGKILL); }
            }

            // 后台线程中等待退出，不阻塞任何界面
            let _ = child.wait();
            killed = true;
            println!("Stopped Java process via Child handle (PID {})", pid);
        }

        *child_lock = None;

        // 方式二：通过 JPID 文件杀（兜底，应对 Child 句柄丢失或 Tauri 重启）
        if let Some(jpid) = self.read_jpid() {
            if !killed {
                println!("Child handle not available, killing via JPID {}...", jpid);
                Self::kill_pid(jpid);
            }
            self.remove_jpid();
        }

        Ok(())
    }

    // 检查是否正在运行
    fn is_running(&self) -> bool {
        let mut child_lock = self.child.lock().unwrap();

        if let Some(ref mut child) = *child_lock {
            match child.try_wait() {
                Ok(Some(_)) => {
                    *child_lock = None;
                    false
                }
                Ok(None) => true,
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

// ========== Tauri Commands ==========

// 获取 agent4j-web 状态
#[tauri::command]
fn get_agent4j_web_status(state: tauri::State<'_, Agent4jWebManager>) -> serde_json::Value {
    serde_json::json!({
        "installed": state.is_installed(),
        "running": state.is_running(),
        "install_dir": state.get_install_dir().to_string_lossy()
    })
}

// 检查是否需要安装（比对 jar hash）
#[tauri::command]
fn check_install_needed(state: tauri::State<'_, Agent4jWebManager>, resource_dir: String) -> serde_json::Value {
    let resource_path = PathBuf::from(&resource_dir);
    let archive_path = resource_path.join("agent4j-web-dist.tar.gz");
    let installed_jar = state.get_install_dir().join("bin").join("agent4j-web.jar");

    if !state.is_installed() {
        return serde_json::json!({
            "needed": true,
            "reason": "not_installed"
        });
    }

    if let (Ok(archive_hash), Ok(installed_hash)) = (
        Agent4jWebManager::jar_hash_in_archive(&archive_path),
        Agent4jWebManager::sha256(&installed_jar),
    ) {
        if archive_hash != installed_hash {
            serde_json::json!({
                "needed": true,
                "reason": "version_mismatch"
            })
        } else {
            serde_json::json!({
                "needed": false,
                "reason": "up_to_date"
            })
        }
    } else {
        serde_json::json!({
            "needed": true,
            "reason": "hash_check_failed"
        })
    }
}

// ========== 分步安装命令（前端逐步调用） ==========

// 步骤1：检查 Java 环境
#[tauri::command]
fn install_step1_check_java(state: tauri::State<'_, Agent4jWebManager>, resource_dir: String) -> Result<String, String> {
    state.install_step1_check_java(&PathBuf::from(&resource_dir))
}

// 步骤2：解压安装包
#[tauri::command]
fn install_step2_extract(state: tauri::State<'_, Agent4jWebManager>, resource_dir: String) -> Result<String, String> {
    state.install_step2_extract(&PathBuf::from(&resource_dir))
}

// 步骤3：复制文件
#[tauri::command]
fn install_step3_copy_files(state: tauri::State<'_, Agent4jWebManager>, resource_dir: String) -> Result<String, String> {
    state.install_step3_copy_files(&PathBuf::from(&resource_dir))
}

// 启动服务（返回端口号）
#[tauri::command]
fn start_agent4j_web(app: tauri::AppHandle, state: tauri::State<'_, Agent4jWebManager>) -> Result<u32, String> {
    state.start(Some(&app))
}

// 获取当前端口号
#[tauri::command]
fn get_agent4j_web_port(state: tauri::State<'_, Agent4jWebManager>) -> u16 {
    state.get_port()
}

// 停止服务
#[tauri::command]
fn stop_agent4j_web(state: tauri::State<'_, Agent4jWebManager>) -> Result<(), String> {
    state.stop()
}

// 获取资源目录路径
#[tauri::command]
fn get_resource_dir(app: tauri::AppHandle) -> Result<String, String> {
    let dir = app.path().resource_dir()
        .map_err(|e| format!("Failed to get resource dir: {}", e))?
        .join("resources");
    Ok(dir.to_string_lossy().to_string())
}

// 获取应用信息
#[tauri::command]
fn get_app_info() -> serde_json::Value {
    serde_json::json!({
        "name": "Agent4j",
        "version": "2.0.0",
        "description": "智能 AI 代码助手桌面端"
    })
}

#[tauri::command]
fn pick_folder() -> Result<String, String> {
    let folder = rfd::FileDialog::new()
        .set_title("选择项目文件夹")
        .pick_folder();
    match folder {
        Some(path) => Ok(path.to_string_lossy().to_string()),
        None => Err("未选择文件夹".to_string())
    }
}

// 获取系统信息
#[tauri::command]
fn get_system_info() -> serde_json::Value {
    serde_json::json!({
        "os": std::env::consts::OS,
        "arch": std::env::consts::ARCH,
        "family": std::env::consts::FAMILY
    })
}

// ── Java 异步下载进度命令 ──

// 快速检查 jre25 是否已安装（不复用系统 Java）
#[tauri::command]
fn check_java_quick(state: tauri::State<'_, Agent4jWebManager>) -> serde_json::Value {
    let install_dir = state.get_install_dir();
    let bundled_path = Agent4jWebManager::get_bundled_java_path(&install_dir);
    if bundled_path.exists() {
        if let Ok(ver) = Agent4jWebManager::check_java_at(&bundled_path) {
            return serde_json::json!({"found": true, "version": ver, "source": "bundled"});
        }
    }
    serde_json::json!({"found": false})
}

// 启动异步 JDK 下载（在后台线程执行，通过事件推送进度）
#[tauri::command]
fn start_java_download(app: tauri::AppHandle) -> Result<String, String> {
    let install_dir = dirs::home_dir()
        .unwrap_or_else(|| std::path::PathBuf::from("."))
        .join(".agent4j");

    std::thread::spawn(move || {
        match Agent4jWebManager::download_java_with_progress(&install_dir, &app) {
            Ok(ver) => {
                let _ = app.emit("java-download-progress", serde_json::json!({
                    "phase": "done",
                    "percent": 100,
                    "version": ver,
                    "message": format!("JDK 安装完成: {}", ver)
                }));
            }
            Err(e) => {
                let _ = app.emit("java-download-progress", serde_json::json!({
                    "phase": "error",
                    "error": e,
                    "message": format!("JDK 下载失败: {}", e)
                }));
            }
        }
    });

    Ok("started".to_string())
}

// ========== Entry ==========

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    let manager = Agent4jWebManager::new();

    tauri::Builder::default()
        .plugin(tauri_plugin_shell::init())
        .manage(manager)
        .invoke_handler(tauri::generate_handler![
            get_app_info,
            get_system_info,
            get_resource_dir,
            pick_folder,
            get_agent4j_web_status,
            check_install_needed,
            install_step1_check_java,
            install_step2_extract,
            install_step3_copy_files,
            start_agent4j_web,
            stop_agent4j_web,
            get_agent4j_web_port,
            check_java_quick,
            start_java_download
        ])
        .setup(|app| {
            // 窗口标题
            if let Some(window) = app.get_webview_window("main") {
                window.set_title("Agent4j").ok();
            }

            // 注意：不再在这里做阻塞式安装/启动
            // 安装流程由前端 SplashScreen 驱动，通过 Tauri commands 交互

            Ok(())
        })
        .on_window_event(|window, event| {
            if let tauri::WindowEvent::CloseRequested { .. } = event {
                // 后台线程清理 Java 进程，不阻塞窗口关闭
                let handle = window.app_handle().clone();
                std::thread::spawn(move || {
                    let manager = handle.state::<Agent4jWebManager>();
                    if let Err(e) = manager.stop() {
                        eprintln!("Failed to stop Agent4j Web: {}", e);
                    }
                });
            }
        })
        .run(tauri::generate_context!())
        .expect("error while running tauri application");
}
