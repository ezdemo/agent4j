#!/bin/bash
# =============================================
#  Agent4j Web Uninstall Script (Linux / macOS)
#  完全卸载 Agent4j Web，包括配置目录
# =============================================

echo ""
echo "============================================"
echo "   Agent4j Web Uninstaller"
echo "============================================"
echo ""

INSTALL_DIR="$HOME/.agent4j"

# 检查是否已安装
if [ ! -d "$INSTALL_DIR" ]; then
    echo "[Info] Agent4j Web is not installed."
    echo "       Directory not found: $INSTALL_DIR"
    exit 0
fi

# 确认卸载
echo "This will remove Agent4j Web completely:"
echo "  - Executables and configuration"
echo "  - Sessions and memory data"
echo "  - Skills modules"
echo "  - PATH configuration"
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
echo "[1/4] Cleaning shell configuration files..."

clean_shell_config() {
    local config_file="$1"
    if [ -f "$config_file" ]; then
        # 备份
        cp "$config_file" "${config_file}.bak" 2>/dev/null
        
        # 移除 Agent4j Web 相关行
        if [[ "$OS_TYPE" == "Darwin" ]]; then
            # macOS sed
            sed -i '' '/# Agent4j Web/d' "$config_file" 2>/dev/null
            sed -i '' '/\.agent4j\/bin/d' "$config_file" 2>/dev/null
        else
            # Linux sed
            sed -i '/# Agent4j Web/d' "$config_file" 2>/dev/null
            sed -i '/\.agent4j\/bin/d' "$config_file" 2>/dev/null
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
        sed -i '' '/# Agent4j Web/d' "$FISH_CONFIG" 2>/dev/null
        sed -i '' '/set -gx PATH.*agent4j/d' "$FISH_CONFIG" 2>/dev/null
    else
        sed -i '/# Agent4j Web/d' "$FISH_CONFIG" 2>/dev/null
        sed -i '/set -gx PATH.*agent4j/d' "$FISH_CONFIG" 2>/dev/null
    fi
    echo "      Cleaned: $FISH_CONFIG"
fi

# ============================================
#  [2/4] 删除符号链接
# ============================================
echo ""
echo "[2/4] Removing command symlinks..."

# 系统级链接
if [ -L "/usr/local/bin/agent4j-web" ] || [ -f "/usr/local/bin/agent4j-web" ]; then
    if [ "$(id -u)" -eq 0 ]; then
        rm -f /usr/local/bin/agent4j-web 2>/dev/null && echo "      Removed /usr/local/bin/agent4j-web"
    elif command -v sudo &> /dev/null; then
        sudo rm -f /usr/local/bin/agent4j-web 2>/dev/null && echo "      Removed /usr/local/bin/agent4j-web"
    fi
fi

# 用户级链接 (homebrew 或用户 bin)
if [ -L "$HOME/.local/bin/agent4j-web" ] || [ -f "$HOME/.local/bin/agent4j-web" ]; then
    rm -f "$HOME/.local/bin/agent4j-web" 2>/dev/null && echo "      Removed ~/.local/bin/agent4j-web"
fi

if [ -L "$HOME/bin/agent4j-web" ] || [ -f "$HOME/bin/agent4j-web" ]; then
    rm -f "$HOME/bin/agent4j-web" 2>/dev/null && echo "      Removed ~/bin/agent4j-web"
fi

# ============================================
#  [3/4] 询问是否保留配置
# ============================================
echo ""
echo "[3/4] Configuration files..."

read -p "Keep configuration files (config.json, sessions, memory)? (Y/N): " -n 1 -r
echo ""
KEEP_CONFIG=$REPLY

# ============================================
#  [4/4] 删除安装目录
# ============================================
echo ""
echo "[4/4] Removing installation directory..."

if [ -d "$INSTALL_DIR" ]; then
    if [[ $KEEP_CONFIG =~ ^[Yy]$ ]]; then
        # 只删除 bin 目录
        if [ -d "$INSTALL_DIR/bin" ]; then
            rm -rf "$INSTALL_DIR/bin"
            echo "      Removed bin/ directory"
        fi
        echo "      Preserved config.json, sessions/, memory/"
    else
        # 删除整个目录
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
    echo "  Agent4j Web has been removed."
    echo "  Configuration files preserved at: $INSTALL_DIR"
else
    echo "  Agent4j Web has been fully removed."
fi

echo ""
echo "  [Note] Please restart your terminal or run:"
echo "         source ~/.bashrc   (or ~/.zshrc)"
echo ""
