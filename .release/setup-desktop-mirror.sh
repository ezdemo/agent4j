#!/bin/bash
#
# Loopra Desktop Installer for macOS / Linux (GitHub Mirror)
# Usage: curl -fsSL https://gh-proxy.org/https://raw.githubusercontent.com/ezdemo/loopra/main/.release/setup-desktop-mirror.sh | bash
#
# 与 setup-desktop.sh 完全相同，仅默认走 gh-proxy.org 镜像（可用 LOOPRA_MIRROR 覆盖）。
# 薄壳脚本不维护版本号，始终从主脚本拉取逻辑，避免版本漂移。
#

set -euo pipefail

MIRROR="${LOOPRA_MIRROR:-https://gh-proxy.org/}"
export LOOPRA_MIRROR="$MIRROR"
curl -fsSL "${MIRROR%/}/https://raw.githubusercontent.com/ezdemo/loopra/main/.release/setup-desktop.sh" | bash