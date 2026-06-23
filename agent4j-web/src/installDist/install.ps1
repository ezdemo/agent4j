#
# Agent4j Web Installer for Windows PowerShell
# 支持重复安装，保留已有 config.json
# 自动下载 JRE 25（无需系统 Java）
#
$ErrorActionPreference = "Stop"

Write-Host ""
Write-Host "============================================" -ForegroundColor Cyan
Write-Host "   Agent4j Web Installer (PowerShell)" -ForegroundColor Cyan
Write-Host "============================================" -ForegroundColor Cyan
Write-Host ""

# =============================================
# 设置源目录和目标目录
# =============================================
$SOURCE_DIR = Split-Path -Parent $MyInvocation.MyCommand.Definition
if (-not $SOURCE_DIR) { $SOURCE_DIR = $PWD.Path }

$SOURCE_BIN_DIR = Join-Path $SOURCE_DIR "bin"
$SOURCE_CONFIG = Join-Path $SOURCE_DIR "config.json"
$SOURCE_AGENTS = Join-Path $SOURCE_DIR "agent4j.md"

$TARGET_DIR = Join-Path $env:USERPROFILE ".agent4j"
$TARGET_BIN_DIR = Join-Path $TARGET_DIR "bin"
$TARGET_CONFIG = Join-Path $TARGET_DIR "config.json"
$TARGET_AGENTS = Join-Path $TARGET_DIR "agent4j.md"
$JRE25_DIR = Join-Path $TARGET_DIR "jre25"

# =============================================
# 检测 OS / ARCH
# =============================================
function Get-AdoptiumOS {
    if ($IsMacOS) { return "mac" }
    if ($IsLinux) { return "linux" }
    return "windows"
}

function Get-AdoptiumArch {
    $arch = (Get-CimInstance Win32_Processor).Architecture
    # 常见: 9=x64/AMD64, 12=ARM64, 5=ARM, 0=x86
    if ($arch -eq 9 -or $arch -eq 12) {
        # 进一步判断
        $envArch = $env:PROCESSOR_ARCHITECTURE
        if ($envArch -eq "ARM64") { return "aarch64" }
        if ($envArch -eq "AMD64" -or $envArch -eq "x86_64") { return "x64" }
    }
    # 通过环境变量判断
    switch ($env:PROCESSOR_ARCHITECTURE) {
        "ARM64"   { return "aarch64" }
        "AMD64"   { return "x64" }
        "x86_64"  { return "x64" }
        default   { return "x64" }
    }
}

# =============================================
# 下载并安装 JRE 25 到 ~/.agent4j/jre25/
# 参考 lib.rs: Adoptium API → 清华镜像
# =============================================
function Install-JRE25 {
    $os = Get-AdoptiumOS
    $arch = Get-AdoptiumArch

    Write-Host "[JRE 25] Downloading JRE 25 for $arch/$os..." -ForegroundColor Yellow

    # 1. 从 Adoptium API 获取最新包名
    $apiUrl = "https://api.adoptium.net/v3/assets/feature_releases/25/ga?architecture=$arch&image_type=jre&os=$os&page_size=1"
    Write-Host "      Querying Adoptium API..."  -ForegroundColor Gray

    $packageName = $null
    try {
        $apiResponse = Invoke-WebRequest -Uri $apiUrl -UseBasicParsing -TimeoutSec 30
        $json = $apiResponse | ConvertFrom-Json
        $packageName = $json[0].binaries[0].package.name
    } catch {
        Write-Host "      API request failed: $_" -ForegroundColor DarkGray
    }

    # 2. 兜底文件名
    if (-not $packageName) {
        $ext = if ($os -eq "windows") { "zip" } else { "tar.gz" }
        $packageName = "OpenJDK25U-jre_${arch}_${os}_hotspot_25.0.3_9.${ext}"
        Write-Host "      API unavailable, using fallback name: $packageName" -ForegroundColor Yellow
    } else {
        Write-Host "      Latest package: $packageName" -ForegroundColor Gray
    }

    # 3. 从清华镜像下载
    $mirrorUrl = "https://mirrors.tuna.tsinghua.edu.cn/Adoptium/25/jre/$arch/$os/$packageName"
    $tmpDir = Join-Path $TARGET_DIR ".tmp-jre"

    if (Test-Path $tmpDir) { Remove-Item -Recurse -Force $tmpDir }
    New-Item -ItemType Directory -Path $tmpDir -Force | Out-Null

    $archivePath = Join-Path $tmpDir $packageName

    Write-Host "      Downloading from Tsinghua mirror..." -ForegroundColor Gray
    try {
        Invoke-WebRequest -Uri $mirrorUrl -OutFile $archivePath -UseBasicParsing
    } catch {
        Write-Host "[ERROR] Download failed: $_" -ForegroundColor Red
        Write-Host "      URL: $mirrorUrl" -ForegroundColor Gray
        exit 1
    }

    # 4. 解压
    Write-Host "      Extracting..." -ForegroundColor Gray
    $extractDir = Join-Path $tmpDir "extract"
    New-Item -ItemType Directory -Path $extractDir -Force | Out-Null

    if ($packageName -match "\.(tar\.gz|tgz)$") {
        tar -xzf $archivePath -C $extractDir
    } elseif ($packageName -match "\.zip$") {
        Expand-Archive -Path $archivePath -DestinationPath $extractDir -Force
    } else {
        Write-Host "[ERROR] Unknown archive format: $packageName" -ForegroundColor Red
        exit 1
    }

    # 5. 找到 JRE 顶层目录
    $subDirs = Get-ChildItem -Path $extractDir -Directory | Select-Object -First 1
    $jreSource = if ($subDirs) { $subDirs.FullName } else { $extractDir }

    # 6. 移动到最终位置
    if (Test-Path $JRE25_DIR) { Remove-Item -Recurse -Force $JRE25_DIR }
    Move-Item -Path $jreSource -Destination $JRE25_DIR

    # 7. 清理
    Remove-Item -Recurse -Force $tmpDir -ErrorAction SilentlyContinue

    # 8. 验证
    $javaExe = if ($os -eq "windows") { Join-Path $JRE25_DIR "bin\java.exe" } else { Join-Path $JRE25_DIR "bin/java" }
    if (-not (Test-Path $javaExe)) {
        Write-Host "[ERROR] JRE 25 installation failed: java not found at $javaExe" -ForegroundColor Red
        exit 1
    }

    $process = New-Object System.Diagnostics.Process
    $process.StartInfo.FileName = $javaExe
    $process.StartInfo.Arguments = "-version"
    $process.StartInfo.RedirectStandardError = $true
    $process.StartInfo.RedirectStandardOutput = $true
    $process.StartInfo.UseShellExecute = $false
    $process.Start() | Out-Null
    $jreVer = $process.StandardError.ReadToEnd().Trim()
    $process.WaitForExit() | Out-Null
    Write-Host "      JRE 25 installed: $($jreVer -split "`n" | Select-Object -First 1)" -ForegroundColor Green
}

# =============================================
# 确保 JRE 25 可用
# =============================================
function Ensure-Java {
    $javaExe = if ((Get-AdoptiumOS) -eq "windows") {
        Join-Path $JRE25_DIR "bin\java.exe"
    } else {
        Join-Path $JRE25_DIR "bin\java"
    }

    if (Test-Path $javaExe) {
        $process = New-Object System.Diagnostics.Process
        $process.StartInfo.FileName = $javaExe
        $process.StartInfo.Arguments = "-version"
        $process.StartInfo.RedirectStandardError = $true
        $process.StartInfo.RedirectStandardOutput = $true
        $process.StartInfo.UseShellExecute = $false
        $process.Start() | Out-Null
        $ver = ($process.StandardError.ReadToEnd() -split "`n" | Select-Object -First 1).Trim()
        $process.WaitForExit() | Out-Null
        Write-Host "[Pre-check] Bundled JRE 25 found: $ver" -ForegroundColor Green
        return
    }

    Write-Host "[Pre-check] JRE 25 not found, will download automatically..." -ForegroundColor Yellow
    Write-Host ""
    Install-JRE25
    Write-Host ""
}

# =============================================
# 执行 Pre-check
# =============================================
Ensure-Java

# =============================================
# 检查源目录是否存在
# =============================================
if (-not (Test-Path $SOURCE_BIN_DIR)) {
    Write-Host "[Error] Source bin directory not found: $SOURCE_BIN_DIR" -ForegroundColor Red
    Read-Host "Press Enter to exit"
    exit 1
}

# =============================================
# [1/5] 检查并备份已有的 config.json 和 agent4j.md
# =============================================
Write-Host "[1/5] Checking for existing configuration..." -ForegroundColor Yellow

$CONFIG_BACKUP = $null
$AGENTS_BACKUP = $null

if (Test-Path $TARGET_CONFIG) {
    $CONFIG_BACKUP = Join-Path $env:TEMP "agent4j_config_backup_$(Get-Random).json"
    Copy-Item $TARGET_CONFIG $CONFIG_BACKUP -Force
    Write-Host "      Found existing config.json (will be preserved)" -ForegroundColor Gray
} else {
    Write-Host "      No existing config.json found" -ForegroundColor Gray
}

if (Test-Path $TARGET_AGENTS) {
    $AGENTS_BACKUP = Join-Path $env:TEMP "agent4j_agents_backup_$(Get-Random).md"
    Copy-Item $TARGET_AGENTS $AGENTS_BACKUP -Force
    Write-Host "      Found existing agent4j.md (will be preserved)" -ForegroundColor Gray
} else {
    Write-Host "      No existing agent4j.md found" -ForegroundColor Gray
}

# =============================================
# [2/5] 创建目标目录结构
# =============================================
Write-Host ""
Write-Host "[2/5] Preparing target directory: $TARGET_DIR" -ForegroundColor Yellow

if (-not (Test-Path $TARGET_DIR)) { New-Item -ItemType Directory -Path $TARGET_DIR | Out-Null }
if (-not (Test-Path $TARGET_BIN_DIR)) { New-Item -ItemType Directory -Path $TARGET_BIN_DIR | Out-Null }

Write-Host "      Created directory structure" -ForegroundColor Gray

# =============================================
# [3/5] 复制文件
# =============================================
Write-Host ""
Write-Host "[3/5] Copying files to target directory..." -ForegroundColor Yellow

Copy-Item -Path "$SOURCE_BIN_DIR\*" -Destination $TARGET_BIN_DIR -Recurse -Force
Write-Host "      Copied bin/ directory" -ForegroundColor Gray

if (Test-Path $SOURCE_CONFIG) {
    Copy-Item $SOURCE_CONFIG $TARGET_CONFIG -Force
    Write-Host "      Copied config.json" -ForegroundColor Gray
}

if (Test-Path $SOURCE_AGENTS) {
    Copy-Item $SOURCE_AGENTS $TARGET_AGENTS -Force
    Write-Host "      Copied agent4j.md" -ForegroundColor Gray
}

Write-Host "      Files copied successfully" -ForegroundColor Green

# =============================================
# [4/5] 恢复 config.json 和 agent4j.md 并检查 jar 文件
# =============================================
Write-Host ""
Write-Host "[4/5] Finalizing installation..." -ForegroundColor Yellow

if ($CONFIG_BACKUP -and (Test-Path $CONFIG_BACKUP)) {
    Copy-Item $CONFIG_BACKUP $TARGET_CONFIG -Force
    Remove-Item $CONFIG_BACKUP -Force
    Write-Host "      Preserved existing config.json" -ForegroundColor Gray
}

if ($AGENTS_BACKUP -and (Test-Path $AGENTS_BACKUP)) {
    Copy-Item $AGENTS_BACKUP $TARGET_AGENTS -Force
    Remove-Item $AGENTS_BACKUP -Force
    Write-Host "      Preserved existing agent4j.md" -ForegroundColor Gray
}

$JAR_FILE = Join-Path $TARGET_BIN_DIR "agent4j-web.jar"
if (-not (Test-Path $JAR_FILE)) {
    Write-Host ""
    Write-Host "[Error] agent4j-web.jar not found in $TARGET_BIN_DIR" -ForegroundColor Red
    Read-Host "Press Enter to exit"
    exit 1
}
Write-Host "      Found agent4j-web.jar" -ForegroundColor Gray

# =============================================
# [5/5] 创建启动脚本并配置 PATH（使用捆绑 JRE 25）
# =============================================
Write-Host ""
Write-Host "[5/5] Setting up 'agent4j' command..." -ForegroundColor Yellow

# —— PowerShell 启动脚本 (agent4j.ps1) ——
$LAUNCHER_PS1 = Join-Path $TARGET_BIN_DIR "agent4j.ps1"
$LAUNCHER_PS1_CONTENT = @'
# Agent4j Launcher for PowerShell — uses bundled JRE 25
param([Parameter(ValueFromRemainingArguments)]$RestArgs)

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Definition
$Agent4jHome = Split-Path -Parent $ScriptDir
$JreDir = Join-Path $Agent4jHome "jre25"
$JavaBin = Join-Path $JreDir "bin\java.exe"

# 如果捆绑 JRE 不存在，回退到系统 Java
if (-not (Test-Path $JavaBin)) {
    $sysJava = Get-Command java -ErrorAction SilentlyContinue
    if ($sysJava) {
        $JavaBin = $sysJava.Source
    } else {
        Write-Host "[ERROR] No Java found." -ForegroundColor Red
        Write-Host "  Expected bundled JRE at: $JavaBin" -ForegroundColor Gray
        Write-Host "  Please re-run the installer to download JRE 25." -ForegroundColor Gray
        exit 1
    }
}

# 显示帮助
$ShowHelp = ($RestArgs.Count -eq 0)
if (-not $ShowHelp -and $RestArgs.Count -ge 1) {
    $first = $RestArgs[0]
    if ($first -eq '-h' -or $first -eq '--help' -or $first -eq 'help') {
        $ShowHelp = $true
    }
}
if ($ShowHelp) {
    Write-Host "Agent4j — AI Coding Assistant" -ForegroundColor Cyan
    Write-Host ""
    Write-Host "Usage:" -ForegroundColor Yellow
    Write-Host "  agent4j web [port]    Start the web server"
    Write-Host ""
    Write-Host "Options:" -ForegroundColor Yellow
    Write-Host "  port    0 = random port, 8097 = default, or any port number"
    Write-Host ""
    Write-Host "Examples:" -ForegroundColor Yellow
    Write-Host "  agent4j web           Start on default port (8097)"
    Write-Host "  agent4j web 0         Start on a random available port"
    Write-Host "  agent4j web 9636      Start on port 9636"
    Write-Host ""
    Write-Host "  agent4j -h            Show this help"
    exit 0
}

$JarFile = Join-Path $ScriptDir "agent4j-web.jar"

if (-not (Test-Path $JarFile)) {
    Write-Host "[Error] agent4j-web.jar not found" -ForegroundColor Red
    Write-Host "Expected path: $JarFile"
    exit 1
}

# 设置控制台编码为 UTF-8
try {
    $OutputEncoding = [System.Text.Encoding]::UTF8
    [Console]::OutputEncoding = [System.Text.Encoding]::UTF8
    [Console]::InputEncoding = [System.Text.Encoding]::UTF8
} catch { }

# 检测 Java 版本，如果是 21+ 则添加 --enable-native-access 参数
$JavaArgs = @("-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8", "-Dstdin.encoding=UTF-8")

try {
    $VerProcess = New-Object System.Diagnostics.Process
    $VerProcess.StartInfo.FileName = $JavaBin
    $VerProcess.StartInfo.Arguments = "-version"
    $VerProcess.StartInfo.RedirectStandardError = $true
    $VerProcess.StartInfo.RedirectStandardOutput = $true
    $VerProcess.StartInfo.UseShellExecute = $false
    $VerProcess.Start() | Out-Null
    $VerOutput = $VerProcess.StandardError.ReadToEnd()
    $VerProcess.WaitForExit()
    
    if ($VerOutput -match '"(\d+)') {
        $JavaMajor = [int]$Matches[1]
        if ($JavaMajor -ge 21) {
            $JavaArgs += "--enable-native-access=ALL-UNNAMED"
        }
    }
} catch { }

# 解析 "web [port]" 子命令
$PassThroughArgs = @()
$i = 0
while ($i -lt $RestArgs.Count) {
    if ($RestArgs[$i] -eq 'web' -and $i -eq 0) {
        $PassThroughArgs += "--solon.logging.appender.console.enable=false"
        $i++
        if ($i -lt $RestArgs.Count -and $RestArgs[$i] -match '^\d+$') {
            $portArg = $RestArgs[$i]
            if ($portArg -eq '0') {
                $found = $false
                for ($n = 0; $n -lt 30; $n++) {
                    $candidate = Get-Random -Minimum 1024 -Maximum 65535
                    try {
                        $tcp = New-Object System.Net.Sockets.TcpClient
                        $tcp.Connect('127.0.0.1', $candidate)
                        $tcp.Close()
                    } catch {
                        $portArg = $candidate
                        $found = $true
                        break
                    }
                }
                if (-not $found) { $portArg = Get-Random -Minimum 1024 -Maximum 65535 }
                Write-Host "Random port: $portArg" -ForegroundColor Green
            }
            $PassThroughArgs += "--server.port=$portArg"
            $i++
        }
    } else {
        $PassThroughArgs += $RestArgs[$i]
        $i++
    }
}

# 运行 Java 程序（使用捆绑 JRE）
& $JavaBin @JavaArgs -jar $JarFile @PassThroughArgs
'@

Set-Content -Path $LAUNCHER_PS1 -Value $LAUNCHER_PS1_CONTENT -Encoding UTF8
Write-Host "      Created: agent4j.ps1" -ForegroundColor Gray

# —— CMD/.bat 启动脚本 (agent4j.bat) ——
# NOTE: .bat must be ASCII-only (no Chinese), written in system default encoding (GBK on zh-CN Windows)
$LAUNCHER_BAT = Join-Path $TARGET_BIN_DIR "agent4j.bat"
$LAUNCHER_BAT_CONTENT = @'
@echo off
rem Agent4j Launcher for CMD - uses bundled JRE 25
setlocal enabledelayedexpansion

rem Locate script dir and JRE
set "SCRIPT_DIR=%~dp0"
set "AGENT4J_HOME=%SCRIPT_DIR%.."
set "JAVA_BIN=%AGENT4J_HOME%\jre25\bin\java.exe"

rem Fallback to system Java if bundled JRE missing
if not exist "%JAVA_BIN%" (
    where java >nul 2>&1
    if %ERRORLEVEL% equ 0 (
        set "JAVA_BIN=java"
    ) else (
        echo [ERROR] No Java found.
        echo   Expected bundled JRE at: %JAVA_BIN%
        echo   Please re-run the installer to download JRE 25.
        exit /b 1
    )
)

rem Show help (no args / -h / --help / help)
if "%~1"=="" goto :show_help
if "%~1"=="-h" goto :show_help
if "%~1"=="--help" goto :show_help
if "%~1"=="help" goto :show_help
goto :parse_args

:show_help
echo Agent4j - AI Coding Assistant
echo.
echo Usage:
echo   agent4j web [port]    Start the web server
echo.
echo Options:
echo   port    0 = random port, 8097 = default, or any port number
echo.
echo Examples:
echo   agent4j web           Start on default port (8097)
echo   agent4j web 0         Start on a random available port
echo   agent4j web 9636      Start on port 9636
echo.
echo   agent4j -h            Show this help
goto :end

:parse_args
set "JAR_FILE=%SCRIPT_DIR%agent4j-web.jar"

if not exist "%JAR_FILE%" (
    echo [Error] agent4j-web.jar not found
    echo Expected path: %JAR_FILE%
    exit /b 1
)

rem Java encoding options
set "JAVA_OPTS=-Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 -Dstdin.encoding=UTF-8"

rem Add --enable-native-access for Java 21+
for /f "tokens=3" %%v in ('"%JAVA_BIN%" -version 2^>^&1 ^| findstr /i version') do (
    set "VER=%%v"
    set "VER=!VER:~1!"
    for /f "tokens=1 delims=." %%j in ("!VER!") do (
        if %%j GEQ 21 set "JAVA_OPTS=!JAVA_OPTS! --enable-native-access=ALL-UNNAMED"
    )
)

rem Parse "web [port]" subcommand
set "PASS_ARGS="
set "NEXT_IS_PORT="
set "FIRST_ARG=1"
for %%a in (%*) do (
    if "!FIRST_ARG!"=="1" (
        if "%%a"=="web" (
            set "PASS_ARGS=!PASS_ARGS! --solon.logging.appender.console.enable=false"
            set "NEXT_IS_PORT=1"
        ) else (
            set "PASS_ARGS=!PASS_ARGS! %%a"
        )
        set "FIRST_ARG=0"
    ) else (
        if defined NEXT_IS_PORT (
            if "%%a"=="0" (
                set /a PORT=!RANDOM! + 30000
                echo Random port: !PORT!
                set "PASS_ARGS=!PASS_ARGS! --server.port=!PORT!"
            ) else (
                set "PASS_ARGS=!PASS_ARGS! --server.port=%%a"
            )
            set "NEXT_IS_PORT="
        ) else (
            set "PASS_ARGS=!PASS_ARGS! %%a"
        )
    )
)

rem Run Java with bundled JRE
"%JAVA_BIN%" %JAVA_OPTS% -jar "%JAR_FILE%" %PASS_ARGS%

:end
'@

# Write .bat in system default encoding (GBK on zh-CN Windows) to avoid garbled chars in CMD
[System.IO.File]::WriteAllText($LAUNCHER_BAT, $LAUNCHER_BAT_CONTENT, [System.Text.Encoding]::Default)
Write-Host "      Created: agent4j.bat" -ForegroundColor Gray

# —— Git Bash 启动脚本 (agent4j) ——
$LAUNCHER_SH = Join-Path $TARGET_BIN_DIR "agent4j"
$LAUNCHER_SH_CONTENT = @'
#!/bin/bash
# Agent4j Launcher for Git Bash / WSL — uses bundled JRE 25

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
AGENT4J_HOME="$(cd "$SCRIPT_DIR/.." && pwd)"
JAVA_BIN="$AGENT4J_HOME/jre25/bin/java"

# 如果捆绑 JRE 不存在，回退到系统 Java
if [ ! -f "$JAVA_BIN" ]; then
    if command -v java &> /dev/null; then
        JAVA_BIN="java"
    else
        echo "[ERROR] No Java found."
        echo "  Expected bundled JRE at: $AGENT4J_HOME/jre25/bin/java"
        echo "  Please re-run the installer to download JRE 25."
        exit 1
    fi
fi

# 显示帮助（无参数 或 -h/--help/help）
if [ $# -eq 0 ] || [ "$1" = "-h" ] || [ "$1" = "--help" ] || [ "$1" = "help" ]; then
    echo -e "\033[0;36mAgent4j — AI Coding Assistant\033[0m"
    echo ""
    echo -e "\033[0;33mUsage:\033[0m"
    echo "  agent4j web [port]    Start the web server"
    echo ""
    echo -e "\033[0;33mOptions:\033[0m"
    echo "  port    0 = random port, 8097 = default, or any port number"
    echo ""
    echo -e "\033[0;33mExamples:\033[0m"
    echo "  agent4j web           Start on default port (8097)"
    echo "  agent4j web 0         Start on a random available port"
    echo "  agent4j web 9636      Start on port 9636"
    echo ""
    echo "  agent4j -h            Show this help"
    exit 0
fi

# 检测 Java 版本，如果是 21+ 则添加 --enable-native-access 参数
JAVA_VER=$("$JAVA_BIN" -version 2>&1 | head -n1 | grep -oE '"[0-9]+' | grep -oE '[0-9]+' | head -1)
if [ -z "$JAVA_VER" ]; then
    JAVA_VER=$("$JAVA_BIN" -version 2>&1 | head -n1 | cut -d'"' -f2 | cut -d'.' -f1)
fi
JAVA_OPTS="-Dfile.encoding=UTF-8"
if [ -n "$JAVA_VER" ] && [ "$JAVA_VER" -ge 21 ]; then
    JAVA_OPTS="$JAVA_OPTS --enable-native-access=ALL-UNNAMED"
fi

# 解析 "web [port]" 子命令
PASSTHROUGH_ARGS=()
while [ $# -gt 0 ]; do
    case "$1" in
        web)
            PASSTHROUGH_ARGS+=("--solon.logging.appender.console.enable=false")
            shift
            if [ $# -gt 0 ] && echo "$1" | grep -qE '^[0-9]+$'; then
                if [ "$1" = "0" ]; then
                    PORT=$(( RANDOM % 55536 + 10000 ))
                    echo "Random port: $PORT"
                    PASSTHROUGH_ARGS+=("--server.port=$PORT")
                else
                    PASSTHROUGH_ARGS+=("--server.port=$1")
                fi
                shift
            fi
            ;;
        *)
            PASSTHROUGH_ARGS+=("$1")
            shift
            ;;
    esac
done

"$JAVA_BIN" $JAVA_OPTS -jar "$SCRIPT_DIR/agent4j-web.jar" "${PASSTHROUGH_ARGS[@]}"
'@

Set-Content -Path $LAUNCHER_SH -Value $LAUNCHER_SH_CONTENT -Encoding UTF8 -NoNewline
Write-Host "      Created: agent4j (for Git Bash)" -ForegroundColor Gray

# =============================================
# 配置 PATH 环境变量
# =============================================
Write-Host ""
Write-Host "Configuring PATH..." -ForegroundColor Yellow

$USER_PATH = [Environment]::GetEnvironmentVariable("Path", "User")
if ($USER_PATH -like "*$TARGET_BIN_DIR*") {
    Write-Host "      Already in user PATH" -ForegroundColor Gray
} else {
    $NEW_PATH = if ($USER_PATH) { "$USER_PATH;$TARGET_BIN_DIR" } else { $TARGET_BIN_DIR }
    [Environment]::SetEnvironmentVariable("Path", $NEW_PATH, "User")
    Write-Host "      Added to user PATH" -ForegroundColor Green
}

# =============================================
# 完成
# =============================================
Write-Host ""
Write-Host "============================================" -ForegroundColor Green
Write-Host "   Installation Complete!" -ForegroundColor Green
Write-Host "============================================" -ForegroundColor Green
Write-Host ""
Write-Host "  Install path: $TARGET_DIR" -ForegroundColor White
Write-Host "  JRE path:     $JRE25_DIR" -ForegroundColor White
Write-Host ""
Write-Host "  Usage:" -ForegroundColor Cyan
Write-Host "    1. Open a NEW terminal window (PowerShell or Git Bash)"
Write-Host "    2. Run: 'agent4j web'        (default port 8097)"
Write-Host "       Run: 'agent4j web 0'      (random port)"
Write-Host "       Run: 'agent4j web 9636'   (specify port 9636)"
Write-Host ""
Write-Host "  Directory structure:" -ForegroundColor Cyan
Write-Host "    $env:USERPROFILE\.agent4j\"
Write-Host "    +-- config.json      (configuration, preserved)"
Write-Host "    +-- agent4j.md       (project docs, preserved)"
Write-Host "    +-- jre25/           (bundled JRE 25)"
Write-Host "    |   +-- bin/java.exe"
Write-Host "    |   +-- ..."
Write-Host "    +-- bin/             (executables)"
Write-Host "    |   +-- agent4j-web.jar"
Write-Host "    |   +-- agent4j.ps1       (PowerShell launcher)"
Write-Host "    |   +-- agent4j.bat       (CMD launcher)"
Write-Host "    |   +-- agent4j           (Git Bash launcher)"
Write-Host "    |   +-- uninstall.ps1     (uninstall script)"
Write-Host ""
Write-Host "  API Endpoint:" -ForegroundColor Cyan
Write-Host "    http://localhost:8097"
Write-Host ""
Write-Host "  [Tip] To use agent4j immediately in current terminal:" -ForegroundColor Yellow
Write-Host "    PowerShell: `$env:Path = [Environment]::GetEnvironmentVariable('Path','User')"
Write-Host ""

# If not called from setup.ps1, wait for user input
if (-not $env:AGENT4J_SETUP) {
    Read-Host "Press Enter to exit"
}
