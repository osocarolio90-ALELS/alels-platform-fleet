package com.alels.backend.telemetry.device.deviceworkspace.tab.telemetry.repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.alels.backend.telemetry.device.deviceworkspace.tab.telemetry.dto.DeviceWorkspaceTelemetryDtos.DataParameter;
import com.alels.backend.telemetry.device.deviceworkspace.tab.telemetry.dto.DeviceWorkspaceTelemetryDtos.DeviceInfo;
import com.alels.backend.telemetry.device.deviceworkspace.tab.telemetry.dto.DeviceWorkspaceTelemetryDtos.DriverInfo;
import com.alels.backend.telemetry.device.deviceworkspace.tab.telemetry.dto.DeviceWorkspaceTelemetryDtos.RecentEvent;
import com.alels.backend.telemetry.device.deviceworkspace.tab.telemetry.dto.DeviceWorkspaceTelemetryDtos.TrackPoint;
import com.alels.backend.telemetry.device.deviceworkspace.tab.telemetry.dto.DeviceWorkspaceTelemetryDtos.VehicleInfo;

@Repository
public class DeviceWorkspaceTelemetryRepository {
    private final JdbcTemplate jdbc;

    public DeviceWorkspaceTelemetryRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<ScopedDevice> device(Long deviceId, String imei, Long companyId) {
        return jdbc.query("""
                SELECT d.id, d.company_id, d.imei,
                       COALESCE(db.brand_name, '-') AS brand,
                       COALESCE(dm.model_name, d.device_model, '-') AS model,
                       c.company_name,
                       (d.last_seen IS NOT NULL AND d.last_seen >= NOW() - interval '30 minutes') AS online,
                       COALESCE(d.tcp_enabled, TRUE) AS tcp_enabled,
                       d.last_seen
                FROM devices d
                JOIN companies c ON c.id = d.company_id AND c.deleted_at IS NULL
                LEFT JOIN device_brands db ON db.id = d.device_brand_id AND db.deleted_at IS NULL
                LEFT JOIN device_models dm ON dm.id = d.device_model_id AND dm.deleted_at IS NULL
                WHERE d.id = ? AND d.imei = ? AND d.company_id = ? AND d.deleted_at IS NULL
                LIMIT 1
                """, (rs, rowNum) -> new ScopedDevice(
                    new DeviceInfo(
                            rs.getLong("id"), rs.getString("imei"), rs.getString("brand"),
                            rs.getString("model"), rs.getString("company_name"), rs.getBoolean("online"),
                            rs.getBoolean("tcp_enabled"), rs.getString("last_seen")
                    ),
                    rs.getLong("company_id")
                ), deviceId, imei, companyId).stream().findFirst();
    }

    public Optional<VehicleResult> vehicle(Long deviceId, Long companyId) {
        return jdbc.query("""
                SELECT v.id,
                       COALESCE(NULLIF(v.vehicle_name, ''), NULLIF(v.vehicle_code, ''), NULLIF(v.vehicle_number, ''), '-') AS vehicle_name,
                       COALESCE(NULLIF(v.vehicle_code, ''), NULLIF(v.vehicle_number, ''), '-') AS vehicle_code,
                       COALESCE(v.plate_number, '-') AS plate_number,
                       COALESCE(vt.type_name, '-') AS vehicle_type,
                       COALESCE(vb.brand_name, '-') AS brand,
                       COALESCE(vm.model_name, '-') AS model,
                       v.year_manufacture,
                       COALESCE(et.energy_name, v.energy_code, '-') AS energy,
                       COALESCE(vot.ownership_name, '-') AS ownership,
                       CASE WHEN v.capacity_value IS NULL THEN '-'
                            ELSE v.capacity_value::text ||
                                 CASE WHEN vcu.unit_name IS NULL THEN '' ELSE ' ' || vcu.unit_name END END AS capacity,
                       COALESCE(country.country_name, v.country_code, '-') AS country,
                       COALESCE(v.operational_status, 'UNKNOWN') AS operational_status
                FROM vehicle_device_assignments assignment
                JOIN vehicles v ON v.id = assignment.vehicle_id AND v.deleted_at IS NULL
                LEFT JOIN vehicle_types vt ON vt.id = v.vehicle_type_id AND vt.deleted_at IS NULL
                LEFT JOIN vehicle_brands vb ON vb.id = v.brand_id AND vb.deleted_at IS NULL
                LEFT JOIN vehicle_models vm ON vm.id = v.model_id AND vm.deleted_at IS NULL
                LEFT JOIN energy_types et ON et.energy_code = v.energy_code
                LEFT JOIN vehicle_ownership_types vot ON vot.id = v.ownership_type_id AND vot.deleted_at IS NULL
                LEFT JOIN vehicle_capacity_units vcu ON vcu.id = v.capacity_unit_id AND vcu.deleted_at IS NULL
                LEFT JOIN energy_reference_countries country ON country.country_code = v.country_code
                WHERE assignment.device_id = ?
                  AND assignment.company_id = ?
                  AND v.company_id = ?
                  AND assignment.assignment_status = 'ACTIVE'
                  AND assignment.deleted_at IS NULL
                ORDER BY assignment.assigned_at DESC
                LIMIT 1
                """, (rs, rowNum) -> new VehicleResult(
                    rs.getLong("id"),
                    new VehicleInfo(
                            true, rs.getString("vehicle_name"), rs.getString("vehicle_code"),
                            rs.getString("plate_number"), rs.getString("vehicle_type"), rs.getString("brand"),
                            rs.getString("model"), rs.getObject("year_manufacture", Integer.class),
                            rs.getString("energy"), rs.getString("ownership"), rs.getString("capacity"),
                            rs.getString("country"), rs.getString("operational_status")
                    )
                ), deviceId, companyId, companyId).stream().findFirst();
    }

    public Optional<DriverInfo> driver(Long deviceId, Long companyId) {
        return jdbc.query("""
                SELECT COALESCE(driver.driver_name, driver.full_name, driver.driver_code, '-') AS driver_name,
                       driver.driver_code, driver.employee_id,
                       driver.license_number, license.name AS license_type,
                       COALESCE(country.country_name, driver.country_code, '-') AS country,
                       driver.phone_number, driver.rfid_ibutton,
                       CASE WHEN NULLIF(driver.metadata ->> 'photo_filename', '') IS NULL THEN NULL
                            ELSE '/api/asset-register/drivers/photo/' || (driver.metadata ->> 'photo_filename') END AS photo_url,
                       COALESCE(driver.status, 'ACTIVE') AS status
                FROM (
                    SELECT active.driver_id, active.event_time
                    FROM (
                        SELECT manual.driver_id, manual.assigned_at AS event_time
                        FROM driver_manual_assignments manual
                        WHERE manual.device_id = ? AND manual.company_id = ? AND manual.assignment_status = 'ACTIVE' AND manual.deleted_at IS NULL
                        UNION ALL
                        SELECT auto_session.driver_id, auto_session.last_seen_at
                        FROM driver_auto_sessions auto_session
                        WHERE auto_session.device_id = ? AND auto_session.company_id = ? AND auto_session.session_status = 'ACTIVE'
                    ) active
                    ORDER BY active.event_time DESC
                    LIMIT 1
                ) selected
                JOIN asset_drivers driver ON driver.id = selected.driver_id AND driver.company_id = ? AND driver.deleted_at IS NULL
                LEFT JOIN license_master license ON license.id = driver.license_master_id AND license.deleted_at IS NULL
                LEFT JOIN energy_reference_countries country ON country.country_code = driver.country_code
                """, (rs, rowNum) -> new DriverInfo(
                    true,
                    valueOrDash(rs.getString("driver_name")), valueOrDash(rs.getString("driver_code")),
                    valueOrDash(rs.getString("employee_id")), valueOrDash(rs.getString("license_number")),
                    valueOrDash(rs.getString("license_type")), valueOrDash(rs.getString("country")),
                    valueOrDash(rs.getString("phone_number")), valueOrDash(rs.getString("rfid_ibutton")),
                    rs.getString("photo_url"), valueOrDash(rs.getString("status"))
                ), deviceId, companyId, deviceId, companyId, companyId).stream().findFirst();
    }

    public Optional<LatestPacket> latestPacket(String imei) {
        return jdbc.query("""
                SELECT id, packet_sequence, server_time, device_time, protocol, channel,
                       latitude, longitude, speed, angle, altitude, satellites, hdop,
                       priority, event_io_id, io_data::text AS io_data
                FROM telemetry
                WHERE imei = ?
                ORDER BY COALESCE(device_time, server_time) DESC, server_time DESC, id DESC
                LIMIT 1
                """, (rs, rowNum) -> new LatestPacket(
                    rs.getLong("id"), rs.getObject("packet_sequence", Long.class),
                    rs.getString("server_time"), rs.getString("device_time"),
                    rs.getString("protocol"), rs.getString("channel"),
                    rs.getObject("latitude", Double.class), rs.getObject("longitude", Double.class),
                    rs.getObject("speed", Double.class), rs.getObject("angle", Integer.class),
                    rs.getObject("altitude", Integer.class), rs.getObject("satellites", Integer.class),
                    rs.getObject("hdop", Double.class), rs.getObject("priority", Integer.class),
                    rs.getObject("event_io_id", Integer.class), rs.getString("io_data")
                ), imei).stream().findFirst();
    }

    public List<TrackPoint> recentTrack(String imei, int limit) {
        return jdbc.query("""
                SELECT id, latitude, longitude, angle, speed, occurred_at
                FROM (
                    SELECT id, latitude, longitude, angle, speed,
                           COALESCE(device_time, server_time) AS occurred_at
                    FROM telemetry
                    WHERE imei = ?
                      AND latitude BETWEEN -90 AND 90
                      AND longitude BETWEEN -180 AND 180
                      AND NOT (latitude = 0 AND longitude = 0)
                    ORDER BY COALESCE(device_time, server_time) DESC, server_time DESC, id DESC
                    LIMIT ?
                ) recent
                ORDER BY occurred_at, id
                """, (rs, rowNum) -> new TrackPoint(
                    rs.getObject("latitude", Double.class), rs.getObject("longitude", Double.class),
                    rs.getObject("angle", Integer.class), rs.getObject("speed", Double.class),
                    rs.getString("occurred_at")
                ), imei, limit);
    }

    public List<List<TrackPoint>> historicalRoutes(String imei, int maximumPoints, int gapMinutes) {
        Map<Long, List<TrackPoint>> routes = new java.util.LinkedHashMap<>();
        jdbc.query("""
                WITH valid AS (
                    SELECT id, latitude, longitude, angle, speed,
                           COALESCE(device_time, server_time) AS occurred_at
                    FROM telemetry
                    WHERE imei = ?
                      AND latitude BETWEEN -90 AND 90
                      AND longitude BETWEEN -180 AND 180
                      AND NOT (latitude = 0 AND longitude = 0)
                ), boundaries AS (
                    SELECT *,
                           CASE WHEN LAG(occurred_at) OVER (ORDER BY occurred_at, id) IS NULL
                                  OR occurred_at - LAG(occurred_at) OVER (ORDER BY occurred_at, id) > (? * INTERVAL '1 minute')
                                THEN 1 ELSE 0 END AS starts_route
                    FROM valid
                ), segmented AS (
                    SELECT *, SUM(starts_route) OVER (ORDER BY occurred_at, id) AS route_id
                    FROM boundaries
                ), ranked AS (
                    SELECT *,
                           ROW_NUMBER() OVER (PARTITION BY route_id ORDER BY occurred_at, id) AS route_row,
                           COUNT(*) OVER (PARTITION BY route_id) AS route_count,
                           COUNT(*) OVER () AS total_count
                    FROM segmented
                )
                SELECT route_id, latitude, longitude, angle, speed, occurred_at
                FROM ranked
                WHERE route_row = 1
                   OR route_row = route_count
                   OR MOD(route_row - 1, GREATEST(1, CEIL(total_count::numeric / ?::numeric)::bigint)) = 0
                ORDER BY route_id, occurred_at
                """, rs -> {
            long routeId = rs.getLong("route_id");
            routes.computeIfAbsent(routeId, ignored -> new java.util.ArrayList<>()).add(new TrackPoint(
                    rs.getObject("latitude", Double.class), rs.getObject("longitude", Double.class),
                    rs.getObject("angle", Integer.class), rs.getObject("speed", Double.class),
                    rs.getString("occurred_at")
            ));
        }, imei, gapMinutes, maximumPoints);
        return routes.values().stream().filter(route -> route.size() > 1).toList();
    }

    public List<DataParameter> normalizedParameters(Long telemetryId, String imei) {
        return jdbc.query("""
                SELECT field_code, COALESCE(NULLIF(field_name, ''), field_code) AS label,
                       COALESCE(text_value, numeric_value::text, CASE WHEN boolean_value IS NULL THEN NULL ELSE boolean_value::text END, raw_value, '-') AS display_value,
                       numeric_value, boolean_value, unit, source_io_id,
                       COALESCE(NULLIF(category, ''), 'OTHER') AS category,
                       source_protocol, dictionary_code, device_model_id
                FROM telemetry_normalized
                WHERE telemetry_id = ? AND imei = ?
                ORDER BY id
                """, (rs, rowNum) -> new DataParameter(
                    rs.getString("field_code"), rs.getString("label"), rs.getString("display_value"),
                    rs.getObject("numeric_value", Double.class), rs.getObject("boolean_value", Boolean.class),
                    rs.getString("unit"), rs.getString("source_io_id"), rs.getString("category"),
                    rs.getString("source_protocol"), rs.getString("dictionary_code"),
                    rs.getObject("device_model_id", Long.class)
                ), telemetryId, imei);
    }

    public List<DataParameter> ioParameters(Long telemetryId, String imei) {
        return jdbc.query("""
                SELECT COALESCE(NULLIF(io_name, ''), 'IO ' || io_id) AS label,
                       COALESCE(real_value::text, numeric_value::text, raw_value, '-') AS display_value,
                       COALESCE(real_value, numeric_value) AS numeric_value,
                       unit, io_id, COALESCE(NULLIF(io_category, ''), 'OTHER') AS category, protocol
                FROM telemetry_io
                WHERE telemetry_id = ? AND imei = ?
                ORDER BY id
                """, (rs, rowNum) -> new DataParameter(
                    "io." + rs.getString("io_id"), rs.getString("label"), rs.getString("display_value"),
                    rs.getObject("numeric_value", Double.class), null, rs.getString("unit"),
                    rs.getString("io_id"), rs.getString("category"), rs.getString("protocol"), null, null
                ), telemetryId, imei);
    }

    public List<RecentEvent> events(String imei, Long companyId, Long beforeId, int limit) {
        String cursor = beforeId == null ? "" : """
                  AND (e.occurred_at, e.id) < (
                      SELECT anchor.occurred_at, anchor.id
                      FROM telemetry_events anchor
                      WHERE anchor.id = ? AND anchor.imei = ?
                        AND (anchor.company_id IS NULL OR anchor.company_id = ?)
                  )
                """;
        String sql = """
                SELECT e.id,
                       COALESCE(NULLIF(mapping.source_name, ''), NULLIF(e.io_name, ''), NULLIF(e.title, ''), e.event_code, 'Telemetry event') AS title,
                       COALESCE(e.message, '') AS message, COALESCE(e.severity, 'INFO') AS severity,
                       e.occurred_at
                FROM telemetry_events e
                LEFT JOIN LATERAL (
                    SELECT m.source_name
                    FROM device_io_mappings m
                    WHERE e.event_io_id IS NOT NULL
                      AND m.source_io_id = e.event_io_id
                      AND m.status = 'ACTIVE'
                      AND (m.dictionary_code = e.metadata ->> 'dictionaryCode' OR m.dictionary_code IS NULL OR e.metadata ->> 'dictionaryCode' IS NULL)
                      AND (m.source_protocol = e.metadata ->> 'sourceProtocol' OR m.source_protocol IN ('TELTONIKA_AUTO', 'ANY')
                           OR m.source_protocol IS NULL OR e.metadata ->> 'sourceProtocol' IS NULL)
                    ORDER BY CASE WHEN m.dictionary_code = e.metadata ->> 'dictionaryCode' THEN 0 ELSE 1 END,
                             CASE WHEN m.source_protocol = e.metadata ->> 'sourceProtocol' THEN 0
                                  WHEN m.source_protocol = 'TELTONIKA_AUTO' THEN 1 ELSE 2 END,
                             CASE WHEN m.normalized_field_id IS NOT NULL THEN 0 ELSE 1 END,
                             m.id DESC
                    LIMIT 1
                ) mapping ON TRUE
                WHERE e.imei = ?
                  AND (e.company_id IS NULL OR e.company_id = ?)
                  %s
                ORDER BY e.occurred_at DESC, e.id DESC
                LIMIT ?
                """.formatted(cursor);
        Object[] parameters = beforeId == null
                ? new Object[]{imei, companyId, limit}
                : new Object[]{imei, companyId, beforeId, imei, companyId, limit};
        return jdbc.query(sql, (rs, rowNum) -> new RecentEvent(
                rs.getLong("id"), rs.getString("title"), rs.getString("message"),
                rs.getString("severity"), rs.getString("occurred_at")
        ), parameters);
    }

    public Optional<String> configuration(Long deviceId, Long userId) {
        return jdbc.query("""
                SELECT configuration::text
                FROM telemetry_device_workspace_configs
                WHERE device_id = ? AND user_id = ?
                """, (rs, rowNum) -> rs.getString(1), deviceId, userId).stream().findFirst();
    }

    public void saveConfiguration(Long deviceId, Long companyId, Long userId, String configurationJson) {
        jdbc.update("""
                INSERT INTO telemetry_device_workspace_configs(device_id, company_id, user_id, configuration)
                VALUES (?, ?, ?, CAST(? AS jsonb))
                ON CONFLICT (device_id, user_id) DO UPDATE
                SET company_id = EXCLUDED.company_id,
                    configuration = EXCLUDED.configuration,
                    updated_at = NOW()
                """, deviceId, companyId, userId, configurationJson);
    }

    private static String valueOrDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    public record ScopedDevice(DeviceInfo info, Long companyId) {}
    public record VehicleResult(Long id, VehicleInfo info) {}
    public record LatestPacket(
            Long id, Long sequence, String serverTime, String deviceTime, String protocol, String channel,
            Double latitude, Double longitude, Double speed, Integer angle,
            Integer altitude, Integer satellites, Double hdop, Integer priority,
            Integer eventIoId, String ioDataJson
    ) {}
}
