param(
  [string]$InviteUrl = "",
  [string]$StripeSecretKey = "",
  [string]$StripePriceId = "",
  [string]$StripeWebhookSecret = "",
  [switch]$RedeployAfterStripe,
  [string]$BaseUrl = "https://grandflipout.com"
)

$ErrorActionPreference = "Stop"

function Info($msg) { Write-Host "[INFO] $msg" -ForegroundColor Cyan }
function Pass($msg) { Write-Host "[PASS] $msg" -ForegroundColor Green }
function Warn($msg) { Write-Host "[WARN] $msg" -ForegroundColor Yellow }
function Fail($msg) { Write-Host "[FAIL] $msg" -ForegroundColor Red }

$projectRoot = Split-Path -Parent $PSScriptRoot
Set-Location $projectRoot

Info "Grand Flip Out operator launch sequence starting..."

if ($InviteUrl -and $InviteUrl.Trim().Length -gt 0) {
  Info "Applying Discord invite replacement..."
  & powershell -ExecutionPolicy Bypass -File ".\scripts\replace-discord-invite.ps1" -InviteUrl $InviteUrl
  if ($LASTEXITCODE -eq 0) { Pass "Discord links updated" } else { Fail "Discord replacement failed"; exit 1 }
} else {
  Warn "InviteUrl not provided. Discord placeholders will remain until replaced."
}

$hasStripe = $StripeSecretKey -and $StripePriceId
if ($hasStripe) {
  Info "Applying Stripe variables to Railway..."
  $args = @(
    "-ExecutionPolicy", "Bypass",
    "-File", ".\scripts\set-stripe-vars.ps1",
    "-StripeSecretKey", $StripeSecretKey,
    "-StripePriceId", $StripePriceId
  )
  if ($StripeWebhookSecret -and $StripeWebhookSecret.Trim().Length -gt 0) {
    $args += @("-StripeWebhookSecret", $StripeWebhookSecret)
  }
  if ($RedeployAfterStripe) {
    $args += "-Redeploy"
  }
  & powershell @args
  if ($LASTEXITCODE -eq 0) { Pass "Stripe variables applied" } else { Fail "Stripe setup failed"; exit 1 }
} else {
  Warn "Stripe keys not provided. Checkout will remain disabled."
}

Info "Running one-command go-live status check..."
& powershell -ExecutionPolicy Bypass -File ".\scripts\go-live-status.ps1" -BaseUrl $BaseUrl
if ($LASTEXITCODE -eq 0) {
  Pass "Operator launch sequence finished with no blocking failures."
  exit 0
}

Fail "Operator launch sequence completed with blocking failures."
exit 1
