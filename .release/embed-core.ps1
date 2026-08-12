# 将核心分发包（loopra-web-dist.tar.gz）解压到 loopra-front/resources/loopra-core，
# 供 electron-builder 的 extraResources 嵌入前端安装包（Windows 本地打包用）。
# 用法：powershell -ExecutionPolicy Bypass -File .release/embed-core.ps1 [-Tarball <path>]
param(
    [string]$Tarball = (Join-Path $PSScriptRoot "..\loopra-web\target\loopra-web-dist.tar.gz")
)

$ErrorActionPreference = "Stop"

$root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$dest = Join-Path $root "loopra-front\resources\loopra-core"

if (-not (Test-Path $Tarball)) {
    Write-Host "[ERROR] 未找到核心分发包: $Tarball" -ForegroundColor Red
    Write-Host "        请先构建 loopra-web：mvn -B -ntp -DskipTests -pl loopra-web package" -ForegroundColor Yellow
    exit 1
}

if (Test-Path $dest) { Remove-Item -Recurse -Force $dest }
New-Item -ItemType Directory -Path $dest -Force | Out-Null

tar -xzf $Tarball -C $dest

Write-Host "核心运行时已嵌入: $dest" -ForegroundColor Green
Get-ChildItem -Path $dest -Recurse -File | ForEach-Object { Write-Host "  $($_.FullName)" }
