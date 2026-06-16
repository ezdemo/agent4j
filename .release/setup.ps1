#
# Agent4j Web Installer for Windows
# Usage: irm https://gitee.com/ezdemo/agent4j/releases/download/v26.6.8/setup.ps1 | iex
#

$ErrorActionPreference = "Stop"

$VERSION = "v26.6.16.1"
$PACKAGE_URL = "https://gitee.com/ezdemo/agent4j/releases/download/$VERSION/agent4j-web-dist.tar.gz"
$TEMP_DIR = Join-Path $env:TEMP "agent4j-install"

function Write-Info {
    param([string]$Message)
    Write-Host "[INFO] " -ForegroundColor Green -NoNewline
    Write-Host $Message
}

function Write-Error {
    param([string]$Message)
    Write-Host "[ERROR] " -ForegroundColor Red -NoNewline
    Write-Host $Message
}

# Cleanup temp directory
if (Test-Path $TEMP_DIR) {
    Remove-Item -Recurse -Force $TEMP_DIR
}
New-Item -ItemType Directory -Path $TEMP_DIR | Out-Null

try {
    Write-Info "Downloading Agent4j Web $VERSION..."

    $packageFile = Join-Path $TEMP_DIR "package.tar.gz"
    Invoke-WebRequest -Uri $PACKAGE_URL -OutFile $packageFile -UseBasicParsing

    Write-Info "Extracting package..."

    # Extract tar.gz using built-in tar (Windows 10+)
    tar -xzf $packageFile -C $TEMP_DIR

    # Find install.ps1
    $installScript = Get-ChildItem -Path $TEMP_DIR -Filter "install.ps1" -Recurse | Select-Object -First 1

    if (-not $installScript) {
        Write-Error "install.ps1 not found in package"
        exit 1
    }

    Write-Info "Running installer..."

    # Run PowerShell installer
    $installPath = $installScript.FullName
    $installDir = Split-Path $installPath -Parent
    
    Write-Host "Install path: $installPath" -ForegroundColor Gray
    
    # Set environment variable to tell install.ps1 not to wait
    $env:AGENT4J_SETUP = "1"
    
    # Execute the installer script
    & $installPath
    
    if ($LASTEXITCODE -ne 0 -and $LASTEXITCODE -ne $null) {
        Write-Error "Installer failed with exit code: $LASTEXITCODE"
        throw "Installation failed"
    }

    # Refresh PATH for current session
    $env:Path = [Environment]::GetEnvironmentVariable('Path', 'User') + ';' + [Environment]::GetEnvironmentVariable('Path', 'Machine')
    $env:Path = $env:Path.TrimEnd(';')

    Write-Host ""
    Write-Info "Installation complete!"
    Write-Host ""
    Write-Host "You can now run: " -NoNewline
    Write-Host "agent4j web" -ForegroundColor Cyan -NoNewline
    Write-Host " or " -NoNewline
    Write-Host "agent4j web 0" -ForegroundColor Cyan
    Write-Host ""

} catch {
    Write-Error $_.Exception.Message
    throw $_
}
