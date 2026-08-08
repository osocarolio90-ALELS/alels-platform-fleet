param(
    [string]$Database = "alels_db",
    [string]$User = "alels",
    [string]$HostName = "localhost",
    [int]$Port = 5432,
    [string]$PsqlPath = "C:\Program Files\PostgreSQL\18\bin\psql.exe"
)

$scriptPath = Join-Path $PSScriptRoot "check-phase1-foundation.sql"
& $PsqlPath -X -v ON_ERROR_STOP=1 -h $HostName -p $Port -U $User -d $Database -f $scriptPath
if ($LASTEXITCODE -ne 0) { throw "ALELS P0/P1 database readiness check failed." }
