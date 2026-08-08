param(
    [string]$HostName = "127.0.0.1",
    [int]$Port = 5050,
    [int]$Connections = 10000,
    [int]$DurationSeconds = 300,
    [int]$SendIntervalSeconds = 30,
    [int]$RampPerSecond = 1000,
    [string]$ImeiPrefix = "99000",
    [string]$EvidencePath = "tools/capacity/evidence/gateway-capacity.json"
)

$ErrorActionPreference = "Stop"
$root = (Resolve-Path (Join-Path $PSScriptRoot "../..")).Path
$source = Join-Path $PSScriptRoot "GatewayCapacityProbe.java"
$evidence = Join-Path $root $EvidencePath

Push-Location $root
try {
    java $source --host $HostName --port $Port --connections $Connections `
        --duration-seconds $DurationSeconds --send-interval-seconds $SendIntervalSeconds `
        --ramp-per-second $RampPerSecond --imei-prefix $ImeiPrefix --output $evidence
    if ($LASTEXITCODE -ne 0) { throw "Gateway capacity probe failed with exit code $LASTEXITCODE." }
} finally {
    Pop-Location
}
