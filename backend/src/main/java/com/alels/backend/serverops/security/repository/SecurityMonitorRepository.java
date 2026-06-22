package com.alels.backend.serverops.security.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class SecurityMonitorRepository {

    private final JdbcTemplate jdbcTemplate;

    public SecurityMonitorRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public long totalUsers() {
        return count("SELECT COUNT(*) FROM users WHERE deleted_at IS NULL");
    }

    public long activeUsers() {
        return count("SELECT COUNT(*) FROM users WHERE deleted_at IS NULL AND status = 'ACTIVE'");
    }

    public long inactiveUsers() {
        return count("SELECT COUNT(*) FROM users WHERE deleted_at IS NULL AND status = 'INACTIVE'");
    }

    public long suspendedUsers() {
        return count("SELECT COUNT(*) FROM users WHERE deleted_at IS NULL AND status = 'SUSPENDED'");
    }

    public long adminUsers() {
        return count("SELECT COUNT(*) FROM users WHERE deleted_at IS NULL AND UPPER(role) IN ('SUPERADMIN','ADMIN','OWNER','ALELS_SUPER_ADMIN')");
    }

    public long mustChangePasswordUsers() {
        return count("SELECT COUNT(*) FROM users WHERE deleted_at IS NULL AND must_change_password IS TRUE");
    }

    public long neverLoginUsers() {
        return count("SELECT COUNT(*) FROM users WHERE deleted_at IS NULL AND last_login_at IS NULL");
    }

    public long openSecurityAlerts() {
        long dedicated = hasTable("security_events")
                ? count("SELECT COUNT(*) FROM security_events WHERE status = 'OPEN' AND severity IN ('WARNING','CRITICAL','EMERGENCY')")
                : 0L;
        long aiOps = count("SELECT COUNT(*) FROM ai_ops_alerts WHERE status = 'OPEN' AND UPPER(category) IN ('SECURITY','AUTH','AUTHENTICATION','AUTHORIZATION')");
        return dedicated + aiOps;
    }

    public long criticalSecurityAlerts() {
        long dedicated = hasTable("security_events")
                ? count("SELECT COUNT(*) FROM security_events WHERE status = 'OPEN' AND severity IN ('CRITICAL','EMERGENCY')")
                : 0L;
        long aiOps = count("SELECT COUNT(*) FROM ai_ops_alerts WHERE status = 'OPEN' AND severity IN ('CRITICAL','EMERGENCY') AND UPPER(category) IN ('SECURITY','AUTH','AUTHENTICATION','AUTHORIZATION')");
        return dedicated + aiOps;
    }

    public long failedLoginEvents24h() {
        if (hasTable("login_events")) {
            return count("SELECT COUNT(*) FROM login_events WHERE created_at >= NOW() - INTERVAL '24 hours' AND success IS FALSE");
        }
        return count("SELECT COUNT(*) FROM ai_ops_alerts WHERE detected_at >= NOW() - INTERVAL '24 hours' AND (LOWER(title) LIKE '%failed login%' OR LOWER(problem) LIKE '%failed login%')");
    }

    public long unauthorizedEvents24h() {
        if (hasTable("security_events")) {
            return count("SELECT COUNT(*) FROM security_events WHERE created_at >= NOW() - INTERVAL '24 hours' AND event_type IN ('UNAUTHORIZED_ACCESS','RBAC_VIOLATION','FORBIDDEN_ACCESS')");
        }
        return count("SELECT COUNT(*) FROM ai_ops_alerts WHERE detected_at >= NOW() - INTERVAL '24 hours' AND (LOWER(title) LIKE '%unauthorized%' OR LOWER(problem) LIKE '%unauthorized%' OR LOWER(title) LIKE '%403%' OR LOWER(problem) LIKE '%403%')");
    }

    public long tokenIssueEvents24h() {
        if (hasTable("security_events")) {
            return count("SELECT COUNT(*) FROM security_events WHERE created_at >= NOW() - INTERVAL '24 hours' AND event_type IN ('TOKEN_ISSUE','JWT_INVALID','JWT_EXPIRED','SESSION_INVALID')");
        }
        return count("SELECT COUNT(*) FROM ai_ops_alerts WHERE detected_at >= NOW() - INTERVAL '24 hours' AND (LOWER(title) LIKE '%token%' OR LOWER(problem) LIKE '%token%' OR LOWER(title) LIKE '%jwt%' OR LOWER(problem) LIKE '%jwt%')");
    }

    public long suspiciousIpEvents24h() {
        if (hasTable("security_events")) {
            return count("SELECT COUNT(DISTINCT ip_address) FROM security_events WHERE created_at >= NOW() - INTERVAL '24 hours' AND event_type IN ('SUSPICIOUS_IP','IP_ABUSE','RATE_LIMIT_HIT') AND ip_address IS NOT NULL");
        }
        return count("SELECT COUNT(*) FROM ai_ops_alerts WHERE detected_at >= NOW() - INTERVAL '24 hours' AND (LOWER(title) LIKE '%suspicious ip%' OR LOWER(problem) LIKE '%suspicious ip%' OR LOWER(title) LIKE '%ip abuse%' OR LOWER(problem) LIKE '%ip abuse%')");
    }

    public long loginEventCount24h() {
        if (!hasTable("login_events")) {
            return 0L;
        }
        return count("SELECT COUNT(*) FROM login_events WHERE created_at >= NOW() - INTERVAL '24 hours'");
    }

    public long auditLogCount24h() {
        if (!hasTable("audit_logs")) {
            return 0L;
        }
        return count("SELECT COUNT(*) FROM audit_logs WHERE created_at >= NOW() - INTERVAL '24 hours'");
    }

    public boolean hasDedicatedSecurityEventTable() {
        return hasTable("security_events") && hasTable("audit_logs") && hasTable("login_events");
    }

    private boolean hasTable(String tableName) {
        return count("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_name = '" + tableName + "'") > 0;
    }

    private long count(String sql) {
        try {
            Long value = jdbcTemplate.queryForObject(sql, Long.class);
            return value == null ? 0L : value;
        } catch (Exception e) {
            return 0L;
        }
    }
}
