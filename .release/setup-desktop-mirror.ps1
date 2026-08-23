#
# Loopra Desktop Installer for Windows (GitHub Mirror)
# Usage: irm https://gh-proxy.org/https://raw.githubusercontent.com/ezdemo/loopra/main/.release/setup-desktop-mirror.ps1 | iex
#
# 与 setup-desktop.ps1 完全相同，仅默认走 gh-proxy.org 镜像（可用 LOOPRA_MIRROR 覆盖）。
# 薄壳脚本不维护版本号，始终从主脚本拉取逻辑，避免版本漂移。
#

if (-not $env:LOOPRA_MIRROR) { $env:LOOPRA_MIRROR = "https://gh-proxy.org/" }
$mainUrl = $env:LOOPRA_MIRROR.TrimEnd('/') + "/https://raw.githubusercontent.com/ezdemo/loopra/main/.release/setup-desktop.ps1"
iex (irm $mainUrl)