package com.alels.backend.serverops.aiops.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Repository;

@Repository
public class AiOpsMonitorRepository {

    private final JdbcTemplate jdbcTemplate;

    public AiOpsMonitorRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean isDatabaseHealthy() {
        try {
            Integer value = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            return value != null && value == 1;
        } catch (Exception e) {
            return false;
        }
    }

    public long countActiveDatabaseConnections() {
        return count("SELECT COUNT(*) FROM pg_stat_activity WHERE datname = current_database() AND state = 'active'");
    }

    public long countTotalDatabaseConnections() {
        return count("SELECT COUNT(*) FROM pg_stat_activity WHERE datname = current_database()");
    }

    public long countConnectedDevices() {
        return count("SELECT COUNT(*) FROM devices WHERE online = true OR presence_status = 'ONLINE'");
    }

    public long countOfflineDevices() {
        return count("SELECT COUNT(*) FROM devices WHERE COALESCE(online, false) = false AND presence_status <> 'ONLINE'");
    }

    public long countTelemetryLastMinute() {
        return count("SELECT COUNT(*) FROM telemetry WHERE server_time >= NOW() - INTERVAL '1 minute'");
    }

    public long countInvalidPacketsLastHour() {
        return count("SELECT COUNT(*) FROM telemetry WHERE parse_status <> 'VALID' AND server_time >= NOW() - INTERVAL '1 hour'");
    }

    public long countUnknownIo() {
        return count("SELECT COALESCE(SUM(seen_count), 0)::bigint FROM unknown_io_registry");
    }

    public long countOpenAiOpsAlerts() {
        return countIfTableExists("ai_ops_alerts", "SELECT COUNT(*) FROM ai_ops_alerts WHERE status = 'OPEN'");
    }

    private long count(@NonNull String sql) {
        try {
            Long value = jdbcTemplate.queryForObject(sql, Long.class);
            return value == null ? 0L : value;
        } catch (Exception e) {
            return 0L;
        }
    }

    private long countIfTableExists(String tableName, @NonNull String sql) {
        try {
            Boolean exists = jdbcTemplate.queryForObject(
                    "SELECT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = 'public' AND table_name = ?)",
                    Boolean.class,
                    tableName
            );
            if (Boolean.TRUE.equals(exists)) {
                return count(sql);
            }
            return 0L;
        } catch (Exception e) {
            return 0L;
        }
    }
}
