$client = New-Object System.Net.Sockets.TcpClient("127.0.0.1",5050)
$stream = $client.GetStream()

# ================================
# 1. SEND TELTONIKA IMEI HANDSHAKE
# ================================
$imei = "356307041111111"
$imeiBytes = [System.Text.Encoding]::ASCII.GetBytes($imei)

$imeiPacket = New-Object byte[] ($imeiBytes.Length + 2)
$imeiPacket[0] = 0
$imeiPacket[1] = $imeiBytes.Length

[Array]::Copy($imeiBytes, 0, $imeiPacket, 2, $imeiBytes.Length)

Write-Host "SEND IMEI:"
[BitConverter]::ToString($imeiPacket)

$stream.Write($imeiPacket, 0, $imeiPacket.Length)

$buffer = New-Object byte[] 64
$count = $stream.Read($buffer, 0, $buffer.Length)

Write-Host "IMEI ACK:"
[BitConverter]::ToString($buffer, 0, $count)

Start-Sleep -Milliseconds 300

# ================================
# 2. SEND VALID TELTONIKA CODEC8E
# ================================
$hex = "000000000000004A8E010000016B412CEE000100000000000000000000000000000000010005000100010100010011001D00010010015E2C880002000B000000003544C87A000E000000001DD7E06A00000100002994"

$bytes = for ($i = 0; $i -lt $hex.Length; $i += 2) {
    [Convert]::ToByte($hex.Substring($i, 2), 16)
}

Write-Host "SEND CODEC8E:"
[BitConverter]::ToString($bytes)

$stream.Write($bytes, 0, $bytes.Length)

$buffer2 = New-Object byte[] 64
$count2 = $stream.Read($buffer2, 0, $buffer2.Length)

Write-Host "AVL ACK:"
[BitConverter]::ToString($buffer2, 0, $count2)

$stream.Close()
$client.Close()