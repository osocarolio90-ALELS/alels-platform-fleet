package com.alels.backend.telemetry.device.deviceworkspace.tab.triproute.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Repository;

import com.alels.backend.telemetry.device.deviceworkspace.tab.triproute.dto.DeviceWorkspaceTripRouteDtos.ParameterValue;
import com.alels.backend.telemetry.device.deviceworkspace.tab.triproute.dto.DeviceWorkspaceTripRouteDtos.TripEvent;
import com.alels.backend.telemetry.device.deviceworkspace.tab.triproute.dto.DeviceWorkspaceTripRouteDtos.TripLogRow;

@Repository
public class DeviceWorkspaceTripRouteRepository {
    public static final int MAX_RANGE_POINTS = 50_000;
    public static final int MAX_DETAIL_POINTS = 10_000;
    private final JdbcTemplate jdbc;

    public DeviceWorkspaceTripRouteRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<TelemetryPoint> points(String imei, Instant from, Instant to) {
        return jdbc.query("""
                SELECT t.id, COALESCE(t.device_time, t.server_time) AS occurred_at,
                       t.latitude, t.longitude, t.speed, t.angle, t.altitude, t.satellites,
                       t.hdop, t.protocol, t.channel, t.driver_name, t.vehicle_status,
                       MAX(CASE WHEN LOWER(n.field_code) = 'ignition' THEN
                           CASE WHEN n.boolean_value IS NOT NULL THEN CASE WHEN n.boolean_value THEN 1.0 ELSE 0.0 END
                                ELSE n.numeric_value END END) AS ignition,
                       MAX(CASE WHEN LOWER(n.field_code) = 'movement' THEN
                           CASE WHEN n.boolean_value IS NOT NULL THEN CASE WHEN n.boolean_value THEN 1.0 ELSE 0.0 END
                                ELSE n.numeric_value END END) AS movement
                FROM telemetry t
                LEFT JOIN telemetry_normalized n ON n.telemetry_id = t.id AND n.imei = t.imei
                WHERE t.imei = ? AND COALESCE(t.device_time, t.server_time) BETWEEN ? AND ?
                GROUP BY t.id, t.server_time, t.device_time, t.latitude, t.longitude, t.speed,
                         t.angle, t.altitude, t.satellites, t.hdop, t.protocol, t.channel,
                         t.driver_name, t.vehicle_status
                ORDER BY occurred_at, t.id LIMIT ?
                """, (rs, rowNum) -> point(rs), imei, Timestamp.from(from), Timestamp.from(to), MAX_RANGE_POINTS + 1);
    }

    public List<TelemetryPoint> detailPoints(String imei, Instant from, Instant to) {
        return jdbc.query("""
                SELECT t.id, COALESCE(t.device_time, t.server_time) AS occurred_at,
                       t.latitude, t.longitude, t.speed, t.angle, t.altitude, t.satellites,
                       t.hdop, t.protocol, t.channel, t.driver_name, t.vehicle_status,
                       NULL::double precision AS ignition, NULL::double precision AS movement
                FROM telemetry t
                WHERE t.imei = ? AND COALESCE(t.device_time, t.server_time) BETWEEN ? AND ?
                ORDER BY occurred_at, t.id LIMIT ?
                """, (rs, rowNum) -> point(rs), imei, Timestamp.from(from), Timestamp.from(to), MAX_DETAIL_POINTS + 1);
    }

    public Map<Long, List<ParameterValue>> normalizedParameters(String imei, Instant from, Instant to,
                                                                 List<Long> telemetryIds) {
        if (telemetryIds.isEmpty()) return Map.of();
        String placeholders = String.join(",", telemetryIds.stream().map(id -> "?").toList());
        String sql = """
                SELECT n.telemetry_id, n.field_code, COALESCE(NULLIF(n.field_name, ''), n.field_code) AS label,
                       COALESCE(n.text_value, n.raw_value, n.numeric_value::text,
                           CASE WHEN n.boolean_value IS NULL THEN NULL ELSE n.boolean_value::text END, '-') AS display_value,
                       n.unit, COALESCE(NULLIF(n.category, ''), 'OTHER') AS category
                FROM telemetry_normalized n JOIN telemetry t ON t.id = n.telemetry_id AND t.imei = n.imei
                WHERE n.imei = ? AND n.telemetry_id IN (%s)
                  AND COALESCE(t.device_time, t.server_time) BETWEEN ? AND ?
                ORDER BY n.telemetry_id, n.id
                """.formatted(placeholders);
        List<Object> args = new ArrayList<>();
        args.add(imei); args.addAll(telemetryIds); args.add(Timestamp.from(from)); args.add(Timestamp.from(to));
        Map<Long, List<ParameterValue>> result = new LinkedHashMap<>();
        jdbc.query(sql, (RowCallbackHandler) rs -> result.computeIfAbsent(rs.getLong("telemetry_id"), ignored -> new ArrayList<>()).add(
                new ParameterValue(rs.getString("field_code"), rs.getString("label"),
                        rs.getString("display_value"), rs.getString("unit"), rs.getString("category"))
        ), args.toArray());
        return result;
    }

    public List<TripEvent> events(String imei, Long companyId, Instant from, Instant to) {
        return jdbc.query("""
                SELECT a.id, a.telemetry_id, COALESCE(NULLIF(a.title, ''), a.rule_code, 'Telemetry event') AS title,
                       COALESCE(a.message, '') AS message, COALESCE(a.severity, 'INFO') AS severity,
                       COALESCE(t.device_time, t.server_time, a.created_at) AS occurred_at, t.latitude, t.longitude, t.speed
                FROM telemetry_alerts a LEFT JOIN telemetry t ON t.id = a.telemetry_id AND t.imei = a.imei
                WHERE a.imei = ? AND (a.company_id IS NULL OR a.company_id = ?)
                  AND COALESCE(t.device_time, t.server_time, a.created_at) BETWEEN ? AND ?
                ORDER BY occurred_at, a.id
                """, (rs, rowNum) -> new TripEvent(
                        rs.getLong("id"), rs.getObject("telemetry_id", Long.class), rs.getString("title"),
                        rs.getString("message"), rs.getString("severity"), rs.getString("occurred_at"),
                        rs.getObject("latitude", Double.class), rs.getObject("longitude", Double.class),
                        rs.getObject("speed", Double.class)), imei, companyId, Timestamp.from(from), Timestamp.from(to));
    }

    public List<TripLogRow> logs(String imei, Instant from, Instant to, List<TelemetryPoint> points) {
        Map<Long, List<ParameterValue>> parameters = normalizedParameters(
                imei, from, to, points.stream().map(TelemetryPoint::id).toList());
        return points.stream().map(point -> new TripLogRow(
                point.id(), imei, point.occurredAt().toString(), point.protocol(), point.channel(),
                point.latitude(), point.longitude(), point.altitude(), point.angle(), point.speed(),
                point.satellites(), point.hdop(), parameters.getOrDefault(point.id(), List.of()))).toList();
    }

    private TelemetryPoint point(ResultSet rs) throws SQLException {
        return new TelemetryPoint(rs.getLong("id"), rs.getTimestamp("occurred_at").toInstant(),
                rs.getObject("latitude", Double.class), rs.getObject("longitude", Double.class),
                rs.getObject("speed", Double.class), rs.getObject("angle", Integer.class),
                rs.getObject("altitude", Integer.class), rs.getObject("satellites", Integer.class),
                rs.getObject("hdop", Double.class), rs.getString("protocol"), rs.getString("channel"),
                rs.getString("driver_name"), rs.getString("vehicle_status"),
                rs.getObject("ignition", Double.class), rs.getObject("movement", Double.class));
    }

    public record TelemetryPoint(Long id, Instant occurredAt, Double latitude, Double longitude, Double speed,
                                 Integer angle, Integer altitude, Integer satellites, Double hdop,
                                 String protocol, String channel, String driverName, String vehicleStatus,
                                 Double ignition, Double movement) {}
}
