$hex =
"000000000000004A8E010000016B412CEE000100000000000000000000000000000000010005000100010100010011001D00010010015E2C880002000B000000003544C87A000E000000001DD7E06A00000100002994"

$bytes =
for(
    $i=0;
    $i -lt $hex.Length;
    $i+=2
){
    [Convert]::ToByte(
        $hex.Substring($i,2),
        16
    )
}

$client =
    New-Object System.Net.Sockets.TcpClient(
        "127.0.0.1",
        5050
    )

$stream =
    $client.GetStream()

$stream.Write(
    $bytes,
    0,
    $bytes.Length
)

$buffer =
    New-Object byte[] 32

$count =
    $stream.Read(
        $buffer,
        0,
        $buffer.Length
    )

"AVL ACK:"
[BitConverter]::ToString(
    $buffer,
    0,
    $count
)

$stream.Close()
$client.Close()