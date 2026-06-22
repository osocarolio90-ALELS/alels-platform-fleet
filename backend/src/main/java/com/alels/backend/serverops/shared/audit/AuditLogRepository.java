package com.alels.backend.serverops.shared.audit;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AuditLogRepository {
    private final JdbcTemplate jdbcTemplate;

    public AuditLogRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void insert(Long userId, Long companyId, String action, String entityType, String entityId, String ipAddress, String metadataJson) {
        jdbcTemplate.update("""
                INSERT INTO audit_logs (user_id, company_id, action, entity_type, entity_id, ip_address, metadata)
                VALUES (?, ?, ?, ?, ?, CAST(NULLIF(?, '') AS inet), CAST(? AS jsonb))
                """, userId, companyId, action, entityType, entityId, safeIp(ipAddress), metadataJson == null ? "{}" : metadataJson);
    }

    private String safeIp(String ipAddress) {
        if (ipAddress == null || ipAddress.isBlank()) {
            return null;
        }
        String first = ipAddress.split(",")[0].trim();
        return first.length() > 64 ? null : first;
    }
}
