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

    [ValidateRange(1, 2147483647)]
    [int]$RandomSeed = 150650
)

# Twenty ordered points along Jl. M.H. Thamrin, from Plaza Indonesia to Sarinah.
$route = @(
    @(-6.19372, 106.82291), @(-6.19338, 106.82296), @(-6.19303, 106.82300),
    @(-6.19268, 106.82304), @(-6.19233, 106.82308), @(-6.19198, 106.82312),
    @(-6.19163, 106.82316), @(-6.19128, 106.82320), @(-6.19093, 106.82324),
    @(-6.19058, 106.82329), @(-6.19023, 106.82334), @(-6.18988, 106.82339),
    @(-6.18953, 106.82344), @(-6.18918, 106.82349), @(-6.18883, 106.82354),
    @(-6.18848, 106.82359), @(-6.18813, 106.82364), @(-6.18778, 106.82369),
    @(-6.18743, 106.82374), @(-6.18712, 106.82379)
)

$client = $null
$stream = $null
$random = New-Object System.Random $RandomSeed

function Add-U8($List, [long]$Value) { $List.Add([byte]($Value -band 0xFF)) }
function Add-U16($List, [int]$Value) {
    Add-U8 $List ($Value -shr 8)
    Add-U8 $List $Value
}
function Add-U32($List, [long]$Value) {
    Add-U8 $List ($Value -shr 24)
    Add-U8 $List ($Value -shr 16)
    Add-U8 $List ($Value -shr 8)
    Add-U8 $List $Value
}
function Add-U64($List, [long]$Value) {
    for ($shift = 56; $shift -ge 0; $shift -= 8) { Add-U8 $List ($Value -shr $shift) }
}
function Read-Exact([System.IO.Stream]$InputStream, [int]$Length) {
    [byte[]]$buffer = New-Object byte[] $Length
    $offset = 0
    while ($offset -lt $Length) {
        $read = $InputStream.Read($buffer, $offset, $Length - $offset)
        if ($read -le 0) { throw "Gateway closed the connection before returning $Length byte(s)." }
        $offset += $read
    }
    return $buffer
}
function Get-Crc16([byte[]]$Bytes) {
    $crc = 0
    foreach ($value in $Bytes) {
        $crc = $crc -bxor $value
        for ($bit = 0; $bit -lt 8; $bit++) {
            if (($crc -band 1) -ne 0) { $crc = (($crc -shr 1) -bxor 0xA001) }
            else { $crc = $crc -shr 1 }
        }
    }
    return $crc -band 0xFFFF
}
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
function New-Codec8EFrame([double]$Latitude, [double]$Longitude, [int]$Speed, [int]$Angle, [int]$Index, [long]$TimestampMs) {
    $data = New-Object 'System.Collections.Generic.List[byte]'
    Add-U8 $data 0x8E
    Add-U8 $data 1
    Add-U64 $data $TimestampMs
    Add-U8 $data 0
    Add-U32 $data ([Math]::Round($Longitude * 10000000))
    Add-U32 $data ([Math]::Round($Latitude * 10000000))
    Add-U16 $data (11 + ($Index % 3))
    Add-U16 $data $Angle
    Add-U8 $data 12
    Add-U16 $data $Speed

    Add-U16 $data 239
    Add-U16 $data 13

    Add-U16 $data 5
    Add-U16 $data 21;  Add-U8 $data (3 + ($Index % 3))
    Add-U16 $data 23;  Add-U8 $data (27 + ($Index % 5))
    Add-U16 $data 37;  Add-U8 $data (76 - [Math]::Floor($Index / 6))
    Add-U16 $data 239; Add-U8 $data 1
    Add-U16 $data 240; Add-U8 $data $(if ($Speed -gt 0) { 1 } else { 0 })

    Add-U16 $data 6
    Add-U16 $data 24;  Add-U16 $data $Speed
    Add-U16 $data 25;  Add-U16 $data (815 + ($Index % 8))
    Add-U16 $data 35;  Add-U16 $data $(if ($Speed -gt 0) { 950 + ($Speed * 35) + $random.Next(-35,36) } else { 720 + $random.Next(-15,16) })
    Add-U16 $data 66;  Add-U16 $data (13760 + $random.Next(-45,46))
    Add-U16 $data 67;  Add-U16 $data (4040 + $random.Next(-18,19))
    Add-U16 $data 182; Add-U16 $data (7 + ($Index % 3))

    Add-U16 $data 2
    Add-U16 $data 14; Add-U32 $data (12000 + $Index)
    Add-U16 $data 16; Add-U32 $data (2294900 + ($Index * 42))

    Add-U16 $data 0
    Add-U16 $data 0
    Add-U8 $data 1

    [byte[]]$dataBytes = $data.ToArray()
    $crc = Get-Crc16 $dataBytes
    $frame = New-Object 'System.Collections.Generic.List[byte]'
    Add-U32 $frame 0
    Add-U32 $frame $dataBytes.Length
    $frame.AddRange($dataBytes)
    Add-U32 $frame $crc
    return $frame.ToArray()
}

try {
    if (($StartPoint + $PointCount) -gt $route.Count) {
        throw "StartPoint + PointCount must not exceed $($route.Count)."
    }
    $client = New-Object System.Net.Sockets.TcpClient
    $client.NoDelay = $true
    $connect = $client.BeginConnect($HostName, $Port, $null, $null)
    if (-not $connect.AsyncWaitHandle.WaitOne($TimeoutMs)) { throw "Connection timeout to ${HostName}:$Port." }
    $client.EndConnect($connect)
    $stream = $client.GetStream()
    $stream.ReadTimeout = $TimeoutMs
    $stream.WriteTimeout = $TimeoutMs

    [byte[]]$imeiBytes = [System.Text.Encoding]::ASCII.GetBytes($Imei)
    [byte[]]$imeiPacket = New-Object byte[] ($imeiBytes.Length + 2)
    $imeiPacket[0] = 0
    $imeiPacket[1] = [byte]$imeiBytes.Length
    [Array]::Copy($imeiBytes, 0, $imeiPacket, 2, $imeiBytes.Length)
    $stream.Write($imeiPacket, 0, $imeiPacket.Length)
    $stream.Flush()
    [byte[]]$imeiAck = Read-Exact $stream 1
    if ($imeiAck[0] -ne 1) { throw "IMEI $Imei was rejected." }

    Write-Host "[PASS] IMEI accepted by gateway (01)."
    Write-Host "[SIMULATION] Teltonika Codec 8E | GSM/TCP | $PointCount points | interval=${IntervalSeconds}s"
    Write-Host "[ROUTE] Plaza Indonesia -> Sarinah, Jl. M.H. Thamrin, Jakarta"
    for ($index = 0; $index -lt $PointCount; $index++) {
        $routeIndex = $StartPoint + $index
        $nextIndex = [Math]::Min($routeIndex + 1, $route.Count - 1)
        $previousIndex = [Math]::Max(0, $routeIndex - 1)
        $from = if ($routeIndex -lt ($route.Count - 1)) { $route[$routeIndex] } else { $route[$previousIndex] }
        $to = if ($routeIndex -lt ($route.Count - 1)) { $route[$nextIndex] } else { $route[$routeIndex] }
        $angle = Get-Bearing $from[0] $from[1] $to[0] $to[1]
        $distance = if ($routeIndex -eq 0) { 0 } else { Get-DistanceMeters $route[$previousIndex][0] $route[$previousIndex][1] $route[$routeIndex][0] $route[$routeIndex][1] }
        $speed = if ($routeIndex -eq 0 -or $routeIndex -eq ($route.Count - 1) -or $IntervalSeconds -eq 0) { 0 } else { [Math]::Max(3, [Math]::Round(($distance / $IntervalSeconds) * 3.6 + $random.Next(-2,3))) }
        $timestamp = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
        [byte[]]$frame = New-Codec8EFrame $route[$routeIndex][0] $route[$routeIndex][1] $speed $angle $routeIndex $timestamp
        $ackTimer = [System.Diagnostics.Stopwatch]::StartNew()
        $stream.Write($frame, 0, $frame.Length)
        $stream.Flush()
        [byte[]]$ack = Read-Exact $stream 4
        $ackTimer.Stop()
        $accepted = ([int]$ack[0] -shl 24) -bor ([int]$ack[1] -shl 16) -bor ([int]$ack[2] -shl 8) -bor [int]$ack[3]
        if ($accepted -ne 1) { throw "Point $($index + 1) was rejected with ACK $([BitConverter]::ToString($ack))." }
        Write-Host ("[{0:00}/{1:00}] ACK=1 latency={2}ms routePoint={3} lat={4:F5} lon={5:F5} speed={6}km/h heading={7}deg sat={8}" -f ($index + 1), $PointCount, $ackTimer.ElapsedMilliseconds, ($routeIndex + 1), $route[$routeIndex][0], $route[$routeIndex][1], $speed, $angle, (11 + ($routeIndex % 3)))
        if ($index -lt ($PointCount - 1) -and $IntervalSeconds -gt 0) { Start-Sleep -Seconds $IntervalSeconds }
    }
    Write-Host "[PASS] Realistic drive session complete. Final position: Sarinah Thamrin."
    Write-Host "[PRESENCE] Expected ONLINE now; expected OFFLINE only after 30 minutes without a new packet."
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
