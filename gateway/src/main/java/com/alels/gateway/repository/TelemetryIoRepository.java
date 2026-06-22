package com.alels.gateway.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.Map;

import com.alels.gateway.config.DatabaseConfig;
import com.alels.gateway.dictionary.AvlDefinition;
import com.alels.gateway.dictionary.DeviceDictionary;
import com.alels.gateway.dictionary.DeviceDictionaryLoader;
import com.alels.gateway.model.TelemetryData;

public class TelemetryIoRepository {

    public static void insertAll(
            Long telemetryId,
            Long rawPacketId,
            TelemetryData telemetryData,
            String protocol,
            String channel,
            String deviceModel
    ) {
        if (telemetryData == null) {
            return;
        }

        Map<String, Object> ioData = telemetryData.getIoData();

        if (ioData == null || ioData.isEmpty()) {
            return;
        }

        DeviceDictionary dictionary = null;

        try {
            dictionary = DeviceDictionaryLoader.load(deviceModel);
        } catch (Exception e) {
            System.err.println("[DB TELEMETRY IO] Dictionary load failed model="
                    + deviceModel + " error=" + e.getMessage());
        }

        for (Map.Entry<String, Object> entry : ioData.entrySet()) {
            String ioId = entry.getKey();
            Object rawValue = entry.getValue();

            AvlDefinition definition = dictionary != null
                    ? dictionary.find(ioId)
                    : null;

            String ioName = definition != null ? definition.getName() : null;
            String ioCategory = definition != null ? definition.getCategory() : null;
            String unit = definition != null ? definition.getUnit() : null;
            double multiplier = definition != null ? definition.getMultiplier() : 1.0;

            Double numericValue = toDouble(rawValue);
            Double realValue = numericValue != null
                    ? numericValue * multiplier
                    : null;

            boolean isEvent = false;

            try {
                isEvent = Integer.parseInt(ioId) == telemetryData.getEventIoId();
            } catch (Exception ignored) {
            }

            insertOne(
                    telemetryId,
                    rawPacketId,
                    telemetryData.getImei(),
                    protocol,
                    channel,
                    ioId,
                    ioName,
                    ioCategory,
                    String.valueOf(rawValue),
                    numericValue,
                    realValue,
                    unit,
                    multiplier,
                    isEvent,
                    "VALID",
                    null
            );
        }
    }

    private static void insertOne(
            Long telemetryId,
            Long rawPacketId,
            String imei,
            String protocol,
            String channel,
            String ioId,
            String ioName,
            String ioCategory,
            String rawValue,
            Double numericValue,
            Double realValue,
            String unit,
            Double multiplier,
            Boolean isEvent,
            String validationStatus,
            String validationMessage
    ) {
        String sql = """
                INSERT INTO telemetry_io (
                    telemetry_id,
                    raw_packet_id,
                    imei,
                    protocol,
                    channel,
                    io_id,
                    io_name,
                    io_category,
                    raw_value,
                    numeric_value,
                    real_value,
                    unit,
                    multiplier,
                    is_event,
                    validation_status,
                    validation_message
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

        try (
                Connection conn = DatabaseConfig.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)
        ) {
            setLongOrNull(stmt, 1, telemetryId);
            setLongOrNull(stmt, 2, rawPacketId);

            stmt.setString(3, imei);
            stmt.setString(4, protocol);
            stmt.setString(5, channel);
            stmt.setString(6, ioId);
            stmt.setString(7, ioName);
            stmt.setString(8, ioCategory);
            stmt.setString(9, rawValue);

            if (numericValue == null) {
                stmt.setNull(10, java.sql.Types.DOUBLE);
            } else {
                stmt.setDouble(10, numericValue);
            }

            if (realValue == null) {
                stmt.setNull(11, java.sql.Types.DOUBLE);
            } else {
                stmt.setDouble(11, realValue);
            }

            stmt.setString(12, unit);

            if (multiplier == null) {
                stmt.setNull(13, java.sql.Types.DOUBLE);
            } else {
                stmt.setDouble(13, multiplier);
            }

            stmt.setBoolean(14, Boolean.TRUE.equals(isEvent));
            stmt.setString(15, validationStatus);
            stmt.setString(16, validationMessage);

            stmt.executeUpdate();

            System.out.println("[DB TELEMETRY IO] inserted telemetryId="
                    + telemetryId
                    + " ioId=" + ioId
                    + " raw=" + rawValue
                    + " real=" + realValue);

        } catch (Exception e) {
            System.err.println("[DB TELEMETRY IO ERROR] " + e.getMessage());
        }
    }

    private static void setLongOrNull(
            PreparedStatement stmt,
            int index,
            Long value
    ) throws Exception {
        if (value == null) {
            stmt.setNull(index, java.sql.Types.BIGINT);
        } else {
            stmt.setLong(index, value);
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
}