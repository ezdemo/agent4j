#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
FRONT_DIR="$ROOT_DIR/loopra-front"
DIST_DIR="$FRONT_DIR/dist/renderer"
STATIC_DIR="$ROOT_DIR/loopra/src/main/resources/static"
NODE_VERSION="22.14.0"
NODE_DIR="$ROOT_DIR/.ci/node-v${NODE_VERSION}-linux-x64"
NODE_ARCHIVE="$ROOT_DIR/.ci/node-v${NODE_VERSION}-linux-x64.tar.xz"

mkdir -p "$ROOT_DIR/.ci"

if [[ ! -x "$NODE_DIR/bin/node" ]]; then
  curl --fail --location --retry 3 \
    "https://npmmirror.com/mirrors/node/v${NODE_VERSION}/node-v${NODE_VERSION}-linux-x64.tar.xz" \
    --output "$NODE_ARCHIVE"
  tar -xJf "$NODE_ARCHIVE" -C "$ROOT_DIR/.ci"
fi

export PATH="$NODE_DIR/bin:$PATH"
corepack prepare pnpm@10.24.0 --activate

pushd "$FRONT_DIR" >/dev/null
pnpm install --frozen-lockfile
pnpm build
popd >/dev/null

[[ -d "$DIST_DIR" ]] || { echo "Frontend build output not found: $DIST_DIR" >&2; exit 1; }
mkdir -p "$STATIC_DIR"

find "$STATIC_DIR" -mindepth 1 -maxdepth 1 ! -name config.json -exec rm -rf {} +
find "$DIST_DIR" -mindepth 1 -maxdepth 1 ! -name config.json -exec cp -a {} "$STATIC_DIR"/ \;
