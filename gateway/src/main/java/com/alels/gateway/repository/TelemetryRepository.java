package com.alels.gateway.repository;

import com.alels.gateway.util.TimeUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;

import com.alels.gateway.config.DatabaseConfig;
import com.alels.gateway.model.DriverInfo;
import com.alels.gateway.model.TelemetryData;
import com.alels.gateway.service.DriverResolver;
import com.alels.gateway.service.VehicleStatusResolver;
import com.alels.gateway.util.TelemetryNumericNormalizer;
import com.fasterxml.jackson.databind.ObjectMapper;

public class TelemetryRepository {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static Long insert(
            Long rawPacketId,
            TelemetryData data,
            String protocol,
            String channel,
            String dictionaryCode
    ) {
        try {
            PreparedInsert prepared = prepare(data);
            if (prepared == null) return null;
            try (Connection conn = DatabaseConfig.getConnection()) {
                return insert(conn, rawPacketId, data, protocol, channel, dictionaryCode, prepared);
            }
        } catch (Exception e) {
            System.err.println("[DB TELEMETRY ERROR] " + e.getMessage());
            return null;
        }
    }

    public record PreparedInsert(String vehicleStatus, DriverInfo driver) {}

    /** Resolve existing enrichment before borrowing the write connection; resolvers use the pool. */
    public static PreparedInsert prepare(TelemetryData data) {
        if (data == null) return null;
        TelemetryNumericNormalizer.normalizeForPersistence(data);

        String vehicleStatus =
                VehicleStatusResolver.resolve(
                        data
                );

        Long companyId =
                DeviceCompanyRepository.findCompanyIdByImei(
                        data.getImei()
                );

        DriverInfo driver =
                DriverResolver.resolve(
                        companyId,
                        data.getIoData()
                );

        return new PreparedInsert(vehicleStatus, driver);
    }

    public static Long insert(
            Connection conn, Long rawPacketId, TelemetryData data,
            String protocol, String channel, String dictionaryCode, PreparedInsert prepared
    ) {
        if (data == null || prepared == null) return null;
        String vehicleStatus = prepared.vehicleStatus();
        DriverInfo driver = prepared.driver();

        String sql = """
                INSERT INTO telemetry (
                    raw_packet_id,
                    imei,
                    protocol,
                    channel,
                    dictionary_code,
                    source_protocol,
                    device_time,
                    packet_sequence,
                    latitude,
                    longitude,
                    altitude,
                    angle,
                    satellites,
                    speed,
                    hdop,
                    priority,
                    event_io_id,
                    driver_id,
                    driver_name,
                    driver_rfid,
                    vehicle_status,
                    io_data,
                    io,
                    parse_status
                )
                VALUES (
                    ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
                    ?, ?, ?, ?, CAST(? AS jsonb), CAST(? AS jsonb), ?
                )
                RETURNING id
                """;

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            // Sparse packets (for example IO 10500) do not signal an ignition change.
            // Read durable history so reconnects/restarts retain the last known state.
            if ("UNKNOWN".equals(vehicleStatus)) {
                vehicleStatus = lastKnownVehicleStatus(conn, data.getImei(), parseTimestamp(data.getDeviceTime()));
            }
            if (rawPacketId == null) {
                stmt.setNull(1, java.sql.Types.BIGINT);
            } else {
                stmt.setLong(1, rawPacketId);
            }

            stmt.setString(2, data.getImei());
            stmt.setString(3, protocol);
            stmt.setString(4, channel);
            stmt.setString(5, dictionaryCode);
            stmt.setString(6, protocol);

            Timestamp deviceTimestamp =
                    parseTimestamp(
                            data.getDeviceTime()
                    );

            if (deviceTimestamp == null) {
                stmt.setNull(7, java.sql.Types.TIMESTAMP);
            } else {
                stmt.setTimestamp(7, deviceTimestamp);
            }

            stmt.setLong(8, data.getPacketSequence());
            stmt.setDouble(9, data.getLatitude());
            stmt.setDouble(10, data.getLongitude());
            stmt.setInt(11, data.getAltitude());
            stmt.setInt(12, data.getAngle());
            stmt.setInt(13, data.getSatellites());
            stmt.setInt(14, data.getSpeed());
            stmt.setDouble(15, data.getHdop());
            stmt.setInt(16, data.getPriority());
            stmt.setInt(17, data.getEventIoId());

            if (driver == null) {

                stmt.setNull(18, java.sql.Types.BIGINT);
                stmt.setNull(19, java.sql.Types.VARCHAR);
                stmt.setNull(20, java.sql.Types.VARCHAR);

            } else {

                stmt.setLong(
                        18,
                        driver.getDriverId()
                );

                stmt.setString(
                        19,
                        driver.getDriverName()
                );

                stmt.setString(
                        20,
                        driver.getDriverRfid()
                );
            }

            stmt.setString(21, vehicleStatus);
            String ioJson = MAPPER.writeValueAsString(data.getIoData());
            stmt.setString(22, ioJson);
            stmt.setString(23, ioJson);
            stmt.setString(24, "VALID");

            try (ResultSet rs = stmt.executeQuery()) {

                if (rs.next()) {

                    Long id =
                            rs.getLong("id");

                    System.out.println(
                            "[DB TELEMETRY] inserted id="
                                    + id
                                    + " imei=" + data.getImei()
                                    + " protocol=" + protocol
                                    + " dictionary=" + dictionaryCode
                                    + " vehicleStatus=" + vehicleStatus
                                    + " driver="
                                    + (
                                            driver == null
                                                    ? "NONE"
                                                    : driver.getDriverName()
                                    )
                                    + " rfid="
                                    + (
                                            driver == null
                                                    ? "-"
                                                    : driver.getDriverRfid()
                                    )
                    );

                    return id;
                }
            }

        } catch (Exception e) {

            System.err.println(
                    "[DB TELEMETRY ERROR] "
                            + e.getMessage()
            );
        }

        return null;
    }

    private static Timestamp parseTimestamp(String value) {
        return TimeUtil.parseDeviceTimestampUtc(value);
    }

    private static String lastKnownVehicleStatus(Connection conn, String imei, Timestamp deviceTime) throws Exception {
        String sql = """
                SELECT vehicle_status FROM telemetry
                WHERE imei = ? AND vehicle_status IN ('STOP', 'IDLE', 'TRIP')
                  AND COALESCE(device_time, server_time) <= COALESCE(?::timestamptz, NOW())
                ORDER BY COALESCE(device_time, server_time) DESC, server_time DESC, id DESC
                LIMIT 1
                """;
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, imei);
            stmt.setTimestamp(2, deviceTime);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getString("vehicle_status") : "UNKNOWN";
            }
        }
    }
}
