package com.alels.gateway.dictionary;

import java.util.LinkedHashMap;
import java.util.Map;

import com.alels.gateway.util.TelemetryNumericNormalizer;

public class AvlValueConverter {

    public static Map<String, Object> convert(
        Map<String, Object> rawIo,
        String deviceModel
) {
    Map<String, Object> converted = new LinkedHashMap<>();

    if (rawIo == null || rawIo.isEmpty()) {
        return converted;
    }

    DeviceDictionary dictionary =
            DeviceDictionaryLoader.load(deviceModel);

    for (Map.Entry<String, Object> entry : rawIo.entrySet()) {

        String avlId = entry.getKey();
        Object rawValue = entry.getValue();

        AvlDefinition definition =
                dictionary.find(avlId);

        if (definition == null) {
            converted.put(avlId, rawValue);
            continue;
        }

        String key =
                definition.getName()
                        + " ("
                        + avlId
                        + ")";

        /*
         * Special handling:
         * AVL 78 = iButton
         * Preserve original value
         */
        if ("78".equals(avlId)) {

            converted.put(
                    key,
                    String.valueOf(rawValue)
            );

            continue;
        }

        double numericValue =
                toDouble(rawValue);

        double realValue =
                numericValue
                        * definition.getMultiplier();

        String value =
                formatValue(
                        realValue,
                        definition.getUnit()
                );

        converted.put(
                key,
                value
        );
    }

    return converted;
}

    private static double toDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }

        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (Exception e) {
            return 0.0;
        }
    }

    private static String formatValue(double value, String unit) {
        String formattedValue = TelemetryNumericNormalizer.formatDisplay(value);

        if (unit == null || unit.isBlank() || unit.equals("-")) {
            return formattedValue;
        }

        return formattedValue + " " + unit;
    }
}