package com.alels.gateway.parser;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TeltonikaCodec8EParserTest {

    private static final String VALID_FRAME =
            "000000000000004A8E010000016B412CEE000100000000000000000000000000000000010005000100010100010011001D00010010015E2C880002000B000000003544C87A000E000000001DD7E06A00000100002994";

    @Test
    void acceptsGoldenCodec8EFrameIncludingVariableIoCount() {
        ParserResult result = new TeltonikaCodec8EParser().parse(hex(VALID_FRAME));

        assertTrue(result.isValid());
        assertEquals(1, result.getRecordCount());
        assertEquals(1, result.getTelemetryList().size());
    }

    @Test
    void rejectsCorruptedCrc() {
        byte[] corrupted = hex(VALID_FRAME);
        corrupted[corrupted.length - 1] ^= 0x01;

        assertFalse(new TeltonikaCodec8EParser().parse(corrupted).isValid());
    }

    private byte[] hex(String value) {
        byte[] bytes = new byte[value.length() / 2];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) Integer.parseInt(value.substring(i * 2, i * 2 + 2), 16);
        }
        return bytes;
    }
}
