param([switch]$Online)
& (Join-Path $PSScriptRoot 'build.ps1') -Online:$Online -Tasks ':app:penPolicyTest'
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
