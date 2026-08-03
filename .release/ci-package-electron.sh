#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
FRONT_DIR="$ROOT_DIR/loopra-front"
NODE_VERSION="22.14.0"
NODE_DIR="$ROOT_DIR/.ci/node-v${NODE_VERSION}-linux-x64"
NODE_ARCHIVE="$ROOT_DIR/.ci/node-v${NODE_VERSION}-linux-x64.tar.xz"
PNPM_VERSION="10.24.0"

mkdir -p "$ROOT_DIR/.ci"

if [[ ! -x "$NODE_DIR/bin/node" ]]; then
  curl --fail --location --retry 3 \
    "https://npmmirror.com/mirrors/node/v${NODE_VERSION}/node-v${NODE_VERSION}-linux-x64.tar.xz" \
    --output "$NODE_ARCHIVE"
  tar -xJf "$NODE_ARCHIVE" -C "$ROOT_DIR/.ci"
fi

export PATH="$NODE_DIR/bin:$PATH"
export npm_config_registry="https://registry.npmmirror.com"
export ELECTRON_MIRROR="https://npmmirror.com/mirrors/electron/"
export ELECTRON_BUILDER_BINARIES_MIRROR="https://npmmirror.com/mirrors/electron-builder-binaries/"
corepack prepare "pnpm@${PNPM_VERSION}" --activate

run_as_root() {
  if [[ "$(id -u)" -eq 0 ]]; then
    "$@"
  elif command -v sudo >/dev/null 2>&1; then
    sudo "$@"
  else
    echo "Root privileges are required to install Wine." >&2
    return 1
  fi
}

install_wine() {
  if command -v wine >/dev/null 2>&1; then
    return
  fi

  echo "Wine is required for the Windows NSIS package; installing it..."
  if command -v yum >/dev/null 2>&1; then
    run_as_root yum install -y epel-release || true
    run_as_root yum install -y wine
  elif command -v dnf >/dev/null 2>&1; then
    run_as_root dnf install -y wine
  elif command -v apt-get >/dev/null 2>&1; then
    run_as_root apt-get update
    run_as_root env DEBIAN_FRONTEND=noninteractive apt-get install -y wine
  else
    echo "No supported package manager found; install Wine before running electron-builder." >&2
    return 1
  fi

  command -v wine >/dev/null 2>&1 || {
    echo "Wine installation completed without a usable wine command." >&2
    return 1
  }
}

install_wine

pushd "$FRONT_DIR" >/dev/null
rm -rf release
pnpm install --frozen-lockfile
pnpm build:vite
pnpm exec electron-builder --mac --win --linux --publish=never
test -n "$(find release -maxdepth 1 -type f -name '*.zip' -print -quit)"
test -n "$(find release -maxdepth 1 -type f -name '*.exe' -print -quit)"
test -n "$(find release -maxdepth 1 -type f -name '*.deb' -print -quit)"
find release -maxdepth 1 -type f \( -name '*.zip' -o -name '*.exe' -o -name '*.deb' \) -print
popd >/dev/null
