param(
    [Parameter(Mandatory=$true, HelpMessage="New version number, e.g. 2026.7.1")]
    [string]$Version
)

$root = $PSScriptRoot
if (-not $root) { $root = Split-Path -Parent $MyInvocation.MyCommand.Path }
if (-not $root) { $root = Get-Location }

# UTF8 without BOM to avoid breaking JSON parsers
$utf8NoBom = New-Object System.Text.UTF8Encoding $false

Write-Host "[bump] Updating version to $Version"
Write-Host ""

# 1. pom.xml
$path = Join-Path $root "pom.xml"
$c = [System.IO.File]::ReadAllText($path, [System.Text.Encoding]::UTF8)
$old = $c
$re = [regex]'(?<=<artifactId>agent4j</artifactId>\s*\r?\n\s*<version>)\d[\d.]*-?[A-Z]*(?=</version>)'
$c = $re.Replace($c, $Version)
if ($c -ne $old) {
    $c = $c.TrimStart("`u{FEFF}")
    [System.IO.File]::WriteAllText($path, $c, $utf8NoBom)
    Write-Host "  [OK] pom.xml"
} else { Write-Host "  [--] pom.xml (unchanged)" }

# 2. tauri.conf.json
$path = Join-Path $root "agent4j-tauri/src-tauri/tauri.conf.json"
$c = [System.IO.File]::ReadAllText($path, [System.Text.Encoding]::UTF8)
$old = $c
$c = [regex]::Replace($c, '(?<="version"\s*:\s*")[\d.]+(?=")', $Version)
if ($c -ne $old) {
    [System.IO.File]::WriteAllText($path, $c, $utf8NoBom)
    Write-Host "  [OK] tauri.conf.json"
} else { Write-Host "  [--] tauri.conf.json (unchanged)" }

# 3. Cargo.toml
$path = Join-Path $root "agent4j-tauri/src-tauri/Cargo.toml"
$lines = [System.IO.File]::ReadAllLines($path, [System.Text.Encoding]::UTF8)
$changed = $false
for ($i = 0; $i -lt $lines.Length; $i++) {
    if ($lines[$i] -match '^version\s*=\s*"[\d.]+"') {
        $lines[$i] = 'version = "' + $Version + '"'
        $changed = $true
        break
    }
}
if ($changed) {
    [System.IO.File]::WriteAllText($path, ($lines -join "`r`n"), $utf8NoBom)
    Write-Host "  [OK] Cargo.toml"
} else { Write-Host "  [--] Cargo.toml (unchanged)" }

# 4. package.json
$path = Join-Path $root "agent4j-tauri/package.json"
$c = [System.IO.File]::ReadAllText($path, [System.Text.Encoding]::UTF8)
$old = $c
$c = [regex]::Replace($c, '(?<="version"\s*:\s*")[\d.]+(?=")', $Version)
if ($c -ne $old) {
    [System.IO.File]::WriteAllText($path, $c, $utf8NoBom)
    Write-Host "  [OK] package.json"
} else { Write-Host "  [--] package.json (unchanged)" }

# 5. agent4j-bin/pom.xml
$path = Join-Path $root "agent4j-bin/pom.xml"
$c = [System.IO.File]::ReadAllText($path, [System.Text.Encoding]::UTF8)
$old = $c
$re = [regex]'(?<=<artifactId>agent4j</artifactId>\s*\r?\n\s*<version>)\d[\d.]*-?[A-Z]*(?=</version>)'
$c = $re.Replace($c, $Version)
if ($c -ne $old) {
    $c = $c.TrimStart("`u{FEFF}")
    [System.IO.File]::WriteAllText($path, $c, $utf8NoBom)
    Write-Host "  [OK] agent4j-bin/pom.xml"
} else { Write-Host "  [--] agent4j-bin/pom.xml (unchanged)" }

# 6. agent4j-tool/pom.xml
$path = Join-Path $root "agent4j-tool/pom.xml"
$c = [System.IO.File]::ReadAllText($path, [System.Text.Encoding]::UTF8)
$old = $c
$c = $re.Replace($c, $Version)
if ($c -ne $old) {
    $c = $c.TrimStart("`u{FEFF}")
    [System.IO.File]::WriteAllText($path, $c, $utf8NoBom)
    Write-Host "  [OK] agent4j-tool/pom.xml"
} else { Write-Host "  [--] agent4j-tool/pom.xml (unchanged)" }

# 7. agent4j-web/pom.xml
$path = Join-Path $root "agent4j-web/pom.xml"
$c = [System.IO.File]::ReadAllText($path, [System.Text.Encoding]::UTF8)
$old = $c
$c = $re.Replace($c, $Version)
if ($c -ne $old) {
    $c = $c.TrimStart("`u{FEFF}")
    [System.IO.File]::WriteAllText($path, $c, $utf8NoBom)
    Write-Host "  [OK] agent4j-web/pom.xml"
} else { Write-Host "  [--] agent4j-web/pom.xml (unchanged)" }

# 8. .release/setup.sh
$path = Join-Path $root ".release/setup.sh"
$c = [System.IO.File]::ReadAllText($path, [System.Text.Encoding]::UTF8)
$old = $c
$c = [regex]::Replace($c, '(?<=VERSION=")v[\d.]+(?=")', "v$Version")
if ($c -ne $old) {
    [System.IO.File]::WriteAllText($path, $c, $utf8NoBom)
    Write-Host "  [OK] .release/setup.sh"
} else { Write-Host "  [--] .release/setup.sh (unchanged)" }

# 9. .release/setup.ps1
$path = Join-Path $root ".release/setup.ps1"
$c = [System.IO.File]::ReadAllText($path, [System.Text.Encoding]::UTF8)
$old = $c
$c = [regex]::Replace($c, '(?<=\$VERSION = ")v[\d.]+(?=")', "v$Version")
if ($c -ne $old) {
    [System.IO.File]::WriteAllText($path, $c, $utf8NoBom)
    Write-Host "  [OK] .release/setup.ps1"
} else { Write-Host "  [--] .release/setup.ps1 (unchanged)" }

Write-Host ""
Write-Host "Done! Version unified to $Version"
Write-Host "Run: git diff to review, then commit."
