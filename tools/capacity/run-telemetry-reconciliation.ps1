param(
    [string]$Database = "alels_db",
    [string]$User = "alels",
    [string]$HostName = "localhost",
    [int]$Port = 5432,
    [int]$WindowHours = 24,
    [string]$PsqlPath = "C:\Program Files\PostgreSQL\18\bin\psql.exe"
)

$ErrorActionPreference = "Stop"
if ($WindowHours -le 0) { throw "WindowHours must be greater than zero." }
$scriptPath = Join-Path $PSScriptRoot "check-telemetry-reconciliation.sql"
& $PsqlPath -X -v ON_ERROR_STOP=1 -v window_hours=$WindowHours `
    -h $HostName -p $Port -U $User -d $Database -f $scriptPath
if ($LASTEXITCODE -ne 0) { throw "ALELS telemetry reconciliation failed." }
