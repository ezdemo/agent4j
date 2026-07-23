#!/bin/bash
# =============================================
#  Loopra Web Installer (Linux / macOS)
#  支持重复安装，保留已有 config.json
#  自动下载 JRE 25（无需系统 Java）
#  兼容 bash, zsh, sh 等多种 shell
# =============================================

set -e

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[0;33m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

echo ""
echo "============================================"
echo -e "   Loopra Web Installer"
echo "============================================"
echo ""

# =============================================
# 源目录（脚本所在目录）
# =============================================
SOURCE_DIR="$(cd "$(dirname "$0")" && pwd)"

# 目标目录
TARGET_DIR="$HOME/.loopra"
TARGET_BIN_DIR="$TARGET_DIR/bin"
JRE25_DIR="$TARGET_DIR/jre25"
JRE25_JAVA="$JRE25_DIR/bin/java"

# 源目录
SOURCE_BIN_DIR="$SOURCE_DIR/bin"
SOURCE_CONFIG="$SOURCE_DIR/config.json"
SOURCE_AGENTS="$SOURCE_DIR/loopra.md"

# =============================================
# 检测 OS / ARCH（与 Adoptium API 对齐）
# =============================================
detect_os() {
    case "$(uname -s)" in
        Darwin)  echo "mac" ;;
        Linux)   echo "linux" ;;
        *)       echo "linux" ;;  # fallback
    esac
}

detect_arch() {
    case "$(uname -m)" in
        x86_64|amd64) echo "x64" ;;
        aarch64|arm64) echo "aarch64" ;;
        *)             echo "x64" ;;  # fallback
    esac
}

# =============================================
# 下载并安装 JRE 25 到 ~/.loopra/jre25/
# 参考 lib.rs: Adoptium API → 清华镜像
# =============================================
download_jre25() {
    local os=$(detect_os)
    local arch=$(detect_arch)

    echo -e "${YELLOW}[JRE 25]${NC} Downloading JRE 25 for ${os}/${arch}..."

    # 1. 从 Adoptium API 获取最新包名
    local api_url="https://api.adoptium.net/v3/assets/feature_releases/25/ga?architecture=${arch}&image_type=jre&os=${os}&page_size=1"
    echo -e "      Querying Adoptium API..."

    local package_name=""
    if command -v curl &> /dev/null; then
        # 匹配 name 中含 jre 且以 .tar.gz 结尾的字段（跳过 source 和 .pkg）
        package_name=$(curl -fsSL "$api_url" 2>/dev/null | grep -o '"name": *"[^"]*jre[^"]*\.tar\.gz"' | cut -d'"' -f4 | tr -d '\r' | head -1)
    elif command -v wget &> /dev/null; then
        package_name=$(wget -qO- "$api_url" 2>/dev/null | grep -o '"name": *"[^"]*jre[^"]*\.tar\.gz"' | cut -d'"' -f4 | tr -d '\r' | head -1)
    fi

    # 2. 兜底文件名（API 不可用时）
    if [ -z "$package_name" ]; then
        local ext
        case "$os" in
            mac)     ext="tar.gz" ;;
            windows) ext="zip"    ;;
            *)       ext="tar.gz" ;;
        esac
        package_name="OpenJDK25U-jre_${arch}_${os}_hotspot_25.0.3_9.${ext}"
        echo -e "      ${YELLOW}API unavailable, using fallback name: ${package_name}${NC}"
    else
        echo -e "      Latest package: ${package_name}"
    fi

    # 3. 从清华镜像下载
    local download_url="https://mirrors.tuna.tsinghua.edu.cn/Adoptium/25/jre/${arch}/${os}/${package_name}"
    local tmp_dir="$TARGET_DIR/.tmp-jre"
    local archive_path="$tmp_dir/$package_name"

    rm -rf "$tmp_dir"
    mkdir -p "$tmp_dir"

    echo -e "      Downloading from Tsinghua mirror..."
    if command -v curl &> /dev/null; then
        curl -fSL --progress-bar "$download_url" -o "$archive_path"
    else
        wget -q --show-progress "$download_url" -O "$archive_path"
    fi

    # 4. 解压
    echo -e "      Extracting..."
    local extract_dir="$tmp_dir/extract"
    mkdir -p "$extract_dir"

    case "$package_name" in
        *.tar.gz|*.tgz)
            tar -xzf "$archive_path" -C "$extract_dir" ;;
        *.zip)
            if command -v unzip &> /dev/null; then
                unzip -q "$archive_path" -d "$extract_dir"
            else
                echo -e "${RED}[ERROR]${NC} unzip is required to extract .zip files"
                exit 1
            fi
            ;;
        *)
            echo -e "${RED}[ERROR]${NC} Unknown archive format: $package_name"
            exit 1
            ;;
    esac

    # 5. 找到 JRE 顶层目录（解压后唯一的子目录）
    local jre_subdir=$(ls -d "$extract_dir"/*/ 2>/dev/null | head -1)
    if [ -z "$jre_subdir" ]; then
        # 可能直接解压到了 extract_dir 根目录
        jre_subdir="$extract_dir"
    fi

    # 6. 移动到最终位置
    if [ -d "$JRE25_DIR" ]; then
        rm -rf "$JRE25_DIR"
    fi
    mkdir -p "$(dirname "$JRE25_DIR")"
    mv "$jre_subdir" "$JRE25_DIR"

    # macOS: JRE tar.gz 解出来是 Contents/Home/bin/java 而不是 bin/java
    # 把 Contents/Home 的内容提到 jre25/ 根目录，和 Linux 保持一致
    if [ ! -f "$JRE25_JAVA" ] && [ -d "$JRE25_DIR/Contents/Home" ]; then
        local mac_tmp="$tmp_dir/mac-flatten"
        mkdir -p "$mac_tmp"
        mv "$JRE25_DIR/Contents/Home/"* "$mac_tmp/" 2>/dev/null || true
        mv "$JRE25_DIR/Contents/Home/".* "$mac_tmp/" 2>/dev/null || true
        rm -rf "$JRE25_DIR"
        mv "$mac_tmp" "$JRE25_DIR"
    fi

    # 7. 清理
    rm -rf "$tmp_dir"

    # 8. 验证
    if [ ! -f "$JRE25_JAVA" ]; then
        echo -e "${RED}[ERROR]${NC} JRE 25 installation failed: java binary not found at $JRE25_JAVA"
        exit 1
    fi

    local jre_ver=$("$JRE25_JAVA" -version 2>&1 | head -1)
    echo -e "      ${GREEN}JRE 25 installed: ${jre_ver}${NC}"
}

# =============================================
# 确保 JRE 25 可用
# =============================================
ensure_java() {
    if [ -f "$JRE25_JAVA" ]; then
        local ver=$("$JRE25_JAVA" -version 2>&1 | head -1)
        echo -e "${GREEN}[Pre-check]${NC} Bundled JRE 25 found: ${ver}"
        return 0
    fi

    echo -e "${YELLOW}[Pre-check]${NC} JRE 25 not found, will download automatically..."
    echo ""
    download_jre25
    echo ""
}

# =============================================
# 执行 Pre-check
# =============================================
ensure_java

# =============================================
# 检查源目录是否存在
# =============================================
if [ ! -d "$SOURCE_BIN_DIR" ]; then
    echo "[Error] Source bin directory not found: $SOURCE_BIN_DIR"
    exit 1
fi

# =============================================
# [1/5] 检查并备份已有的 config.json 和 loopra.md
# =============================================
echo "[1/5] Checking for existing configuration..."
CONFIG_BACKUP=""
AGENTS_BACKUP=""
TARGET_CONFIG="$TARGET_DIR/config.json"
TARGET_AGENTS="$TARGET_DIR/loopra.md"

# 备份现有的配置文件
if [ -f "$TARGET_CONFIG" ]; then
    CONFIG_BACKUP=$(mktemp)
    cp "$TARGET_CONFIG" "$CONFIG_BACKUP"
    echo "      Found existing config.json (will be preserved)"
else
    echo "      No existing config.json found"
fi

if [ -f "$TARGET_AGENTS" ]; then
    AGENTS_BACKUP=$(mktemp)
    cp "$TARGET_AGENTS" "$AGENTS_BACKUP"
    echo "      Found existing loopra.md (will be preserved)"
else
    echo "      No existing loopra.md found"
fi

# =============================================
# [2/5] 创建目标目录结构
# =============================================
echo ""
echo "[2/5] Preparing target directory: $TARGET_DIR"

mkdir -p "$TARGET_DIR"
mkdir -p "$TARGET_BIN_DIR"

echo "      Created directory structure"

# =============================================
# [3/5] 复制文件
# =============================================
echo ""
echo "[3/5] Copying files..."

# 复制 bin 目录内容
cp -R "$SOURCE_BIN_DIR/"* "$TARGET_BIN_DIR/" 2>/dev/null || true
echo "      Copied bin/ directory"

# 复制 config.json（从根目录）
if [ -f "$SOURCE_CONFIG" ]; then
    cp "$SOURCE_CONFIG" "$TARGET_CONFIG" 2>/dev/null || true
    echo "      Copied config.json"
fi

# 复制 loopra.md（从根目录）
if [ -f "$SOURCE_AGENTS" ]; then
    cp "$SOURCE_AGENTS" "$TARGET_AGENTS" 2>/dev/null || true
    echo "      Copied loopra.md"
fi

# =============================================
# [4/5] 恢复 config.json 和 loopra.md（如果之前存在）
# =============================================
echo ""
echo "[4/5] Finalizing installation..."

if [ -n "$CONFIG_BACKUP" ]; then
    cp "$CONFIG_BACKUP" "$TARGET_CONFIG"
    rm -f "$CONFIG_BACKUP"
    echo "      Preserved existing config.json"
fi

if [ -n "$AGENTS_BACKUP" ]; then
    cp "$AGENTS_BACKUP" "$TARGET_AGENTS"
    rm -f "$AGENTS_BACKUP"
    echo "      Preserved existing loopra.md"
fi

# 检查 jar 文件是否存在
if [ ! -f "$TARGET_BIN_DIR/loopra-web.jar" ]; then
    echo "[Error] loopra-web.jar not found in $TARGET_BIN_DIR"
    exit 1
fi
echo "      Found loopra-web.jar"

# =============================================
# [5/5] 创建 loopra 命令脚本（使用捆绑 JRE 25）
# =============================================
echo ""
echo "[5/5] Creating 'loopra' command..."

cat > "$TARGET_BIN_DIR/loopra" << 'LAUNCHER_EOF'
#!/bin/bash
# Loopra Launcher — uses bundled JRE 25

# 获取脚本真实路径（兼容软链接）
SCRIPT_PATH="$0"
while [ -L "$SCRIPT_PATH" ]; do
    SCRIPT_DIR="$(cd "$(dirname "$SCRIPT_PATH")" && pwd)"
    SCRIPT_PATH="$(readlink "$SCRIPT_PATH")"
    case "$SCRIPT_PATH" in
        /*) ;;
        *)  SCRIPT_PATH="$SCRIPT_DIR/$SCRIPT_PATH" ;;
    esac
done
SCRIPT_DIR="$(cd "$(dirname "$SCRIPT_PATH")" && pwd)"

# 捆绑的 JRE 25 路径
LOOPRA_HOME="$(cd "$SCRIPT_DIR/.." && pwd)"
JAVA_BIN="$LOOPRA_HOME/jre25/bin/java"

# macOS: JRE 有时在 Contents/Home 下
if [ ! -f "$JAVA_BIN" ] && [ -f "$LOOPRA_HOME/jre25/Contents/Home/bin/java" ]; then
    JAVA_BIN="$LOOPRA_HOME/jre25/Contents/Home/bin/java"
fi

# 如果捆绑 JRE 不存在，回退到系统 Java
if [ ! -f "$JAVA_BIN" ]; then
    if command -v java &> /dev/null; then
        JAVA_BIN="java"
    else
        echo "[ERROR] No Java found."
        echo "  Expected bundled JRE at: $LOOPRA_HOME/jre25/bin/java"
        echo "  Please re-run the installer to download JRE 25."
        exit 1
    fi
fi

# 显示帮助（无参数 或 -h/--help/help）
if [ $# -eq 0 ] || [ "$1" = "-h" ] || [ "$1" = "--help" ] || [ "$1" = "help" ]; then
    echo "Loopra — AI Coding Assistant"
    echo ""
    echo "Usage:"
    echo "  loopra web [port]    Start the web server"
    echo "  loopra acp [wsPort]  Start with ACP protocol support"
    echo ""
    echo "Options:"
    echo "  port    0 = random port, 8097 = default, or any port number"
    echo "  wsPort  ACP WebSocket port (omit for stdio mode)"
    echo ""
    echo "Examples:"
    echo "  loopra web           Start on default port (8097)"
    echo "  loopra web 0         Start on a random available port"
    echo "  loopra acp           Start ACP stdio + Web random"
    echo "  loopra acp 8765      Start ACP WebSocket:8765 + Web random"
    echo ""
    echo "  loopra -h            Show this help"
    echo "  loopra -h            Show this help"
    exit 0
fi

# 检测 Java 版本，如果是 21+ 则添加 --enable-native-access 参数
JAVA_VER=$("$JAVA_BIN" -version 2>&1 | head -n1 | grep -oE '"[0-9]+' | grep -oE '[0-9]+' | head -1)
if [ -z "$JAVA_VER" ]; then
    JAVA_VER=$("$JAVA_BIN" -version 2>&1 | head -n1 | cut -d'"' -f2 | cut -d'.' -f1)
fi
JAVA_OPTS="-Dfile.encoding=UTF-8"
if [ -n "$JAVA_VER" ] && [ "$JAVA_VER" -ge 21 ]; then
    JAVA_OPTS="$JAVA_OPTS --enable-native-access=ALL-UNNAMED"
fi

# 解析子命令
PASSTHROUGH_ARGS=()
while [ $# -gt 0 ]; do
    case "$1" in
        web)
            PASSTHROUGH_ARGS+=("--solon.logging.appender.console.enable=false")
            shift
            if [ $# -gt 0 ] && echo "$1" | grep -qE '^[0-9]+$'; then
                if [ "$1" = "0" ]; then
                    PORT=$(( RANDOM % 55536 + 10000 ))
                    echo "Random port: $PORT"
                    PASSTHROUGH_ARGS+=("--server.port=$PORT")
                else
                    PASSTHROUGH_ARGS+=("--server.port=$1")
                fi
                shift
            fi
            ;;
        acp)
            # ACP 模式：Web 随机端口 + ACP (stdio/WebSocket)
            PASSTHROUGH_ARGS+=("--solon.logging.appender.console.enable=false")
            # Web UI 始终随机端口
            PORT=$(( RANDOM % 55536 + 10000 ))
            echo "Web random port: $PORT"
            PASSTHROUGH_ARGS+=("--server.port=$PORT")
            # ACP 标志
            PASSTHROUGH_ARGS+=("--loopra.acp=true")
            shift
            # 可选参数：ACP WebSocket 端口（不传则 stdio 模式）
            if [ $# -gt 0 ] && echo "$1" | grep -qE '^[0-9]+$'; then
                echo "ACP WebSocket port: $1"
                PASSTHROUGH_ARGS+=("--loopra.acp.ws.port=$1")
                shift
            fi
            ;;
        *)
            PASSTHROUGH_ARGS+=("$1")
            shift
            ;;
    esac
done

# Git Bash / MSYS terminals on Windows often need winpty
if [ -n "$MSYSTEM" ]; then
    JAVA_OPTS="$JAVA_OPTS -Djline.terminal.type=xterm-256color"
    if [ -t 0 ] && [ -t 1 ] && command -v winpty >/dev/null 2>&1; then
        exec winpty "$JAVA_BIN" $JAVA_OPTS -jar "$SCRIPT_DIR/loopra-web.jar" "${PASSTHROUGH_ARGS[@]}"
    fi
fi

"$JAVA_BIN" $JAVA_OPTS -jar "$SCRIPT_DIR/loopra-web.jar" "${PASSTHROUGH_ARGS[@]}"
LAUNCHER_EOF

chmod +x "$TARGET_BIN_DIR/loopra"
echo "      Created: $TARGET_BIN_DIR/loopra"

# =============================================
# 配置 PATH 环境变量（兼容多种 shell 和系统）
# =============================================
echo ""
echo "Configuring PATH..."

# 要添加的 PATH 配置
PATH_LINE='export PATH="$PATH:$HOME/.loopra/bin"'
PATH_MARKER='# Loopra Web'

# 检测当前用户默认 shell
USER_SHELL=$(basename "$SHELL" 2>/dev/null || echo "unknown")

# 定义需要配置的 shell 配置文件（按优先级排序）
declare -a CONFIG_FILES=()

case "$USER_SHELL" in
    zsh)
        CONFIG_FILES+=("$HOME/.zshrc")
        ;;
    bash)
        if [[ "$(uname -s)" == "Darwin" ]]; then
            CONFIG_FILES+=("$HOME/.bash_profile")
            CONFIG_FILES+=("$HOME/.bashrc")
        else
            CONFIG_FILES+=("$HOME/.bashrc")
            CONFIG_FILES+=("$HOME/.bash_profile")
        fi
        ;;
    fish)
        CONFIG_FILES+=("$HOME/.config/fish/config.fish")
        PATH_LINE='set -gx PATH $PATH $HOME/.loopra/bin'
        ;;
    *)
        CONFIG_FILES+=("$HOME/.profile")
        CONFIG_FILES+=("$HOME/.bashrc")
        CONFIG_FILES+=("$HOME/.zshrc")
        ;;
esac

# 写入配置文件
CONFIG_UPDATED=false
for CONFIG_FILE in "${CONFIG_FILES[@]}"; do
    if [[ "$USER_SHELL" == "fish" && "$CONFIG_FILE" == *".fish" ]]; then
        PATH_LINE='set -gx PATH $PATH $HOME/.loopra/bin'
    else
        PATH_LINE='export PATH="$PATH:$HOME/.loopra/bin"'
    fi

    if [ -f "$CONFIG_FILE" ]; then
        if grep -qF '.loopra/bin' "$CONFIG_FILE" 2>/dev/null; then
            echo "      PATH already configured in $(basename "$CONFIG_FILE")"
            CONFIG_UPDATED=true
            continue
        fi
    fi

    CONFIG_DIR=$(dirname "$CONFIG_FILE")
    if [ ! -d "$CONFIG_DIR" ]; then
        mkdir -p "$CONFIG_DIR" 2>/dev/null || continue
    fi

    echo "" >> "$CONFIG_FILE" 2>/dev/null || continue
    echo "$PATH_MARKER" >> "$CONFIG_FILE" 2>/dev/null || continue
    echo "$PATH_LINE" >> "$CONFIG_FILE" 2>/dev/null || continue
    echo "      Added to PATH in $(basename "$CONFIG_FILE")"
    CONFIG_UPDATED=true
done

# =============================================
# 尝试创建软链接到 /usr/local/bin（可选）
# =============================================
SYMLINK_CREATED=false
if [ ! -e "/usr/local/bin/loopra" ]; then
    if [ -w "/usr/local/bin" ] 2>/dev/null; then
        ln -sf "$TARGET_BIN_DIR/loopra" /usr/local/bin/loopra 2>/dev/null && SYMLINK_CREATED=true
    elif command -v sudo >/dev/null 2>&1; then
        if sudo -n true 2>/dev/null; then
            sudo ln -sf "$TARGET_BIN_DIR/loopra" /usr/local/bin/loopra 2>/dev/null && SYMLINK_CREATED=true
        fi
    fi
fi

if [ "$SYMLINK_CREATED" = true ]; then
    echo "      Created symlink: /usr/local/bin/loopra"
fi

# =============================================
# 完成
# =============================================
echo ""
echo -e "${GREEN}============================================${NC}"
echo -e "${GREEN}   Installation Complete!${NC}"
echo -e "${GREEN}============================================${NC}"
echo ""
echo "  Install path: $TARGET_DIR"
echo "  JRE path:     $JRE25_DIR"
echo ""

if [ "$SYMLINK_CREATED" = true ]; then
    echo -e "  ${CYAN}Symlink created: /usr/local/bin/loopra${NC}"
    echo -e "  You can run ${CYAN}loopra web${NC} directly now!"
else
    echo -e "  ${CYAN}Usage:${NC}"
    echo "    1. Run: source ~/.${USER_SHELL}rc"
    echo "    2. Or restart your terminal"
    echo "    3. Then run: 'loopra web'         (default port 8097)"
    echo "                'loopra web 0'       (random port)"
    echo "                'loopra web 9636'    (specify port 9636)"
fi

echo ""
echo -e "  ${CYAN}Directory structure:${NC}"
echo "    ~/.loopra/"
echo "    ├── config.json      (configuration, preserved if exists)"
echo "    ├── loopra.md       (project docs, preserved if exists)"
echo "    ├── jre25/           (bundled JRE 25)"
echo "    │   ├── bin/java"
echo "    │   └── ..."
echo "    ├── bin/             (executables)"
echo "    │   ├── loopra-web.jar"
echo "    │   ├── loopra           (launcher)"
echo "    │   └── uninstall.sh      (uninstall script)"
echo ""
echo -e "  ${CYAN}API Endpoint:${NC}"
echo "    http://localhost:8097"
echo ""
echo -e "  ${YELLOW}[Tip]${NC} To use loopra immediately in current terminal:"
echo "    source ~/.${USER_SHELL}rc"
echo ""

# If not called from setup.sh, wait for user input
if [ -z "$LOOPRA_SETUP" ]; then
    echo "Press Enter to exit..."
    read -r
fi
