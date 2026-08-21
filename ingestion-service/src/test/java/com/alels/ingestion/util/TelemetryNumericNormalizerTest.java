package com.alels.ingestion.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.LinkedHashMap;

import org.junit.jupiter.api.Test;

import com.alels.ingestion.model.TelemetryMessage;

class TelemetryNumericNormalizerTest {
    @Test
    void appliesTheSamePersistencePolicyAtKafkaIngestionBoundary() {
        TelemetryMessage message = new TelemetryMessage();
        message.latitude = -6.2111383;
        message.longitude = 107.0097783;
        message.hdop = 0.856;
        message.ioData = new LinkedHashMap<>();
        message.ioData.put("voltage", 12.457);
        message.ioData.put("rpm", 1500);

        TelemetryNumericNormalizer.normalizeForPersistence(message);

        assertEquals(-6.2111383, message.latitude);
        assertEquals(107.0097783, message.longitude);
        assertEquals(0.86, message.hdop);
        assertEquals(12.46, ((Number) message.ioData.get("voltage")).doubleValue());
        assertEquals(1500, message.ioData.get("rpm"));
    }
}
