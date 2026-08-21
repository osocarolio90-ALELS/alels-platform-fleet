package com.alels.gateway.service;

import com.alels.gateway.util.TimeUtil;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

import com.alels.gateway.dictionary.AvlDefinition;
import com.alels.gateway.dictionary.DeviceDictionary;
import com.alels.gateway.dictionary.DeviceDictionaryLoader;
import com.alels.gateway.model.AlertRule;
import com.alels.gateway.model.DriverInfo;
import com.alels.gateway.model.TelemetryData;
import com.alels.gateway.repository.AlertRuleRepository;
import com.alels.gateway.repository.DeviceCompanyRepository;
import com.alels.gateway.repository.TelemetryAlertRepository;

public class TelemetryAlertEvaluator {

    private TelemetryAlertEvaluator() {
    }

    public static void evaluateAndStore(
            Long telemetryId,
            TelemetryData telemetryData,
            String protocol,
            String channel,
            String dictionaryCode
    ) {
        if (telemetryId == null || telemetryData == null) {
            return;
        }

        String imei =
                telemetryData.getImei();

        if (imei == null || imei.isBlank()) {
            return;
        }

        Long companyId =
                DeviceCompanyRepository.findCompanyIdByImei(imei);

        Long deviceId =
                DeviceCompanyRepository.findDeviceIdByImei(imei);

        List<AlertRule> rules =
                AlertRuleRepository.findMatchingRules(
                        companyId,
                        deviceId,
                        imei,
                        dictionaryCode,
                        protocol
                );

        if (rules.isEmpty()) {
            return;
        }

        DriverInfo driver =
                DriverResolver.resolve(
                        companyId,
                        telemetryData.getIoData()
                );

        DeviceDictionary dictionary =
                loadDictionary(dictionaryCode);

        for (AlertRule rule : rules) {
            evaluateRule(
                    rule,
                    companyId,
                    deviceId,
                    telemetryId,
                    telemetryData,
                    protocol,
                    channel,
                    dictionaryCode,
                    dictionary,
                    driver
            );
        }
    }

    private static void evaluateRule(
            AlertRule rule,
            Long companyId,
            Long deviceId,
            Long telemetryId,
            TelemetryData telemetryData,
            String protocol,
            String channel,
            String dictionaryCode,
            DeviceDictionary dictionary,
            DriverInfo driver
    ) {
        String ioId =
                rule.getIoId();

        Object rawValue;
        String ioName = null;

        if ("SPEED".equalsIgnoreCase(ioId)) {
            rawValue =
                    telemetryData.getSpeed();
            ioName =
                    "Speed";
        } else {
            Map<String, Object> ioData =
                    telemetryData.getIoData();

            if (ioData == null || !ioData.containsKey(ioId)) {
                return;
            }

            rawValue =
                    ioData.get(ioId);

            AvlDefinition definition =
                    dictionary != null
                            ? dictionary.find(ioId)
                            : null;

            ioName =
                    definition != null
                            ? definition.getName()
                            : null;
        }

        Double numericValue =
                toDouble(rawValue);

        Double realValue =
                numericValue;

        if (!matches(rule, rawValue, realValue)) {
            return;
        }

        String driverName =
                driver != null
                        ? driver.getDriverName()
                        : null;

        String message =
                buildMessage(
                        rule,
                        telemetryData,
                        protocol,
                        ioId,
                        rawValue,
                        realValue,
                        driverName
                );

        TelemetryAlertRepository.insert(
                companyId,
                deviceId,
                telemetryId,
                telemetryData.getImei(),
                rule.getRuleCode(),
                dictionaryCode,
                protocol,
                channel,
                ioId,
                ioName,
                rawValue == null ? null : String.valueOf(rawValue),
                numericValue,
                realValue,
                rule.getAlertType(),
                rule.getSeverity(),
                rule.getTitle(),
                message,
                driverName,
                telemetryData.getLatitude(),
                telemetryData.getLongitude(),
                telemetryData.getSpeed(),
                parseTimestamp(telemetryData.getDeviceTime())
        );
    }

    private static boolean matches(
            AlertRule rule,
            Object rawValue,
            Double numericValue
    ) {
        String operator =
                rule.getConditionOperator() == null
                        ? "GT"
                        : rule.getConditionOperator().trim().toUpperCase();

        if ("EQ".equals(operator)) {
            return rule.getMatchValue() != null
                    && rawValue != null
                    && rule.getMatchValue().equalsIgnoreCase(String.valueOf(rawValue));
        }

        if (numericValue == null || rule.getThresholdValue() == null) {
            return false;
        }

        double threshold =
                rule.getThresholdValue();

        return switch (operator) {
            case "GTE" -> numericValue >= threshold;
            case "LT" -> numericValue < threshold;
            case "LTE" -> numericValue <= threshold;
            case "NEQ" -> numericValue != threshold;
            default -> numericValue > threshold;
        };
    }

    private static String buildMessage(
            AlertRule rule,
            TelemetryData telemetryData,
            String protocol,
            String ioId,
            Object rawValue,
            Double realValue,
            String driverName
    ) {
        if ("OVERSPEEDING".equalsIgnoreCase(rule.getAlertType())) {
            return "Overspeeding "
                    + telemetryData.getSpeed()
                    + "km > "
                    + (driverName == null || driverName.isBlank()
                            ? telemetryData.getImei()
                            : driverName)
                    + " | Device - "
                    + protocol
                    + " data - IO "
                    + ioId
                    + " value "
                    + valueText(realValue, rawValue);
        }

        return "Device - "
                + protocol
                + " data - IO "
                + ioId
                + " value "
                + valueText(realValue, rawValue);
    }

    private static String valueText(
            Double realValue,
            Object rawValue
    ) {
        if (realValue != null) {
            return String.valueOf(realValue);
        }

        return rawValue == null
                ? "-"
                : String.valueOf(rawValue);
    }

    private static DeviceDictionary loadDictionary(String dictionaryCode) {
        try {
            return DeviceDictionaryLoader.load(dictionaryCode);
        } catch (Exception e) {
            return null;
        }
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

    private static Timestamp parseTimestamp(String value) {
        return TimeUtil.parseDeviceTimestampUtc(value);
    }
}
