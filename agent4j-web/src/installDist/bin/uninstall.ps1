#
# Agent4j Uninstaller for Windows PowerShell
# 完全卸载 Agent4j Web，包括配置目录
#

$ErrorActionPreference = "Stop"

Write-Host ""
Write-Host "============================================" -ForegroundColor Cyan
Write-Host "   Agent4j Uninstaller (PowerShell)" -ForegroundColor Cyan
Write-Host "============================================" -ForegroundColor Cyan
Write-Host ""

# 检测管理员权限
$IS_ADMIN = ([Security.Principal.WindowsPrincipal] [Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)

if ($IS_ADMIN) {
    Write-Host "[Info] Running with Administrator privileges" -ForegroundColor Yellow
} else {
    Write-Host "[Info] Running without Administrator privileges" -ForegroundColor Yellow
}

# 安装目录
$INSTALL_DIR = Join-Path $env:USERPROFILE ".agent4j"

# 检查是否已安装
if (-not (Test-Path $INSTALL_DIR)) {
    Write-Host ""
    Write-Host "[Info] Agent4j is not installed." -ForegroundColor Yellow
    Write-Host "       Directory not found: $INSTALL_DIR" -ForegroundColor Gray
    Read-Host "Press Enter to exit"
    exit 0
}

Write-Host ""
Write-Host "This will remove Agent4j completely:" -ForegroundColor White
Write-Host "  - Executables and configuration"
Write-Host "  - Sessions and memory data"
Write-Host "  - Skills modules"
Write-Host "  - PATH configuration"
Write-Host ""

$CONFIRM = Read-Host "Continue? (Y/N)"
if ($CONFIRM -ne "Y" -and $CONFIRM -ne "y") {
    Write-Host "Cancelled." -ForegroundColor Yellow
    Read-Host "Press Enter to exit"
    exit 0
}

# ============================================
#  [1/4] 从 PATH 中移除
# ============================================
Write-Host ""
Write-Host "[1/4] Removing from PATH..." -ForegroundColor Yellow

# 从用户 PATH 移除
$USER_PATH = [Environment]::GetEnvironmentVariable("Path", "User")
if ($USER_PATH) {
    $NEW_PATH = ($USER_PATH -split ';' | Where-Object { $_ -notmatch 'agent4j' }) -join ';'
    $NEW_PATH = $NEW_PATH.TrimStart(';').TrimEnd(';')
    [Environment]::SetEnvironmentVariable("Path", $NEW_PATH, "User")
    Write-Host "      Cleaned User PATH" -ForegroundColor Gray
}

# 从系统 PATH 移除（如果是管理员）
if ($IS_ADMIN) {
    $MACHINE_PATH = [Environment]::GetEnvironmentVariable("Path", "Machine")
    if ($MACHINE_PATH) {
        $NEW_PATH = ($MACHINE_PATH -split ';' | Where-Object { $_ -notmatch 'agent4j' }) -join ';'
        $NEW_PATH = $NEW_PATH.TrimStart(';').TrimEnd(';')
        [Environment]::SetEnvironmentVariable("Path", $NEW_PATH, "Machine")
        Write-Host "      Cleaned System PATH" -ForegroundColor Gray
    }
}

# ============================================
#  [2/4] 删除符号链接（如果有）
# ============================================
Write-Host ""
Write-Host "[2/4] Removing command symlinks..." -ForegroundColor Yellow

# 检查 ProgramData 目录
$PROGRAM_DATA_DIR = "C:\ProgramData\agent4j"
if (Test-Path $PROGRAM_DATA_DIR) {
    try {
        Remove-Item -Path $PROGRAM_DATA_DIR -Recurse -Force -ErrorAction Stop
        Write-Host "      Removed $PROGRAM_DATA_DIR" -ForegroundColor Green
    } catch {
        Write-Host "      [Note] Could not remove $PROGRAM_DATA_DIR (need admin)" -ForegroundColor Yellow
    }
} else {
    Write-Host "      No ProgramData launcher found" -ForegroundColor Gray
}

# ============================================
#  [3/4] 询问是否保留配置
# ============================================
Write-Host ""
Write-Host "[3/4] Configuration files..." -ForegroundColor Yellow

$KEEP_CONFIG = Read-Host "Keep configuration files (config.json, sessions, memory)? (Y/N)"
$KEEP_CONFIG_BOOL = ($KEEP_CONFIG -eq "Y" -or $KEEP_CONFIG -eq "y")

if ($KEEP_CONFIG_BOOL) {
    Write-Host "      Configuration files will be preserved" -ForegroundColor Gray
} else {
    Write-Host "      Configuration files will be removed" -ForegroundColor Gray
}

# ============================================
#  [4/4] 删除安装目录
# ============================================
Write-Host ""
Write-Host "[4/4] Removing installation directory..." -ForegroundColor Yellow

if (Test-Path $INSTALL_DIR) {
    if ($KEEP_CONFIG_BOOL) {
        # 只删除 bin 目录
        $BIN_DIR = Join-Path $INSTALL_DIR "bin"
        if (Test-Path $BIN_DIR) {
            Remove-Item -Path $BIN_DIR -Recurse -Force
            Write-Host "      Removed bin/ directory" -ForegroundColor Green
        }
        Write-Host "      Preserved config.json, sessions/, memory/" -ForegroundColor Gray
    } else {
        # 删除整个目录
        try {
            Remove-Item -Path $INSTALL_DIR -Recurse -Force -ErrorAction Stop
            Write-Host "      Removed: $INSTALL_DIR" -ForegroundColor Green
        } catch {
            Write-Host "      [Warning] Could not remove $INSTALL_DIR" -ForegroundColor Yellow
            Write-Host "      Some files may be in use. Please restart and try again." -ForegroundColor Yellow
        }
    }
} else {
    Write-Host "      Directory already removed" -ForegroundColor Gray
}

# ============================================
#  完成
# ============================================
Write-Host ""
Write-Host "============================================" -ForegroundColor Green
Write-Host "   Uninstall Complete!" -ForegroundColor Green
Write-Host "============================================" -ForegroundColor Green
Write-Host ""

if ($KEEP_CONFIG_BOOL) {
    Write-Host "  Agent4j has been removed." -ForegroundColor White
    Write-Host "  Configuration files preserved at: $INSTALL_DIR" -ForegroundColor White
} else {
    Write-Host "  Agent4j has been fully removed." -ForegroundColor White
}

Write-Host ""
Write-Host "  [Note] Please restart your terminal for" -ForegroundColor Yellow
Write-Host "         PATH changes to take effect." -ForegroundColor Yellow
Write-Host ""

Read-Host "Press Enter to exit"
