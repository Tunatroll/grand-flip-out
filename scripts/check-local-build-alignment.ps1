$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $PSScriptRoot
$buildFile = Join-Path $projectRoot "build.gradle"
$launcherLog = "$env:USERPROFILE\.runelite\logs\launcher.log"
$clientLog = "$env:USERPROFILE\.runelite\logs\client.log"

if (-not (Test-Path $buildFile)) {
  Write-Error "Missing build.gradle at $buildFile"
}

$buildContent = Get-Content $buildFile -Raw
$declaredVersion = $null
if ($buildContent -match "def runeLiteVersion = '([^']+)'") {
  $declaredVersion = $Matches[1]
}

$launcherVersion = $null
if (Test-Path $launcherLog) {
  $line = Select-String -Path $launcherLog -Pattern "RuneLite Launcher version" | Select-Object -Last 1
  if ($line -and $line.Line -match "version ([0-9.]+)") {
    $launcherVersion = $Matches[1]
  }

  # Fallback source for client version: "Verified hash of client-1.12.20.jar"
  $clientJarLine = Select-String -Path $launcherLog -Pattern "Verified hash of client-[0-9.]+\.jar" | Select-Object -Last 1
  if ($clientJarLine -and $clientJarLine.Line -match "client-([0-9.]+)\.jar") {
    $clientVersion = $Matches[1]
  }
}

$injectedBuild = $null
if (Test-Path $clientLog) {
  $clientLine = Select-String -Path $clientLog -Pattern "RuneLite\s+[0-9.]+\s+\(launcher version" | Select-Object -Last 1
  if (-not $clientVersion -and $clientLine -and $clientLine.Line -match "RuneLite\s+([0-9.]+)\s+\(launcher version") {
    $clientVersion = $Matches[1]
  }

  $injectedLine = Select-String -Path $clientLog -Pattern "injected-client\s+[0-9]+\.[0-9]+" | Select-Object -Last 1
  if ($injectedLine -and $injectedLine.Line -match "injected-client\s+([0-9]+\.[0-9]+)") {
    $injectedBuild = $Matches[1]
  }
}

Write-Host "=== Local Build Alignment Check ==="
Write-Host "build.gradle runeLiteVersion : $declaredVersion"
Write-Host "Local RuneLite client version: $clientVersion"
Write-Host "Local launcher version       : $launcherVersion"
Write-Host "Local injected-client build  : $injectedBuild"
Write-Host ""

if (-not $declaredVersion) {
  Write-Host "WARN: Could not parse runeLiteVersion from build.gradle"
  exit 1
}

if (-not $clientVersion) {
  Write-Host "WARN: Could not read local RuneLite client version from logs."
  Write-Host "      Start RuneLite once and run this script again."
  exit 1
}

if ($declaredVersion -eq $clientVersion) {
  Write-Host "PASS: Project dependency matches local RuneLite client."
  exit 0
}

Write-Host "WARN: Version mismatch detected."
Write-Host "      Consider updating build.gradle runeLiteVersion to $clientVersion"
exit 2
