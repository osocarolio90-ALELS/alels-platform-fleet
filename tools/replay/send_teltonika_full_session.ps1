param(
    [ValidateNotNullOrEmpty()]
    [string]$HostName = "127.0.0.1",

    [ValidateRange(1, 65535)]
    [int]$Port = 5050,

    [ValidatePattern('^\d{15}$')]
    [string]$Imei = "123456789876543",

    [ValidateRange(100, 60000)]
    [int]$TimeoutMs = 5000
)

$codec8eHex = "000000000000004A8E010000016B412CEE000100000000000000000000000000000000010005000100010100010011001D00010010015E2C880002000B000000003544C87A000E000000001DD7E06A00000100002994"
$client = $null
$stream = $null

function Convert-HexToBytes([string]$Hex) {
    if (($Hex.Length % 2) -ne 0) {
        throw "Hex payload length must be even."
    }

    [byte[]]$bytes = New-Object byte[] ($Hex.Length / 2)
    for ($index = 0; $index -lt $Hex.Length; $index += 2) {
        $bytes[$index / 2] = [Convert]::ToByte($Hex.Substring($index, 2), 16)
    }
    return $bytes
}

function Read-Exact([System.IO.Stream]$InputStream, [int]$Length) {
    [byte[]]$buffer = New-Object byte[] $Length
    $offset = 0
    while ($offset -lt $Length) {
        $read = $InputStream.Read($buffer, $offset, $Length - $offset)
        if ($read -le 0) {
            throw "Gateway closed the connection before returning $Length response byte(s)."
        }
        $offset += $read
    }
    return $buffer
}

try {
    $client = New-Object System.Net.Sockets.TcpClient
    $connect = $client.BeginConnect($HostName, $Port, $null, $null)
    if (-not $connect.AsyncWaitHandle.WaitOne($TimeoutMs)) {
        throw "Connection timeout to ${HostName}:$Port."
    }
    $client.EndConnect($connect)

    $stream = $client.GetStream()
    $stream.ReadTimeout = $TimeoutMs
    $stream.WriteTimeout = $TimeoutMs

    [byte[]]$imeiBytes = [System.Text.Encoding]::ASCII.GetBytes($Imei)
    [byte[]]$imeiPacket = New-Object byte[] ($imeiBytes.Length + 2)
    $imeiPacket[0] = [byte](($imeiBytes.Length -shr 8) -band 0xFF)
    $imeiPacket[1] = [byte]($imeiBytes.Length -band 0xFF)
    [Array]::Copy($imeiBytes, 0, $imeiPacket, 2, $imeiBytes.Length)

    Write-Host "[TCP] Connected to ${HostName}:$Port"
    Write-Host "[SEND] IMEI $Imei"
    $stream.Write($imeiPacket, 0, $imeiPacket.Length)
    $stream.Flush()

    [byte[]]$imeiAck = Read-Exact $stream 1
    Write-Host "[RECV] IMEI ACK $([BitConverter]::ToString($imeiAck))"
    if ($imeiAck[0] -ne 1) {
        throw "IMEI was rejected by the gateway."
    }

    [byte[]]$codec8eBytes = Convert-HexToBytes $codec8eHex
    Write-Host "[SEND] Codec 8E frame bytes=$($codec8eBytes.Length) records=1"
    $stream.Write($codec8eBytes, 0, $codec8eBytes.Length)
    $stream.Flush()

    [byte[]]$avlAck = Read-Exact $stream 4
    $acceptedRecords = ([int]$avlAck[0] -shl 24) -bor
                       ([int]$avlAck[1] -shl 16) -bor
                       ([int]$avlAck[2] -shl 8) -bor
                       [int]$avlAck[3]
    Write-Host "[RECV] AVL ACK $([BitConverter]::ToString($avlAck)) accepted=$acceptedRecords"
    if ($acceptedRecords -ne 1) {
        throw "Gateway did not acknowledge the expected AVL record."
    }

    Write-Host "[PASS] Teltonika TCP handshake and Codec 8E response are valid."
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
