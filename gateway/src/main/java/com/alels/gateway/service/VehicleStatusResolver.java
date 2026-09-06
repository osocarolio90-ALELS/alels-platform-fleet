package com.alels.gateway.service;

import java.util.Map;
import com.alels.gateway.model.TelemetryData;
import com.alels.gateway.model.DeviceModelInfo;
import com.alels.gateway.repository.NormalizedFieldRepository;

public class VehicleStatusResolver {

    private static final String AVL_IGNITION = "239";
    private static final NormalizedFieldRepository MAPPINGS = new NormalizedFieldRepository();

    public static String resolve(TelemetryData data) {
        if (data == null) return "UNKNOWN";
        DeviceModelInfo model = DeviceModelResolver.resolve(data.getImei());
        if (model == null || data.getIoData() == null) return "UNKNOWN";
        Integer ignition = null;
        Double speed = null;
        for (Map.Entry<String, Object> entry : data.getIoData().entrySet()) {
            var mapping = MAPPINGS.findMapping(model.getModelId(), String.valueOf(data.getSourceProtocol()), entry.getKey());
            if (mapping == null) continue;
            String code = mapping.getFieldCode();
            Double value = toDouble(entry.getValue());
            if ("ignition".equals(code) && value != null) ignition = value.intValue();
            if ("speed".equals(code) && value != null) {
                double multiplier = mapping.getMultiplier() == null ? 1.0 : mapping.getMultiplier();
                double offset = mapping.getOffsetValue() == null ? 0.0 : mapping.getOffsetValue();
                speed = value * multiplier + offset;
            }
        }
        return resolveValues(ignition, speed);
    }

    public static String resolve(Map<String, Object> ioData, Integer speed) {
        if (ioData == null || ioData.isEmpty()) {
            return "UNKNOWN";
        }

        Integer ignition = toInteger(ioData.get(AVL_IGNITION));
        return resolveValues(ignition, speed == null ? null : speed.doubleValue());
    }

    private static String resolveValues(Integer ignition, Double speed) {
        if (ignition != null && ignition == 1 && speed != null && speed > 5) {
            return "TRIP";
        }

        if (ignition != null && ignition == 1 && speed != null && speed <= 5) {
            return "IDLE";
        }

        if (ignition != null && ignition == 0 && speed != null && speed <= 5) {
            return "STOP";
        }

        return "UNKNOWN";
    }

    private static Double toDouble(Object value) {
        if (value instanceof Number number) return number.doubleValue();
        if (value == null) return null;
        try { return Double.parseDouble(String.valueOf(value)); }
        catch (NumberFormatException ignored) { return null; }
    }

    private static Integer toInteger(Object value) {
        if (value == null) {
            return null;
        }

        if (value instanceof Number number) {
            return number.intValue();
        }

        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception e) {
            return null;
        }
    }
}
