param(
  [string]$BaseUrl = "https://grandflipout.com"
)

$ErrorActionPreference = "Stop"

function Info($msg) { Write-Host "[INFO] $msg" -ForegroundColor Cyan }
function Pass($msg) { Write-Host "[PASS] $msg" -ForegroundColor Green }
function Warn($msg) { Write-Host "[WARN] $msg" -ForegroundColor Yellow }
function Fail($msg) { Write-Host "[FAIL] $msg" -ForegroundColor Red }

$projectRoot = Split-Path -Parent $PSScriptRoot
Set-Location $projectRoot

Info "Grand Flip Out go-live status check"
Write-Host ""

$preflightExit = 0
$infraExit = 0
$smokeExit = 0

try {
  & powershell -ExecutionPolicy Bypass -File ".\scripts\publish-preflight.ps1"
  $preflightExit = $LASTEXITCODE
} catch {
  $preflightExit = 1
}

Write-Host ""
try {
  & powershell -ExecutionPolicy Bypass -File ".\scripts\check-infra.ps1"
  $infraExit = $LASTEXITCODE
} catch {
  $infraExit = 1
}

Write-Host ""
try {
  & powershell -ExecutionPolicy Bypass -File ".\scripts\smoke-prod.ps1" -BaseUrl $BaseUrl
  $smokeExit = $LASTEXITCODE
} catch {
  $smokeExit = 1
}

Write-Host ""
Info "Summary"
if ($preflightExit -eq 0) { Pass "Preflight: pass" } else { Fail "Preflight: fail" }
if ($infraExit -eq 0) { Pass "Infra: pass (warnings may still exist)" } else { Fail "Infra: fail" }
if ($smokeExit -eq 0) { Pass "Smoke: pass (warnings may still exist)" } else { Fail "Smoke: fail" }

Write-Host ""
Info "Likely remaining tasks if not fully live yet"
Write-Host "- Set Stripe vars: STRIPE_SECRET_KEY, STRIPE_PRICE_ID (and STRIPE_WEBHOOK_SECRET if used)."
Write-Host "- Replace Discord placeholders: scripts/replace-discord-invite.ps1"
Write-Host "- Add screenshots to screenshots/ for Plugin Hub listing quality."
Write-Host "- Ensure www DNS is set in Cloudflare (use scripts/get-www-dns-target.ps1)."

if ($preflightExit -ne 0 -or $infraExit -ne 0 -or $smokeExit -ne 0) {
  exit 1
}

exit 0
