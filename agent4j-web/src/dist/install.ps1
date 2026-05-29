#
# Agent4j Web Installer for Windows PowerShell
# 支持重复安装，保留已有 config.json
#
$ErrorActionPreference = "Stop"

Write-Host ""
Write-Host "============================================" -ForegroundColor Cyan
Write-Host "   Agent4j Web Installer (PowerShell)" -ForegroundColor Cyan
Write-Host "============================================" -ForegroundColor Cyan
Write-Host ""

# =============================================
# 检查 Java 是否安装
# =============================================
Write-Host "[Pre-check] Verifying Java installation..." -ForegroundColor Yellow

$javaPath = Get-Command java -ErrorAction SilentlyContinue
if (-not $javaPath) {
    Write-Host ""
    Write-Host "[Error] Java is not installed or not in PATH" -ForegroundColor Red
    Write-Host ""
    Write-Host "  Please install Java 17 or later:" -ForegroundColor White
    Write-Host "    - Download from: https://adoptium.net/" -ForegroundColor White
    Write-Host ""
    Read-Host "Press Enter to exit"
    exit 1
}

# 获取 Java 版本
$process = New-Object System.Diagnostics.Process
$process.StartInfo.FileName = "java"
$process.StartInfo.Arguments = "-version"
$process.StartInfo.RedirectStandardError = $true
$process.StartInfo.RedirectStandardOutput = $true
$process.StartInfo.UseShellExecute = $false
$process.Start() | Out-Null
$javaVersionOutput = $process.StandardError.ReadToEnd()
$process.WaitForExit()
$javaVersion = ($javaVersionOutput -split "`n" | Where-Object { $_ -match "version" } | Select-Object -First 1).Trim()
Write-Host "      $javaVersion" -ForegroundColor Gray

# 检查 Java 版本是否 >= 17
if ($javaVersionOutput -match '"(\d+)') {
    $javaMajor = [int]$Matches[1]
    if ($javaMajor -lt 17) {
        Write-Host ""
        Write-Host "[Error] Java 17 or later is required (found: Java $javaMajor)" -ForegroundColor Red
        Write-Host ""
        Read-Host "Press Enter to exit"
        exit 1
    }
}

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

# 备份现有的配置文件
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

# 复制 bin 目录内容
Copy-Item -Path "$SOURCE_BIN_DIR\*" -Destination $TARGET_BIN_DIR -Recurse -Force
Write-Host "      Copied bin/ directory" -ForegroundColor Gray

# 复制 config.json（从根目录）
if (Test-Path $SOURCE_CONFIG) {
    Copy-Item $SOURCE_CONFIG $TARGET_CONFIG -Force
    Write-Host "      Copied config.json" -ForegroundColor Gray
}

# 复制 agent4j.md（从根目录）
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

# 恢复 config.json 备份（如果之前存在）
if ($CONFIG_BACKUP -and (Test-Path $CONFIG_BACKUP)) {
    Copy-Item $CONFIG_BACKUP $TARGET_CONFIG -Force
    Remove-Item $CONFIG_BACKUP -Force
    Write-Host "      Preserved existing config.json" -ForegroundColor Gray
}

# 恢复 agent4j.md 备份（如果之前存在）
if ($AGENTS_BACKUP -and (Test-Path $AGENTS_BACKUP)) {
    Copy-Item $AGENTS_BACKUP $TARGET_AGENTS -Force
    Remove-Item $AGENTS_BACKUP -Force
    Write-Host "      Preserved existing agent4j.md" -ForegroundColor Gray
}

# 检查 jar 文件是否存在
$JAR_FILE = Join-Path $TARGET_BIN_DIR "agent4j-web.jar"
if (-not (Test-Path $JAR_FILE)) {
    Write-Host ""
    Write-Host "[Error] agent4j-web.jar not found in $TARGET_BIN_DIR" -ForegroundColor Red
    Read-Host "Press Enter to exit"
    exit 1
}
Write-Host "      Found agent4j-web.jar" -ForegroundColor Gray

# =============================================
# [5/5] 创建启动脚本并配置 PATH
# =============================================
Write-Host ""
Write-Host "[5/5] Setting up 'agent4j-web' command..." -ForegroundColor Yellow

# 创建 PowerShell 启动脚本 (agent4j-web.ps1)
$LAUNCHER_PS1 = Join-Path $TARGET_BIN_DIR "agent4j-web.ps1"
$LAUNCHER_CONTENT = @'
# Agent4j Web Launcher for PowerShell
param([Parameter(ValueFromRemainingArguments)]$RestArgs)

$JarDir = Split-Path -Parent $MyInvocation.MyCommand.Definition
$JarFile = Join-Path $JarDir "agent4j-web.jar"

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
} catch {
    # 某些终端环境不支持设置编码，忽略错误
}

# 检测 Java 版本，如果是 21+ 则添加 --enable-native-access 参数
$JavaArgs = @("-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8", "-Dstdin.encoding=UTF-8")

try {
    $VerProcess = New-Object System.Diagnostics.Process
    $VerProcess.StartInfo.FileName = "java"
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
} catch {
    # 版本检测失败时忽略，继续执行
}

# 运行 Java 程序
& java @JavaArgs -jar $JarFile @RestArgs
'@

Set-Content -Path $LAUNCHER_PS1 -Value $LAUNCHER_CONTENT -Encoding UTF8
Write-Host "      Created: agent4j-web.ps1" -ForegroundColor Gray

# 创建 CMD/.bat 启动脚本 (agent4j-web.bat)
$LAUNCHER_BAT = Join-Path $TARGET_BIN_DIR "agent4j-web.bat"
$LAUNCHER_BAT_CONTENT = @'
@echo off
rem Agent4j Web Launcher for CMD
setlocal enabledelayedexpansion

rem 获取脚本所在目录
set "SCRIPT_DIR=%~dp0"
set "JAR_FILE=%SCRIPT_DIR%agent4j-web.jar"

rem 检查 jar 文件是否存在
if not exist "%JAR_FILE%" (
    echo [Error] agent4j-web.jar not found
    echo Expected path: %JAR_FILE%
    exit /b 1
)

rem 设置 Java 编码参数
set "JAVA_OPTS=-Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 -Dstdin.encoding=UTF-8"

rem 检测 Java 版本，如果是 21+ 则添加 --enable-native-access 参数
for /f "tokens=3" %%v in ('java -version 2^>^&1 ^| findstr /i version') do (
    set "VER=%%v"
    set "VER=!VER:~1!"
    for /f "tokens=1 delims=." %%j in ("!VER!") do (
        if %%j GEQ 21 set "JAVA_OPTS=!JAVA_OPTS! --enable-native-access=ALL-UNNAMED"
    )
)

rem 运行 Java 程序
java %JAVA_OPTS% -jar "%JAR_FILE%" %*
'@

New-Item -Path $LAUNCHER_BAT -Value $LAUNCHER_BAT_CONTENT -Force | Out-Null
Write-Host "      Created: agent4j-web.bat" -ForegroundColor Gray

# 创建 Git Bash 启动脚本 (agent4j-web)
$LAUNCHER_SH = Join-Path $TARGET_BIN_DIR "agent4j-web"
$LAUNCHER_SH_CONTENT = @'
#!/bin/bash
# Agent4j Web Launcher for Git Bash / WSL

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

# 检测 Java 版本，如果是 21+ 则添加 --enable-native-access 参数
JAVA_VER=$(java -version 2>&1 | head -n1 | grep -oE '"[0-9]+' | grep -oE '[0-9]+' | head -1)
if [ -z "$JAVA_VER" ]; then
    JAVA_VER=$(java -version 2>&1 | head -n1 | cut -d'"' -f2 | cut -d'.' -f1)
fi
JAVA_OPTS="-Dfile.encoding=UTF-8"
if [ -n "$JAVA_VER" ] && [ "$JAVA_VER" -ge 21 ]; then
    JAVA_OPTS="$JAVA_OPTS --enable-native-access=ALL-UNNAMED"
fi

java $JAVA_OPTS -jar "$SCRIPT_DIR/agent4j-web.jar" "$@"
'@

Set-Content -Path $LAUNCHER_SH -Value $LAUNCHER_SH_CONTENT -Encoding UTF8 -NoNewline
Write-Host "      Created: agent4j-web (for Git Bash)" -ForegroundColor Gray

# =============================================
# 配置 PATH 环境变量
# =============================================
Write-Host ""
Write-Host "Configuring PATH..." -ForegroundColor Yellow

# 检查是否已在 PATH 中
$USER_PATH = [Environment]::GetEnvironmentVariable("Path", "User")
if ($USER_PATH -like "*$TARGET_BIN_DIR*") {
    Write-Host "      Already in user PATH" -ForegroundColor Gray
} else {
    # 添加到用户 PATH
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
Write-Host "  Java version: $javaVersion" -ForegroundColor White
Write-Host ""
Write-Host "  Usage:" -ForegroundColor Cyan
Write-Host "    1. Open a NEW terminal window (PowerShell or Git Bash)"
Write-Host "    2. Run: 'agent4j-web'"
Write-Host ""
Write-Host "  Directory structure:" -ForegroundColor Cyan
Write-Host "    $env:USERPROFILE\.agent4j\"
Write-Host "    +-- config.json      (configuration, preserved)"
Write-Host "    +-- agent4j.md       (project docs, preserved)"
Write-Host "    +-- bin/             (executables)"
Write-Host "    |   +-- agent4j-web.jar"
Write-Host "    |   +-- agent4j-web.ps1   (PowerShell launcher)"
Write-Host "    |   +-- agent4j-web.bat   (CMD launcher)"
Write-Host "    |   +-- agent4j-web       (Git Bash launcher)"
Write-Host "    |   +-- uninstall.ps1     (uninstall script)"
Write-Host ""
Write-Host "  API Endpoint:" -ForegroundColor Cyan
Write-Host "    http://localhost:8097"
Write-Host ""
Write-Host "  [Tip] To use agent4j-web immediately in current terminal:" -ForegroundColor Yellow
Write-Host "    PowerShell: `$env:Path = [Environment]::GetEnvironmentVariable('Path','User')"
Write-Host ""

# If not called from setup.ps1, wait for user input
if (-not $env:AGENT4J_SETUP) {
    Read-Host "Press Enter to exit"
}
