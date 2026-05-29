#
# 构建脚本：编译 agent4j-web 并复制到 Tauri 资源目录
#
$ErrorActionPreference = "Stop"

$SCRIPT_DIR = Split-Path -Parent $MyInvocation.MyCommand.Definition
$PROJECT_ROOT = Split-Path -Parent (Split-Path -Parent $SCRIPT_DIR)
$TAURI_RESOURCES = Join-Path $SCRIPT_DIR "src-tauri\resources"

Write-Host ""
Write-Host "============================================" -ForegroundColor Cyan
Write-Host "  Building Agent4j Web for Tauri" -ForegroundColor Cyan
Write-Host "============================================" -ForegroundColor Cyan

# 1. 编译 agent4j-web
Write-Host ""
Write-Host "[1/3] Building agent4j-web..." -ForegroundColor Yellow
Set-Location $PROJECT_ROOT
& mvn clean package -pl agent4j-web -am -DskipTests -q

if ($LASTEXITCODE -ne 0) {
    Write-Host "[Error] Maven build failed" -ForegroundColor Red
    exit 1
}

# 2. 复制分发包到 Tauri 资源目录
Write-Host ""
Write-Host "[2/3] Copying distribution to Tauri resources..." -ForegroundColor Yellow

if (-not (Test-Path $TAURI_RESOURCES)) {
    New-Item -ItemType Directory -Path $TAURI_RESOURCES | Out-Null
}

# 复制 zip（Windows）
$ZIP_FILE = Join-Path $PROJECT_ROOT "agent4j-web\target\agent4j-web-dist.zip"
if (Test-Path $ZIP_FILE) {
    Copy-Item $ZIP_FILE $TAURI_RESOURCES -Force
    Write-Host "  Copied agent4j-web-dist.zip" -ForegroundColor Gray
}

# 复制 tar.gz（Linux/macOS）
$TAR_FILE = Join-Path $PROJECT_ROOT "agent4j-web\target\agent4j-web-dist.tar.gz"
if (Test-Path $TAR_FILE) {
    Copy-Item $TAR_FILE $TAURI_RESOURCES -Force
    Write-Host "  Copied agent4j-web-dist.tar.gz" -ForegroundColor Gray
}

# 3. 完成
Write-Host ""
Write-Host "[3/3] Done!" -ForegroundColor Green
Write-Host ""
Write-Host "Resources directory contents:" -ForegroundColor Cyan
Get-ChildItem $TAURI_RESOURCES | Format-Table Name, Length, LastWriteTime
