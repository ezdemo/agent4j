#!/bin/bash
# Loopra Desktop Web Installer
# Usage: curl -fsSL https://raw.giteeusercontent.com/ezdemo/loopra/raw/main/.release/setup-gui.sh | bash

set -euo pipefail

export LOOPRA_GUI_INSTALL=1
export LOOPRA_INSTALL_DIR="${LOOPRA_INSTALL_DIR:-$HOME/.loopra-gui}"

curl -fsSL "https://raw.giteeusercontent.com/ezdemo/loopra/raw/main/.release/setup.sh" | bash
