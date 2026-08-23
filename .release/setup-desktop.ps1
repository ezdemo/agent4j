#
# Loopra Desktop Installer for Windows — command-line one-click install
# Usage: irm https://raw.giteeusercontent.com/ezdemo/loopra/raw/main/.release/setup-desktop.ps1 | iex
#
# 行为：
#   1. 下载当前版本桌面端安装包（Loopra.Setup.*.exe，支持 LOOPRA_MIRROR 镜像）
#   2. NSIS 静默安装（/S）到 %LOCALAPPDATA%\Programs\Loopra（per-user，无需管理员）
#   3. 自动创建桌面/开始菜单快捷方式；卸载走系统"设置 → 应用"
#   4. 安装完成后自动启动 Loopra
# 可选：$env:LOOPRA_DESKTOP_DIR 指定安装目录（对应 NSIS /D，路径勿含空格）
#

$ErrorActionPreference = "Stop"

$VERSION = "v26.8.231"
$VER = $VERSION.TrimStart('v')
$MIRROR = $env:LOOPRA_MIRROR
$RELEASE_BASE = "https://github.com/ezdemo/loopra/releases/download/$VERSION"

function Write-Info {
    param([string]$Message)
    Write-Host "[INFO] " -ForegroundColor Green -NoNewline
    Write-Host $Message
}

function Write-Warn {
    param([string]$Message)
    Write-Host "[WARN] " -ForegroundColor Yellow -NoNewline
    Write-Host $Message
}

function Write-Err {
    param([string]$Message)
    Write-Host "[ERROR] " -ForegroundColor Red -NoNewline
    Write-Host $Message
}

$TEMP_DIR = Join-Path $env:TEMP "loopra-desktop-install"
if (Test-Path $TEMP_DIR) { Remove-Item -Recurse -Force $TEMP_DIR }
New-Item -ItemType Directory -Path $TEMP_DIR | Out-Null

try {
    # ---------- 1. 解析安装包地址 ----------
    # 候选文件名（按 electron-builder 产物命名，兼容带空格命名）
    $ASSET_NAME = $null
    $PACKAGE_URL = $null
    foreach ($name in @("Loopra.Setup.$VER.exe", "Loopra Setup $VER.exe")) {
        $url = "$RELEASE_BASE/$name"
        if ($MIRROR) { $url = $MIRROR.TrimEnd('/') + "/" + $url }
        try {
            $head = Invoke-WebRequest -Uri $url -Method Head -UseBasicParsing -TimeoutSec 20
            if ($head.StatusCode -eq 200) { $ASSET_NAME = $name; $PACKAGE_URL = $url; break }
        } catch { }
    }

    # 回退：GitHub API 按 tag 解析资产名
    if (-not $PACKAGE_URL) {
        Write-Info "Direct asset not found, resolving via GitHub API..."
        $apiUrl = "https://api.github.com/repos/ezdemo/loopra/releases/tags/$VERSION"
        if ($MIRROR) { $apiUrl = $MIRROR.TrimEnd('/') + "/" + $apiUrl }
        $release = Invoke-RestMethod -Uri $apiUrl -Headers @{ "User-Agent" = "Loopra/Installer" } -TimeoutSec 30
        $asset = $release.assets | Where-Object { $_.name -match '\.exe$' -and $_.name -notmatch 'portable' } | Select-Object -First 1
        if ($asset) {
            $ASSET_NAME = $asset.name
            $PACKAGE_URL = "$RELEASE_BASE/$ASSET_NAME"
            if ($MIRROR) { $PACKAGE_URL = $MIRROR.TrimEnd('/') + "/" + $PACKAGE_URL }
        }
    }

    if (-not $PACKAGE_URL) {
        Write-Err "Cannot locate Loopra Desktop installer for $VERSION."
        Write-Err "Please download manually: https://github.com/ezdemo/loopra/releases/latest"
        throw "Asset resolution failed"
    }

    # ---------- 2. 下载 ----------
    Write-Info "Downloading $ASSET_NAME ($VERSION)$(if ($MIRROR) { " via mirror $MIRROR" } else { '' })..."
    $installerFile = Join-Path $TEMP_DIR $ASSET_NAME
    Invoke-WebRequest -Uri $PACKAGE_URL -OutFile $installerFile -UseBasicParsing -TimeoutSec 900
    # 去掉 Mark-of-the-Web，避免 SmartScreen 拦截静默安装/首次启动
    Unblock-File -Path $installerFile

    # ---------- 3. 静默安装 ----------
    if (Get-Process -Name "Loopra" -ErrorAction SilentlyContinue) {
        Write-Warn "Loopra is running. Please close it first (the installer may fail while the app is running)."
    }
    Write-Info "Running silent installer..."
    $nsisArgs = "/S"
    $installDir = $env:LOOPRA_DESKTOP_DIR
    if ($installDir) {
        $nsisArgs += " /D=$installDir"
        Write-Info "Install directory: $installDir"
    }
    $proc = Start-Process -FilePath $installerFile -ArgumentList $nsisArgs -Wait -PassThru
    if ($proc.ExitCode -ne 0) {
        Write-Err "Installer failed with exit code: $($proc.ExitCode)"
        throw "Silent install failed"
    }

    # ---------- 4. 定位并启动 ----------
    $appExe = $null
    if ($installDir -and (Test-Path (Join-Path $installDir "Loopra.exe"))) {
        $appExe = Join-Path $installDir "Loopra.exe"
    }
    if (-not $appExe) {
        $found = Get-ChildItem -Path (Join-Path $env:LOCALAPPDATA "Programs") -Filter "Loopra.exe" -Recurse -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($found) { $appExe = $found.FullName }
    }

    Write-Host ""
    Write-Info "Desktop installation complete!"
    if ($appExe) {
        Write-Info "Starting Loopra..."
        Start-Process $appExe
    } else {
        Write-Warn "Loopra.exe not found at the expected location; start it from the Start Menu instead."
    }
    Write-Host ""
    Write-Host "  Install path:  " -NoNewline
    if ($appExe) { Write-Host (Split-Path $appExe -Parent) -ForegroundColor White }
    else { Write-Host "$env:LOCALAPPDATA\Programs\Loopra" -ForegroundColor White }
    Write-Host "  Uninstall:     系统设置 → 应用 → Loopra" -ForegroundColor White
    Write-Host ""
} catch {
    Write-Err $_.Exception.Message
    throw $_
} finally {
    Remove-Item -Recurse -Force $TEMP_DIR -ErrorAction SilentlyContinue
}