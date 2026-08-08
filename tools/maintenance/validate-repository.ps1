[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$script:passes = 0
$script:failures = 0
$script:warnings = 0

function Write-Result {
    param(
        [ValidateSet('PASS', 'WARN', 'FAIL')]
        [string]$Level,
        [string]$Message
    )

    Write-Host ("{0} {1}" -f $Level, $Message)
    if ($Level -eq 'PASS') { $script:passes++ }
    if ($Level -eq 'WARN') { $script:warnings++ }
    if ($Level -eq 'FAIL') { $script:failures++ }
}

function Test-RequiredPath {
    param([string]$RelativePath, [string]$Kind)

    $path = Join-Path $root $RelativePath
    $exists = if ($Kind -eq 'folder') {
        Test-Path -LiteralPath $path -PathType Container
    } else {
        Test-Path -LiteralPath $path -PathType Leaf
    }

    if ($exists) {
        Write-Result PASS ("Required {0}: {1}" -f $Kind, $RelativePath)
    } else {
        Write-Result FAIL ("Missing required {0}: {1}" -f $Kind, $RelativePath)
    }
}

Write-Host "ALELS repository validation (read-only)"
Write-Host ("Root: {0}" -f $root)

$requiredFolders = @(
    'backend',
    'database',
    'database/migrations',
    'deploy',
    'docs',
    'gateway',
    'ingestion-service',
    'tools',
    'tools/capacity',
    'tools/capacity/workloads',
    'tools/maintenance',
    'deploy/ha',
    'web-react'
)

$requiredDocuments = @(
    'docs/ENGINEERING_CONSTITUTION.md',
    'docs/REPOSITORY_STANDARD.md',
    'docs/BACKEND_STRUCTURE_STANDARD.md',
    'docs/FRONTEND_STRUCTURE_STANDARD.md',
    'docs/DATABASE_STRUCTURE_STANDARD.md',
    'docs/GATEWAY_STANDARD.md',
    'docs/MIGRATION_STANDARD.md',
    'docs/TESTING_STANDARD.md',
    'docs/RELEASE_STANDARD.md',
    'docs/UI_FREEZE_RULES.md',
    'docs/RBAC_FREEZE_RULES.md',
    'docs/API_COMPATIBILITY_RULES.md',
    'docs/TELEMETRY_INTEGRITY_RULES.md',
    'docs/DEFINITION_OF_DONE.md',
    'docs/P2_HA_CAPACITY_CERTIFICATION.md'
)

foreach ($folder in $requiredFolders) {
    Test-RequiredPath $folder 'folder'
}
foreach ($document in $requiredDocuments) {
    Test-RequiredPath $document 'file'
}
Test-RequiredPath '.gitignore' 'file'
Test-RequiredPath '.env.example' 'file'
Test-RequiredPath 'tools/capacity/GatewayCapacityProbe.java' 'file'
Test-RequiredPath 'tools/capacity/check-telemetry-reconciliation.sql' 'file'
Test-RequiredPath 'tools/database/check-p2-ha.sql' 'file'
Test-RequiredPath 'deploy/ha/haproxy.cfg' 'file'
Test-RequiredPath 'deploy/ha/docker-compose.capacity-lab.yml' 'file'
Test-RequiredPath 'deploy/ha/prometheus-rules.yml' 'file'

$workloadFiles = @(Get-ChildItem -LiteralPath (Join-Path $root 'tools/capacity/workloads') -File -Filter '*.json')
foreach ($workloadFile in $workloadFiles) {
    try {
        $workload = Get-Content -LiteralPath $workloadFile.FullName -Raw | ConvertFrom-Json
        if ([int64]$workload.concurrentDevices -le 0 -or [int]$workload.minimumHeadroomPercent -lt 30) {
            Write-Result FAIL ("Invalid capacity workload: {0}" -f $workloadFile.Name)
        } else {
            Write-Result PASS ("Capacity workload is valid: {0}" -f $workloadFile.Name)
        }
    } catch {
        Write-Result FAIL ("Capacity workload JSON is invalid: {0}" -f $workloadFile.Name)
    }
}

$seedPath = Join-Path $root 'database/seed.sql'
if (Test-Path -LiteralPath $seedPath -PathType Leaf) {
    $seed = Get-Content -LiteralPath $seedPath -Raw
    if ($seed -match '(?is)ON\s+CONFLICT.*DO\s+UPDATE\s+SET.*password_hash\s*=\s*EXCLUDED\.password_hash') {
        Write-Result FAIL 'database/seed.sql may overwrite an existing password hash.'
    } else {
        Write-Result PASS 'Seed does not overwrite existing password hashes.'
    }
}

$trackedText = @(& git -C $root grep -n -I -E 'Alels2026!|Alels@2026!|alels1234567' -- ':!docs/AUDIT_ALELS_PLATFORM_1M_2026-08-08.md' ':!tools/maintenance/validate-repository.ps1' 2>$null)
if ($trackedText.Count -gt 0) {
    foreach ($match in $trackedText) { Write-Result FAIL ("Known password found in tracked source: {0}" -f $match) }
} else {
    Write-Result PASS 'No known historical passwords found in tracked source.'
}

$migrationRoot = Join-Path $root 'database/migrations'
$migrationFiles = @(Get-ChildItem -LiteralPath $migrationRoot -File -Filter '*.sql' -ErrorAction SilentlyContinue)
$validMigrations = @()
foreach ($file in $migrationFiles) {
    if ($file.Name -match '^(?<number>\d{3})_[a-z0-9][a-z0-9_-]*\.sql$') {
        $validMigrations += [pscustomobject]@{
            Number = [int]$Matches.number
            Name = $file.Name
        }
    } else {
        Write-Result FAIL ("Invalid migration filename: database/migrations/{0}" -f $file.Name)
    }
}

if ($migrationFiles.Count -eq 0) {
    Write-Result WARN 'No migration SQL files found.'
} elseif ($validMigrations.Count -eq $migrationFiles.Count) {
    Write-Result PASS ("Migration filenames are valid ({0} files)." -f $validMigrations.Count)
}

$duplicates = @($validMigrations | Group-Object Number | Where-Object Count -gt 1)
if ($duplicates.Count -gt 0) {
    foreach ($duplicate in $duplicates) {
        Write-Result FAIL ("Duplicate migration number {0:D3}: {1}" -f [int]$duplicate.Name, (($duplicate.Group.Name) -join ', '))
    }
} else {
    Write-Result PASS 'Migration numbers are unique.'
}

if ($validMigrations.Count -gt 0) {
    $numbers = @($validMigrations.Number | Sort-Object -Unique)
    $expected = @($numbers[0]..$numbers[-1])
    $missing = @($expected | Where-Object { $_ -notin $numbers })
    if ($numbers[0] -ne 1) {
        Write-Result FAIL ("Migration sequence starts at {0:D3}, expected 001." -f $numbers[0])
    } elseif ($missing.Count -gt 0) {
        Write-Result FAIL ("Missing migration numbers: {0}" -f (($missing | ForEach-Object { '{0:D3}' -f $_ }) -join ', '))
    } else {
        Write-Result PASS ("Migration sequence is contiguous (001-{0:D3})." -f $numbers[-1])
    }
}

$generatedDirectories = @(
    'backend/target',
    'gateway/target',
    'ingestion-service/target',
    'web-react/node_modules',
    'web-react/dist',
    'web-react/coverage'
)
foreach ($relativePath in $generatedDirectories) {
    if (Test-Path -LiteralPath (Join-Path $root $relativePath) -PathType Container) {
        Write-Result WARN ("Generated folder present locally (must remain untracked): {0}" -f $relativePath)
    } else {
        Write-Result PASS ("Generated folder absent: {0}" -f $relativePath)
    }
}

$gitCommand = Get-Command git -ErrorAction SilentlyContinue
if ($null -eq $gitCommand) {
    Write-Result WARN 'Git is unavailable; tracked generated files were not checked.'
} else {
    $tracked = @(& git -C $root ls-files)
    if ($LASTEXITCODE -ne 0) {
        Write-Result WARN 'Git could not enumerate tracked files.'
    } else {
        $generatedPattern = '(^|/)(target|node_modules|dist|build|coverage|logs|uploads)/|[.](class|jar|log)$'
        $trackedGenerated = @($tracked | Where-Object { $_ -match $generatedPattern })
        if ($trackedGenerated.Count -gt 0) {
            foreach ($file in $trackedGenerated) {
                Write-Result FAIL ("Generated/runtime file is tracked: {0}" -f $file)
            }
        } else {
            Write-Result PASS 'No generated/runtime files are tracked.'
        }
    }
}

Write-Host ("Summary: PASS={0} WARN={1} FAIL={2}" -f $script:passes, $script:warnings, $script:failures)
if ($script:failures -gt 0) {
    exit 1
}
exit 0
