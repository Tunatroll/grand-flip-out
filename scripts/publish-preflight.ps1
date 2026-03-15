$ErrorActionPreference = "Stop"

function Info($msg) { Write-Host "[INFO] $msg" -ForegroundColor Cyan }
function Pass($msg) { Write-Host "[PASS] $msg" -ForegroundColor Green }
function Warn($msg) { Write-Host "[WARN] $msg" -ForegroundColor Yellow }
function Fail($msg) { Write-Host "[FAIL] $msg" -ForegroundColor Red }

$projectRoot = Split-Path -Parent $PSScriptRoot
Set-Location $projectRoot

$failed = $false

Info "Grand Flip Out publish preflight starting..."

# 1) Build + tests
try {
  Info "Running Gradle compile/test"
  & .\gradlew.bat compileJava test | Out-Null
  Pass "Gradle compile/test passed"
} catch {
  Fail "Gradle compile/test failed"
  $failed = $true
}

# 2) Local RuneLite alignment
try {
  Info "Checking local RuneLite build alignment"
  & powershell -ExecutionPolicy Bypass -File ".\scripts\check-local-build-alignment.ps1" | Out-Null
  Pass "Local RuneLite dependency alignment passed"
} catch {
  Warn "Local RuneLite build alignment check failed"
}

# 3) Plugin metadata sanity
$propsPath = Join-Path $projectRoot "runelite-plugin.properties"
if (Test-Path $propsPath) {
  $props = Get-Content $propsPath -Raw
  $required = @("displayName=", "author=", "description=", "tags=", "plugins=")
  $missing = @()
  foreach ($k in $required) {
    if (-not $props.Contains($k)) { $missing += $k }
  }
  if ($missing.Count -eq 0) {
    Pass "runelite-plugin.properties has required keys"
  } else {
    Fail "runelite-plugin.properties missing: $($missing -join ', ')"
    $failed = $true
  }
} else {
  Fail "runelite-plugin.properties missing"
  $failed = $true
}

# 4) Screenshot check (recommended for plugin hub listing quality)
$screenshotGlobs = @(
  "screenshots\*.png",
  "screenshots\*.jpg",
  "screenshots\*.jpeg"
)
$hasScreenshot = $false
foreach ($g in $screenshotGlobs) {
  if (Get-ChildItem -Path $g -ErrorAction SilentlyContinue) {
    $hasScreenshot = $true
    break
  }
}
if ($hasScreenshot) {
  Pass "At least one screenshot found"
} else {
  Warn "No screenshots found in screenshots/. Add one before submission."
}

# 5) Website placeholder scan
$websiteFiles = Get-ChildItem -Path ".\website" -Filter "*.html" -ErrorAction SilentlyContinue
$placeholderHits = @()
foreach ($f in $websiteFiles) {
  $text = Get-Content $f.FullName -Raw
  if ($text -match "discord\.gg\/grandflipout") {
    $placeholderHits += $f.Name
  }
}
if ($placeholderHits.Count -gt 0) {
  Warn "Discord placeholder still present in: $($placeholderHits -join ', ')"
} else {
  Pass "No Discord placeholders detected in website HTML"
}

# 6) Railway subscription env status (names only, never print values)
try {
  Info "Checking Railway production env names for gfo-server"
  $jsonText = & railway variable list --service gfo-server --json 2>$null
  if ($LASTEXITCODE -eq 0 -and $jsonText) {
    $vars = $jsonText | ConvertFrom-Json
    $names = @($vars.PSObject.Properties.Name)
    if ($names -contains "STRIPE_SECRET_KEY" -and $names -contains "STRIPE_PRICE_ID") {
      Pass "Stripe env vars detected in Railway (name check only)"
    } else {
      Warn "Stripe env vars missing: requires STRIPE_SECRET_KEY and STRIPE_PRICE_ID for paid checkout"
    }
    if ($names -contains "DATABASE_URL" -and $names -contains "JWT_SECRET") {
      Pass "Core env vars detected (DATABASE_URL + JWT_SECRET)"
    } else {
      Fail "Core env vars missing (DATABASE_URL and/or JWT_SECRET)"
      $failed = $true
    }
  } else {
    Warn "Could not read Railway env vars (check CLI auth/link)"
  }
} catch {
  Warn "Railway env var check failed (check CLI auth/link)"
}

if ($failed) {
  Fail "Preflight completed with blocking failures."
  exit 1
}

Pass "Preflight completed with no blocking failures."
Write-Host "Tip: run scripts/check-infra.ps1 for Railway + Cloudflare DNS/edge validation."
exit 0
