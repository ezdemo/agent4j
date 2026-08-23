#!/bin/bash
#
# Loopra Desktop Installer for macOS / Linux — command-line one-click install
# Usage: curl -fsSL https://raw.giteeusercontent.com/ezdemo/loopra/raw/main/.release/setup-desktop.sh | bash
#
# 行为：
#   1. 按平台下载当前版本安装包（macOS: Loopra-*.zip；Linux: loopra-front_*.deb），支持 LOOPRA_MIRROR
#   2. macOS：解压 → xattr -cr 放行 Gatekeeper → 安装到 /Applications/Loopra.app → 自动启动
#   3. Linux：apt 安装 .deb（应用菜单可启动）
#

set -euo pipefail

VERSION="v26.8.231"
VER="${VERSION#v}"
RELEASE_BASE="https://github.com/ezdemo/loopra/releases/download/${VERSION}"
TEMP_DIR="/tmp/loopra-desktop-install"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[0;33m'
NC='\033[0m'

info()  { echo -e "${GREEN}[INFO]${NC} $1"; }
warn()  { echo -e "${YELLOW}[WARN]${NC} $1"; }
error() { echo -e "${RED}[ERROR]${NC} $1"; }

cleanup() { rm -rf "$TEMP_DIR"; }
trap cleanup EXIT

mkdir -p "$TEMP_DIR"

# ---------- 平台识别 ----------
OS="$(uname -s)"
case "$OS" in
    Darwin) PLATFORM="mac" ;;
    Linux)  PLATFORM="linux" ;;
    *)
        error "Unsupported platform: $OS"
        echo "  Loopra Desktop currently supports macOS / Linux / Windows."
        exit 1
        ;;
esac

# ---------- 1. 解析安装包地址 ----------
if [ "$PLATFORM" = "mac" ]; then
    ASSET_PATTERN='\.zip$'
    ASSET_CANDIDATES="Loopra-${VER}-arm64-mac.zip Loopra-${VER}-x64-mac.zip Loopra-${VER}-mac.zip"
else
    ASSET_PATTERN='\.deb$'
    ASSET_CANDIDATES="loopra-front_${VER}_amd64.deb loopra-front_${VER}_arm64.deb"
fi

ASSET_NAME=""
ASSET_URL=""
for name in $ASSET_CANDIDATES; do
    url="${RELEASE_BASE}/${name}"
    if [ -n "${LOOPRA_MIRROR:-}" ]; then url="${LOOPRA_MIRROR%/}/${url}"; fi
    if curl -fsSI --max-time 20 "$url" >/dev/null 2>&1; then
        ASSET_NAME="$name"
        ASSET_URL="$url"
        break
    fi
done

# 回退：GitHub API 按 tag 解析资产名
if [ -z "$ASSET_NAME" ]; then
    info "Direct asset not found, resolving via GitHub API..."
    API_URL="https://api.github.com/repos/ezdemo/loopra/releases/tags/${VERSION}"
    if [ -n "${LOOPRA_MIRROR:-}" ]; then API_URL="${LOOPRA_MIRROR%/}/${API_URL}"; fi
    ASSET_NAME="$(curl -fsSL --max-time 30 "$API_URL" \
        | grep -oE '"name":"[^"]+"' \
        | sed 's/"name":"//; s/"$//' \
        | grep -E "$ASSET_PATTERN" | head -1 || true)"
    if [ -n "$ASSET_NAME" ]; then
        ASSET_URL="${RELEASE_BASE}/${ASSET_NAME}"
        if [ -n "${LOOPRA_MIRROR:-}" ]; then ASSET_URL="${LOOPRA_MIRROR%/}/${ASSET_URL}"; fi
    fi
fi

if [ -z "$ASSET_NAME" ]; then
    error "Cannot locate Loopra Desktop package for ${VERSION}."
    error "Please download manually: https://github.com/ezdemo/loopra/releases/latest"
    exit 1
fi

# ---------- 2. 下载 ----------
info "Downloading ${ASSET_NAME} ($VERSION)${LOOPRA_MIRROR:+ via mirror $LOOPRA_MIRROR}..."
TEMP_FILE="${TEMP_DIR}/${ASSET_NAME}"
if command -v curl &>/dev/null; then
    curl -fSL --retry 3 "$ASSET_URL" -o "$TEMP_FILE"
elif command -v wget &>/dev/null; then
    wget -q "$ASSET_URL" -O "$TEMP_FILE"
else
    error "curl or wget is required"
    exit 1
fi

# ---------- 3. 安装 ----------
if [ "$PLATFORM" = "mac" ]; then
    info "Extracting archive..."
    unzip -q "$TEMP_FILE" -d "$TEMP_DIR/app"
    APP_DIR="$(find "$TEMP_DIR/app" -maxdepth 2 -type d -name '*.app' | head -1)"
    if [ -z "$APP_DIR" ]; then
        error "Loopra.app not found in archive"
        exit 1
    fi
    # 放行 Gatekeeper（当前未签名/公证，首次打开会被拦截）
    xattr -cr "$APP_DIR" 2>/dev/null || true
    if [ -d "/Applications/Loopra.app" ]; then
        warn "Replacing existing /Applications/Loopra.app"
        rm -rf "/Applications/Loopra.app"
    fi
    mv "$APP_DIR" "/Applications/Loopra.app"
    info "Installed to /Applications/Loopra.app"
    open "/Applications/Loopra.app"
    echo ""
    info "Installation complete! Loopra is starting..."
elif [ "$PLATFORM" = "linux" ]; then
    info "Installing ${ASSET_NAME} with apt (sudo may prompt for password)..."
    if command -v apt-get &>/dev/null; then
        if [ "$(id -u)" -eq 0 ]; then
            apt-get install -y "$TEMP_FILE" || { dpkg -i "$TEMP_FILE"; apt-get -f install -y; }
        else
            sudo apt-get install -y "$TEMP_FILE" || { sudo dpkg -i "$TEMP_FILE"; sudo apt-get -f install -y; }
        fi
    else
        error "apt-get not found. Please install the .deb manually: ${ASSET_URL}"
        exit 1
    fi
    echo ""
    info "Installation complete! Find Loopra in your application menu."
fi