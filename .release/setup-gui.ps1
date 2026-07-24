# Loopra Desktop Web Installer for Windows
# Usage: irm https://raw.giteeusercontent.com/ezdemo/loopra/raw/main/.release/setup-gui.ps1 | iex

$ErrorActionPreference = "Stop"

$env:LOOPRA_GUI_INSTALL = "1"
if (-not $env:LOOPRA_INSTALL_DIR) {
    $env:LOOPRA_INSTALL_DIR = Join-Path $env:USERPROFILE ".loopra-gui"
}

irm https://raw.giteeusercontent.com/ezdemo/loopra/raw/main/.release/setup.ps1 | iex
