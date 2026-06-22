$ErrorActionPreference = "Stop"

$ProjectRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$BackendRoot = Join-Path $ProjectRoot "backend"
$JavaRoot = Join-Path $BackendRoot "src\main\java"
$Canonical = Join-Path $JavaRoot "com\alels\backend\company\service\CompanyRegisterService.java"

Write-Host "Project root: $ProjectRoot"
Write-Host "Backend root: $BackendRoot"

if (!(Test-Path $Canonical)) {
    throw "Canonical CompanyRegisterService not found: $Canonical"
}

$duplicates = Get-ChildItem $JavaRoot -Recurse -Filter "CompanyRegisterService.java" |
    Where-Object { $_.FullName -ne $Canonical }

if ($duplicates.Count -eq 0) {
    Write-Host "No duplicate CompanyRegisterService.java found."
} else {
    foreach ($file in $duplicates) {
        Write-Host "Removing duplicate: $($file.FullName)"
        Remove-Item -Force $file.FullName
    }
}

$Contract = Join-Path $JavaRoot "com\alels\backend\domain\company\AlelsRootCompanyContract.java"
if (!(Test-Path $Contract)) {
    throw "AlelsRootCompanyContract.java is missing. Please copy the patch backend/src content into backend/src first."
}

Write-Host "OK: CompanyRegisterService duplicate cleanup completed."
Write-Host "OK: AlelsRootCompanyContract exists."
