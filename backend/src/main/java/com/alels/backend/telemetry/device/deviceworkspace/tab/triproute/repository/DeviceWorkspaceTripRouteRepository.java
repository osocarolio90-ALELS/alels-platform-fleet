package com.alels.backend.telemetry.device.deviceworkspace.tab.triproute.repository;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Repository;

import com.alels.backend.telemetry.device.deviceworkspace.tab.triproute.dto.DeviceWorkspaceTripRouteDtos.ParameterValue;
import com.alels.backend.telemetry.device.deviceworkspace.tab.triproute.dto.DeviceWorkspaceTripRouteDtos.TripEvent;
import com.alels.backend.telemetry.device.deviceworkspace.tab.triproute.dto.DeviceWorkspaceTripRouteDtos.TripLogRow;
import com.alels.backend.telemetry.device.deviceworkspace.tab.triproute.dto.DeviceWorkspaceTripRouteDtos.TripInstrumentSnapshot;
import com.alels.backend.telemetry.device.deviceworkspace.tab.triproute.dto.DeviceWorkspaceTripRouteDtos.DeleteDeviceHistoryResponse;
import com.alels.backend.telemetry.device.deviceworkspace.tab.triproute.dto.DeviceWorkspaceTripRouteDtos.InstrumentSourceProfile;
import com.alels.backend.telemetry.device.deviceworkspace.tab.triproute.dto.DeviceWorkspaceTripRouteDtos.InstrumentSourceOption;

@Repository
public class DeviceWorkspaceTripRouteRepository {
    public static final int MAX_RANGE_POINTS = 50_000;
    public static final int MAX_DETAIL_POINTS = 10_000;
    private final JdbcTemplate jdbc;

    public DeviceWorkspaceTripRouteRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public DeleteDeviceHistoryResponse deleteAllHistory(String imei, Instant resetAt) {
        Long telemetryCutoff = jdbc.queryForObject("SELECT MAX(id) FROM telemetry WHERE imei = ?", Long.class, imei);
        Long rawPacketCutoff = jdbc.queryForObject("SELECT MAX(id) FROM raw_packets WHERE imei = ?", Long.class, imei);
        int events = telemetryCutoff == null ? jdbc.update("DELETE FROM telemetry_events WHERE imei = ?", imei) : jdbc.update(
                "DELETE FROM telemetry_events WHERE imei = ? AND (telemetry_id IS NULL OR telemetry_id <= ?)", imei, telemetryCutoff);
        int alerts = telemetryCutoff == null ? 0 : jdbc.update(
                "DELETE FROM telemetry_alerts WHERE imei = ? AND (telemetry_id IS NULL OR telemetry_id <= ?)", imei, telemetryCutoff);
        int normalized = telemetryCutoff == null ? 0 : jdbc.update(
                "DELETE FROM telemetry_normalized WHERE imei = ? AND telemetry_id <= ?", imei, telemetryCutoff);
        int io = telemetryCutoff == null ? 0 : jdbc.update(
                "DELETE FROM telemetry_io WHERE imei = ? AND telemetry_id <= ?", imei, telemetryCutoff);
        int latestPosition = jdbc.update("DELETE FROM device_latest_position WHERE imei = ?", imei);
        int presence = jdbc.update("DELETE FROM device_presence_cache_shadow WHERE imei = ?", imei);
        int telemetry = telemetryCutoff == null ? 0 : jdbc.update(
                "DELETE FROM telemetry WHERE imei = ? AND id <= ?", imei, telemetryCutoff);
        int rawPackets = rawPacketCutoff == null ? 0 : jdbc.update(
                "DELETE FROM raw_packets WHERE imei = ? AND id <= ?", imei, rawPacketCutoff);
        return new DeleteDeviceHistoryResponse(imei, resetAt.toString(), telemetry, io, normalized,
                alerts, events, rawPackets, latestPosition, presence);
    }


    public void scanPoints(String imei, Instant from, Instant to, Consumer<TelemetryPoint> consumer) {
        String sql = """
                SELECT t.id, COALESCE(t.device_time, t.server_time) AS occurred_at, t.server_time AS received_at,
                       t.latitude, t.longitude, t.speed, t.angle, t.altitude, t.satellites,
                       t.hdop, t.protocol, t.channel, t.driver_name, t.vehicle_status,
                       MAX(CASE WHEN n.field_code = 'ignition' THEN
                           CASE WHEN n.boolean_value IS NOT NULL THEN CASE WHEN n.boolean_value THEN 1.0 ELSE 0.0 END
                                ELSE n.numeric_value END END) AS ignition,
                       MAX(CASE WHEN n.field_code = 'movement' THEN
                           CASE WHEN n.boolean_value IS NOT NULL THEN CASE WHEN n.boolean_value THEN 1.0 ELSE 0.0 END
                                ELSE n.numeric_value END END) AS movement
                FROM telemetry t
                LEFT JOIN telemetry_normalized n ON n.telemetry_id = t.id AND n.imei = t.imei
                     AND n.field_code IN ('ignition', 'movement')
                WHERE t.imei = ? AND COALESCE(t.device_time, t.server_time) BETWEEN ? AND ?
                GROUP BY t.id, t.server_time, t.device_time, t.latitude, t.longitude, t.speed,
                         t.angle, t.altitude, t.satellites, t.hdop, t.protocol, t.channel,
                         t.driver_name, t.vehicle_status
                ORDER BY occurred_at, t.id
                """;
        jdbc.query(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    sql, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
            statement.setFetchSize(2_000);
            statement.setString(1, imei);
            statement.setTimestamp(2, Timestamp.from(from));
            statement.setTimestamp(3, Timestamp.from(to));
            return statement;
        }, (RowCallbackHandler) rs -> consumer.accept(point(rs)));
    }

    public void scanSelectedRoutePoints(String imei, List<RouteWindow> ranges, BiConsumer<String, TelemetryPoint> consumer) {
        if (ranges == null || ranges.isEmpty()) return;
        String values = String.join(",", ranges.stream()
                .map(ignored -> "(?::integer, ?::text, ?::timestamptz, ?::timestamptz)")
                .toList());
        String sql = """
                WITH selected_ranges(range_order, trip_id, from_at, to_at) AS (VALUES %s)
                SELECT r.range_order, r.trip_id, t.id,
                       COALESCE(t.device_time, t.server_time) AS occurred_at,
                       t.server_time AS received_at, t.latitude, t.longitude, t.speed, t.angle,
                       t.altitude, t.satellites, t.hdop, t.protocol, t.channel, t.driver_name,
                       t.vehicle_status, NULL::double precision AS ignition, NULL::double precision AS movement
                FROM selected_ranges r
                JOIN telemetry t
                  ON t.imei = ?
                 AND COALESCE(t.device_time, t.server_time) BETWEEN r.from_at AND r.to_at
                WHERE t.latitude BETWEEN -90 AND 90
                  AND t.longitude BETWEEN -180 AND 180
                  AND NOT (t.latitude = 0 AND t.longitude = 0)
                ORDER BY r.range_order, occurred_at, t.id
                """.formatted(values);
        jdbc.query(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    sql, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
            statement.setFetchSize(2_000);
            int index = 1;
            for (int rangeIndex = 0; rangeIndex < ranges.size(); rangeIndex++) {
                RouteWindow range = ranges.get(rangeIndex);
                statement.setInt(index++, rangeIndex);
                statement.setString(index++, range.tripId());
                statement.setTimestamp(index++, Timestamp.from(range.from()));
                statement.setTimestamp(index++, Timestamp.from(range.to()));
            }
            statement.setString(index, imei);
            return statement;
        }, (RowCallbackHandler) rs -> consumer.accept(rs.getString("trip_id"), point(rs)));
    }

    public List<TelemetryPoint> playbackPointsByIds(String imei, List<Long> telemetryIds) {
        if (telemetryIds == null || telemetryIds.isEmpty()) return List.of();
        List<TelemetryPoint> result = new ArrayList<>();
        final int chunkSize = 4_000;
        for (int offset = 0; offset < telemetryIds.size(); offset += chunkSize) {
            List<Long> chunk = telemetryIds.subList(offset, Math.min(telemetryIds.size(), offset + chunkSize));
            String placeholders = String.join(",", chunk.stream().map(ignored -> "?").toList());
            String sql = """
                    SELECT t.id, COALESCE(t.device_time, t.server_time) AS occurred_at, t.server_time AS received_at,
                           t.latitude, t.longitude, t.speed, t.angle, t.altitude, t.satellites,
                           t.hdop, t.protocol, t.channel, t.driver_name, t.vehicle_status,
                           NULL::double precision AS ignition, NULL::double precision AS movement
                    FROM telemetry t
                    WHERE t.imei = ? AND t.id IN (%s)
                    ORDER BY occurred_at, t.id
                    """.formatted(placeholders);
            List<Object> args = new ArrayList<>();
            args.add(imei);
            args.addAll(chunk);
            result.addAll(jdbc.query(sql, (rs, rowNum) -> point(rs), args.toArray()));
        }
        result.sort((a, b) -> {
            int time = a.occurredAt().compareTo(b.occurredAt());
            return time != 0 ? time : a.id().compareTo(b.id());
        });
        return result;
    }

    public List<TelemetryPoint> detailPoints(String imei, Instant from, Instant to) {
        return jdbc.query("""
                SELECT t.id, COALESCE(t.device_time, t.server_time) AS occurred_at, t.server_time AS received_at,
                       t.latitude, t.longitude, t.speed, t.angle, t.altitude, t.satellites,
                       t.hdop, t.protocol, t.channel, t.driver_name, t.vehicle_status,
                       MAX(CASE WHEN n.field_code = 'ignition' THEN
                           CASE WHEN n.boolean_value IS NOT NULL THEN CASE WHEN n.boolean_value THEN 1.0 ELSE 0.0 END
                                ELSE n.numeric_value END END) AS ignition,
                       MAX(CASE WHEN n.field_code = 'movement' THEN
                           CASE WHEN n.boolean_value IS NOT NULL THEN CASE WHEN n.boolean_value THEN 1.0 ELSE 0.0 END
                                ELSE n.numeric_value END END) AS movement
                FROM telemetry t
                LEFT JOIN telemetry_normalized n ON n.telemetry_id = t.id AND n.imei = t.imei
                     AND n.field_code IN ('ignition', 'movement')
                WHERE t.imei = ? AND COALESCE(t.device_time, t.server_time) BETWEEN ? AND ?
                GROUP BY t.id, t.server_time, t.device_time, t.latitude, t.longitude, t.speed,
                         t.angle, t.altitude, t.satellites, t.hdop, t.protocol, t.channel,
                         t.driver_name, t.vehicle_status
                ORDER BY occurred_at, t.id LIMIT ?
                """, (rs, rowNum) -> point(rs), imei, Timestamp.from(from), Timestamp.from(to), MAX_DETAIL_POINTS + 1);
    }

    public List<TelemetryPoint> logPoints(String imei, Instant from, Instant to,
                                          Instant beforeTime, Long beforeId, int limit) {
        boolean hasCursor = beforeTime != null && beforeId != null;
        String cursor = hasCursor ? """
                  AND (t.server_time < ? OR (t.server_time = ? AND t.id < ?))
                """ : "";
        String sql = """
                SELECT t.id, COALESCE(t.device_time, t.server_time) AS occurred_at, t.server_time AS received_at,
                       t.latitude, t.longitude, t.speed, t.angle, t.altitude, t.satellites,
                       t.hdop, t.protocol, t.channel, t.driver_name, t.vehicle_status,
                       NULL::double precision AS ignition, NULL::double precision AS movement
                FROM telemetry t
                WHERE t.imei = ? AND (t.server_time BETWEEN ? AND ? OR t.device_time BETWEEN ? AND ?)
                %s
                ORDER BY received_at DESC, t.id DESC LIMIT ?
                """.formatted(cursor);
        List<Object> args = new ArrayList<>();
        args.add(imei);
        args.add(Timestamp.from(from));
        args.add(Timestamp.from(to));
        args.add(Timestamp.from(from));
        args.add(Timestamp.from(to));
        if (hasCursor) {
            args.add(Timestamp.from(beforeTime));
            args.add(Timestamp.from(beforeTime));
            args.add(beforeId);
        }
        args.add(limit);
        return jdbc.query(sql, (rs, rowNum) -> point(rs), args.toArray());
    }


    public List<TelemetryPoint> selectedLogPoints(String imei, List<TimeWindow> ranges, int page, int size) {
        String values = String.join(",", ranges.stream().map(ignored -> "(?::timestamptz, ?::timestamptz)").toList());
        String sql = """
                WITH selected_ranges(from_at, to_at) AS (VALUES %s)
                SELECT t.id, COALESCE(t.device_time, t.server_time) AS occurred_at, t.server_time AS received_at,
                       t.latitude, t.longitude, t.speed, t.angle, t.altitude, t.satellites,
                       t.hdop, t.protocol, t.channel, t.driver_name, t.vehicle_status,
                       NULL::double precision AS ignition, NULL::double precision AS movement
                FROM telemetry t
                WHERE t.imei = ?
                  AND EXISTS (
                      SELECT 1 FROM selected_ranges r
                      WHERE COALESCE(t.device_time, t.server_time) BETWEEN r.from_at AND r.to_at
                  )
                ORDER BY received_at DESC, t.id DESC
                LIMIT ? OFFSET ?
                """.formatted(values);
        List<Object> args = selectedRangeArgs(ranges);
        args.add(imei);
        args.add(size);
        args.add((long) page * size);
        return jdbc.query(sql, (rs, rowNum) -> point(rs), args.toArray());
    }

    public long selectedLogCount(String imei, List<TimeWindow> ranges) {
        String values = String.join(",", ranges.stream().map(ignored -> "(?::timestamptz, ?::timestamptz)").toList());
        String sql = """
                WITH selected_ranges(from_at, to_at) AS (VALUES %s)
                SELECT COUNT(*)
                FROM telemetry t
                WHERE t.imei = ?
                  AND EXISTS (
                      SELECT 1 FROM selected_ranges r
                      WHERE COALESCE(t.device_time, t.server_time) BETWEEN r.from_at AND r.to_at
                  )
                """.formatted(values);
        List<Object> args = selectedRangeArgs(ranges);
        args.add(imei);
        Long count = jdbc.queryForObject(sql, Long.class, args.toArray());
        return count == null ? 0L : count;
    }

    public Long latestSelectedTelemetryId(String imei, List<TimeWindow> ranges) {
        String values = String.join(",", ranges.stream().map(ignored -> "(?::timestamptz, ?::timestamptz)").toList());
        String sql = """
                WITH selected_ranges(from_at, to_at) AS (VALUES %s)
                SELECT MAX(t.id)
                FROM telemetry t
                WHERE t.imei = ?
                  AND EXISTS (
                      SELECT 1 FROM selected_ranges r
                      WHERE COALESCE(t.device_time, t.server_time) BETWEEN r.from_at AND r.to_at
                  )
                """.formatted(values);
        List<Object> args = selectedRangeArgs(ranges);
        args.add(imei);
        return jdbc.queryForObject(sql, Long.class, args.toArray());
    }

    private List<Object> selectedRangeArgs(List<TimeWindow> ranges) {
        List<Object> args = new ArrayList<>(ranges.size() * 2 + 3);
        for (TimeWindow range : ranges) {
            args.add(Timestamp.from(range.from()));
            args.add(Timestamp.from(range.to()));
        }
        return args;
    }


    public String vehicleEnergyGroup(Long deviceId, Long companyId) {
        List<String> groups = jdbc.query("""
                SELECT UPPER(COALESCE(NULLIF(et.energy_group, ''),
                           CASE WHEN UPPER(COALESCE(v.energy_code, '')) LIKE 'ELECTRIC%' THEN 'ELECTRIC'
                                WHEN UPPER(COALESCE(v.energy_code, '')) IN ('CNG', 'LNG', 'LPG_AUTOGAS') THEN 'GAS'
                                ELSE 'FUEL' END)) AS energy_group
                FROM vehicle_device_assignments assignment
                JOIN vehicles v ON v.id = assignment.vehicle_id AND v.deleted_at IS NULL
                LEFT JOIN energy_types et ON et.energy_code = v.energy_code
                WHERE assignment.device_id = ?
                  AND assignment.company_id = ?
                  AND v.company_id = ?
                  AND assignment.assignment_status = 'ACTIVE'
                  AND assignment.deleted_at IS NULL
                ORDER BY assignment.assigned_at DESC
                LIMIT 1
                """, (rs, rowNum) -> rs.getString("energy_group"), deviceId, companyId, companyId);
        return groups.isEmpty() || groups.getFirst() == null || groups.getFirst().isBlank() ? "FUEL" : groups.getFirst();
    }


    public List<InstrumentSourceOption> instrumentSourceOptions(String imei) {
        return jdbc.query("""
                WITH recent AS (
                    SELECT id
                    FROM telemetry
                    WHERE imei = ?
                    ORDER BY id DESC
                    LIMIT 500
                ), source_rows AS (
                    SELECT n.field_code AS field_code,
                           COALESCE(NULLIF(n.field_name, ''), n.field_code) AS label,
                           NULLIF(n.unit, '') AS unit,
                           COALESCE(NULLIF(n.category, ''), 'OTHER') AS category,
                           MAX(n.telemetry_id) AS last_telemetry_id
                    FROM telemetry_normalized n
                    WHERE n.imei = ?
                      AND n.numeric_value IS NOT NULL
                      AND n.telemetry_id IN (SELECT id FROM recent)
                      AND n.field_code IS NOT NULL
                      AND n.field_code <> ''
                    GROUP BY n.field_code, COALESCE(NULLIF(n.field_name, ''), n.field_code), NULLIF(n.unit, ''), COALESCE(NULLIF(n.category, ''), 'OTHER')
                    UNION ALL
                    SELECT 'io.' || i.io_id AS field_code,
                           COALESCE(NULLIF(i.io_name, ''), 'IO ' || i.io_id) AS label,
                           NULLIF(i.unit, '') AS unit,
                           COALESCE(NULLIF(i.io_category, ''), 'OTHER') AS category,
                           MAX(i.telemetry_id) AS last_telemetry_id
                    FROM telemetry_io i
                    WHERE i.imei = ?
                      AND COALESCE(i.real_value, i.numeric_value) IS NOT NULL
                      AND i.telemetry_id IN (SELECT id FROM recent)
                    GROUP BY i.io_id, COALESCE(NULLIF(i.io_name, ''), 'IO ' || i.io_id), NULLIF(i.unit, ''), COALESCE(NULLIF(i.io_category, ''), 'OTHER')
                )
                SELECT DISTINCT ON (field_code) field_code, label, unit, category
                FROM source_rows
                ORDER BY field_code, last_telemetry_id DESC
                LIMIT 500
                """, (rs, rowNum) -> new InstrumentSourceOption(
                        rs.getString("field_code"), rs.getString("label"), rs.getString("unit"), rs.getString("category")),
                imei, imei, imei);
    }

    public Map<Long, TripInstrumentSnapshot> instrumentSnapshots(String imei, Instant from, Instant to) {
        Map<Long, InstrumentAccumulator> values = new LinkedHashMap<>();
        jdbc.query("""
                SELECT n.telemetry_id, n.field_code, n.numeric_value, n.unit
                FROM telemetry_normalized n
                JOIN telemetry t ON t.id = n.telemetry_id AND t.imei = n.imei
                WHERE n.imei = ?
                  AND COALESCE(t.device_time, t.server_time) BETWEEN ? AND ?
                  AND n.field_code IN (
                      'engine_rpm', 'fuel_level', 'fuel_rate', 'fuel_used',
                      'battery_soc', 'high_voltage_battery_voltage', 'high_voltage_battery_current'
                  )
                  AND n.numeric_value IS NOT NULL
                ORDER BY n.telemetry_id, n.id
                """, (RowCallbackHandler) rs -> {
            InstrumentAccumulator item = values.computeIfAbsent(rs.getLong("telemetry_id"), ignored -> new InstrumentAccumulator());
            item.acceptNormalized(rs.getString("field_code"), rs.getObject("numeric_value", Double.class), rs.getString("unit"));
        }, imei, Timestamp.from(from), Timestamp.from(to));

        jdbc.query("""
                SELECT i.telemetry_id, LOWER(COALESCE(i.io_name, '')) AS io_name,
                       COALESCE(i.real_value, i.numeric_value) AS numeric_value, i.unit
                FROM telemetry_io i
                JOIN telemetry t ON t.id = i.telemetry_id AND t.imei = i.imei
                WHERE i.imei = ?
                  AND COALESCE(t.device_time, t.server_time) BETWEEN ? AND ?
                  AND COALESCE(i.real_value, i.numeric_value) IS NOT NULL
                  AND (
                      LOWER(COALESCE(i.io_name, '')) IN ('engine rpm', 'engine speed', 'fuel level', 'fuel level percent',
                                                        'fuel rate', 'fuel consumption', 'generic state of charge',
                                                        'battery level percent', 'cng level', 'cng used', 'lpg level', 'lpg used')
                      OR LOWER(COALESCE(i.io_name, '')) LIKE '%gas%pressure%'
                      OR LOWER(COALESCE(i.io_name, '')) LIKE '%battery%power%'
                  )
                ORDER BY i.telemetry_id, i.id
                """, (RowCallbackHandler) rs -> {
            InstrumentAccumulator item = values.computeIfAbsent(rs.getLong("telemetry_id"), ignored -> new InstrumentAccumulator());
            item.acceptIo(rs.getString("io_name"), rs.getObject("numeric_value", Double.class), rs.getString("unit"));
        }, imei, Timestamp.from(from), Timestamp.from(to));

        Map<Long, TripInstrumentSnapshot> result = new LinkedHashMap<>();
        values.forEach((telemetryId, accumulator) -> result.put(telemetryId, accumulator.snapshot()));
        return result;
    }

    public Long latestTelemetryId(String imei, Instant from, Instant to) {
        return jdbc.queryForObject("""
                SELECT MAX(id) FROM telemetry
                WHERE imei = ? AND (server_time BETWEEN ? AND ? OR device_time BETWEEN ? AND ?)
                """, Long.class, imei, Timestamp.from(from), Timestamp.from(to), Timestamp.from(from), Timestamp.from(to));
    }

    public Map<Long, List<ParameterValue>> normalizedParameters(String imei, Instant from, Instant to,
                                                                 List<Long> telemetryIds) {
        if (telemetryIds.isEmpty()) return Map.of();
        String placeholders = String.join(",", telemetryIds.stream().map(id -> "?").toList());
        String sql = """
                SELECT n.telemetry_id, n.field_code, COALESCE(NULLIF(n.field_name, ''), n.field_code) AS label,
                       COALESCE(n.text_value, n.numeric_value::text, n.raw_value,
                           CASE WHEN n.boolean_value IS NULL THEN NULL ELSE n.boolean_value::text END, '-') AS display_value,
                       n.unit, COALESCE(NULLIF(n.category, ''), 'OTHER') AS category
                FROM telemetry_normalized n
                WHERE n.imei = ? AND n.telemetry_id IN (%s)
                ORDER BY n.telemetry_id, n.id
                """.formatted(placeholders);
        List<Object> args = new ArrayList<>();
        args.add(imei); args.addAll(telemetryIds);
        Map<Long, List<ParameterValue>> result = new LinkedHashMap<>();
        jdbc.query(sql, (RowCallbackHandler) rs -> result.computeIfAbsent(rs.getLong("telemetry_id"), ignored -> new ArrayList<>()).add(
                new ParameterValue(rs.getString("field_code"), rs.getString("label"),
                        rs.getString("display_value"), rs.getString("unit"), rs.getString("category"))
        ), args.toArray());
        String ioSql = """
                SELECT i.telemetry_id, 'io.' || i.io_id AS field_code,
                       COALESCE(NULLIF(i.io_name, ''), 'IO ' || i.io_id) AS label,
                       COALESCE(i.real_value::text, i.numeric_value::text, i.raw_value, '-') AS display_value,
                       i.unit, COALESCE(NULLIF(i.io_category, ''), 'OTHER') AS category
                FROM telemetry_io i
                WHERE i.imei = ? AND i.telemetry_id IN (%s)
                  AND NOT EXISTS (
                      SELECT 1 FROM telemetry_normalized n
                      WHERE n.telemetry_id = i.telemetry_id AND n.imei = i.imei
                        AND n.source_io_id = i.io_id
                  )
                ORDER BY i.telemetry_id, i.id
                """.formatted(placeholders);
        jdbc.query(ioSql, (RowCallbackHandler) rs -> result.computeIfAbsent(rs.getLong("telemetry_id"), ignored -> new ArrayList<>()).add(
                new ParameterValue(rs.getString("field_code"), rs.getString("label"),
                        rs.getString("display_value"), rs.getString("unit"), rs.getString("category"))
        ), args.toArray());
        return result;
    }

    public List<TripEvent> events(String imei, Long companyId, Instant from, Instant to) {
        return jdbc.query("""
                SELECT e.id, e.telemetry_id,
                       COALESCE(NULLIF(mapping.source_name, ''), NULLIF(e.io_name, ''), NULLIF(e.title, ''), e.event_code, 'Telemetry event') AS title,
                       COALESCE(e.message, '') AS message, COALESCE(e.severity, 'INFO') AS severity,
                       e.occurred_at, e.latitude, e.longitude, e.speed
                FROM telemetry_events e
                LEFT JOIN LATERAL (
                    SELECT m.source_name
                    FROM device_io_mappings m
                    WHERE e.event_io_id IS NOT NULL AND m.source_io_id = e.event_io_id AND m.status = 'ACTIVE'
                      AND (m.dictionary_code = e.metadata ->> 'dictionaryCode' OR m.dictionary_code IS NULL OR e.metadata ->> 'dictionaryCode' IS NULL)
                      AND (m.source_protocol = e.metadata ->> 'sourceProtocol' OR m.source_protocol IN ('TELTONIKA_AUTO', 'ANY')
                           OR m.source_protocol IS NULL OR e.metadata ->> 'sourceProtocol' IS NULL)
                    ORDER BY CASE WHEN m.dictionary_code = e.metadata ->> 'dictionaryCode' THEN 0 ELSE 1 END,
                             CASE WHEN m.source_protocol = e.metadata ->> 'sourceProtocol' THEN 0 WHEN m.source_protocol = 'TELTONIKA_AUTO' THEN 1 ELSE 2 END,
                             CASE WHEN m.normalized_field_id IS NOT NULL THEN 0 ELSE 1 END, m.id DESC
                    LIMIT 1
                ) mapping ON TRUE
                WHERE e.imei = ? AND (e.company_id IS NULL OR e.company_id = ?)
                  AND e.occurred_at BETWEEN ? AND ?
                ORDER BY e.occurred_at, e.id
                """, (rs, rowNum) -> tripEvent(rs), imei, companyId, Timestamp.from(from), Timestamp.from(to));
    }

    public List<TripEvent> selectedEvents(String imei, Long companyId, List<TimeWindow> ranges) {
        if (ranges == null || ranges.isEmpty()) return List.of();
        String values = String.join(",", ranges.stream().map(ignored -> "(?::timestamptz, ?::timestamptz)").toList());
        String sql = """
                WITH selected_ranges(from_at, to_at) AS (VALUES %s)
                SELECT e.id, e.telemetry_id,
                       COALESCE(NULLIF(mapping.source_name, ''), NULLIF(e.io_name, ''), NULLIF(e.title, ''), e.event_code, 'Telemetry event') AS title,
                       COALESCE(e.message, '') AS message, COALESCE(e.severity, 'INFO') AS severity,
                       e.occurred_at, e.latitude, e.longitude, e.speed
                FROM telemetry_events e
                LEFT JOIN LATERAL (
                    SELECT m.source_name
                    FROM device_io_mappings m
                    WHERE e.event_io_id IS NOT NULL AND m.source_io_id = e.event_io_id AND m.status = 'ACTIVE'
                      AND (m.dictionary_code = e.metadata ->> 'dictionaryCode' OR m.dictionary_code IS NULL OR e.metadata ->> 'dictionaryCode' IS NULL)
                      AND (m.source_protocol = e.metadata ->> 'sourceProtocol' OR m.source_protocol IN ('TELTONIKA_AUTO', 'ANY')
                           OR m.source_protocol IS NULL OR e.metadata ->> 'sourceProtocol' IS NULL)
                    ORDER BY CASE WHEN m.dictionary_code = e.metadata ->> 'dictionaryCode' THEN 0 ELSE 1 END,
                             CASE WHEN m.source_protocol = e.metadata ->> 'sourceProtocol' THEN 0 WHEN m.source_protocol = 'TELTONIKA_AUTO' THEN 1 ELSE 2 END,
                             CASE WHEN m.normalized_field_id IS NOT NULL THEN 0 ELSE 1 END, m.id DESC
                    LIMIT 1
                ) mapping ON TRUE
                WHERE e.imei = ?
                  AND (e.company_id IS NULL OR e.company_id = ?)
                  AND EXISTS (
                      SELECT 1 FROM selected_ranges r
                      WHERE e.occurred_at BETWEEN r.from_at AND r.to_at
                  )
                ORDER BY e.occurred_at, e.id
                """.formatted(values);
        List<Object> args = selectedRangeArgs(ranges);
        args.add(imei);
        args.add(companyId);
        return jdbc.query(sql, (rs, rowNum) -> tripEvent(rs), args.toArray());
    }

    public List<TripLogRow> logs(String imei, Instant from, Instant to, List<TelemetryPoint> points) {
        Map<Long, List<ParameterValue>> parameters = normalizedParameters(
                imei, from, to, points.stream().map(TelemetryPoint::id).toList());
        List<VehicleAssignmentPeriod> vehicleAssignments = vehicleAssignmentPeriods(imei, from, to);
        List<DriverAssignmentPeriod> driverAssignments = driverAssignmentPeriods(imei, from, to);
        return points.stream().map(point -> {
            String historicalDriver = valueOrDash(point.driverName());
            if ("-".equals(historicalDriver)) historicalDriver = driverAt(driverAssignments, point.occurredAt());
            String plateNumber = vehiclePlateAt(vehicleAssignments, point.occurredAt());
            return new TripLogRow(
                    point.id(), imei, point.occurredAt().toString(), point.receivedAt().toString(), point.protocol(), point.channel(),
                    historicalDriver, plateNumber,
                    point.latitude(), point.longitude(), point.altitude(), point.angle(), point.speed(),
                    point.satellites(), point.hdop(), parameters.getOrDefault(point.id(), List.of()));
        }).toList();
    }

    private List<VehicleAssignmentPeriod> vehicleAssignmentPeriods(String imei, Instant from, Instant to) {
        return jdbc.query("""
                SELECT a.assigned_at AS start_at,
                       CASE WHEN a.removed_at IS NOT NULL THEN a.removed_at
                            WHEN a.assignment_status = 'ACTIVE' THEN NULL
                            ELSE a.updated_at END AS end_at,
                       v.plate_number
                FROM devices d
                JOIN vehicle_device_assignments a ON a.device_id = d.id AND a.deleted_at IS NULL
                JOIN vehicles v ON v.id = a.vehicle_id
                WHERE d.imei = ?
                  AND a.assigned_at <= ?
                  AND COALESCE(a.removed_at, CASE WHEN a.assignment_status = 'ACTIVE' THEN NULL ELSE a.updated_at END, 'infinity'::timestamptz) >= ?
                ORDER BY a.assigned_at DESC, a.id DESC
                """, (rs, rowNum) -> new VehicleAssignmentPeriod(
                rs.getTimestamp("start_at").toInstant(), timestampInstant(rs, "end_at"), valueOrDash(rs.getString("plate_number"))),
                imei, Timestamp.from(to), Timestamp.from(from));
    }

    private List<DriverAssignmentPeriod> driverAssignmentPeriods(String imei, Instant from, Instant to) {
        return jdbc.query("""
                WITH device_scope AS (
                    SELECT id, company_id FROM devices WHERE imei = ? LIMIT 1
                ), manual_direct AS (
                    SELECT a.assigned_at AS start_at,
                           CASE WHEN a.removed_at IS NOT NULL THEN a.removed_at
                                WHEN a.assignment_status = 'ACTIVE' THEN NULL
                                ELSE a.updated_at END AS end_at,
                           COALESCE(NULLIF(driver.driver_name, ''), NULLIF(driver.full_name, ''), NULLIF(driver.driver_code, '')) AS driver_name
                    FROM device_scope d
                    JOIN driver_manual_assignments a ON a.device_id = d.id AND a.company_id = d.company_id AND a.deleted_at IS NULL
                    JOIN asset_drivers driver ON driver.id = a.driver_id
                    WHERE a.assigned_at <= ?
                      AND COALESCE(a.removed_at, CASE WHEN a.assignment_status = 'ACTIVE' THEN NULL ELSE a.updated_at END, 'infinity'::timestamptz) >= ?
                ), manual_vehicle AS (
                    SELECT GREATEST(a.assigned_at, vehicle_assignment.assigned_at) AS start_at,
                           CASE
                               WHEN a.removed_at IS NULL AND a.assignment_status = 'ACTIVE'
                                AND vehicle_assignment.removed_at IS NULL AND vehicle_assignment.assignment_status = 'ACTIVE' THEN NULL
                               ELSE LEAST(
                                   COALESCE(a.removed_at, CASE WHEN a.assignment_status = 'ACTIVE' THEN NULL ELSE a.updated_at END, 'infinity'::timestamptz),
                                   COALESCE(vehicle_assignment.removed_at, CASE WHEN vehicle_assignment.assignment_status = 'ACTIVE' THEN NULL ELSE vehicle_assignment.updated_at END, 'infinity'::timestamptz)
                               )
                           END AS end_at,
                           COALESCE(NULLIF(driver.driver_name, ''), NULLIF(driver.full_name, ''), NULLIF(driver.driver_code, '')) AS driver_name
                    FROM device_scope d
                    JOIN vehicle_device_assignments vehicle_assignment
                      ON vehicle_assignment.device_id = d.id AND vehicle_assignment.company_id = d.company_id AND vehicle_assignment.deleted_at IS NULL
                    JOIN driver_manual_assignments a
                      ON a.vehicle_id = vehicle_assignment.vehicle_id AND a.company_id = d.company_id AND a.deleted_at IS NULL
                    JOIN asset_drivers driver ON driver.id = a.driver_id
                    WHERE GREATEST(a.assigned_at, vehicle_assignment.assigned_at) <= ?
                      AND LEAST(
                          COALESCE(a.removed_at, CASE WHEN a.assignment_status = 'ACTIVE' THEN NULL ELSE a.updated_at END, 'infinity'::timestamptz),
                          COALESCE(vehicle_assignment.removed_at, CASE WHEN vehicle_assignment.assignment_status = 'ACTIVE' THEN NULL ELSE vehicle_assignment.updated_at END, 'infinity'::timestamptz)
                      ) >= ?
                ), automatic AS (
                    SELECT session.started_at AS start_at,
                           CASE WHEN session.ended_at IS NOT NULL THEN session.ended_at
                                WHEN session.session_status = 'ACTIVE' THEN NULL
                                ELSE session.last_seen_at END AS end_at,
                           COALESCE(NULLIF(driver.driver_name, ''), NULLIF(driver.full_name, ''), NULLIF(driver.driver_code, '')) AS driver_name
                    FROM device_scope d
                    JOIN driver_auto_sessions session ON session.device_id = d.id AND session.company_id = d.company_id
                    JOIN asset_drivers driver ON driver.id = session.driver_id
                    WHERE session.started_at <= ?
                      AND COALESCE(session.ended_at, CASE WHEN session.session_status = 'ACTIVE' THEN NULL ELSE session.last_seen_at END, 'infinity'::timestamptz) >= ?
                )
                SELECT start_at, end_at, driver_name
                FROM (
                    SELECT * FROM automatic
                    UNION ALL SELECT * FROM manual_direct
                    UNION ALL SELECT * FROM manual_vehicle
                ) assignments
                WHERE driver_name IS NOT NULL
                ORDER BY start_at DESC
                """, (rs, rowNum) -> new DriverAssignmentPeriod(
                rs.getTimestamp("start_at").toInstant(), timestampInstant(rs, "end_at"), valueOrDash(rs.getString("driver_name"))),
                imei, Timestamp.from(to), Timestamp.from(from), Timestamp.from(to), Timestamp.from(from),
                Timestamp.from(to), Timestamp.from(from));
    }

    private static String vehiclePlateAt(List<VehicleAssignmentPeriod> periods, Instant occurredAt) {
        return periods.stream().filter(period -> period.includes(occurredAt)).map(VehicleAssignmentPeriod::plateNumber)
                .filter(value -> value != null && !value.isBlank() && !"-".equals(value)).findFirst().orElse("-");
    }

    private static String driverAt(List<DriverAssignmentPeriod> periods, Instant occurredAt) {
        return periods.stream().filter(period -> period.includes(occurredAt)).map(DriverAssignmentPeriod::driverName)
                .filter(value -> value != null && !value.isBlank() && !"-".equals(value)).findFirst().orElse("-");
    }

    private static Instant timestampInstant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private static String valueOrDash(String value) {
        return value == null || value.isBlank() ? "-" : value.trim();
    }

    private TripEvent tripEvent(ResultSet rs) throws SQLException {
        return new TripEvent(
                rs.getLong("id"), rs.getObject("telemetry_id", Long.class), rs.getString("title"),
                rs.getString("message"), rs.getString("severity"), rs.getString("occurred_at"),
                rs.getObject("latitude", Double.class), rs.getObject("longitude", Double.class),
                rs.getObject("speed", Double.class));
    }

    private TelemetryPoint point(ResultSet rs) throws SQLException {
        return new TelemetryPoint(rs.getLong("id"), rs.getTimestamp("occurred_at").toInstant(),
                rs.getTimestamp("received_at").toInstant(),
                rs.getObject("latitude", Double.class), rs.getObject("longitude", Double.class),
                rs.getObject("speed", Double.class), rs.getObject("angle", Integer.class),
                rs.getObject("altitude", Integer.class), rs.getObject("satellites", Integer.class),
                rs.getObject("hdop", Double.class), rs.getString("protocol"), rs.getString("channel"),
                rs.getString("driver_name"), rs.getString("vehicle_status"),
                rs.getObject("ignition", Double.class), rs.getObject("movement", Double.class));
    }


    private static final class InstrumentAccumulator {
        private Double engineRpm;
        private Double fuelLevel;
        private Double fuelConsumption;
        private String fuelConsumptionUnit;
        private Double batterySoc;
        private Double highVoltageBatteryVoltage;
        private Double highVoltageBatteryCurrent;
        private Double batteryPowerKw;
        private Double gasLevelPressure;
        private String gasLevelPressureUnit;
        private Double gasConsumption;
        private String gasConsumptionUnit;

        void acceptNormalized(String fieldCode, Double value, String unit) {
            if (fieldCode == null || value == null) return;
            switch (fieldCode) {
                case "engine_rpm" -> engineRpm = prefer(engineRpm, value);
                case "fuel_level" -> fuelLevel = prefer(fuelLevel, value);
                case "fuel_rate" -> { fuelConsumption = prefer(fuelConsumption, value); fuelConsumptionUnit = preferUnit(fuelConsumptionUnit, unit, "l/h"); }
                case "fuel_used" -> { if (fuelConsumption == null) { fuelConsumption = value; fuelConsumptionUnit = preferUnit(null, unit, "l"); } }
                case "battery_soc" -> batterySoc = prefer(batterySoc, value);
                case "high_voltage_battery_voltage" -> highVoltageBatteryVoltage = prefer(highVoltageBatteryVoltage, value);
                case "high_voltage_battery_current" -> highVoltageBatteryCurrent = prefer(highVoltageBatteryCurrent, value);
                default -> { }
            }
        }

        void acceptIo(String ioName, Double value, String unit) {
            if (ioName == null || value == null) return;
            String name = ioName.trim().toLowerCase();
            if ((name.equals("engine rpm") || name.equals("engine speed")) && engineRpm == null) engineRpm = value;
            else if ((name.equals("fuel level") || name.equals("fuel level percent")) && fuelLevel == null) fuelLevel = value;
            else if ((name.equals("fuel rate") || name.equals("fuel consumption")) && fuelConsumption == null) {
                fuelConsumption = value; fuelConsumptionUnit = preferUnit(null, unit, name.equals("fuel rate") ? "l/h" : null);
            } else if ((name.equals("generic state of charge") || name.equals("battery level percent")) && batterySoc == null) batterySoc = value;
            else if ((name.equals("cng level") || name.equals("lpg level"))) {
                gasLevelPressure = prefer(gasLevelPressure, value); gasLevelPressureUnit = preferUnit(gasLevelPressureUnit, unit, "%");
            } else if (name.contains("gas") && name.contains("pressure")) {
                if (gasLevelPressure == null || "%".equals(gasLevelPressureUnit)) { gasLevelPressure = value; gasLevelPressureUnit = preferUnit(unit, null, "kPa"); }
            } else if (name.equals("cng used") || name.equals("lpg used")) {
                gasConsumption = prefer(gasConsumption, value); gasConsumptionUnit = preferUnit(gasConsumptionUnit, unit, name.startsWith("cng") ? "kg" : "l");
            } else if (name.contains("battery") && name.contains("power") && batteryPowerKw == null) {
                batteryPowerKw = toKilowatts(value, unit);
            }
        }

        TripInstrumentSnapshot snapshot() {
            Double computedPower = batteryPowerKw;
            if (computedPower == null && highVoltageBatteryVoltage != null && highVoltageBatteryCurrent != null) {
                computedPower = Math.abs(highVoltageBatteryVoltage * highVoltageBatteryCurrent) / 1000.0;
            }
            return new TripInstrumentSnapshot(engineRpm, fuelLevel, fuelConsumption, fuelConsumptionUnit,
                    batterySoc, computedPower, gasLevelPressure, gasLevelPressureUnit, gasConsumption, gasConsumptionUnit);
        }

        private static Double prefer(Double current, Double candidate) { return current == null ? candidate : current; }
        private static String preferUnit(String current, String candidate, String fallback) {
            if (current != null && !current.isBlank()) return current;
            if (candidate != null && !candidate.isBlank() && !"-".equals(candidate)) return candidate;
            return fallback;
        }
        private static Double toKilowatts(Double value, String unit) {
            if (value == null) return null;
            String normalized = unit == null ? "" : unit.trim().toLowerCase();
            return normalized.equals("w") || normalized.equals("watt") || normalized.equals("watts") ? Math.abs(value) / 1000.0 : Math.abs(value);
        }
    }



    public List<InstrumentSourceProfile> instrumentSourceProfiles(Long companyId) {
        return jdbc.query("""
                SELECT id, profile_name, rpm_source, speed_source, level_source,
                       consumption_source, odometer_source, is_company_default
                FROM trip_route_instrument_source_profiles
                WHERE company_id = ?
                ORDER BY is_company_default DESC, LOWER(profile_name), id
                """, (rs, rowNum) -> new InstrumentSourceProfile(
                rs.getLong("id"), rs.getString("profile_name"), rs.getString("rpm_source"),
                rs.getString("speed_source"), rs.getString("level_source"),
                rs.getString("consumption_source"), rs.getString("odometer_source"),
                rs.getBoolean("is_company_default")), companyId);
    }

    public Optional<InstrumentSourceProfile> instrumentSourceProfile(Long companyId, Long profileId) {
        List<InstrumentSourceProfile> rows = jdbc.query("""
                SELECT id, profile_name, rpm_source, speed_source, level_source,
                       consumption_source, odometer_source, is_company_default
                FROM trip_route_instrument_source_profiles
                WHERE company_id = ? AND id = ?
                """, (rs, rowNum) -> new InstrumentSourceProfile(
                rs.getLong("id"), rs.getString("profile_name"), rs.getString("rpm_source"),
                rs.getString("speed_source"), rs.getString("level_source"),
                rs.getString("consumption_source"), rs.getString("odometer_source"),
                rs.getBoolean("is_company_default")), companyId, profileId);
        return rows.stream().findFirst();
    }

    public Long effectiveInstrumentSourceProfileId(Long companyId, Long deviceId) {
        List<Long> rows = jdbc.query("""
                SELECT COALESCE(a.profile_id, d.id) AS profile_id
                FROM (SELECT 1) seed
                LEFT JOIN trip_route_instrument_source_assignments a
                       ON a.company_id = ? AND a.device_id = ?
                LEFT JOIN trip_route_instrument_source_profiles d
                       ON d.company_id = ? AND d.is_company_default = TRUE
                WHERE a.profile_id IS NOT NULL OR d.id IS NOT NULL
                LIMIT 1
                """, (rs, rowNum) -> rs.getLong("profile_id"), companyId, deviceId, companyId);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    public Long upsertInstrumentSourceProfile(Long companyId, Long userId, String name,
                                               String rpmSource, String speedSource, String levelSource,
                                               String consumptionSource, String odometerSource) {
        return jdbc.queryForObject("""
                INSERT INTO trip_route_instrument_source_profiles(
                    company_id, profile_name, rpm_source, speed_source, level_source,
                    consumption_source, odometer_source, created_by, updated_by
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (company_id, LOWER(profile_name)) DO UPDATE SET
                    profile_name = EXCLUDED.profile_name,
                    rpm_source = EXCLUDED.rpm_source,
                    speed_source = EXCLUDED.speed_source,
                    level_source = EXCLUDED.level_source,
                    consumption_source = EXCLUDED.consumption_source,
                    odometer_source = EXCLUDED.odometer_source,
                    updated_by = EXCLUDED.updated_by,
                    updated_at = NOW()
                RETURNING id
                """, Long.class, companyId, name, rpmSource, speedSource, levelSource,
                consumptionSource, odometerSource, userId, userId);
    }

    public void applyInstrumentSourceProfileToDevice(Long companyId, Long deviceId, Long profileId, Long userId) {
        jdbc.update("""
                INSERT INTO trip_route_instrument_source_assignments(company_id, device_id, profile_id, assigned_by)
                VALUES (?, ?, ?, ?)
                ON CONFLICT (company_id, device_id) DO UPDATE SET
                    profile_id = EXCLUDED.profile_id,
                    assigned_by = EXCLUDED.assigned_by,
                    updated_at = NOW()
                """, companyId, deviceId, profileId, userId);
    }

    public void applyInstrumentSourceProfileToAll(Long companyId, Long profileId) {
        jdbc.update("UPDATE trip_route_instrument_source_profiles SET is_company_default = FALSE, updated_at = NOW() WHERE company_id = ?", companyId);
        jdbc.update("UPDATE trip_route_instrument_source_profiles SET is_company_default = TRUE, updated_at = NOW() WHERE company_id = ? AND id = ?", companyId, profileId);
        jdbc.update("DELETE FROM trip_route_instrument_source_assignments WHERE company_id = ?", companyId);
    }

    private record VehicleAssignmentPeriod(Instant startAt, Instant endAt, String plateNumber) {
        boolean includes(Instant value) { return value != null && !value.isBefore(startAt) && (endAt == null || !value.isAfter(endAt)); }
    }
    private record DriverAssignmentPeriod(Instant startAt, Instant endAt, String driverName) {
        boolean includes(Instant value) { return value != null && !value.isBefore(startAt) && (endAt == null || !value.isAfter(endAt)); }
    }

    public record TimeWindow(Instant from, Instant to) {}
    public record RouteWindow(String tripId, Instant from, Instant to) {}
    public record TelemetryPoint(Long id, Instant occurredAt, Instant receivedAt, Double latitude, Double longitude, Double speed,
                                 Integer angle, Integer altitude, Integer satellites, Double hdop,
                                 String protocol, String channel, String driverName, String vehicleStatus,
                                 Double ignition, Double movement) {}
}
