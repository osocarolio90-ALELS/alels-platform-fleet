package com.alels.gateway.parser;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TeltonikaUdpDatagramParserTest {
    private static final String TELTONIKA_CODEC8_UDP_EXAMPLE =
            "003DCAFE0105000F33353230393330383634303336353508010000016B4F815B30010000000000000000000000000000000103021503010101425DBC000001";

    @Test
    void decodesOfficialCodec8UdpEnvelopeAndReusesCodecParser() {
        TeltonikaUdpDatagramParser parser = new TeltonikaUdpDatagramParser();
        TeltonikaUdpDatagram datagram = parser.parse(hex(TELTONIKA_CODEC8_UDP_EXAMPLE)).orElseThrow();

        assertEquals(0xCAFE, datagram.channelPacketId());
        assertEquals(0x05, datagram.avlPacketId());
        assertEquals("352093086403655", datagram.imei());
        assertEquals(0x08, datagram.codecId());

        ParserResult result = new TeltonikaCodec8Parser().parse(datagram.tcpCompatiblePacket());
        assertTrue(result.isValid());
        assertEquals(1, result.getRecordCount());
        assertArrayEquals(hex("0005CAFE010501"), parser.acknowledgement(datagram, 1));
    }

    private byte[] hex(String value) {
        byte[] bytes = new byte[value.length() / 2];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) Integer.parseInt(value.substring(i * 2, i * 2 + 2), 16);
        }
        return bytes;
    }
}
