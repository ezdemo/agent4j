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

# 2. agent4j-bin/pom.xml
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

# 3. agent4j-tool/pom.xml
$path = Join-Path $root "agent4j-tool/pom.xml"
$c = [System.IO.File]::ReadAllText($path, [System.Text.Encoding]::UTF8)
$old = $c
$c = $re.Replace($c, $Version)
if ($c -ne $old) {
    $c = $c.TrimStart("`u{FEFF}")
    [System.IO.File]::WriteAllText($path, $c, $utf8NoBom)
    Write-Host "  [OK] agent4j-tool/pom.xml"
} else { Write-Host "  [--] agent4j-tool/pom.xml (unchanged)" }

# 4. agent4j-web/pom.xml
$path = Join-Path $root "agent4j-web/pom.xml"
$c = [System.IO.File]::ReadAllText($path, [System.Text.Encoding]::UTF8)
$old = $c
$c = $re.Replace($c, $Version)
if ($c -ne $old) {
    $c = $c.TrimStart("`u{FEFF}")
    [System.IO.File]::WriteAllText($path, $c, $utf8NoBom)
    Write-Host "  [OK] agent4j-web/pom.xml"
} else { Write-Host "  [--] agent4j-web/pom.xml (unchanged)" }

# 4.1 agent4j-web/dependency-reduced-pom.xml
$path = Join-Path $root "agent4j-web/dependency-reduced-pom.xml"
if (Test-Path $path) {
    $c = [System.IO.File]::ReadAllText($path, [System.Text.Encoding]::UTF8)
    $old = $c
    # dependency-reduced-pom.xml has <groupId> between <artifactId> and <version>
    $re2 = [regex]'(?<=<artifactId>agent4j</artifactId>\s*\r?\n\s*<groupId>site\.sorghum\.agent</groupId>\s*\r?\n\s*<version>)\d[\d.]*-?[A-Z]*(?=</version>)'
    $c = $re2.Replace($c, $Version)
    if ($c -ne $old) {
        $c = $c.TrimStart("`u{FEFF}")
        [System.IO.File]::WriteAllText($path, $c, $utf8NoBom)
        Write-Host "  [OK] agent4j-web/dependency-reduced-pom.xml"
    } else { Write-Host "  [--] agent4j-web/dependency-reduced-pom.xml (unchanged)" }
} else { Write-Host "  [--] agent4j-web/dependency-reduced-pom.xml (not found)" }

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

# 7. agent4j-front/package.json (Electron)
$path = Join-Path $root "agent4j-front/package.json"
$c = [System.IO.File]::ReadAllText($path, [System.Text.Encoding]::UTF8)
$old = $c
$c = [regex]::Replace($c, '(?<="version"\s*:\s*")[\d.]+(?=")', $Version)
if ($c -ne $old) {
    $c = $c.TrimStart("`u{FEFF}")
    [System.IO.File]::WriteAllText($path, $c, $utf8NoBom)
    Write-Host "  [OK] agent4j-front/package.json"
} else { Write-Host "  [--] agent4j-front/package.json (unchanged)" }

Write-Host ""
Write-Host "Done! Version unified to $Version"
Write-Host "Run: git diff to review, then commit."
