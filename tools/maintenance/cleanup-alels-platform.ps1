param(
    [switch]$RemovePatchReports = $true,
    [switch]$RemoveBuildArtifacts = $true,
    [switch]$RemoveNodeModules = $true
)

$ErrorActionPreference = "Stop"
$Root = Resolve-Path (Join-Path $PSScriptRoot "..\..")
Set-Location $Root

Write-Host "ALELS cleanup running in: $Root" -ForegroundColor Cyan

if ($RemovePatchReports) {
    $reportFiles = @(
        "AI_OPS_V4_GATEWAY_MONITOR_PATCH_REPORT.md",
        "DATABASE_MONITOR_ACCURACY_FIX_REPORT.md",
        "DATABASE_MONITOR_BUG_FIX_REPORT.md",
        "DATABASE_MONITOR_COMPILE_FIX_REPORT.md",
        "FRONTEND_DATABASE_RESTRUCTURE_REPORT.md",
        "ROLE_SCOPE_ROOT_COMPANY_AUDIT.md",
        "SERVER_MONITOR_REUSABLE_FRAMEWORK_REPORT.md",
        "SERVER_MONITOR_TRAFFIC_STEP_PATCH_REPORT.md",
        "DELETE_WRONG_DUPLICATE_FILE.ps1"
    )

    foreach ($file in $reportFiles) {
        if (Test-Path $file) {
            git rm -f $file 2>$null
            if (Test-Path $file) { Remove-Item -Force $file }
            Write-Host "Removed root patch/report artifact: $file"
        }
    }
}

if ($RemoveBuildArtifacts) {
    $paths = @(
        "backend\target",
        "gateway\target",
        "ingestion-service\target",
        "web-react\dist",
        "web-react\tsconfig.tsbuildinfo",
        "backend\spring-boot.err.log",
        "backend\spring-boot.out.log",
        "web-react\vite.err.log",
        "web-react\vite.out.log"
    )

    foreach ($path in $paths) {
        if (Test-Path $path) {
            Remove-Item -Recurse -Force $path
            Write-Host "Removed local artifact: $path"
        }
    }
}

if ($RemoveNodeModules) {
    $nodeModules = "web-react\node_modules"
    if (Test-Path $nodeModules) {
        Remove-Item -Recurse -Force $nodeModules
        Write-Host "Removed local dependency folder: $nodeModules"
        Write-Host "Run 'npm install' in web-react before next frontend build." -ForegroundColor Yellow
    }
}

Write-Host "Cleanup complete. Run: git status" -ForegroundColor Green
