package com.alels.backend.masterdata.repository;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.alels.backend.masterdata.dto.MasterDataDtos.MasterOptionRequest;
import com.alels.backend.masterdata.dto.MasterDataDtos.MasterOptionRow;

@Repository
public class MasterOptionRepository {
    private final JdbcTemplate jdbcTemplate;

    public MasterOptionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<MasterOptionRow> list(String table, String codeColumn, String nameColumn) {
        return jdbcTemplate.query("""
                SELECT id,
                       %s AS code,
                       %s AS name,
                       description,
                       is_active,
                       is_system,
                       sort_order,
                       created_at,
                       updated_at
                FROM %s
                WHERE deleted_at IS NULL
                ORDER BY sort_order, %s
                """.formatted(codeColumn, nameColumn, table, nameColumn),
                (rs, rowNum) -> new MasterOptionRow(
                        rs.getLong("id"),
                        rs.getString("code"),
                        rs.getString("name"),
                        rs.getString("description"),
                        rs.getBoolean("is_active"),
                        rs.getBoolean("is_system"),
                        rs.getInt("sort_order"),
                        rs.getString("created_at"),
                        rs.getString("updated_at")
                ));
    }

    public void create(String table, String codeColumn, String nameColumn, MasterOptionRequest request, Long userId) {
        jdbcTemplate.update("""
                INSERT INTO %s (%s, %s, description, is_active, is_system, sort_order, created_by, updated_by)
                VALUES (?, ?, ?, COALESCE(?, TRUE), FALSE, COALESCE(?, 1000), ?, ?)
                ON CONFLICT (%s) DO UPDATE
                SET %s = EXCLUDED.%s,
                    description = EXCLUDED.description,
                    is_active = EXCLUDED.is_active,
                    sort_order = EXCLUDED.sort_order,
                    updated_by = EXCLUDED.updated_by,
                    updated_at = NOW(),
                    deleted_at = NULL,
                    deleted_by = NULL,
                    deleted_reason = NULL,
                    delete_permanent_at = NULL
                """.formatted(table, codeColumn, nameColumn, codeColumn, nameColumn, nameColumn),
                normalizeCode(request.code(), request.name()), request.name(), request.description(), request.active(), request.sortOrder(), userId, userId);
    }

    public void update(String table, String codeColumn, String nameColumn, Long id, MasterOptionRequest request, Long userId) {
        jdbcTemplate.update("""
                UPDATE %s
                SET %s = COALESCE(NULLIF(TRIM(?), ''), %s),
                    %s = COALESCE(NULLIF(TRIM(?), ''), %s),
                    description = ?,
                    is_active = COALESCE(?, is_active),
                    sort_order = COALESCE(?, sort_order),
                    updated_by = ?,
                    updated_at = NOW()
                WHERE id = ?
                  AND deleted_at IS NULL
                """.formatted(table, codeColumn, codeColumn, nameColumn, nameColumn),
                normalizeCode(request.code(), request.name()), request.name(), request.description(), request.active(), request.sortOrder(), userId, id);
    }

    public void softDelete(String table, Long id, Long userId) {
        jdbcTemplate.update("""
                UPDATE %s
                SET is_active = FALSE,
                    deleted_at = NOW(),
                    deleted_by = ?,
                    deleted_reason = COALESCE(deleted_reason, 'Deleted from Master Data'),
                    delete_permanent_at = COALESCE(delete_permanent_at, NOW() + INTERVAL '30 days'),
                    updated_by = ?,
                    updated_at = NOW()
                WHERE id = ?
                  AND deleted_at IS NULL
                """.formatted(table), userId, userId, id);
    }

    public void log(Long actorUserId, Long actorCompanyId, String targetType, Long targetId, String action) {
        jdbcTemplate.update("""
                INSERT INTO organization_activity_logs(actor_user_id, actor_company_id, target_type, target_id, action, details)
                VALUES (?, ?, ?, ?, ?, '{}'::jsonb)
                """, actorUserId, actorCompanyId, targetType, targetId, action);
    }

    private String normalizeCode(String value, String fallback) {
        String source = value == null || value.isBlank() ? fallback : value;
        if (source == null || source.isBlank()) return null;
        return source.trim().toUpperCase().replaceAll("[^A-Z0-9]+", "_").replaceAll("^_+|_+$", "");
    }
}
