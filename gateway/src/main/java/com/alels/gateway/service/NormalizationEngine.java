package com.alels.gateway.service;

import java.util.Map;

import com.alels.gateway.model.DeviceModelInfo;
import com.alels.gateway.model.NormalizedFieldInfo;
import com.alels.gateway.model.TelemetryData;
import com.alels.gateway.repository.NormalizedFieldRepository;
import com.alels.gateway.repository.TelemetryNormalizedRepository;
import com.alels.gateway.repository.UnknownIoRepository;

public class NormalizationEngine {

    private static final NormalizedFieldRepository fieldRepository =
            new NormalizedFieldRepository();

    private static final TelemetryNormalizedRepository normalizedRepository =
            new TelemetryNormalizedRepository();

    private NormalizationEngine() {
    }

    public static void normalizeAndStore(
            Long telemetryId,
            Long rawPacketId,
            Long companyId,
            Long vehicleId,
            TelemetryData telemetryData,
            String sourceProtocol,
            String dictionaryCode
    ) {
        if (telemetryId == null || telemetryData == null) {
            return;
        }

        if (telemetryData.getImei() == null || telemetryData.getImei().isBlank()) {
            return;
        }

        DeviceModelInfo deviceModel =
                DeviceModelResolver.resolve(telemetryData.getImei());

        if (deviceModel == null || deviceModel.getModelId() == null) {
            System.out.println(
                    "[NORMALIZATION] skip imei="
                            + telemetryData.getImei()
                            + " reason=device model not found"
            );
            return;
        }

        Map<String, Object> ioData =
                telemetryData.getIoData();

        if (ioData == null || ioData.isEmpty()) {
            return;
        }

        int inserted = 0;
        int skipped = 0;

        for (Map.Entry<String, Object> entry : ioData.entrySet()) {

            String ioId = entry.getKey();
            Object raw = entry.getValue();

            NormalizedFieldInfo fieldInfo =
                    fieldRepository.findMapping(
                            deviceModel.getModelId(),
                            sourceProtocol,
                            ioId
                    );

            if (fieldInfo == null) {

                UnknownIoRepository.upsert(
                    telemetryData.getImei(),
                    deviceModel.getModelId(),
                    dictionaryCode,
                    sourceProtocol,
                    ioId,
                    raw
                );

                skipped++;
                continue;
            }

            String valueType =
                    fieldInfo.getValueType() != null
                            ? fieldInfo.getValueType().toUpperCase()
                            : "NUMBER";

            String rawValue =
                    raw != null ? String.valueOf(raw) : null;

            Double numericValue = null;
            String textValue = null;
            Boolean booleanValue = null;

            if ("BOOLEAN".equals(valueType)) {
                booleanValue = toBoolean(raw);
                numericValue = booleanValue != null && booleanValue ? 1.0 : 0.0;
                textValue = String.valueOf(booleanValue);
            } else if ("TEXT".equals(valueType)) {
                textValue = rawValue;
            } else {
                Double rawDouble = toDouble(raw);
                if (rawDouble != null) {
                    double multiplier =
                            fieldInfo.getMultiplier() != null
                                    ? fieldInfo.getMultiplier()
                                    : 1.0;

                    double offset =
                            fieldInfo.getOffsetValue() != null
                                    ? fieldInfo.getOffsetValue()
                                    : 0.0;

                    numericValue = (rawDouble * multiplier) + offset;
                    textValue = String.valueOf(numericValue);
                } else {
                    textValue = rawValue;
                }
            }

            normalizedRepository.insert(
                    telemetryId,
                    rawPacketId,
                    companyId,
                    vehicleId,
                    telemetryData.getImei(),
                    fieldInfo,
                    rawValue,
                    numericValue,
                    textValue,
                    booleanValue,
                    sourceProtocol,
                    dictionaryCode
            );

            inserted++;
        }

        System.out.println(
                "[NORMALIZATION] telemetryId="
                        + telemetryId
                        + " imei="
                        + telemetryData.getImei()
                        + " inserted="
                        + inserted
                        + " skipped="
                        + skipped
        );
    }

    private static Double toDouble(Object value) {
        if (value == null) {
            return null;
        }

        if (value instanceof Number number) {
            return number.doubleValue();
        }

        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (Exception e) {
            return null;
        }
    }

    private static Boolean toBoolean(Object value) {
        if (value == null) {
            return null;
        }

        if (value instanceof Boolean bool) {
            return bool;
        }

        if (value instanceof Number number) {
            return number.intValue() != 0;
        }

        String text = String.valueOf(value).trim();

        if ("1".equals(text)) return true;
        if ("0".equals(text)) return false;
        if ("true".equalsIgnoreCase(text)) return true;
        if ("false".equalsIgnoreCase(text)) return false;

        return null;
    }
}