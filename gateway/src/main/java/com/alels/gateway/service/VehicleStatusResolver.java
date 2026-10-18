package com.alels.gateway.service;

import java.util.Map;
import java.util.List;
import com.alels.gateway.model.TelemetryData;
import com.alels.gateway.model.DeviceModelInfo;
import com.alels.gateway.repository.NormalizedFieldRepository;
import com.alels.gateway.repository.NormalizedFieldRepository.VehicleStatusSource;

public class VehicleStatusResolver {

    private static final String AVL_IGNITION = "239";
    private static final NormalizedFieldRepository MAPPINGS = new NormalizedFieldRepository();

    public static String resolve(TelemetryData data) {
        if (data == null) return "UNKNOWN";
        DeviceModelInfo model = DeviceModelResolver.resolve(data.getImei());
        if (model == null || data.getIoData() == null) return "UNKNOWN";
        return resolve(data.getIoData(), data.getSpeed(),
                MAPPINGS.findVehicleStatusSources(model.getModelId(), String.valueOf(data.getSourceProtocol())));
    }

    static String resolve(Map<String, Object> ioData, Integer gpsSpeed, List<VehicleStatusSource> sources) {
        Integer ignition = null;
        Double speed = gpsSpeed == null ? null : gpsSpeed.doubleValue();
        for (VehicleStatusSource source : sources) {
            Double raw = toDouble(ioData.get(source.ioId()));
            if (raw == null) continue;
            double value = raw * source.multiplier() + source.offset();
            if (!Double.isFinite(value)) continue;
            if ("ignition".equals(source.fieldCode()) && (value == 0 || value == 1)) ignition = (int) value;
            if ("speed".equals(source.fieldCode()) && value >= 0) speed = value;
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
        if (value instanceof Boolean flag) return flag ? 1.0 : 0.0;
        if (value == null) return null;
        try {
            double parsed = Double.parseDouble(String.valueOf(value));
            return Double.isFinite(parsed) ? parsed : null;
        }
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
