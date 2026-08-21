package com.alels.gateway.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.alels.gateway.model.TelemetryData;

class TelemetryNumericNormalizerTest {
    @Test
    void roundsTelemetryNumericsButPreservesCoordinatePrecisionAndIntegers() {
        TelemetryData data = new TelemetryData();
        data.setLatitude(-6.2111383);
        data.setLongitude(107.0097783);
        data.setHdop(0.856);
        Map<String, Object> io = new LinkedHashMap<>();
        io.put("voltage", 12.457);
        io.put("rpm", 1500);
        data.setIoData(io);

        TelemetryNumericNormalizer.normalizeForPersistence(data);

        assertEquals(-6.2111383, data.getLatitude());
        assertEquals(107.0097783, data.getLongitude());
        assertEquals(0.86, data.getHdop());
        assertEquals(12.46, ((Number) data.getIoData().get("voltage")).doubleValue());
        assertEquals(1500, data.getIoData().get("rpm"));
    }

    @Test
    void formatsDisplayWithTwoDecimalsOnlyForNonIntegers() {
        assertEquals("12.46", TelemetryNumericNormalizer.formatDisplay(12.457));
        assertEquals("0.80", TelemetryNumericNormalizer.formatDisplay(0.8));
        assertEquals("25", TelemetryNumericNormalizer.formatDisplay(25));
    }
}
