param(
    [int]$MonthsAhead = 3,
    [switch]$ApplyRetention
)

$ErrorActionPreference = "Stop"
if ($MonthsAhead -lt 1 -or $MonthsAhead -gt 12) { throw "MonthsAhead must be between 1 and 12." }

$required = @("ALELS_DB_USER", "ALELS_DB_PASSWORD")
foreach ($name in $required) {
    if ([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($name))) {
        throw "$name is required."
    }
}

$psql = if ($env:ALELS_PSQL_PATH) { $env:ALELS_PSQL_PATH } else { "psql.exe" }
$dbHost = if ($env:ALELS_DB_HOST) { $env:ALELS_DB_HOST } else { "127.0.0.1" }
$dbPort = if ($env:ALELS_DB_PORT) { $env:ALELS_DB_PORT } else { "5432" }
$dbName = if ($env:ALELS_DB_NAME) { $env:ALELS_DB_NAME } else { "alels_db" }
$env:PGPASSWORD = $env:ALELS_DB_PASSWORD

try {
    & $psql -X -v ON_ERROR_STOP=1 -h $dbHost -p $dbPort -U $env:ALELS_DB_USER -d $dbName -c "SELECT alels_ensure_runtime_partitions($MonthsAhead);"
    if ($LASTEXITCODE -ne 0) { throw "Partition creation failed." }

    $dryRun = if ($ApplyRetention) { "false" } else { "true" }
    foreach ($table in @("telemetry", "raw_packets", "tcp_logs")) {
        & $psql -X -v ON_ERROR_STOP=1 -h $dbHost -p $dbPort -U $env:ALELS_DB_USER -d $dbName -c "SELECT * FROM alels_apply_retention('$table', $dryRun);"
        if ($LASTEXITCODE -ne 0) { throw "Retention maintenance failed for $table." }
    }
} finally {
    Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue
}

Write-Output $(if ($ApplyRetention) { "Partition and approved retention maintenance completed." } else { "Partition maintenance and retention dry-run completed." })
