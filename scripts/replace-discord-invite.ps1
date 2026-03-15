$ErrorActionPreference = "Stop"

param(
  [Parameter(Mandatory = $true)]
  [string]$InviteUrl
)

if ($InviteUrl -notmatch "^https://discord\.gg/[A-Za-z0-9-]+/?$") {
  Write-Error "InviteUrl must look like: https://discord.gg/yourInviteCode"
}

$projectRoot = Split-Path -Parent $PSScriptRoot
$websiteDir = Join-Path $projectRoot "website"

if (-not (Test-Path $websiteDir)) {
  Write-Error "website directory not found at $websiteDir"
}

$files = Get-ChildItem -Path $websiteDir -Filter "*.html" -File
$updated = @()

foreach ($f in $files) {
  $content = Get-Content $f.FullName -Raw
  $newContent = $content -replace "https://discord\.gg/grandflipout", $InviteUrl
  if ($newContent -ne $content) {
    Set-Content -Path $f.FullName -Value $newContent -NoNewline
    $updated += $f.Name
  }
}

if ($updated.Count -eq 0) {
  Write-Host "No placeholder Discord links found in website HTML files."
  exit 0
}

Write-Host "Updated Discord links in:"
$updated | ForEach-Object { Write-Host " - $_" }
Write-Host ""
Write-Host "Done. Re-run scripts/publish-preflight.ps1 to confirm placeholders are gone."
