package com.alels.backend.assetregister.repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.springframework.dao.DataAccessException;
import org.springframework.lang.NonNull;
import org.springframework.dao.DataIntegrityViolationException;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.alels.backend.assetregister.dto.VehicleRegisterDtos.VehicleLookupOption;
import com.alels.backend.assetregister.dto.VehicleRegisterDtos.VehicleRegisterRequest;
import com.alels.backend.assetregister.dto.VehicleRegisterDtos.VehicleRegisterRow;

@Repository
public class VehicleRegisterRepository {
    private final JdbcTemplate jdbcTemplate;

    public VehicleRegisterRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<VehicleRegisterRow> list(Long actorCompanyId, String role) {
        String normalizedRole = normalizeRole(role);
        if ("SUPERADMIN".equals(normalizedRole) || "ADMIN".equals(normalizedRole)) {
            return jdbcTemplate.query(baseListSql() + " ORDER BY v.created_at DESC", rowMapper());
        }
        return jdbcTemplate.query("""
                WITH RECURSIVE visible_companies AS (
                    SELECT id FROM companies WHERE id = ? AND deleted_at IS NULL
                    UNION ALL
                    SELECT c.id FROM companies c JOIN visible_companies vc ON c.parent_company_id = vc.id WHERE c.deleted_at IS NULL
                )
                """ + baseListSql() + " AND v.company_id IN (SELECT id FROM visible_companies) ORDER BY v.created_at DESC",
                rowMapper(), actorCompanyId);
    }

    public List<VehicleLookupOption> companyOptions(Long actorCompanyId, String role) {
        String normalizedRole = normalizeRole(role);
        if ("SUPERADMIN".equals(normalizedRole) || "ADMIN".equals(normalizedRole)) {
            return jdbcTemplate.query("""
                    SELECT id, company_name, company_code, status
                    FROM companies
                    WHERE deleted_at IS NULL
                    ORDER BY company_name
                    """, (rs, rowNum) -> new VehicleLookupOption(rs.getLong("id"), rs.getString("company_name"), rs.getString("company_code"), rs.getString("status")));
        }
        return jdbcTemplate.query("""
                WITH RECURSIVE visible_companies AS (
                    SELECT id, company_name, company_code, status FROM companies WHERE id = ? AND deleted_at IS NULL
                    UNION ALL
                    SELECT c.id, c.company_name, c.company_code, c.status FROM companies c JOIN visible_companies vc ON c.parent_company_id = vc.id WHERE c.deleted_at IS NULL
                )
                SELECT id, company_name, company_code, status
                FROM visible_companies
                ORDER BY company_name
                """, (rs, rowNum) -> new VehicleLookupOption(rs.getLong("id"), rs.getString("company_name"), rs.getString("company_code"), rs.getString("status")), actorCompanyId);
    }

    public boolean canAccessCompany(Long targetCompanyId, Long actorCompanyId, String role) {
        if (targetCompanyId == null || actorCompanyId == null) return false;
        String normalizedRole = normalizeRole(role);
        if ("SUPERADMIN".equals(normalizedRole) || "ADMIN".equals(normalizedRole)) return true;
        Integer count = jdbcTemplate.queryForObject("""
                WITH RECURSIVE visible_companies AS (
                    SELECT id FROM companies WHERE id = ? AND deleted_at IS NULL
                    UNION ALL
                    SELECT c.id FROM companies c JOIN visible_companies vc ON c.parent_company_id = vc.id WHERE c.deleted_at IS NULL
                )
                SELECT COUNT(*) FROM visible_companies WHERE id = ?
                """, Integer.class, actorCompanyId, targetCompanyId);
        return count != null && count > 0;
    }

    public Long create(VehicleRegisterRequest request, Long actorUserId) {
        PriceSnapshot snapshot = findPriceSnapshot(request.companyId(), request.energyCode(), request.countryCode()).orElse(new PriceSnapshot(BigDecimal.ZERO, "IDR", request.energyCode()));
        try {
            return jdbcTemplate.queryForObject("""
                INSERT INTO vehicles (
                    company_id, vehicle_code, vehicle_name, plate_number,
                    vehicle_type_id, brand_id, model_id, year_manufacture,
                    country_code, energy_id, energy_code, energy_price_snapshot, energy_currency,
                    ownership_type_id, capacity_value, capacity_unit_id,
                    operational_status, notes, created_by, updated_by
                )
                VALUES (
                    ?, ?, ?, ?, ?, ?, ?, ?, UPPER(COALESCE(NULLIF(TRIM(?), ''), 'ID')),
                    (SELECT id FROM energy_types WHERE energy_code = ? LIMIT 1), ?, ?, ?, ?, ?, ?, ?, ?, ?, ?
                )
                RETURNING id
                """, Long.class,
                request.companyId(), normalizeVehicleCode(request.vehicleCode(), request.plateNumber()), request.vehicleName(), request.plateNumber(),
                request.vehicleTypeId(), request.brandId(), request.modelId(), request.yearManufacture(),
                request.countryCode(), request.energyCode(), snapshot.energyCode(), snapshot.price(), snapshot.currency(),
                request.ownershipTypeId(), request.capacityValue(), request.capacityUnitId(),
                normalizeStatus(request.operationalStatus()), request.notes(), actorUserId, actorUserId);
        } catch (DataIntegrityViolationException ex) {
            throw duplicateVehicleException(ex);
        }
    }

    public void update(Long id, VehicleRegisterRequest request, Long actorUserId) {
        PriceSnapshot snapshot = findPriceSnapshot(request.companyId(), request.energyCode(), request.countryCode()).orElse(new PriceSnapshot(BigDecimal.ZERO, "IDR", request.energyCode()));
        try {
            jdbcTemplate.update("""
                UPDATE vehicles
                SET vehicle_code = COALESCE(NULLIF(TRIM(?), ''), vehicle_code),
                    vehicle_name = ?,
                    plate_number = COALESCE(NULLIF(TRIM(?), ''), plate_number),
                    vehicle_type_id = ?,
                    brand_id = ?,
                    model_id = ?,
                    year_manufacture = ?,
                    country_code = UPPER(COALESCE(NULLIF(TRIM(?), ''), country_code)),
                    energy_id = (SELECT id FROM energy_types WHERE energy_code = ? LIMIT 1),
                    energy_code = ?,
                    energy_price_snapshot = ?,
                    energy_currency = ?,
                    ownership_type_id = ?,
                    capacity_value = ?,
                    capacity_unit_id = ?,
                    operational_status = COALESCE(NULLIF(TRIM(?), ''), operational_status),
                    notes = ?,
                    updated_by = ?,
                    updated_at = NOW()
                WHERE id = ? AND deleted_at IS NULL
                """, normalizeVehicleCode(request.vehicleCode(), request.plateNumber()), request.vehicleName(), request.plateNumber(), request.vehicleTypeId(), request.brandId(), request.modelId(), request.yearManufacture(), request.countryCode(), request.energyCode(), snapshot.energyCode(), snapshot.price(), snapshot.currency(), request.ownershipTypeId(), request.capacityValue(), request.capacityUnitId(), normalizeStatus(request.operationalStatus()), request.notes(), actorUserId, id);
        } catch (DataIntegrityViolationException ex) {
            throw duplicateVehicleException(ex);
        }
    }

    public void softDelete(Long id, Long actorUserId) {
        jdbcTemplate.update("""
                UPDATE vehicles
                SET deleted_at = NOW(),
                    deleted_by = ?,
                    deleted_reason = COALESCE(deleted_reason, 'Deleted from Vehicle Register'),
                    delete_permanent_at = COALESCE(delete_permanent_at, NOW() + INTERVAL '30 days'),
                    updated_by = ?,
                    updated_at = NOW()
                WHERE id = ? AND deleted_at IS NULL
                """, actorUserId, actorUserId, id);
    }

    public void setMaintenance(Long id, boolean maintenance, Long actorUserId) {
        jdbcTemplate.update("""
                UPDATE vehicles
                SET operational_status = ?,
                    maintenance_at = CASE WHEN ? THEN NOW() ELSE maintenance_at END,
                    maintenance_by = CASE WHEN ? THEN ? ELSE maintenance_by END,
                    updated_by = ?,
                    updated_at = NOW()
                WHERE id = ? AND deleted_at IS NULL
                """, maintenance ? "MAINTENANCE" : "UNKNOWN", maintenance, maintenance, actorUserId, actorUserId, id);
    }


    public boolean plateExists(Long companyId, String plateNumber, Long excludeVehicleId) {
        if (companyId == null || plateNumber == null || plateNumber.isBlank()) {
            return false;
        }
        Integer count;
        if (excludeVehicleId == null) {
            count = jdbcTemplate.queryForObject("""
                    SELECT COUNT(*)
                    FROM vehicles
                    WHERE company_id = ?
                      AND UPPER(TRIM(plate_number)) = UPPER(TRIM(?))
                      AND deleted_at IS NULL
                    """, Integer.class, companyId, plateNumber);
        } else {
            count = jdbcTemplate.queryForObject("""
                    SELECT COUNT(*)
                    FROM vehicles
                    WHERE company_id = ?
                      AND UPPER(TRIM(plate_number)) = UPPER(TRIM(?))
                      AND deleted_at IS NULL
                      AND id <> ?
                    """, Integer.class, companyId, plateNumber, excludeVehicleId);
        }
        return count != null && count > 0;
    }

    public boolean existsActive(String table, Long id) {
        if (id == null) return true;
        String safeTable = switch (table) {
            case "vehicle_types", "vehicle_brands", "vehicle_models", "vehicle_ownership_types", "vehicle_capacity_units" -> table;
            default -> throw new IllegalArgumentException("Unsupported reference table: " + table);
        };
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + safeTable + " WHERE id = ? AND is_active = TRUE AND deleted_at IS NULL", Integer.class, id);
        return count != null && count > 0;
    }

    public boolean modelBelongsToBrand(Long modelId, Long brandId) {
        if (modelId == null || brandId == null) return true;
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM vehicle_models
                WHERE id = ?
                  AND brand_id = ?
                  AND is_active = TRUE
                  AND deleted_at IS NULL
                """, Integer.class, modelId, brandId);
        return count != null && count > 0;
    }

    public Optional<Long> companyIdByVehicle(Long id) {
        return jdbcTemplate.query("SELECT company_id FROM vehicles WHERE id = ? AND deleted_at IS NULL", (rs, rowNum) -> rs.getLong("company_id"), id).stream().findFirst();
    }

    public void log(Long actorUserId, Long actorCompanyId, Long targetId, String action) {
        try {
            jdbcTemplate.update("""
                    INSERT INTO organization_activity_logs(actor_user_id, actor_company_id, target_type, target_id, action, details)
                    VALUES (?, ?, 'VEHICLE', ?, ?, '{}'::jsonb)
                    """, actorUserId, actorCompanyId, targetId, action);
        } catch (DataAccessException ignored) {
            // Audit log must never break the operational transaction.
        }
    }

    private Optional<PriceSnapshot> findPriceSnapshot(Long companyId, String energyCode, String countryCode) {
        if (companyId == null || energyCode == null || energyCode.isBlank()) return Optional.empty();
        return jdbcTemplate.query("""
                SELECT cep.price_energy, cep.currency, et.energy_code
                FROM company_energy_prices cep
                JOIN energy_types et ON et.id = cep.energy_id
                WHERE cep.company_id = ?
                  AND et.energy_code = ?
                  AND COALESCE(cep.country_code, 'ID') = UPPER(COALESCE(NULLIF(TRIM(?), ''), 'ID'))
                LIMIT 1
                """, (rs, rowNum) -> new PriceSnapshot(rs.getBigDecimal("price_energy"), rs.getString("currency"), rs.getString("energy_code")), companyId, energyCode, countryCode)
                .stream().findFirst();
    }

    private String baseListSql() {
        return """
                SELECT v.id, v.company_id, c.company_name, v.vehicle_code, v.vehicle_name, v.plate_number,
                       vt.id AS vehicle_type_id, vt.type_name AS vehicle_type,
                       vb.id AS brand_id, vb.brand_name AS brand,
                       vm.id AS model_id, vm.model_name AS model,
                       v.year_manufacture, v.energy_code, et.energy_name,
                       v.energy_price_snapshot, v.energy_currency, v.country_code,
                       COALESCE(erc.country_name, v.country_code) AS country_name,
                       vot.id AS ownership_type_id, vot.ownership_name AS ownership,
                       v.capacity_value, vcu.id AS capacity_unit_id, vcu.unit_name AS capacity_unit,
                       v.operational_status, v.created_at, u.email AS created_by_email, v.updated_at
                FROM vehicles v
                JOIN companies c ON c.id = v.company_id
                LEFT JOIN vehicle_types vt ON vt.id = v.vehicle_type_id
                LEFT JOIN vehicle_brands vb ON vb.id = v.brand_id
                LEFT JOIN vehicle_models vm ON vm.id = v.model_id
                LEFT JOIN energy_types et ON et.energy_code = v.energy_code
                LEFT JOIN energy_reference_countries erc ON erc.country_code = v.country_code
                LEFT JOIN vehicle_ownership_types vot ON vot.id = v.ownership_type_id
                LEFT JOIN vehicle_capacity_units vcu ON vcu.id = v.capacity_unit_id
                LEFT JOIN users u ON u.id = v.created_by
                WHERE v.deleted_at IS NULL
                  AND c.deleted_at IS NULL
                """;
    }

    @NonNull
    private org.springframework.jdbc.core.RowMapper<VehicleRegisterRow> rowMapper() {
        return (rs, rowNum) -> new VehicleRegisterRow(
                rs.getLong("id"), rs.getLong("company_id"), rs.getString("company_name"), rs.getString("vehicle_code"), rs.getString("vehicle_name"), rs.getString("plate_number"),
                rs.getObject("vehicle_type_id", Long.class), rs.getString("vehicle_type"),
                rs.getObject("brand_id", Long.class), rs.getString("brand"),
                rs.getObject("model_id", Long.class), rs.getString("model"),
                rs.getObject("year_manufacture", Integer.class), rs.getString("energy_code"), rs.getString("energy_name"), rs.getBigDecimal("energy_price_snapshot"), rs.getString("energy_currency"),
                rs.getString("country_code"), rs.getString("country_name"), rs.getObject("ownership_type_id", Long.class), rs.getString("ownership"),
                rs.getBigDecimal("capacity_value"), rs.getObject("capacity_unit_id", Long.class), rs.getString("capacity_unit"), rs.getString("operational_status"),
                rs.getString("created_at"), rs.getString("created_by_email"), rs.getString("updated_at")
        );
    }

    private String normalizeRole(String role) {
        return role == null ? "" : role.trim().toUpperCase().replaceAll("[\\s_-]+", "");
    }

    private String normalizeStatus(String value) {
        if (value == null || value.isBlank()) return "UNKNOWN";
        String status = value.trim().toUpperCase().replaceAll("[\\s_-]+", "_");
        return switch (status) {
            case "MAINTENANCE", "STOP", "MOVING", "IDLE", "UNKNOWN" -> status;
            default -> "UNKNOWN";
        };
    }

    private String normalizeVehicleCode(String code, String plateNumber) {
        String source = code == null || code.isBlank() ? plateNumber : code;
        if (source == null || source.isBlank()) return "VH_" + System.currentTimeMillis();
        return source.trim().toUpperCase().replaceAll("[^A-Z0-9]+", "_").replaceAll("^_+|_+$", "");
    }

    private org.springframework.web.server.ResponseStatusException duplicateVehicleException(DataIntegrityViolationException ex) {
        String message = ex.getMostSpecificCause() == null ? ex.getMessage() : ex.getMostSpecificCause().getMessage();
        if (message != null && message.contains("uq_vehicles_company_plate_number")) {
            return new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT, "Plat Number sudah terdaftar di company tersebut.");
        }
        if (message != null && message.contains("uq_vehicles_company_vehicle_code")) {
            return new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT, "Vehicle code otomatis dari plat number sudah terdaftar.");
        }
        return new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "Data vehicle tidak valid.");
    }

    private record PriceSnapshot(BigDecimal price, String currency, String energyCode) {}
}
