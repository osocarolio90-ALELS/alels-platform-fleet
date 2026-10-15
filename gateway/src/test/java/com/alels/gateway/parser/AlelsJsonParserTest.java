package com.alels.gateway.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import com.alels.gateway.normalizer.AlelsJsonTelemetryNormalizer;

class AlelsJsonParserTest {
    private final AlelsJsonParser parser=new AlelsJsonParser();

    @Test
    void acceptsEnvelopeAndExtractsImei() throws Exception {
        ParserResult result=parser.parse(bytes("{\"T\":\"data\",\"A\":\"123456789012345\"}"));
        assertTrue(result.isValid());
        assertEquals("123456789012345",result.getImei());
    }

    @Test
    void rejectsEnvelopeWithoutType() throws Exception {
        assertFalse(parser.parse(bytes("{\"A\":\"123456789012345\"}")).isValid());
    }

    @Test
    void malformedJsonNeverProducesAValidPacket() {
        assertThrows(Exception.class,()->parser.parse(bytes("{broken")));
    }

    @Test
    void parserAndNormalizerUseTheSameFallbackImei() throws Exception {
        byte[] packet = bytes("{\"T\":\"data\",\"A\":\"\",\"imei\":\"123456789012345\",\"10500\":\"11\"}");
        assertEquals("123456789012345", parser.parse(packet).getImei());
        assertEquals("123456789012345", AlelsJsonTelemetryNormalizer.normalize(packet).getImei());
    }

    private byte[] bytes(String value) {return value.getBytes(StandardCharsets.UTF_8);}
}
