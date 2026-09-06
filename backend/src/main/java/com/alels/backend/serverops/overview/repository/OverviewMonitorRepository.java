package com.alels.backend.serverops.overview.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Repository;

@Repository
public class OverviewMonitorRepository {
    private final JdbcTemplate jdbcTemplate;

    public OverviewMonitorRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public long totalDevices() {
        return count("SELECT COUNT(*) FROM devices");
    }

    public long onlineDevices() {
        return count("SELECT COUNT(*) FROM devices WHERE online = TRUE OR UPPER(COALESCE(presence_status, '')) = 'ONLINE'");
    }

    public long activeUsers() {
        return count("SELECT COUNT(*) FROM users WHERE deleted_at IS NULL AND status = 'ACTIVE'");
    }

    public long openAiOpsAlerts() {
        return count("SELECT COUNT(*) FROM ai_ops_alerts WHERE status = 'OPEN'");
    }

    public long openSecurityEvents() {
        if (!hasTable("security_events")) {
            return 0L;
        }
        return count("SELECT COUNT(*) FROM security_events WHERE status = 'OPEN'");
    }

    public boolean securityEventFoundationReady() {
        return hasTable("security_events") && hasTable("audit_logs") && hasTable("login_events");
    }

    private boolean hasTable(String tableName) {
        return count("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_name = '" + tableName + "'") > 0;
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
