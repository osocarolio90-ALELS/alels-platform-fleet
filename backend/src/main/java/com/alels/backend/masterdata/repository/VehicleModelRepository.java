package com.alels.backend.masterdata.repository;

import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.alels.backend.masterdata.dto.MasterDataDtos.VehicleModelRequest;
import com.alels.backend.masterdata.dto.MasterDataDtos.VehicleModelRow;

@Repository
public class VehicleModelRepository {
    private final JdbcTemplate jdbcTemplate;
    public VehicleModelRepository(JdbcTemplate jdbcTemplate) { this.jdbcTemplate = jdbcTemplate; }

    public List<VehicleModelRow> list(Long brandId) {
        String sql = """
                SELECT vm.id,
                       vm.brand_id,
                       vb.brand_name,
                       vm.model_code,
                       vm.model_name,
                       vm.description,
                       vm.is_active,
                       vm.is_system,
                       vm.sort_order,
                       vm.created_at,
                       vm.updated_at
                FROM vehicle_models vm
                LEFT JOIN vehicle_brands vb ON vb.id = vm.brand_id
                WHERE vm.deleted_at IS NULL
                """;
        if (brandId != null) sql += " AND vm.brand_id = ?";
        sql += " ORDER BY COALESCE(vb.brand_name, ''), vm.sort_order, vm.model_name";
        var mapper = (org.springframework.jdbc.core.RowMapper<VehicleModelRow>) (rs, rowNum) -> new VehicleModelRow(
                rs.getLong("id"),
                rs.getObject("brand_id", Long.class),
                rs.getString("brand_name"),
                rs.getString("model_code"),
                rs.getString("model_name"),
                rs.getString("description"),
                rs.getBoolean("is_active"),
                rs.getBoolean("is_system"),
                rs.getInt("sort_order"),
                rs.getString("created_at"),
                rs.getString("updated_at")
        );
        return brandId == null ? jdbcTemplate.query(sql, mapper) : jdbcTemplate.query(sql, mapper, brandId);
    }

    public void create(VehicleModelRequest request, Long userId) {
        jdbcTemplate.update("""
                INSERT INTO vehicle_models(brand_id, model_code, model_name, description, is_active, is_system, sort_order, created_by, updated_by)
                VALUES (?, ?, ?, ?, COALESCE(?, TRUE), FALSE, COALESCE(?, 1000), ?, ?)
                ON CONFLICT(brand_id, model_code) DO UPDATE
                SET model_name = EXCLUDED.model_name,
                    description = EXCLUDED.description,
                    is_active = EXCLUDED.is_active,
                    sort_order = EXCLUDED.sort_order,
                    updated_by = EXCLUDED.updated_by,
                    updated_at = NOW(),
                    deleted_at = NULL,
                    deleted_by = NULL,
                    deleted_reason = NULL,
                    delete_permanent_at = NULL
                """, request.brandId(), normalizeCode(request.modelCode()), request.modelName(), request.description(), request.active(), request.sortOrder(), userId, userId);
    }

    public void update(Long id, VehicleModelRequest request, Long userId) {
        jdbcTemplate.update("""
                UPDATE vehicle_models
                SET brand_id = COALESCE(?, brand_id),
                    model_code = COALESCE(NULLIF(TRIM(?), ''), model_code),
                    model_name = COALESCE(NULLIF(TRIM(?), ''), model_name),
                    description = ?,
                    is_active = COALESCE(?, is_active),
                    sort_order = COALESCE(?, sort_order),
                    updated_by = ?,
                    updated_at = NOW()
                WHERE id = ?
                  AND deleted_at IS NULL
                """, request.brandId(), normalizeCode(request.modelCode()), request.modelName(), request.description(), request.active(), request.sortOrder(), userId, id);
    }

    public void softDelete(Long id, Long userId) {
        jdbcTemplate.update("""
                UPDATE vehicle_models
                SET is_active = FALSE,
                    deleted_at = NOW(),
                    deleted_by = ?,
                    deleted_reason = COALESCE(deleted_reason, 'Deleted from Master Data'),
                    delete_permanent_at = COALESCE(delete_permanent_at, NOW() + INTERVAL '30 days'),
                    updated_by = ?,
                    updated_at = NOW()
                WHERE id = ?
                  AND deleted_at IS NULL
                """, userId, userId, id);
    }

    private String normalizeCode(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim().toUpperCase().replaceAll("[^A-Z0-9]+", "_").replaceAll("^_+|_+$", "");
    }
}
