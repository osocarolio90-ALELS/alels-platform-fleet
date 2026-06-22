param(
    [string]$Database = "alels_db",
    [string]$User = "alels",
    [string]$HostName = "localhost",
    [string]$Port = "5432",
    [string]$PsqlPath = "C:\Program Files\PostgreSQL\18\bin\psql.exe",
    [switch]$Seed
)

$ErrorActionPreference = "Stop"

if (!(Test-Path $PsqlPath)) {
    throw "psql.exe tidak ditemukan di $PsqlPath"
}

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$MigrationDir = Join-Path $ScriptDir "migrations"

if (!(Test-Path $MigrationDir)) {
    throw "Folder migrations tidak ditemukan: $MigrationDir"
}

Get-ChildItem $MigrationDir -Filter "*.sql" | Sort-Object Name | ForEach-Object {
    Write-Host "Running migration $($_.Name)" -ForegroundColor Cyan
    & $PsqlPath -h $HostName -p $Port -U $User -d $Database -v ON_ERROR_STOP=1 -f $_.FullName
    if ($LASTEXITCODE -ne 0) {
        throw "Migration failed: $($_.FullName)"
    }
}

if ($Seed) {
    $SeedFile = Join-Path $ScriptDir "seed.sql"
    if (Test-Path $SeedFile) {
        Write-Host "Running seed.sql" -ForegroundColor Cyan
        & $PsqlPath -h $HostName -p $Port -U $User -d $Database -v ON_ERROR_STOP=1 -f $SeedFile
        if ($LASTEXITCODE -ne 0) {
            throw "Seed failed: $SeedFile"
        }
    }
}

Write-Host "Database migration completed." -ForegroundColor Green
