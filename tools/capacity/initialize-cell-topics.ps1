param(
    [Parameter(Mandatory = $true)]
    [int]$CellCount,
    [string]$Brokers = "localhost:9092",
    [int]$RawPartitionsPerCell = 96,
    [int]$DlqPartitionsPerCell = 24,
    [int]$Replicas = 3
)

$ErrorActionPreference = "Stop"
if ($CellCount -lt 1 -or $CellCount -gt 1024) { throw "CellCount must be between 1 and 1024." }
if ($RawPartitionsPerCell -le 0 -or $DlqPartitionsPerCell -le 0) { throw "Partition counts must be positive." }
if ($Replicas -lt 1) { throw "Replicas must be positive." }
if (-not (Get-Command rpk -ErrorAction SilentlyContinue)) { throw "rpk is required to initialize cell topics." }

for ($index = 0; $index -lt $CellCount; $index++) {
    $cell = "cell-$index"
    $raw = "telemetry.raw.$cell"
    $dlq = "telemetry.dead-letter.$cell"

    & rpk topic create $raw --brokers $Brokers --partitions $RawPartitionsPerCell --replicas $Replicas
    if ($LASTEXITCODE -ne 0) {
        & rpk topic describe $raw --brokers $Brokers | Out-Null
        if ($LASTEXITCODE -ne 0) { throw "Unable to create or verify $raw." }
    }
    & rpk topic create $dlq --brokers $Brokers --partitions $DlqPartitionsPerCell --replicas $Replicas
    if ($LASTEXITCODE -ne 0) {
        & rpk topic describe $dlq --brokers $Brokers | Out-Null
        if ($LASTEXITCODE -ne 0) { throw "Unable to create or verify $dlq." }
    }
    & rpk topic alter-config $raw --brokers $Brokers --set min.insync.replicas=2
    if ($LASTEXITCODE -ne 0) { throw "Unable to set min ISR for $raw." }
    & rpk topic alter-config $dlq --brokers $Brokers --set min.insync.replicas=2
    if ($LASTEXITCODE -ne 0) { throw "Unable to set min ISR for $dlq." }
}
