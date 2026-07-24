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
$re = [regex]'(?<=<artifactId>loopra</artifactId>\s*\r?\n\s*<version>)\d[\d.]*-?[A-Z]*(?=</version>)'
$c = $re.Replace($c, $Version)
if ($c -ne $old) {
    $c = $c.TrimStart([char]0xFEFF)
    [System.IO.File]::WriteAllText($path, $c, $utf8NoBom)
    Write-Host "  [OK] pom.xml"
} else { Write-Host "  [--] pom.xml (unchanged)" }

# 2. loopra-bin/pom.xml
$path = Join-Path $root "loopra-bin/pom.xml"
$c = [System.IO.File]::ReadAllText($path, [System.Text.Encoding]::UTF8)
$old = $c
$re = [regex]'(?<=<artifactId>loopra</artifactId>\s*\r?\n\s*<version>)\d[\d.]*-?[A-Z]*(?=</version>)'
$c = $re.Replace($c, $Version)
if ($c -ne $old) {
    $c = $c.TrimStart([char]0xFEFF)
    [System.IO.File]::WriteAllText($path, $c, $utf8NoBom)
    Write-Host "  [OK] loopra-bin/pom.xml"
} else { Write-Host "  [--] loopra-bin/pom.xml (unchanged)" }


# 4. loopra-web/pom.xml
$path = Join-Path $root "loopra-web/pom.xml"
$c = [System.IO.File]::ReadAllText($path, [System.Text.Encoding]::UTF8)
$old = $c
$c = $re.Replace($c, $Version)
if ($c -ne $old) {
    $c = $c.TrimStart([char]0xFEFF)
    [System.IO.File]::WriteAllText($path, $c, $utf8NoBom)
    Write-Host "  [OK] loopra-web/pom.xml"
} else { Write-Host "  [--] loopra-web/pom.xml (unchanged)" }

# 4.1 loopra-web/dependency-reduced-pom.xml
$path = Join-Path $root "loopra-web/dependency-reduced-pom.xml"
if (Test-Path $path) {
    $c = [System.IO.File]::ReadAllText($path, [System.Text.Encoding]::UTF8)
    $old = $c
    # dependency-reduced-pom.xml has <groupId> between <artifactId> and <version>
    $re2 = [regex]'(?<=<artifactId>loopra</artifactId>\s*\r?\n\s*<groupId>site\.sorghum\.agent</groupId>\s*\r?\n\s*<version>)\d[\d.]*-?[A-Z]*(?=</version>)'
    $c = $re2.Replace($c, $Version)
    if ($c -ne $old) {
        $c = $c.TrimStart([char]0xFEFF)
        [System.IO.File]::WriteAllText($path, $c, $utf8NoBom)
        Write-Host "  [OK] loopra-web/dependency-reduced-pom.xml"
    } else { Write-Host "  [--] loopra-web/dependency-reduced-pom.xml (unchanged)" }
} else { Write-Host "  [--] loopra-web/dependency-reduced-pom.xml (not found)" }

# 5. .release/setup.sh
$path = Join-Path $root ".release/setup.sh"
$c = [System.IO.File]::ReadAllText($path, [System.Text.Encoding]::UTF8)
$old = $c
$c = [regex]::Replace($c, '(?<=VERSION=")v[\d.]+(?=")', "v$Version")
if ($c -ne $old) {
    [System.IO.File]::WriteAllText($path, $c, $utf8NoBom)
    Write-Host "  [OK] .release/setup.sh"
} else { Write-Host "  [--] .release/setup.sh (unchanged)" }

# 6. .release/setup.ps1
$path = Join-Path $root ".release/setup.ps1"
$c = [System.IO.File]::ReadAllText($path, [System.Text.Encoding]::UTF8)
$old = $c
$c = [regex]::Replace($c, '(?<=\$VERSION = ")v[\d.]+(?=")', "v$Version")
if ($c -ne $old) {
    [System.IO.File]::WriteAllText($path, $c, $utf8NoBom)
    Write-Host "  [OK] .release/setup.ps1"
} else { Write-Host "  [--] .release/setup.ps1 (unchanged)" }

# 7. .release/setup-gui.sh
$path = Join-Path $root ".release/setup-gui.sh"
$c = [System.IO.File]::ReadAllText($path, [System.Text.Encoding]::UTF8)
$old = $c
$c = [regex]::Replace($c, '(?<=VERSION=")v[\d.]+(?=")', "v$Version")
if ($c -ne $old) {
    [System.IO.File]::WriteAllText($path, $c, $utf8NoBom)
    Write-Host "  [OK] .release/setup-gui.sh"
} else { Write-Host "  [--] .release/setup-gui.sh (unchanged)" }

# 8. .release/setup-gui.ps1
$path = Join-Path $root ".release/setup-gui.ps1"
$c = [System.IO.File]::ReadAllText($path, [System.Text.Encoding]::UTF8)
$old = $c
$c = [regex]::Replace($c, '(?<=\$VERSION = ")v[\d.]+(?=")', "v$Version")
if ($c -ne $old) {
    [System.IO.File]::WriteAllText($path, $c, $utf8NoBom)
    Write-Host "  [OK] .release/setup-gui.ps1"
} else { Write-Host "  [--] .release/setup-gui.ps1 (unchanged)" }

# 9. loopra-front/package.json (Electron)
$path = Join-Path $root "loopra-front/package.json"
$c = [System.IO.File]::ReadAllText($path, [System.Text.Encoding]::UTF8)
$old = $c
$c = [regex]::Replace($c, '(?<="version"\s*:\s*")[\d.]+(?=")', $Version)
if ($c -ne $old) {
    $c = $c.TrimStart([char]0xFEFF)
    [System.IO.File]::WriteAllText($path, $c, $utf8NoBom)
    Write-Host "  [OK] loopra-front/package.json"
} else { Write-Host "  [--] loopra-front/package.json (unchanged)" }

# 10. loopra-web/src/installDist/bin/version.txt
$path = Join-Path $root "loopra-web/src/installDist/bin/version.txt"
$c = [System.IO.File]::ReadAllText($path, [System.Text.Encoding]::UTF8)
$old = $c
$c = [regex]::Replace($c.Trim(), '^[\d.]+', $Version)
if ($c -ne $old.Trim()) {
    $c = $c.TrimStart([char]0xFEFF)
    [System.IO.File]::WriteAllText($path, $c + [Environment]::NewLine, $utf8NoBom)
    Write-Host "  [OK] loopra-web/src/installDist/bin/version.txt"
} else { Write-Host "  [--] loopra-web/src/installDist/bin/version.txt (unchanged)" }

Write-Host ""
Write-Host "Done! Version unified to $Version"
Write-Host "Run: git diff to review, then commit."
