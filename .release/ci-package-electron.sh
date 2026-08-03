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
corepack prepare "pnpm@${PNPM_VERSION}" --activate

pushd "$FRONT_DIR" >/dev/null
rm -rf release
pnpm install --frozen-lockfile
pnpm build:vite
pnpm exec electron-builder --linux --publish=never
test -n "$(find release -maxdepth 1 -type f -name '*.deb' -print -quit)"
popd >/dev/null
