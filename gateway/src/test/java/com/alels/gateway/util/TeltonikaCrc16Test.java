package com.alels.gateway.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class TeltonikaCrc16Test {

    @Test
    void matchesCrc16IbmReferenceVector() {
        byte[] value = "123456789".getBytes(StandardCharsets.US_ASCII);
        assertEquals(0xBB3D, TeltonikaCrc16.calculate(value, 0, value.length));
    }
}
