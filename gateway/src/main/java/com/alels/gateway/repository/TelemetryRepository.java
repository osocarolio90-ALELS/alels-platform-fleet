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
        if (data == null) {
            return null;
        }

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
}
