param(
  [string]$Domain = "www.grandflipout.com",
  [string]$Service = "gfo-server"
)

$ErrorActionPreference = "Stop"

function Info($msg) { Write-Host "[INFO] $msg" -ForegroundColor Cyan }
function Pass($msg) { Write-Host "[PASS] $msg" -ForegroundColor Green }
function Warn($msg) { Write-Host "[WARN] $msg" -ForegroundColor Yellow }

Info "Fetching Railway domain list for service $Service..."

$json = railway domain --service $Service --json 2>$null
if (-not $json) {
  Write-Error "No response from Railway CLI. Check auth/link."
}

$obj = $json | ConvertFrom-Json
$domains = @($obj.domains)
$railwayTarget = $domains | Where-Object { $_ -match "up\.railway\.app" } | Select-Object -First 1
if (-not $railwayTarget) {
  Write-Error "Could not detect Railway app domain target from CLI output."
}

$targetHost = $railwayTarget -replace "^https?://", ""

Write-Host ""
Write-Host "Add this in Cloudflare DNS:"
Write-Host "  Type  : CNAME"
Write-Host "  Name  : www"
Write-Host "  Target: $targetHost"
Write-Host "  Proxy : Proxied (orange cloud)"
Write-Host ""
Pass "Use this CNAME to route $Domain through Railway."
