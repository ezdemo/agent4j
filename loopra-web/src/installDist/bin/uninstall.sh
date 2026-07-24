#!/bin/bash
# =============================================
#  Loopra Uninstall Script (Linux / macOS)
#  完全卸载 Loopra，包括配置目录
# =============================================

echo ""
echo "============================================"
echo "   Loopra Uninstaller"
echo "============================================"
echo ""

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
INSTALL_DIR="${LOOPRA_INSTALL_DIR:-$(cd "$SCRIPT_DIR/.." && pwd)}"
CONFIG_DIR="$HOME/.loopra"

# 检查是否已安装
if [ ! -d "$INSTALL_DIR" ]; then
    echo "[Info] Loopra is not installed."
    echo "       Directory not found: $INSTALL_DIR"
    exit 0
fi

# 确认卸载
if [ "$INSTALL_DIR" = "$CONFIG_DIR" ]; then
    echo "This will remove Loopra completely:"
    echo "  - Executables and configuration"
    echo "  - Sessions and memory data"
    echo "  - Skills modules"
    echo "  - PATH configuration"
else
    echo "This will remove the Loopra Desktop runtime:"
    echo "  - Desktop runtime executables and bundled JRE"
    echo "  - Desktop runtime configuration files"
    echo "  - CLI installation and ~/.loopra configuration will be preserved"
fi
echo ""
read -p "Continue? (Y/N): " -n 1 -r
echo ""
if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    echo "Cancelled."
    exit 0
fi

# 检测操作系统
OS_TYPE="$(uname -s)"
echo "[Info] Detected OS: $OS_TYPE"

# ============================================
#  [1/4] 清理 shell 配置文件中的 PATH 配置
# ============================================
echo ""
if [ "$INSTALL_DIR" = "$CONFIG_DIR" ]; then
    echo "[1/4] Cleaning shell configuration files..."

clean_shell_config() {
    local config_file="$1"
    if [ -f "$config_file" ]; then
        # 备份
        cp "$config_file" "${config_file}.bak" 2>/dev/null
        
        # 移除 Loopra Web 相关行
        if [[ "$OS_TYPE" == "Darwin" ]]; then
            # macOS sed
            sed -i '' '/# Loopra Web/d' "$config_file" 2>/dev/null
            sed -i '' '/\.loopra\/bin/d' "$config_file" 2>/dev/null
        else
            # Linux sed
            sed -i '/# Loopra Web/d' "$config_file" 2>/dev/null
            sed -i '/\.loopra\/bin/d' "$config_file" 2>/dev/null
        fi
        echo "      Cleaned: $config_file"
    fi
}

# 清理所有可能的配置文件
# zsh
clean_shell_config "$HOME/.zshrc"

# bash
clean_shell_config "$HOME/.bashrc"
clean_shell_config "$HOME/.bash_profile"
clean_shell_config "$HOME/.profile"

# Fish shell
FISH_CONFIG="$HOME/.config/fish/config.fish"
if [ -f "$FISH_CONFIG" ]; then
    cp "$FISH_CONFIG" "${FISH_CONFIG}.bak" 2>/dev/null
    if [[ "$OS_TYPE" == "Darwin" ]]; then
        sed -i '' '/# Loopra Web/d' "$FISH_CONFIG" 2>/dev/null
        sed -i '' '/set -gx PATH.*loopra/d' "$FISH_CONFIG" 2>/dev/null
    else
        sed -i '/# Loopra Web/d' "$FISH_CONFIG" 2>/dev/null
        sed -i '/set -gx PATH.*loopra/d' "$FISH_CONFIG" 2>/dev/null
    fi
    echo "      Cleaned: $FISH_CONFIG"
fi
else
    echo "[1/4] Skipping shell PATH cleanup for desktop runtime"
fi

# ============================================
#  [2/4] 删除符号链接
# ============================================
echo ""
echo "[2/4] Removing command symlinks..."

# 系统级链接（仅命令行安装）
if [ "$INSTALL_DIR" = "$CONFIG_DIR" ] && { [ -L "/usr/local/bin/loopra" ] || [ -f "/usr/local/bin/loopra" ]; }; then
    if [ "$(id -u)" -eq 0 ]; then
        rm -f /usr/local/bin/loopra 2>/dev/null && echo "      Removed /usr/local/bin/loopra"
    elif command -v sudo &> /dev/null; then
        sudo rm -f /usr/local/bin/loopra 2>/dev/null && echo "      Removed /usr/local/bin/loopra"
    fi
fi

# 用户级链接 (homebrew 或用户 bin)
if [ "$INSTALL_DIR" = "$CONFIG_DIR" ] && { [ -L "$HOME/.local/bin/loopra" ] || [ -f "$HOME/.local/bin/loopra" ]; }; then
    rm -f "$HOME/.local/bin/loopra" 2>/dev/null && echo "      Removed ~/.local/bin/loopra"
fi

if [ "$INSTALL_DIR" = "$CONFIG_DIR" ] && { [ -L "$HOME/bin/loopra" ] || [ -f "$HOME/bin/loopra" ]; }; then
    rm -f "$HOME/bin/loopra" 2>/dev/null && echo "      Removed ~/bin/loopra"
fi

# ============================================
#  [3/4] 询问是否保留配置
# ============================================
echo ""
echo "[3/4] Configuration files..."

if [ "$INSTALL_DIR" != "$CONFIG_DIR" ]; then
    KEEP_CONFIG="Y"
    echo "Desktop runtime detected; configuration at $CONFIG_DIR will be preserved."
else
    read -p "Keep configuration files (config.json, sessions, memory)? (Y/N): " -n 1 -r
    echo ""
    KEEP_CONFIG=$REPLY
fi

# ============================================
#  [4/4] 删除安装目录
# ============================================
echo ""
echo "[4/4] Removing installation directory..."

if [ -d "$INSTALL_DIR" ]; then
    if [ "$INSTALL_DIR" != "$CONFIG_DIR" ]; then
        rm -rf "$INSTALL_DIR"
        if [ -d "$INSTALL_DIR" ]; then
            echo "      [Warning] Could not remove $INSTALL_DIR"
        else
            echo "      Removed runtime: $INSTALL_DIR"
            echo "      Preserved configuration at: $CONFIG_DIR"
        fi
    elif [[ $KEEP_CONFIG =~ ^[Yy]$ ]]; then
        # 只删除 bin 目录
        if [ -d "$INSTALL_DIR/bin" ]; then
            rm -rf "$INSTALL_DIR/bin"
            echo "      Removed bin/ directory"
        fi
        echo "      Preserved config.json, sessions/, memory/"
    else
        rm -rf "$INSTALL_DIR"
        if [ -d "$INSTALL_DIR" ]; then
            echo "      [Warning] Could not remove $INSTALL_DIR"
        else
            echo "      Removed: $INSTALL_DIR"
        fi
    fi
else
    echo "      Directory already removed"
fi

# ============================================
#  清理备份文件
# ============================================
echo ""
echo "Cleaning up backup files..."

read -p "Remove shell config backups (*.bak)? (Y/N): " -n 1 -r
echo ""
if [[ $REPLY =~ ^[Yy]$ ]]; then
    for config_file in "$HOME/.zshrc" "$HOME/.bashrc" "$HOME/.bash_profile" "$HOME/.profile" "$FISH_CONFIG"; do
        if [ -f "${config_file}.bak" ]; then
            rm -f "${config_file}.bak"
            echo "      Removed: ${config_file}.bak"
        fi
    done
fi

# ============================================
#  完成
# ============================================
echo ""
echo "============================================"
echo "   Uninstall Complete!"
echo "============================================"
echo ""

if [[ $KEEP_CONFIG =~ ^[Yy]$ ]]; then
    echo "  Loopra runtime has been removed."
    echo "  Configuration files preserved at: $CONFIG_DIR"
else
    echo "  Loopra has been fully removed."
fi

echo ""
echo "  [Note] Please restart your terminal or run:"
echo "         source ~/.bashrc   (or ~/.zshrc)"
echo ""
