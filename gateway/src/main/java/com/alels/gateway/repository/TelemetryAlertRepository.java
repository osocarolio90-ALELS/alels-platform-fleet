package com.alels.gateway.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Timestamp;

import com.alels.gateway.config.DatabaseConfig;

public class TelemetryAlertRepository {

    public static void insert(
            Long companyId,
            Long deviceId,
            Long telemetryId,
            String imei,
            String dictionaryCode,
            String protocol,
            String channel,
            String ioId,
            String ioName,
            String rawValue,
            Double numericValue,
            Double realValue,
            String alertType,
            String severity,
            String title,
            String message,
            String driverName,
            Double latitude,
            Double longitude,
            Integer speed,
            Timestamp gpsTime
    ) {
        String sql = """
                INSERT INTO telemetry_alerts (
                    company_id,
                    device_id,
                    telemetry_id,
                    imei,
                    dictionary_code,
                    protocol,
                    channel,
                    io_id,
                    io_name,
                    raw_value,
                    numeric_value,
                    real_value,
                    alert_type,
                    severity,
                    title,
                    message,
                    driver_name,
                    latitude,
                    longitude,
                    speed,
                    gps_time
                )
                VALUES (
                    ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
                    ?, ?, ?, ?, ?, ?, ?, ?, ?, ?
                )
                """;

        try (
                Connection conn = DatabaseConfig.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)
        ) {
            setLongOrNull(stmt, 1, companyId);
            setLongOrNull(stmt, 2, deviceId);
            setLongOrNull(stmt, 3, telemetryId);
            stmt.setString(4, imei);
            stmt.setString(5, dictionaryCode);
            stmt.setString(6, protocol);
            stmt.setString(7, channel);
            stmt.setString(8, ioId);
            stmt.setString(9, ioName);
            stmt.setString(10, rawValue);
            setDoubleOrNull(stmt, 11, numericValue);
            setDoubleOrNull(stmt, 12, realValue);
            stmt.setString(13, alertType);
            stmt.setString(14, severity);
            stmt.setString(15, title);
            stmt.setString(16, message);
            stmt.setString(17, driverName);
            setDoubleOrNull(stmt, 18, latitude);
            setDoubleOrNull(stmt, 19, longitude);
            if (speed == null) {
                stmt.setNull(20, java.sql.Types.INTEGER);
            } else {
                stmt.setInt(20, speed);
            }
            if (gpsTime == null) {
                stmt.setNull(21, java.sql.Types.TIMESTAMP);
            } else {
                stmt.setTimestamp(21, gpsTime);
            }

            stmt.executeUpdate();

            System.out.println("[TELEMETRY ALERT] inserted type="
                    + alertType
                    + " imei=" + imei
                    + " io=" + ioId
                    + " message=" + message);

        } catch (Exception e) {
            System.err.println("[TELEMETRY ALERT ERROR] " + e.getMessage());
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

    private static void setDoubleOrNull(
            PreparedStatement stmt,
            int index,
            Double value
    ) throws Exception {
        if (value == null) {
            stmt.setNull(index, java.sql.Types.DOUBLE);
        } else {
            stmt.setDouble(index, value);
        }
    }
}
