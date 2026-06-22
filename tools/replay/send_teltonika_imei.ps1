$client = New-Object System.Net.Sockets.TcpClient("127.0.0.1",5050)

$stream = $client.GetStream()

$imei = "356307042441013"

$imeiBytes =
    [System.Text.Encoding]::ASCII.GetBytes($imei)

$packet =
    New-Object byte[] ($imeiBytes.Length + 2)

$packet[0] = 0
$packet[1] = $imeiBytes.Length

[Array]::Copy(
    $imeiBytes,
    0,
    $packet,
    2,
    $imeiBytes.Length
)

$stream.Write(
    $packet,
    0,
    $packet.Length
)

$buffer =
    New-Object byte[] 32

$count =
    $stream.Read(
        $buffer,
        0,
        $buffer.Length
    )

Write-Host "SERVER RESPONSE:"

[BitConverter]::ToString(
    $buffer,
    0,
    $count
)

$stream.Close()
$client.Close()