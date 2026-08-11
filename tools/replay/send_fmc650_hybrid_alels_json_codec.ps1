param(
    [ValidateNotNullOrEmpty()]
    [string]$HostName = "127.0.0.1",

    [ValidateRange(1, 65535)]
    [int]$Port = 5050,

    [ValidatePattern('^\d{15}$')]
    [string]$Imei = "123456789876543",

    [ValidateRange(0, 3600)]
    [int]$IntervalSeconds = 5,

    [ValidateRange(0, 60)]
    [int]$TransitionDelaySeconds = 2,

    [ValidateRange(100, 60000)]
    [int]$TimeoutMs = 10000
)

$ErrorActionPreference = "Stop"
$scriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$alelsJsonScript = Join-Path $scriptRoot "send_alels_json_fmc650_session.ps1"
$codecScript = Join-Path $scriptRoot "send_teltonika_plaza_indonesia_to_sarinah.ps1"

function Invoke-TestPhase([string]$Name, [string]$Script, [int]$StartPoint, [int]$PointCount) {
    Write-Host ""
    Write-Host "================================================================"
    Write-Host "[HYBRID PHASE] $Name | route points $($StartPoint + 1)-$($StartPoint + $PointCount)"
    Write-Host "================================================================"
    & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $Script `
        -HostName $HostName -Port $Port -Imei $Imei `
        -StartPoint $StartPoint -PointCount $PointCount `
        -IntervalSeconds $IntervalSeconds -TimeoutMs $TimeoutMs
    if ($LASTEXITCODE -ne 0) { throw "$Name failed with exit code $LASTEXITCODE." }
    if ($TransitionDelaySeconds -gt 0) { Start-Sleep -Seconds $TransitionDelaySeconds }
}

try {
    Write-Host "[HYBRID TEST] IMEI=$Imei gateway=${HostName}:$Port"
    Write-Host "[ROUTE] Plaza Indonesia -> Sarinah | 20 chronological points"
    Write-Host "[RULE] Only one transport is active in each phase."

    Invoke-TestPhase "ALELS JSON / WIFI" $alelsJsonScript 0 7
    Invoke-TestPhase "TELTONIKA CODEC 8E / GSM" $codecScript 7 7
    Invoke-TestPhase "ALELS JSON / WIFI (FAILOVER RETURN)" $alelsJsonScript 14 6

    Write-Host ""
    Write-Host "[PASS] Hybrid FMC650 simulation completed."
    Write-Host "[EXPECTED SOURCE HISTORY] WIFI -> GSM -> WIFI"
    Write-Host "[EXPECTED DICTIONARY] FMC650 for all 20 records"
    Write-Host "[EXPECTED TRACK] One ordered Plaza Indonesia-to-Sarinah route without backward jumps"
    Write-Host "[EXPECTED CURRENT SOURCE] WIFI / ALELS_JSON"
    exit 0
}
catch {
    Write-Error "[FAIL] $($_.Exception.Message)"
    exit 1
}
