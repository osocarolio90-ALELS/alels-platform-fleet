package com.alels.gateway.publisher;

import com.alels.gateway.model.TelemetryData;
import com.alels.gateway.repository.TelemetryIoRepository;
import com.alels.gateway.repository.TelemetryRepository;
import com.alels.gateway.repository.DevicePresenceRepository;
import com.alels.gateway.repository.DeviceStatusRepository;
import com.alels.gateway.service.NormalizationEngine;
import com.alels.gateway.service.TelemetryAlertEvaluator;
import com.alels.gateway.normalizer.EyeBeaconTelemetryNormalizer;
import com.alels.gateway.config.DatabaseConfig;
import com.alels.gateway.util.TimeUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.sql.SQLException;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DirectDatabaseTelemetryPublisher implements TelemetryPublisher {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Logger log = LoggerFactory.getLogger(DirectDatabaseTelemetryPublisher.class);
    private final boolean bleHistoryEnabled;

    public DirectDatabaseTelemetryPublisher() {
        this(TelemetryPublisherConfig.bleHistoryEnabled());
    }

    DirectDatabaseTelemetryPublisher(boolean bleHistoryEnabled) {
        this.bleHistoryEnabled = bleHistoryEnabled;
        log.info("event=ble_history_config module=gateway enabled={} source_io_preserved=true", bleHistoryEnabled);
    }

    @Override
    public CompletionStage<Long> publish(
            Long rawPacketId,
            TelemetryData telemetryData,
            String protocol,
            String channel,
            String dictionaryCode,
            String logLabel
    ) {
        try {
            EyeBeaconTelemetryNormalizer.normalize(telemetryData);
            Long telemetryId = !bleHistoryEnabled || telemetryData.getEyeBeacons().isEmpty()
                    ? TelemetryRepository.insert(rawPacketId, telemetryData, protocol, channel, dictionaryCode)
                    : insertWithBle(rawPacketId, telemetryData, protocol, channel, dictionaryCode);
            if (telemetryId == null) {
                throw new IllegalStateException("Telemetry persistence did not return an id");
            }

        DeviceStatusRepository.markChannelOnline(telemetryData.getImei(), channel, protocol);
        DevicePresenceRepository.markPresentOnData(telemetryData.getImei());

        TelemetryIoRepository.insertAll(
                telemetryId,
                rawPacketId,
                telemetryData,
                protocol,
                channel,
                dictionaryCode
        );

        NormalizationEngine.normalizeAndStore(
                telemetryId,
                rawPacketId,
                null,
                null,
                telemetryData,
                protocol,
                dictionaryCode
        );

        TelemetryAlertEvaluator.evaluateAndStore(
                telemetryId,
                telemetryData,
                protocol,
                channel,
                dictionaryCode
        );

        System.out.println("[DB TELEMETRY] persisted imei=" + telemetryData.getImei()
                + " telemetryId=" + telemetryId
                + " protocol=" + protocol
                + " dictionary=" + dictionaryCode
                + " parser=" + logLabel);

            return CompletableFuture.completedFuture(telemetryId);
        } catch (Exception error) {
            log.error("event=direct_telemetry_failed imei={} error={} sqlState={}",
                    telemetryData == null ? null : telemetryData.getImei(), error.getClass().getSimpleName(),
                    error instanceof SQLException sql ? sql.getSQLState() : null);
            log.debug("Direct telemetry persistence failure", error);
            return CompletableFuture.failedFuture(error);
        }
    }

    private Long insertWithBle(Long rawPacketId, TelemetryData data, String protocol,
                               String channel, String dictionaryCode) throws Exception {
        if (rawPacketId == null) throw new IllegalStateException("BLE source raw packet is required");
        var prepared = TelemetryRepository.prepare(data);
        try (Connection connection = DatabaseConfig.getConnection()) {
            connection.setAutoCommit(false);
            try {
                String raw;
                try (PreparedStatement statement = connection.prepareStatement("""
                        SELECT COALESCE(raw_hex, raw_json, payload) FROM raw_packets
                        WHERE id = ? AND imei = ? ORDER BY received_at DESC LIMIT 1
                        """)) {
                    statement.setLong(1, rawPacketId);
                    statement.setString(2, data.getImei());
                    try (ResultSet result = statement.executeQuery()) {
                        if (!result.next() || result.getString(1) == null) {
                            throw new IllegalStateException("BLE source raw packet is unavailable");
                        }
                        raw = result.getString(1);
                    }
                }
                String source = data.getImei() + '\u0000' + protocol + '\u0000' + channel
                        + '\u0000' + data.getPacketSequence() + '\u0000' + raw;
                String sourceKey = HexFormat.of().formatHex(
                        MessageDigest.getInstance("SHA-256").digest(source.getBytes(StandardCharsets.UTF_8)));
                Long telemetryId = TelemetryRepository.insert(
                        connection, rawPacketId, data, protocol, channel, dictionaryCode, prepared);
                if (telemetryId == null) throw new IllegalStateException("BLE source telemetry insert failed");
                Timestamp serverTime;
                try (PreparedStatement statement = connection.prepareStatement("""
                        SELECT server_time FROM telemetry WHERE id = ?
                        ORDER BY server_time DESC LIMIT 1
                        """)) {
                    statement.setLong(1, telemetryId);
                    try (ResultSet result = statement.executeQuery()) {
                        if (!result.next()) throw new IllegalStateException("BLE source telemetry not found");
                        serverTime = result.getTimestamp(1);
                    }
                }
                try (PreparedStatement statement = connection.prepareStatement("""
                        SELECT alels_store_ble_observations(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb)
                        """)) {
                    statement.setString(1, data.getImei());
                    statement.setString(2, sourceKey);
                    statement.setLong(3, telemetryId);
                    statement.setTimestamp(4, serverTime);
                    statement.setString(5, protocol);
                    statement.setString(6, channel);
                    statement.setTimestamp(7, TimeUtil.parseDeviceTimestampUtc(data.getDeviceTime()));
                    statement.setTimestamp(8, serverTime);
                    statement.setDouble(9, data.getLatitude());
                    statement.setDouble(10, data.getLongitude());
                    statement.setInt(11, data.getAltitude());
                    statement.setDouble(12, data.getHdop());
                    statement.setInt(13, data.getSatellites());
                    statement.setString(14, MAPPER.writeValueAsString(data.getEyeBeacons()));
                    statement.executeQuery().close();
                }
                connection.commit();
                return telemetryId;
            } catch (Exception error) {
                connection.rollback();
                throw error;
            }
        }
    }
}
