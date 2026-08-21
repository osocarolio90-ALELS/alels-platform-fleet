package com.alels.gateway.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;

import com.alels.gateway.model.TelemetryData;

/**
 * Canonical numeric policy for telemetry entering an ALELS persistence path.
 *
 * <p>Coordinates deliberately keep their navigation precision. Other telemetry
 * numeric values are rounded to at most two decimal places before persistence.
 * Integer values remain integers. Presentation code can render a non-integer
 * value with exactly two decimal places without changing its stored meaning.</p>
 */
public final class TelemetryNumericNormalizer {
    private static final int SCALE = 2;

    private TelemetryNumericNormalizer() {
    }

    public static void normalizeForPersistence(TelemetryData telemetryData) {
        if (telemetryData == null) {
            return;
        }

        telemetryData.setHdop(round(telemetryData.getHdop()));
        telemetryData.setIoData(normalizeMap(telemetryData.getIoData()));
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
            return stripIntegral(decimal.setScale(SCALE, RoundingMode.HALF_UP));
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

    public static String formatDisplay(Number value) {
        if (value == null) {
            return null;
        }
        double number = value.doubleValue();
        if (!Double.isFinite(number)) {
            return String.valueOf(value);
        }
        double rounded = round(number);
        if (Math.rint(rounded) == rounded) {
            return Long.toString((long) rounded);
        }
        return BigDecimal.valueOf(rounded).setScale(SCALE, RoundingMode.HALF_UP).toPlainString();
    }

    private static Object stripIntegral(BigDecimal value) {
        if (value.stripTrailingZeros().scale() <= 0) {
            try {
                return value.longValueExact();
            } catch (ArithmeticException ignored) {
                return value.stripTrailingZeros();
            }
        }
        return value.doubleValue();
    }
}
