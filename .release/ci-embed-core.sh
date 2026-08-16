#!/usr/bin/env bash
# 将核心分发包（loopra-dist.tar.gz）解压到 loopra-front/resources/loopra-core，
# 供 electron-builder 的 extraResources 嵌入前端安装包（CI 与本地 bash 环境通用）。
# 用法：
#   bash .release/ci-embed-core.sh [tarball]
# 默认使用 loopra/target/loopra-dist.tar.gz（本地 mvn package 产物）；
# CI 中传入 actions/download-artifact 下载的核心包路径。
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TARBALL="${1:-$ROOT_DIR/loopra/target/loopra-dist.tar.gz}"
DEST="$ROOT_DIR/loopra-front/resources/loopra-core"

if [[ ! -f "$TARBALL" ]]; then
  echo "[ERROR] 未找到核心分发包: $TARBALL" >&2
  echo "        请先构建 loopra：mvn -B -ntp -DskipTests -pl loopra package" >&2
  exit 1
fi

rm -rf "$DEST"
mkdir -p "$DEST"
tar -xzf "$TARBALL" -C "$DEST"

echo "核心运行时已嵌入: $DEST"
find "$DEST" -maxdepth 2 -type f | sort
