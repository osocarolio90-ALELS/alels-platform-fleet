param(
    [string]$HostName = "127.0.0.1",
    [int]$Port = 5050,
    [string]$File = ".\samples\alels_fmc650_sample.json"
)

$json = Get-Content $File -Raw
$json = $json.Trim() + "`n"

$client = New-Object System.Net.Sockets.TcpClient($HostName, $Port)
$stream = $client.GetStream()

$data = [System.Text.Encoding]::UTF8.GetBytes($json)
$stream.Write($data, 0, $data.Length)

$buffer = New-Object byte[] 4096
$count = $stream.Read($buffer, 0, $buffer.Length)

$response = [System.Text.Encoding]::UTF8.GetString($buffer, 0, $count)

Write-Host "SERVER RESPONSE:"
Write-Host $response

$stream.Close()
$client.Close()