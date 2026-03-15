$ErrorActionPreference = "Stop"

function Info($msg) { Write-Host "[INFO] $msg" -ForegroundColor Cyan }
function Pass($msg) { Write-Host "[PASS] $msg" -ForegroundColor Green }
function Warn($msg) { Write-Host "[WARN] $msg" -ForegroundColor Yellow }
function Fail($msg) { Write-Host "[FAIL] $msg" -ForegroundColor Red }

$domain = "grandflipout.com"
$wwwDomain = "www.grandflipout.com"

Info "Infrastructure check starting..."

# Railway checks
try {
  $status = (& railway status 2>$null) -join "`n"
  if ($status -match "Service:\s+gfo-server" -and $status -match "Environment:\s+production") {
    Pass "Railway linked to gfo-server production"
  } else {
    Warn "Railway CLI not linked to gfo-server production. Run: railway service link gfo-server"
  }
} catch {
  Warn "Railway status check failed. Ensure Railway CLI auth is active."
}

try {
  $svc = (& railway service status 2>$null) -join "`n"
  if ($svc -match "Status:\s+SUCCESS") {
    Pass "Railway service deployment status is SUCCESS"
  } else {
    Warn "Railway service is not in SUCCESS status. Check: railway logs --service gfo-server --latest"
  }
} catch {
  Warn "Railway service status check failed."
}

# DNS checks
try {
  $apex = Resolve-DnsName $domain -ErrorAction Stop
  $aApex = $apex | Where-Object { $_.Type -eq "A" -or $_.Type -eq "AAAA" }
  if ($aApex.Count -gt 0) {
    Pass "Apex domain resolves ($domain)"
  } else {
    Fail "Apex domain does not have A/AAAA records"
  }
} catch {
  Fail "Apex domain DNS lookup failed for $domain"
}

try {
  $www = Resolve-DnsName $wwwDomain -ErrorAction Stop
  $aWww = $www | Where-Object { $_.Type -eq "A" -or $_.Type -eq "AAAA" -or $_.Type -eq "CNAME" }
  if ($aWww.Count -gt 0) {
    Pass "www subdomain resolves ($wwwDomain)"
  } else {
    Warn "www subdomain exists but no A/AAAA/CNAME records found"
  }
} catch {
  Warn "www subdomain does not resolve ($wwwDomain). Consider adding a CNAME in Cloudflare."
}

# Edge/HTTP checks
try {
  $head = Invoke-WebRequest -Uri "https://$domain/api/health" -Method Head -UseBasicParsing -ErrorAction Stop
  if ($head.StatusCode -eq 200) {
    Pass "https://$domain/api/health returns 200"
  } else {
    Warn "https://$domain/api/health returned status $($head.StatusCode)"
  }
  if ($head.Headers["Server"] -match "cloudflare") {
    Pass "Traffic is flowing through Cloudflare edge"
  } else {
    Warn "Server header is not cloudflare (check DNS/proxy settings)"
  }
} catch {
  Fail "HTTPS health check failed for https://$domain/api/health"
}

Info "Infrastructure check complete."
