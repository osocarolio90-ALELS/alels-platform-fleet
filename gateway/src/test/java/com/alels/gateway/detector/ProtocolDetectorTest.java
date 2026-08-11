package com.alels.gateway.detector;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProtocolDetectorTest {
    @Test
    void detectsCodec8FromCodecByte() {
        assertEquals(ProtocolType.TELTONIKA_CODEC8, ProtocolDetector.detect(frame(0x08)));
    }

    @Test
    void detectsCodec8ExtendedFromCodecByte() {
        assertEquals(ProtocolType.TELTONIKA_CODEC8E, ProtocolDetector.detect(frame(0x8E)));
    }

    @Test
    void detectsTeltonikaImeiHandshake() {
        assertEquals(
                ProtocolType.TELTONIKA_IMEI,
                ProtocolDetector.detect(hex("000F333536333037303432343431303133"))
        );
    }

    private byte[] frame(int codec) {
        return new byte[]{0, 0, 0, 0, 0, 0, 0, 3, (byte) codec, 0, 0, 0, 0, 0, 0};
    }

    private byte[] hex(String value) {
        byte[] bytes = new byte[value.length() / 2];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) Integer.parseInt(value.substring(i * 2, i * 2 + 2), 16);
        }
        return bytes;
    }
}
