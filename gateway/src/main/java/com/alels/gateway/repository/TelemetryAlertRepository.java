package com.alels.gateway.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Timestamp;

import com.alels.gateway.config.DatabaseConfig;

/**
 * Persists rule-engine alerts using the canonical telemetry_alerts schema.
 * Device-originated Event IO records are materialized independently by the
 * database telemetry event pipeline and are not converted into alerts here.
 */
public final class TelemetryAlertRepository {

    private TelemetryAlertRepository() {
    }

    public static void insert(
            Long companyId,
            Long deviceId,
            Long telemetryId,
            String imei,
            String ruleCode,
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
                    vehicle_id,
                    telemetry_id,
                    imei,
                    rule_code,
                    severity,
                    status,
                    title,
                    message,
                    metadata,
                    created_at
                )
                VALUES (
                    ?,
                    (
                        SELECT assignment.vehicle_id
                        FROM vehicle_device_assignments assignment
                        WHERE assignment.device_id = ?
                          AND (? IS NULL OR assignment.company_id = ?)
                          AND assignment.assignment_status = 'ACTIVE'
                          AND assignment.deleted_at IS NULL
                        ORDER BY assignment.assigned_at DESC
                        LIMIT 1
                    ),
                    ?, ?, ?, ?, 'OPEN', ?, ?,
                    jsonb_strip_nulls(jsonb_build_object(
                        'deviceId', ?::bigint,
                        'dictionaryCode', ?::text,
                        'protocol', ?::text,
                        'channel', ?::text,
                        'ioId', ?::text,
                        'ioName', ?::text,
                        'rawValue', ?::text,
                        'numericValue', ?::double precision,
                        'realValue', ?::double precision,
                        'alertType', ?::text,
                        'driverName', ?::text,
                        'latitude', ?::double precision,
                        'longitude', ?::double precision,
                        'speed', ?::integer,
                        'gpsTime', ?::timestamptz
                    )),
                    NOW()
                )
                """;

        try (
                Connection conn = DatabaseConfig.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)
        ) {
            int index = 1;
            setLongOrNull(stmt, index++, companyId);
            setLongOrNull(stmt, index++, deviceId);
            setLongOrNull(stmt, index++, companyId);
            setLongOrNull(stmt, index++, companyId);
            setLongOrNull(stmt, index++, telemetryId);
            stmt.setString(index++, imei);
            stmt.setString(index++, ruleCode == null || ruleCode.isBlank() ? alertType : ruleCode);
            stmt.setString(index++, severity == null || severity.isBlank() ? "WARNING" : severity);
            stmt.setString(index++, title);
            stmt.setString(index++, message);
            setLongOrNull(stmt, index++, deviceId);
            stmt.setString(index++, dictionaryCode);
            stmt.setString(index++, protocol);
            stmt.setString(index++, channel);
            stmt.setString(index++, ioId);
            stmt.setString(index++, ioName);
            stmt.setString(index++, rawValue);
            setDoubleOrNull(stmt, index++, numericValue);
            setDoubleOrNull(stmt, index++, realValue);
            stmt.setString(index++, alertType);
            stmt.setString(index++, driverName);
            setDoubleOrNull(stmt, index++, latitude);
            setDoubleOrNull(stmt, index++, longitude);
            if (speed == null) {
                stmt.setNull(index++, java.sql.Types.INTEGER);
            } else {
                stmt.setInt(index++, speed);
            }
            if (gpsTime == null) {
                stmt.setNull(index, java.sql.Types.TIMESTAMP_WITH_TIMEZONE);
            } else {
                stmt.setTimestamp(index, gpsTime);
            }

            stmt.executeUpdate();

            System.out.println("[TELEMETRY ALERT] inserted rule="
                    + (ruleCode == null ? alertType : ruleCode)
                    + " imei=" + imei
                    + " io=" + ioId
                    + " message=" + message);

        } catch (Exception e) {
            System.err.println("[TELEMETRY ALERT ERROR] " + e.getMessage());
        }
    }

    private static void setLongOrNull(PreparedStatement stmt, int index, Long value) throws Exception {
        if (value == null) {
            stmt.setNull(index, java.sql.Types.BIGINT);
        } else {
            stmt.setLong(index, value);
        }
    }

    private static void setDoubleOrNull(PreparedStatement stmt, int index, Double value) throws Exception {
        if (value == null) {
            stmt.setNull(index, java.sql.Types.DOUBLE);
        } else {
            stmt.setDouble(index, value);
        }
    }
}
