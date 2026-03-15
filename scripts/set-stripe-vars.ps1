param(
  [Parameter(Mandatory = $true)]
  [string]$StripeSecretKey,
  [Parameter(Mandatory = $true)]
  [string]$StripePriceId,
  [string]$StripeWebhookSecret = "",
  [string]$BaseUrl = "https://grandflipout.com",
  [switch]$Redeploy
)

$ErrorActionPreference = "Stop"

function Info($msg) { Write-Host "[INFO] $msg" -ForegroundColor Cyan }
function Pass($msg) { Write-Host "[PASS] $msg" -ForegroundColor Green }

Info "Setting Stripe variables on Railway service gfo-server (production link assumed)..."

# Set values without echoing secrets to terminal output.
$StripeSecretKey | railway variable set --service gfo-server --set-from-stdin STRIPE_SECRET_KEY | Out-Null
$StripePriceId   | railway variable set --service gfo-server --set-from-stdin STRIPE_PRICE_ID   | Out-Null
$BaseUrl         | railway variable set --service gfo-server --set-from-stdin BASE_URL          | Out-Null

if ($StripeWebhookSecret -and $StripeWebhookSecret.Trim().Length -gt 0) {
  $StripeWebhookSecret | railway variable set --service gfo-server --set-from-stdin STRIPE_WEBHOOK_SECRET | Out-Null
  Pass "Set STRIPE_WEBHOOK_SECRET"
}

Pass "Set STRIPE_SECRET_KEY, STRIPE_PRICE_ID, BASE_URL"

if ($Redeploy) {
  Info "Redeploying gfo-server..."
  railway redeploy --service gfo-server | Out-Null
  Pass "Redeploy triggered"
}

Info "Done. Verify with: Invoke-WebRequest https://grandflipout.com/api/checkout/config"
