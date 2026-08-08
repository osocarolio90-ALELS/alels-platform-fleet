$ErrorActionPreference = "Stop"

$repositoryRoot = Resolve-Path (Join-Path $PSScriptRoot "..\..")
$modules = @("backend", "gateway", "ingestion-service")

& (Join-Path $PSScriptRoot "validate-repository.ps1")
if ($LASTEXITCODE -ne 0) { throw "repository validation failed" }

foreach ($module in $modules) {
    Push-Location (Join-Path $repositoryRoot $module)
    try {
        & mvn.cmd --batch-mode --no-transfer-progress clean verify
        if ($LASTEXITCODE -ne 0) { throw "$module quality gate failed" }
    } finally {
        Pop-Location
    }
}

$capacityOutput = Join-Path $repositoryRoot "gateway/target/capacity-probe-check"
& javac -d $capacityOutput (Join-Path $repositoryRoot "tools/capacity/GatewayCapacityProbe.java")
if ($LASTEXITCODE -ne 0) { throw "capacity probe compilation failed" }

Push-Location (Join-Path $repositoryRoot "web-react")
try {
    & npm.cmd run typecheck
    if ($LASTEXITCODE -ne 0) { throw "frontend typecheck failed" }
    & npm.cmd run build
    if ($LASTEXITCODE -ne 0) { throw "frontend build failed" }
} finally {
    Pop-Location
}

Write-Output "ALELS quality gate passed."
