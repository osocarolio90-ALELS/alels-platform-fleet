package com.alels.ingestion.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;

import com.alels.ingestion.model.TelemetryMessage;

/** Persistence-boundary guard matching the gateway telemetry numeric policy. */
public final class TelemetryNumericNormalizer {
    private static final int SCALE = 2;

    private TelemetryNumericNormalizer() {
    }

    public static void normalizeForPersistence(TelemetryMessage message) {
        if (message == null) {
            return;
        }
        message.hdop = round(message.hdop);
        message.ioData = normalizeMap(message.ioData);
    }

    public static Map<String, Object> normalizeMap(Map<String, Object> values) {
        Map<String, Object> normalized = new LinkedHashMap<>();
        if (values == null || values.isEmpty()) {
            return normalized;
        }
        values.forEach((key, value) -> normalized.put(key, normalizeScalar(value)));
        return normalized;
    }

    public static Object normalizeScalar(Object value) {
        if (value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long) {
            return value;
        }
        if (value instanceof BigDecimal decimal) {
            BigDecimal rounded = decimal.setScale(SCALE, RoundingMode.HALF_UP);
            if (rounded.stripTrailingZeros().scale() <= 0) {
                try { return rounded.longValueExact(); } catch (ArithmeticException ignored) { return rounded.stripTrailingZeros(); }
            }
            return rounded.doubleValue();
        }
        if (value instanceof Float || value instanceof Double) {
            double number = ((Number) value).doubleValue();
            if (!Double.isFinite(number)) {
                return value;
            }
            double rounded = round(number);
            if (Math.rint(rounded) == rounded && rounded >= Long.MIN_VALUE && rounded <= Long.MAX_VALUE) {
                return (long) rounded;
            }
            return rounded;
        }
        return value;
    }

    public static Double round(Double value) {
        if (value == null || !Double.isFinite(value)) {
            return value;
        }
        return round(value.doubleValue());
    }

    public static double round(double value) {
        if (!Double.isFinite(value)) {
            return value;
        }
        return BigDecimal.valueOf(value).setScale(SCALE, RoundingMode.HALF_UP).doubleValue();
    }
}
