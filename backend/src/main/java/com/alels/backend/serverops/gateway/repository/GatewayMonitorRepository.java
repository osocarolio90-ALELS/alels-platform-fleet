package com.alels.backend.serverops.gateway.repository;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Repository;

import com.alels.backend.serverops.gateway.model.GatewayMonitorOverviewResponse.GatewayDistribution;

@Repository
public class GatewayMonitorRepository {

    private final JdbcTemplate jdbcTemplate;

    public GatewayMonitorRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public long countTotalDevices() {
        return count("SELECT COUNT(*) FROM devices");
    }

    public long countOnlineDevices() {
        return count("SELECT COUNT(*) FROM devices WHERE online = true OR presence_status = 'ONLINE'");
    }

    public long countOfflineDevices() {
        return count("SELECT COUNT(*) FROM devices WHERE COALESCE(online, false) = false AND presence_status <> 'ONLINE'");
    }

    public long countGsmConnected() {
        return count("SELECT COUNT(*) FROM devices WHERE gsm_connected = true OR UPPER(COALESCE(active_channel, '')) = 'GSM'");
    }

    public long countWifiConnected() {
        return count("SELECT COUNT(*) FROM devices WHERE wifi_connected = true OR UPPER(COALESCE(active_channel, '')) = 'WIFI'");
    }

    public long countTelemetryLastMinute() {
        return count("SELECT COUNT(*) FROM telemetry WHERE server_time >= NOW() - INTERVAL '1 minute'");
    }

    public long countInvalidPacketsLastHour() {
        return count("SELECT COUNT(*) FROM telemetry WHERE COALESCE(parse_status, 'VALID') <> 'VALID' AND server_time >= NOW() - INTERVAL '1 hour'");
    }

    public long countStaleDevices() {
        return count("SELECT COUNT(*) FROM devices WHERE last_seen IS NOT NULL AND last_seen < NOW() - INTERVAL '7 minutes'");
    }

    public List<GatewayDistribution> channelDistribution(long totalDevices) {
        return distribution(
                "SELECT COALESCE(NULLIF(UPPER(active_channel), ''), 'UNKNOWN') AS name, COUNT(*) AS count FROM devices GROUP BY 1 ORDER BY 2 DESC",
                totalDevices
        );
    }

    public List<GatewayDistribution> protocolDistribution(long totalDevices) {
        return distribution(
                "SELECT COALESCE(NULLIF(UPPER(last_protocol), ''), 'UNKNOWN') AS name, COUNT(*) AS count FROM devices GROUP BY 1 ORDER BY 2 DESC",
                totalDevices
        );
    }

    private List<GatewayDistribution> distribution(@NonNull String sql, long total) {
        try {
            return jdbcTemplate.query(sql, (rs, rowNum) -> {
                String name = rs.getString("name");
                long count = rs.getLong("count");
                double percent = total <= 0 ? 0 : count * 100.0 / total;
                return new GatewayDistribution(name, count, percent);
            });
        } catch (Exception e) {
            return List.of();
        }
    }

    private long count(@NonNull String sql) {
        try {
            Long value = jdbcTemplate.queryForObject(sql, Long.class);
            return value == null ? 0L : value;
        } catch (Exception e) {
            return 0L;
        }
    }
}
