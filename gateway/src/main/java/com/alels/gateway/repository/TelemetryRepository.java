package com.alels.gateway.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;

import com.alels.gateway.config.DatabaseConfig;
import com.alels.gateway.model.DriverInfo;
import com.alels.gateway.model.TelemetryData;
import com.alels.gateway.service.DriverResolver;
import com.alels.gateway.service.VehicleStatusResolver;

public class TelemetryRepository {

    public static Long insert(
            Long rawPacketId,
            TelemetryData data,
            String protocol,
            String channel
    ) {
        if (data == null) {
            return null;
        }

        String vehicleStatus =
                VehicleStatusResolver.resolve(
                        data.getIoData()
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

        String sql = """
                INSERT INTO telemetry (
                    raw_packet_id,
                    imei,
                    protocol,
                    channel,
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
                    parse_status
                )
                VALUES (
                    ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
                    ?, ?, ?,
                    ?, ?
                )
                RETURNING id
                """;

        try (
                Connection conn = DatabaseConfig.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)
        ) {
            if (rawPacketId == null) {
                stmt.setNull(1, java.sql.Types.BIGINT);
            } else {
                stmt.setLong(1, rawPacketId);
            }

            stmt.setString(2, data.getImei());
            stmt.setString(3, protocol);
            stmt.setString(4, channel);

            Timestamp deviceTimestamp =
                    parseTimestamp(
                            data.getDeviceTime()
                    );

            if (deviceTimestamp == null) {
                stmt.setNull(5, java.sql.Types.TIMESTAMP);
            } else {
                stmt.setTimestamp(5, deviceTimestamp);
            }

            stmt.setLong(6, data.getPacketSequence());
            stmt.setDouble(7, data.getLatitude());
            stmt.setDouble(8, data.getLongitude());
            stmt.setInt(9, data.getAltitude());
            stmt.setInt(10, data.getAngle());
            stmt.setInt(11, data.getSatellites());
            stmt.setInt(12, data.getSpeed());
            stmt.setDouble(13, data.getHdop());
            stmt.setInt(14, data.getPriority());
            stmt.setInt(15, data.getEventIoId());

            if (driver == null) {

                stmt.setNull(16, java.sql.Types.BIGINT);
                stmt.setNull(17, java.sql.Types.VARCHAR);
                stmt.setNull(18, java.sql.Types.VARCHAR);

            } else {

                stmt.setLong(
                        16,
                        driver.getDriverId()
                );

                stmt.setString(
                        17,
                        driver.getDriverName()
                );

                stmt.setString(
                        18,
                        driver.getDriverRfid()
                );
            }

            stmt.setString(19, vehicleStatus);
            stmt.setString(20, "VALID");

            try (ResultSet rs = stmt.executeQuery()) {

                if (rs.next()) {

                    Long id =
                            rs.getLong("id");

                    System.out.println(
                            "[DB TELEMETRY] inserted id="
                                    + id
                                    + " imei=" + data.getImei()
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

        if (value == null || value.isBlank()) {
            return null;
        }

        try {

            String normalized =
                    value.trim().replace("T", " ");

            if (normalized.length() == 16) {
                normalized += ":00";
            }

            return Timestamp.valueOf(normalized);

        } catch (Exception e) {
            return null;
        }
    }
}