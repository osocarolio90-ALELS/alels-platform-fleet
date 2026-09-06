package com.alels.backend.serverops.traffic.repository;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Repository;

import com.alels.backend.serverops.traffic.model.TrafficMonitorOverviewResponse.TrafficDistribution;

@Repository
public class TrafficMonitorRepository {

    private final JdbcTemplate jdbcTemplate;

    public TrafficMonitorRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public long countRawPacketsLastMinute() {
        return count("SELECT COUNT(*) FROM raw_packets WHERE received_at >= NOW() - INTERVAL '1 minute'");
    }

    public long countRawPacketsLastHour() {
        return count("SELECT COUNT(*) FROM raw_packets WHERE received_at >= NOW() - INTERVAL '1 hour'");
    }

    public long countTelemetryLastMinute() {
        return count("SELECT COUNT(*) FROM telemetry WHERE server_time >= NOW() - INTERVAL '1 minute'");
    }

    public long countInvalidPacketsLastHour() {
        return count("SELECT COUNT(*) FROM telemetry WHERE COALESCE(parse_status, 'VALID') <> 'VALID' AND server_time >= NOW() - INTERVAL '1 hour'");
    }

    public long countUnknownIoRegistry() {
        return count("SELECT COUNT(*) FROM unknown_io_registry");
    }

    public long averageRawPacketSizeLastHour() {
        try {
            Long value = jdbcTemplate.queryForObject(
                    "SELECT COALESCE(ROUND(AVG(LENGTH(COALESCE(payload, '')))), 0)::bigint FROM raw_packets WHERE received_at >= NOW() - INTERVAL '1 hour'",
                    Long.class
            );
            return value == null ? 0L : value;
        } catch (Exception e) {
            return 0L;
        }
    }

    public List<TrafficDistribution> channelDistribution(long totalPackets) {
        return distribution(
                "SELECT COALESCE(NULLIF(UPPER(channel), ''), 'UNKNOWN') AS name, COUNT(*) AS count FROM raw_packets WHERE received_at >= NOW() - INTERVAL '1 hour' GROUP BY 1 ORDER BY 2 DESC",
                totalPackets
        );
    }

    public List<TrafficDistribution> protocolDistribution(long totalPackets) {
        return distribution(
                "SELECT COALESCE(NULLIF(UPPER(protocol), ''), 'UNKNOWN') AS name, COUNT(*) AS count FROM raw_packets WHERE received_at >= NOW() - INTERVAL '1 hour' GROUP BY 1 ORDER BY 2 DESC",
                totalPackets
        );
    }

    private List<TrafficDistribution> distribution(@NonNull String sql, long total) {
        try {
            return jdbcTemplate.query(sql, (rs, rowNum) -> {
                String name = rs.getString("name");
                long count = rs.getLong("count");
                double percent = total <= 0 ? 0 : count * 100.0 / total;
                return new TrafficDistribution(name, count, percent);
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
