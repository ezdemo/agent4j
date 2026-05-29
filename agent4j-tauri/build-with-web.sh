#!/bin/bash
# 构建脚本：编译 agent4j-web 并复制到 Tauri 资源目录
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
TAURI_RESOURCES="$SCRIPT_DIR/src-tauri/resources"

echo "============================================"
echo "  Building Agent4j Web for Tauri"
echo "============================================"

# 1. 编译 agent4j-web
echo ""
echo "[1/3] Building agent4j-web..."
cd "$PROJECT_ROOT"
mvn clean package -pl agent4j-web -am -DskipTests -q

# 2. 复制分发包到 Tauri 资源目录
echo ""
echo "[2/3] Copying distribution to Tauri resources..."
mkdir -p "$TAURI_RESOURCES"

# 复制 tar.gz（Linux/macOS）
if [ -f "$PROJECT_ROOT/agent4j-web/target/agent4j-web-dist.tar.gz" ]; then
    cp "$PROJECT_ROOT/agent4j-web/target/agent4j-web-dist.tar.gz" "$TAURI_RESOURCES/"
    echo "  Copied agent4j-web-dist.tar.gz"
fi

# 复制 zip（Windows）
if [ -f "$PROJECT_ROOT/agent4j-web/target/agent4j-web-dist.zip" ]; then
    cp "$PROJECT_ROOT/agent4j-web/target/agent4j-web-dist.zip" "$TAURI_RESOURCES/"
    echo "  Copied agent4j-web-dist.zip"
fi

# 3. 完成
echo ""
echo "[3/3] Done!"
echo ""
echo "Resources directory contents:"
ls -la "$TAURI_RESOURCES"
