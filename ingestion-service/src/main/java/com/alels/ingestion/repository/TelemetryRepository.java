package com.alels.ingestion.repository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import com.alels.ingestion.model.TelemetryMessage;
import com.alels.ingestion.model.IngestionRecord;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.alels.ingestion.validation.service.TelemetryMessageValidator;

public class TelemetryRepository {

    private static final ObjectMapper mapper = new ObjectMapper();

    private final Database database;

    public TelemetryRepository(Database database) {
        this.database = database;
    }

    public boolean existsDuplicate(TelemetryMessage message) {
        if (message == null || message.imei == null || message.packetSequence == null) {
            return false;
        }

        String sql = """
                SELECT 1
                FROM telemetry
                WHERE imei = ?
                  AND protocol IS NOT DISTINCT FROM ?
                  AND channel IS NOT DISTINCT FROM ?
                  AND packet_sequence = ?
                LIMIT 1
                """;

        try (Connection conn = database.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, message.imei);
            stmt.setString(2, message.protocol);
            stmt.setString(3, message.channel);
            stmt.setLong(4, message.packetSequence);

            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }

        } catch (Exception e) {
            System.err.println("[INGESTION DUPLICATE CHECK ERROR] imei="
                    + message.imei
                    + " q="
                    + message.packetSequence
                    + " error="
                    + e.getMessage());
            return false;
        }
    }

    public Long insertRawPacket(TelemetryMessage message, String payload) {
        String sql = """
                INSERT INTO raw_packets (
                    imei,
                    protocol,
                    channel,
                    payload,
                    payload_hash,
                    parse_status,
                    received_at,
                    created_at
                )
                VALUES (?, ?, ?, ?, ?, 'RECEIVED', NOW(), NOW())
                RETURNING id
                """;

        try (Connection conn = database.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, message.imei);
            stmt.setString(2, message.protocol);
            stmt.setString(3, message.channel);
            stmt.setString(4, payload);
            stmt.setString(5, sha256(payload));

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong("id");
                }
            }
        } catch (Exception e) {
            System.err.println("[INGESTION RAW PACKET INSERT ERROR] imei=" + message.imei + " error=" + e.getMessage());
        }

        return null;
    }

    public Long insertTelemetry(TelemetryMessage message, Long rawPacketId, String payload) {
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
                    vehicle_status,
                    io_data,
                    payload_hash,
                    parse_status,
                    created_at
                )
                VALUES (
                    ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, 'VALID', NOW()
                )
                ON CONFLICT DO NOTHING
                RETURNING id
                """;

        try (Connection conn = database.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            setLong(stmt, 1, rawPacketId);
            stmt.setString(2, message.imei);
            stmt.setString(3, message.protocol);
            stmt.setString(4, message.channel);
            stmt.setString(5, message.dictionaryCode);
            stmt.setString(6, message.sourceProtocol);
            setTimestamp(stmt, 7, parseTimestamp(message.deviceTime));
            setLong(stmt, 8, message.packetSequence);
            setDouble(stmt, 9, message.latitude);
            setDouble(stmt, 10, message.longitude);
            setInteger(stmt, 11, message.altitude);
            setInteger(stmt, 12, message.angle);
            setInteger(stmt, 13, message.satellites);
            setInteger(stmt, 14, message.speed);
            setDouble(stmt, 15, message.hdop);
            setInteger(stmt, 16, message.priority);
            setInteger(stmt, 17, message.eventIoId);
            stmt.setString(18, resolveVehicleStatus(message));
            stmt.setString(19, message.ioData == null ? "{}" : mapper.writeValueAsString(message.ioData));
            stmt.setString(20, sha256(payload));

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong("id");
                }
            }

            System.out.println("[INGESTION IDEMPOTENT SKIP] imei=" + message.imei + " q=" + message.packetSequence);
            return -1L;

        } catch (Exception e) {
            System.err.println("[INGESTION DB INSERT ERROR] imei="
                    + message.imei
                    + " q="
                    + message.packetSequence
                    + " error="
                    + e.getMessage());
        }

        return null;
    }

    public void updateDeviceLatest(TelemetryMessage message) {
        String sql = """
                UPDATE devices
                SET last_seen = NOW(),
                    active_channel = ?,
                    last_protocol = ?,
                    online = TRUE,
                    presence_status = 'ONLINE',
                    status_updated_at = NOW()
                WHERE imei = ?
                """;

        try (Connection conn = database.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, message.channel);
            stmt.setString(2, message.protocol);
            stmt.setString(3, message.imei);
            stmt.executeUpdate();

        } catch (Exception e) {
            System.err.println("[INGESTION DEVICE LATEST ERROR] imei=" + message.imei + " error=" + e.getMessage());
        }
    }

    public void insertDeadLetter(String topic, int partition, long offset, String key, String payload, String failureReason) {
        String sql = """
                INSERT INTO telemetry_ingestion_dlq (
                    topic_name,
                    partition_no,
                    offset_no,
                    message_key,
                    payload,
                    failure_reason,
                    status,
                    created_at
                ) VALUES (?, ?, ?, ?, ?, ?, 'OPEN', NOW())
                ON CONFLICT DO NOTHING
                """;

        try (Connection conn = database.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, topic);
            stmt.setInt(2, partition);
            stmt.setLong(3, offset);
            stmt.setString(4, key);
            stmt.setString(5, payload);
            stmt.setString(6, failureReason);
            stmt.executeUpdate();
        } catch (Exception e) {
            System.err.println("[INGESTION DLQ DB ERROR] " + e.getMessage());
        }
    }

    public void upsertIngestionHealth(int processed, int failed, long consumerLag) {
        String sql = """
                INSERT INTO ingestion_pipeline_health (
                    service_name,
                    status,
                    last_heartbeat_at,
                    processed_last_poll,
                    failed_last_poll,
                    consumer_lag,
                    updated_at
                ) VALUES ('ingestion-service', 'UP', NOW(), ?, ?, ?, NOW())
                ON CONFLICT (service_name)
                DO UPDATE SET
                    status = EXCLUDED.status,
                    last_heartbeat_at = EXCLUDED.last_heartbeat_at,
                    processed_last_poll = EXCLUDED.processed_last_poll,
                    failed_last_poll = EXCLUDED.failed_last_poll,
                    consumer_lag = EXCLUDED.consumer_lag,
                    updated_at = NOW()
                """;

        try (Connection conn = database.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, processed);
            stmt.setInt(2, failed);
            stmt.setLong(3, Math.max(consumerLag, 0));
            stmt.executeUpdate();
        } catch (Exception e) {
            System.err.println("[INGESTION HEALTH DB ERROR] " + e.getMessage());
        }
    }

    public void insertLagSnapshot(String groupId, String topic, int partition, long currentOffset, long endOffset, long lag) {
        String sql = """
                INSERT INTO kafka_consumer_lag_snapshots (
                    service_name, group_id, topic_name, partition_no, current_offset, end_offset, lag, captured_at
                ) VALUES ('ingestion-service', ?, ?, ?, ?, ?, ?, NOW())
                """;
        try (Connection conn = database.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, groupId);
            stmt.setString(2, topic);
            stmt.setInt(3, partition);
            stmt.setLong(4, currentOffset);
            stmt.setLong(5, endOffset);
            stmt.setLong(6, Math.max(lag, 0));
            stmt.executeUpdate();
        } catch (Exception e) {
            System.err.println("[INGESTION LAG SNAPSHOT DB ERROR] " + e.getMessage());
        }
    }


    public BatchIngestionResult insertBatch(List<IngestionRecord> records, List<TelemetryMessage> messages) {
        if (records == null || records.isEmpty()) {
            return new BatchIngestionResult(0, 0);
        }

        String rawSql = """
                INSERT INTO raw_packets (
                    imei, protocol, channel, payload, payload_hash, parse_status,
                    kafka_topic, kafka_partition, kafka_offset, received_at, created_at
                ) VALUES (?, ?, ?, ?, ?, 'RECEIVED', ?, ?, ?, NOW(), NOW())
                ON CONFLICT DO NOTHING
                """;

        String telemetrySql = """
                INSERT INTO telemetry (
                    imei, protocol, channel, dictionary_code, source_protocol, device_time,
                    packet_sequence, latitude, longitude, altitude, angle, satellites, speed, hdop,
                    priority, event_io_id, vehicle_status, io_data, io, payload_hash, parse_status,
                    ingestion_status, kafka_topic, kafka_partition, kafka_offset, created_at
                ) VALUES (
                    ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?,
                    'VALID', 'VALID', ?, ?, ?, NOW()
                )
                ON CONFLICT DO NOTHING
                """;

        String deviceSql = """
                UPDATE devices
                SET last_seen = NOW(),
                    active_channel = ?,
                    last_protocol = ?,
                    online = TRUE,
                    presence_status = 'ONLINE',
                    status_updated_at = NOW(),
                    updated_at = NOW()
                WHERE imei = ?
                """;

        String presenceSql = """
                INSERT INTO device_presence_cache_shadow (
                    imei, presence_status, active_channel, last_protocol, last_seen_at,
                    offline_after, latitude, longitude, speed, angle, satellites,
                    device_time, server_time, redis_key, redis_ttl_seconds, updated_at
                ) VALUES (
                    ?, 'ONLINE', ?, ?, NOW(), NOW() + interval '1800 seconds', ?, ?, ?, ?, ?, ?, NOW(), ?, 1800, NOW()
                )
                ON CONFLICT (imei)
                DO UPDATE SET
                    presence_status = 'ONLINE',
                    active_channel = EXCLUDED.active_channel,
                    last_protocol = EXCLUDED.last_protocol,
                    last_seen_at = EXCLUDED.last_seen_at,
                    offline_after = EXCLUDED.offline_after,
                    latitude = EXCLUDED.latitude,
                    longitude = EXCLUDED.longitude,
                    speed = EXCLUDED.speed,
                    angle = EXCLUDED.angle,
                    satellites = EXCLUDED.satellites,
                    device_time = EXCLUDED.device_time,
                    server_time = EXCLUDED.server_time,
                    redis_key = EXCLUDED.redis_key,
                    redis_ttl_seconds = EXCLUDED.redis_ttl_seconds,
                    updated_at = NOW()
                """;

        String latestPositionSql = """
                INSERT INTO device_latest_position (
                    imei, latitude, longitude, speed, angle, altitude, satellites, hdop,
                    vehicle_status, device_time, server_time, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())
                ON CONFLICT (imei)
                DO UPDATE SET
                    latitude = EXCLUDED.latitude,
                    longitude = EXCLUDED.longitude,
                    speed = EXCLUDED.speed,
                    angle = EXCLUDED.angle,
                    altitude = EXCLUDED.altitude,
                    satellites = EXCLUDED.satellites,
                    hdop = EXCLUDED.hdop,
                    vehicle_status = EXCLUDED.vehicle_status,
                    device_time = EXCLUDED.device_time,
                    server_time = EXCLUDED.server_time,
                    updated_at = NOW()
                WHERE COALESCE(EXCLUDED.device_time, EXCLUDED.server_time)
                      >= COALESCE(device_latest_position.device_time, device_latest_position.server_time)
                """;

        int processed = 0;
        int failed = 0;
        Map<String, TelemetryMessage> latestByDevice = new LinkedHashMap<>();

        try (Connection conn = database.getConnection();
             PreparedStatement rawStmt = conn.prepareStatement(rawSql);
             PreparedStatement telemetryStmt = conn.prepareStatement(telemetrySql);
             PreparedStatement deviceStmt = conn.prepareStatement(deviceSql);
             PreparedStatement presenceStmt = conn.prepareStatement(presenceSql);
             PreparedStatement latestPositionStmt = conn.prepareStatement(latestPositionSql)) {

            conn.setAutoCommit(false);
            Set<String> claimedOffsets = claimOffsets(conn, records, messages);

            for (int i = 0; i < records.size(); i++) {
                IngestionRecord record = records.get(i);
                TelemetryMessage message = messages.get(i);

                if (message == null || message.imei == null || message.imei.isBlank()) {
                    failed++;
                    continue;
                }

                try {
                    if (record.topic != null && !record.topic.isBlank()) {
                        if (!claimedOffsets.contains(offsetKey(record))) {
                            continue;
                        }
                    }

                    rawStmt.setString(1, message.imei);
                    rawStmt.setString(2, message.protocol);
                    rawStmt.setString(3, message.channel);
                    rawStmt.setString(4, record.payload);
                    rawStmt.setString(5, sha256(record.payload));
                    rawStmt.setString(6, record.topic);
                    rawStmt.setInt(7, record.partition);
                    rawStmt.setLong(8, record.offset);
                    rawStmt.addBatch();

                    telemetryStmt.setString(1, message.imei);
                    telemetryStmt.setString(2, message.protocol);
                    telemetryStmt.setString(3, message.channel);
                    telemetryStmt.setString(4, message.dictionaryCode);
                    telemetryStmt.setString(5, message.sourceProtocol);
                    setTimestamp(telemetryStmt, 6, parseTimestamp(message.deviceTime));
                    setLong(telemetryStmt, 7, message.packetSequence);
                    setDouble(telemetryStmt, 8, message.latitude);
                    setDouble(telemetryStmt, 9, message.longitude);
                    setInteger(telemetryStmt, 10, message.altitude);
                    setInteger(telemetryStmt, 11, message.angle);
                    setInteger(telemetryStmt, 12, message.satellites);
                    setInteger(telemetryStmt, 13, message.speed);
                    setDouble(telemetryStmt, 14, message.hdop);
                    setInteger(telemetryStmt, 15, message.priority);
                    setInteger(telemetryStmt, 16, message.eventIoId);
                    telemetryStmt.setString(17, resolveVehicleStatus(message));
                    String ioJson = message.ioData == null ? "{}" : mapper.writeValueAsString(message.ioData);
                    telemetryStmt.setString(18, ioJson);
                    telemetryStmt.setString(19, ioJson);
                    telemetryStmt.setString(20, sha256(record.payload));
                    telemetryStmt.setString(21, record.topic);
                    telemetryStmt.setInt(22, record.partition);
                    telemetryStmt.setLong(23, record.offset);
                    telemetryStmt.addBatch();

                    latestByDevice.put(message.imei, message);
                    processed++;
                } catch (Exception itemError) {
                    throw new IllegalArgumentException(
                            "Invalid ingestion item imei=" + message.imei,
                            itemError
                    );
                }
            }

            for (TelemetryMessage message : latestByDevice.values()) {
                deviceStmt.setString(1, message.channel);
                deviceStmt.setString(2, message.protocol);
                deviceStmt.setString(3, message.imei);
                deviceStmt.addBatch();

                presenceStmt.setString(1, message.imei);
                presenceStmt.setString(2, message.channel);
                presenceStmt.setString(3, message.protocol);
                setDouble(presenceStmt, 4, message.latitude);
                setDouble(presenceStmt, 5, message.longitude);
                setInteger(presenceStmt, 6, message.speed);
                setInteger(presenceStmt, 7, message.angle);
                setInteger(presenceStmt, 8, message.satellites);
                setTimestamp(presenceStmt, 9, parseTimestamp(message.deviceTime));
                presenceStmt.setString(10, "device:presence:" + message.imei);
                presenceStmt.addBatch();

                latestPositionStmt.setString(1, message.imei);
                setDouble(latestPositionStmt, 2, message.latitude);
                setDouble(latestPositionStmt, 3, message.longitude);
                setInteger(latestPositionStmt, 4, message.speed);
                setInteger(latestPositionStmt, 5, message.angle);
                setInteger(latestPositionStmt, 6, message.altitude);
                setInteger(latestPositionStmt, 7, message.satellites);
                setDouble(latestPositionStmt, 8, message.hdop);
                latestPositionStmt.setString(9, resolveVehicleStatus(message));
                setTimestamp(latestPositionStmt, 10, parseTimestamp(message.deviceTime));
                latestPositionStmt.addBatch();
            }

            rawStmt.executeBatch();
            telemetryStmt.executeBatch();
            deviceStmt.executeBatch();
            presenceStmt.executeBatch();
            latestPositionStmt.executeBatch();
            conn.commit();
        } catch (Exception e) {
            throw new IllegalStateException("Atomic ingestion batch failed", e);
        }

        return new BatchIngestionResult(processed, failed, records.size() - processed - failed, List.of());
    }

    private Set<String> claimOffsets(
            Connection connection,
            List<IngestionRecord> records,
            List<TelemetryMessage> messages
    ) throws Exception {
        List<Integer> claimable = new java.util.ArrayList<>();
        for (int i = 0; i < records.size(); i++) {
            IngestionRecord record = records.get(i);
            TelemetryMessage message = messages.get(i);
            if (record != null && record.topic != null && !record.topic.isBlank()
                    && message != null && message.imei != null && !message.imei.isBlank()) {
                claimable.add(i);
            }
        }

        if (claimable.isEmpty()) {
            return Set.of();
        }

        String values = String.join(",", java.util.Collections.nCopies(
                claimable.size(), "(?, ?, ?, 'alels-ingestion-service', ?, ?, 'PROCESSED', NOW(), NOW())"
        ));
        String sql = """
                INSERT INTO kafka_processed_offsets (
                    topic_name, partition_no, offset_no, consumer_group,
                    message_key, payload_hash, status, first_seen_at, processed_at
                ) VALUES %s
                ON CONFLICT DO NOTHING
                RETURNING topic_name, partition_no, offset_no
                """.formatted(values);

        Set<String> claimed = new HashSet<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            int parameter = 1;
            for (int index : claimable) {
                IngestionRecord record = records.get(index);
                statement.setString(parameter++, record.topic);
                statement.setInt(parameter++, record.partition);
                statement.setLong(parameter++, record.offset);
                statement.setString(parameter++, record.key);
                statement.setString(parameter++, sha256(record.payload));
            }
            try (java.sql.ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    claimed.add(offsetKey(
                            result.getString("topic_name"),
                            result.getInt("partition_no"),
                            result.getLong("offset_no")
                    ));
                }
            }
        }
        return claimed;
    }

    private String offsetKey(IngestionRecord record) {
        return offsetKey(record.topic, record.partition, record.offset);
    }

    private String offsetKey(String topic, int partition, long offset) {
        return topic + '\u0000' + partition + '\u0000' + offset;
    }

    public List<DlqItem> fetchOpenDlqItems(int limit) {
        String sql = """
                SELECT id, topic_name, partition_no, offset_no, message_key, payload, retry_count
                FROM telemetry_ingestion_dlq
                WHERE status IN ('OPEN', 'RETRY_FAILED')
                ORDER BY created_at ASC
                LIMIT ?
                """;
        List<DlqItem> items = new ArrayList<>();
        try (Connection conn = database.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, Math.max(limit, 1));
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    DlqItem item = new DlqItem();
                    item.id = rs.getLong("id");
                    item.topicName = rs.getString("topic_name");
                    item.partitionNo = rs.getInt("partition_no");
                    item.offsetNo = rs.getLong("offset_no");
                    item.messageKey = rs.getString("message_key");
                    item.payload = rs.getString("payload");
                    item.retryCount = rs.getInt("retry_count");
                    items.add(item);
                }
            }
        } catch (Exception e) {
            System.err.println("[INGESTION DLQ FETCH ERROR] " + e.getMessage());
        }
        return items;
    }

    public void markDlqResolved(long id) {
        updateDlqStatus(id, "RESOLVED", true);
    }

    public void markDlqRetryFailed(long id) {
        updateDlqStatus(id, "RETRY_FAILED", false);
    }

    private void updateDlqStatus(long id, String status, boolean resolved) {
        String sql = """
                UPDATE telemetry_ingestion_dlq
                SET status = ?,
                    retry_count = retry_count + 1,
                    last_retry_at = NOW(),
                    resolved_at = CASE WHEN ? THEN NOW() ELSE resolved_at END
                WHERE id = ?
                """;
        try (Connection conn = database.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, status);
            stmt.setBoolean(2, resolved);
            stmt.setLong(3, id);
            stmt.executeUpdate();
        } catch (Exception e) {
            System.err.println("[INGESTION DLQ STATUS ERROR] " + e.getMessage());
        }
    }

    private String resolveVehicleStatus(TelemetryMessage message) {
        if (message.speed == null) {
            return "UNKNOWN";
        }

        if (message.speed > 0) {
            return "MOVING";
        }

        return "STOP";
    }

    private Timestamp parseTimestamp(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        java.time.Instant parsed = TelemetryMessageValidator.parseUtc(value);
        return parsed == null ? null : Timestamp.from(parsed);
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            return null;
        }
    }

    private void setTimestamp(PreparedStatement stmt, int index, Timestamp value) throws Exception {
        if (value == null) {
            stmt.setNull(index, java.sql.Types.TIMESTAMP);
        } else {
            stmt.setTimestamp(index, value);
        }
    }

    private void setLong(PreparedStatement stmt, int index, Long value) throws Exception {
        if (value == null) {
            stmt.setNull(index, java.sql.Types.BIGINT);
        } else {
            stmt.setLong(index, value);
        }
    }

    private void setInteger(PreparedStatement stmt, int index, Integer value) throws Exception {
        if (value == null) {
            stmt.setNull(index, java.sql.Types.INTEGER);
        } else {
            stmt.setInt(index, value);
        }
    }

    private void setDouble(PreparedStatement stmt, int index, Double value) throws Exception {
        if (value == null) {
            stmt.setNull(index, java.sql.Types.DOUBLE);
        } else {
            stmt.setDouble(index, value);
        }
    }
}
