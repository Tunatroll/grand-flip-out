param(
  [string]$BaseUrl = "https://grandflipout.com"
)

$ErrorActionPreference = "Stop"

function Info($msg) { Write-Host "[INFO] $msg" -ForegroundColor Cyan }
function Pass($msg) { Write-Host "[PASS] $msg" -ForegroundColor Green }
function Warn($msg) { Write-Host "[WARN] $msg" -ForegroundColor Yellow }
function Fail($msg) { Write-Host "[FAIL] $msg" -ForegroundColor Red }

$failed = $false
$session = New-Object Microsoft.PowerShell.Commands.WebRequestSession
$email = "smoke+" + [Guid]::NewGuid().ToString("N").Substring(0, 8) + "@example.com"
$password = "SmokePass!123"

Info "Running production smoke test against $BaseUrl"

try {
  $health = Invoke-RestMethod -Uri "$BaseUrl/api/health" -Method Get -WebSession $session
  if ($health.status -eq "ok") { Pass "API health OK" } else { Warn "Health response unexpected" }
} catch {
  Fail "Health endpoint failed"
  $failed = $true
}

try {
  $signupBody = @{ email = $email; password = $password } | ConvertTo-Json
  $null = Invoke-RestMethod -Uri "$BaseUrl/api/auth/signup" -Method Post -WebSession $session -ContentType "application/json" -Body $signupBody
  Pass "Signup OK"
} catch {
  Fail "Signup failed"
  $failed = $true
}

try {
  $loginBody = @{ email = $email; password = $password } | ConvertTo-Json
  $null = Invoke-RestMethod -Uri "$BaseUrl/api/auth/login" -Method Post -WebSession $session -ContentType "application/json" -Body $loginBody
  Pass "Login OK"
} catch {
  Fail "Login failed"
  $failed = $true
}

$apiKey = $null
try {
  $keyResp = Invoke-RestMethod -Uri "$BaseUrl/api/user/keys" -Method Post -WebSession $session -ContentType "application/json" -Body (@{ label = "Smoke" } | ConvertTo-Json)
  $apiKey = $keyResp.key
  if ($apiKey) { Pass "API key created" } else { Fail "API key creation returned no key"; $failed = $true }
} catch {
  Fail "API key creation failed"
  $failed = $true
}

if ($apiKey) {
  try {
    $headers = @{ Authorization = "Bearer $apiKey" }
    $market = Invoke-RestMethod -Uri "$BaseUrl/api/market" -Method Get -Headers $headers
    if ($market.items) { Pass "Market endpoint OK" } else { Warn "Market endpoint returned no items" }
  } catch {
    Fail "Market endpoint failed with API key"
    $failed = $true
  }

  try {
    $headers = @{ Authorization = "Bearer $apiKey" }
    $opps = Invoke-RestMethod -Uri "$BaseUrl/api/opportunities?strategy=high_margin" -Method Get -Headers $headers
    if ($opps.opportunities) { Pass "Opportunities endpoint OK" } else { Warn "Opportunities returned no list" }
  } catch {
    Fail "Opportunities endpoint failed with API key"
    $failed = $true
  }
}

try {
  $checkoutCfg = Invoke-RestMethod -Uri "$BaseUrl/api/checkout/config" -Method Get -WebSession $session
  if ($checkoutCfg.configured) {
    Pass "Checkout configured in production"
  } else {
    Warn "Checkout not configured yet (STRIPE_SECRET_KEY/STRIPE_PRICE_ID missing)"
  }
} catch {
  Warn "Checkout config endpoint check failed"
}

if ($failed) {
  Fail "Smoke test completed with failures."
  exit 1
}

Pass "Smoke test completed with no blocking failures."
exit 0
