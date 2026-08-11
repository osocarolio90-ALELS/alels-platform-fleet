param(
    [ValidateNotNullOrEmpty()]
    [string]$HostName = "127.0.0.1",

    [ValidateRange(1, 65535)]
    [int]$Port = 5050,

    [ValidatePattern('^\d{15}$')]
    [string]$Imei = "123456789876543",

    [ValidateRange(0, 19)]
    [int]$StartPoint = 0,

    [ValidateRange(1, 20)]
    [int]$PointCount = 20,

    [ValidateRange(0, 3600)]
    [int]$IntervalSeconds = 5,

    [ValidateRange(100, 60000)]
    [int]$TimeoutMs = 10000,

    [switch]$DryRun
)

$ErrorActionPreference = "Stop"
$utf8 = New-Object System.Text.UTF8Encoding($false)
$client = $null
$stream = $null

# Ordered points along Jl. M.H. Thamrin, Plaza Indonesia to Sarinah.
$route = @(
    @(-6.19372, 106.82291), @(-6.19338, 106.82296), @(-6.19303, 106.82300),
    @(-6.19268, 106.82304), @(-6.19233, 106.82308), @(-6.19198, 106.82312),
    @(-6.19163, 106.82316), @(-6.19128, 106.82320), @(-6.19093, 106.82324),
    @(-6.19058, 106.82329), @(-6.19023, 106.82334), @(-6.18988, 106.82339),
    @(-6.18953, 106.82344), @(-6.18918, 106.82349), @(-6.18883, 106.82354),
    @(-6.18848, 106.82359), @(-6.18813, 106.82364), @(-6.18778, 106.82369),
    @(-6.18743, 106.82374), @(-6.18712, 106.82379)
)

function Get-Bearing([double]$FromLatitude, [double]$FromLongitude, [double]$ToLatitude, [double]$ToLongitude) {
    $lat1 = $FromLatitude * [Math]::PI / 180
    $lat2 = $ToLatitude * [Math]::PI / 180
    $deltaLongitude = ($ToLongitude - $FromLongitude) * [Math]::PI / 180
    $y = [Math]::Sin($deltaLongitude) * [Math]::Cos($lat2)
    $x = [Math]::Cos($lat1) * [Math]::Sin($lat2) - [Math]::Sin($lat1) * [Math]::Cos($lat2) * [Math]::Cos($deltaLongitude)
    return [int][Math]::Round((([Math]::Atan2($y, $x) * 180 / [Math]::PI) + 360) % 360)
}

function Get-DistanceMeters([double]$FromLatitude, [double]$FromLongitude, [double]$ToLatitude, [double]$ToLongitude) {
    $earthRadius = 6371000.0
    $lat1 = $FromLatitude * [Math]::PI / 180
    $lat2 = $ToLatitude * [Math]::PI / 180
    $deltaLat = ($ToLatitude - $FromLatitude) * [Math]::PI / 180
    $deltaLon = ($ToLongitude - $FromLongitude) * [Math]::PI / 180
    $a = [Math]::Sin($deltaLat / 2) * [Math]::Sin($deltaLat / 2) + [Math]::Cos($lat1) * [Math]::Cos($lat2) * [Math]::Sin($deltaLon / 2) * [Math]::Sin($deltaLon / 2)
    return $earthRadius * 2 * [Math]::Atan2([Math]::Sqrt($a), [Math]::Sqrt(1 - $a))
}

function Add-FinalPacketSize([System.Collections.IDictionary]$Packet) {
    $Packet["S"] = 0
    for ($attempt = 0; $attempt -lt 10; $attempt++) {
        $json = $Packet | ConvertTo-Json -Compress -Depth 6
        $actualSize = $utf8.GetByteCount($json)
        if ([long]$Packet["S"] -eq $actualSize) { return $json }
        $Packet["S"] = $actualSize
    }
    throw "Packet size S did not converge."
}

function Write-JsonLine([string]$Json) {
    [byte[]]$bytes = $utf8.GetBytes($Json + "`n")
    $stream.Write($bytes, 0, $bytes.Length)
    $stream.Flush()
}

function Read-GatewayLine {
    $buffer = New-Object 'System.Collections.Generic.List[byte]'
    while ($true) {
        $value = $stream.ReadByte()
        if ($value -lt 0) { throw "Gateway closed the TCP connection." }
        if ($value -eq 10) { break }
        if ($value -ne 13) {
            if ($buffer.Count -ge 4096) { throw "Gateway response exceeded 4096 bytes." }
            $buffer.Add([byte]$value)
        }
    }
    return $utf8.GetString($buffer.ToArray()).Trim()
}

function Send-And-RequireAck([string]$Json, [string]$Label) {
    Write-JsonLine $Json
    $ack = Read-GatewayLine
    if ($ack -ne "01") { throw "$Label rejected; expected ACK 01, received '$ack'." }
    return $ack
}

try {
    if (($StartPoint + $PointCount) -gt $route.Count) {
        throw "StartPoint + PointCount must not exceed $($route.Count)."
    }
    $sequenceBase = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
    $packets = New-Object 'System.Collections.Generic.List[string]'

    for ($index = 0; $index -lt $PointCount; $index++) {
        $routeIndex = $StartPoint + $index
        $nextIndex = [Math]::Min($routeIndex + 1, $route.Count - 1)
        $previousIndex = [Math]::Max(0, $routeIndex - 1)
        $from = if ($routeIndex -lt ($route.Count - 1)) { $route[$routeIndex] } else { $route[$previousIndex] }
        $to = if ($routeIndex -lt ($route.Count - 1)) { $route[$nextIndex] } else { $route[$routeIndex] }
        $angle = Get-Bearing $from[0] $from[1] $to[0] $to[1]
        $distance = if ($routeIndex -eq 0) { 0 } else { Get-DistanceMeters $route[$previousIndex][0] $route[$previousIndex][1] $route[$routeIndex][0] $route[$routeIndex][1] }
        $speed = if ($routeIndex -eq 0 -or $routeIndex -eq ($route.Count - 1) -or $IntervalSeconds -eq 0) { 0 } else { [int][Math]::Max(3, [Math]::Round(($distance / $IntervalSeconds) * 3.6)) }
        $packet = [ordered]@{
            T = "data"
            Q = [long]($sequenceBase + $index)
            A = $Imei
            B = [DateTime]::UtcNow.AddSeconds($index * $IntervalSeconds).ToString("yyyy-MM-dd HH:mm:ss", [Globalization.CultureInfo]::InvariantCulture)
            C = 0.8
            D = [int][Math]::Round($route[$routeIndex][1] * 10000000)
            E = [int][Math]::Round($route[$routeIndex][0] * 10000000)
            F = 11 + ($routeIndex % 3)
            G = $angle
            H = 12
            I = $speed
            J = 0
            K = 239
            "14" = 12000 + $routeIndex
            "16" = 2294900 + ($routeIndex * 42)
            "21" = 4
            "35" = $(if ($speed -gt 0) { 900 + ($speed * 35) } else { 720 })
            "37" = 76 - [Math]::Floor($routeIndex / 6)
            "66" = 13780
            "67" = 4040
            "239" = 1
            "240" = $(if ($speed -gt 0) { 1 } else { 0 })
        }
        $packets.Add((Add-FinalPacketSize $packet))
    }

    if ($DryRun) {
        Write-Host "[DRY RUN] ALELS JSON packets=$PointCount imei=$Imei"
        for ($index = 0; $index -lt $packets.Count; $index++) {
            $parsed = $packets[$index] | ConvertFrom-Json
            $actual = $utf8.GetByteCount($packets[$index])
            if ([long]$parsed.S -ne $actual) { throw "Packet $($index + 1) has invalid S=$($parsed.S), actual=$actual." }
            Write-Host ("[{0:00}/{1:00}] Q={2} S={3} latRaw={4} lonRaw={5}" -f ($index + 1), $PointCount, $parsed.Q, $parsed.S, $parsed.E, $parsed.D)
        }
        Write-Host "[PASS] All generated packets are valid JSON and S matches the UTF-8 byte length."
        exit 0
    }

    $client = New-Object System.Net.Sockets.TcpClient
    $client.NoDelay = $true
    $connect = $client.BeginConnect($HostName, $Port, $null, $null)
    if (-not $connect.AsyncWaitHandle.WaitOne($TimeoutMs)) { throw "Connection timeout to ${HostName}:$Port." }
    $client.EndConnect($connect)
    $stream = $client.GetStream()
    $stream.ReadTimeout = $TimeoutMs
    $stream.WriteTimeout = $TimeoutMs

    $identity = [ordered]@{ T = "id"; imei = $Imei; message = "ALELS HUB test connected" } | ConvertTo-Json -Compress
    $null = Send-And-RequireAck $identity "Identity handshake"
    Write-Host "[PASS] ALELS JSON identity accepted: imei=$Imei ACK=01"
    Write-Host "[SIMULATION] protocol=ALELS_JSON channel=WIFI dictionary=FMC650 points=$PointCount interval=${IntervalSeconds}s"

    for ($index = 0; $index -lt $packets.Count; $index++) {
        $timer = [Diagnostics.Stopwatch]::StartNew()
        $null = Send-And-RequireAck $packets[$index] "Telemetry point $($index + 1)"
        $timer.Stop()
        $parsed = $packets[$index] | ConvertFrom-Json
        Write-Host ("[{0:00}/{1:00}] ACK=01 latency={2}ms Q={3} lat={4:F5} lon={5:F5} speed={6}km/h S={7}" -f ($index + 1), $PointCount, $timer.ElapsedMilliseconds, $parsed.Q, ($parsed.E / 10000000), ($parsed.D / 10000000), $parsed.I, $parsed.S)
        if ($index -lt ($packets.Count - 1) -and $IntervalSeconds -gt 0) { Start-Sleep -Seconds $IntervalSeconds }
    }

    $heartbeat = [ordered]@{ T = "hb"; A = $Imei; Q = [long]($sequenceBase + $PointCount); W = -55; P = 0; H = 180000; U = 60000 } | ConvertTo-Json -Compress
    $null = Send-And-RequireAck $heartbeat "Heartbeat"
    Write-Host "[PASS] ALELS JSON session complete; heartbeat ACK=01."
    Write-Host "[EXPECTED] Device Workspace: Connection=WIFI, Protocol=ALELS_JSON, dictionary=FMC650, status=ONLINE."
    exit 0
}
catch {
    Write-Error "[FAIL] $($_.Exception.Message)"
    exit 1
}
finally {
    if ($null -ne $stream) { $stream.Dispose() }
    if ($null -ne $client) { $client.Dispose() }
}
