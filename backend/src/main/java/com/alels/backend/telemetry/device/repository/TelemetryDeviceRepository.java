package com.alels.backend.telemetry.device.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.alels.backend.telemetry.device.dto.TelemetryDeviceDtos.DeviceRow;
import com.alels.backend.telemetry.device.dto.TelemetryDeviceDtos.GroupFolder;

@Repository
public class TelemetryDeviceRepository {
    private final JdbcTemplate jdbc;
    public TelemetryDeviceRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<DeviceRow> list(Long companyId, String role) {
        String scope = scope(role, "d");
        String sql = """
            WITH RECURSIVE visible_companies AS (
              SELECT id FROM companies WHERE id=?
              UNION ALL SELECT child.id FROM companies child JOIN visible_companies parent ON child.parent_company_id=parent.id WHERE child.deleted_at IS NULL
            )
            SELECT d.id,d.imei,COALESCE(db.brand_name,'-') brand,COALESCE(dm.model_name,d.device_model,'-') model,
                   COALESCE(vm.model_name,v.vehicle_name,'-') vehicle_model,COALESCE(vt.type_name,'-') vehicle_type,COALESCE(v.plate_number,'-') plate_number,
                   COALESCE(et.energy_name,v.energy_code,'-') energy_type,
                   COALESCE(cep.reference_price_country_idr,v.energy_price_snapshot,0) reference_price,
                   COALESCE(driver.driver_name,'-') driver_name,COALESCE(driver.phone_number,'-') driver_phone,
                   COALESCE(NULLIF(TRIM(CONCAT_WS(' / ',driver.license_type,driver.license_number)),''),'-') driver_license,
                   CASE
                     WHEN latest.server_time IS NULL OR latest.server_time < NOW()-(COALESCE(d.presence_timeout_seconds,420)||' seconds')::interval THEN 'STOP'
                     WHEN LOWER(COALESCE(latest.ignition,'')) IN ('1','true','on','yes') AND COALESCE(latest.speed,0)>0 THEN 'MOVING'
                     WHEN LOWER(COALESCE(latest.ignition,'')) IN ('1','true','on','yes') THEN 'IDLE'
                     ELSE 'STOP'
                   END movement_status,
                   COALESCE(d.tcp_enabled,TRUE) tcp_enabled,
                   (COALESCE(d.online,FALSE) OR UPPER(COALESCE(d.presence_status,''))='ONLINE'
                    OR COALESCE(d.gsm_connected,FALSE) OR COALESCE(d.wifi_connected,FALSE)) connected,
                   g.id group_id,g.group_name,(g.deleted_at IS NOT NULL) group_deleted,
                   c.company_name,latest.server_time last_updated
            FROM devices d
            JOIN companies c ON c.id=d.company_id AND c.deleted_at IS NULL
            LEFT JOIN device_brands db ON db.id=d.device_brand_id AND db.deleted_at IS NULL
            LEFT JOIN device_models dm ON dm.id=d.device_model_id AND dm.deleted_at IS NULL
            LEFT JOIN LATERAL (
              SELECT assignment.vehicle_id FROM vehicle_device_assignments assignment
              WHERE assignment.device_id=d.id AND assignment.assignment_status='ACTIVE' AND assignment.deleted_at IS NULL
              ORDER BY assignment.assigned_at DESC LIMIT 1
            ) assigned ON TRUE
            LEFT JOIN vehicles v ON v.id=COALESCE(assigned.vehicle_id,d.vehicle_id) AND v.deleted_at IS NULL
            LEFT JOIN vehicle_models vm ON vm.id=v.model_id AND vm.deleted_at IS NULL
            LEFT JOIN vehicle_types vt ON vt.id=v.vehicle_type_id AND vt.deleted_at IS NULL
            LEFT JOIN energy_types et ON et.id=v.energy_id
            LEFT JOIN company_energy_prices cep ON cep.company_id=d.company_id AND cep.energy_id=v.energy_id AND cep.country_code=COALESCE(v.country_code,'ID')
            LEFT JOIN LATERAL (
              SELECT COALESCE(ad.driver_name,ad.full_name,ad.driver_code) driver_name,ad.phone_number,
                     lm.name license_type,ad.license_number
              FROM (
                SELECT manual.driver_id,manual.assigned_at event_time FROM driver_manual_assignments manual
                WHERE manual.device_id=d.id AND manual.assignment_status='ACTIVE' AND manual.deleted_at IS NULL
                UNION ALL
                SELECT auto_session.driver_id,auto_session.started_at FROM driver_auto_sessions auto_session
                WHERE auto_session.device_id=d.id AND auto_session.session_status='ACTIVE'
              ) active_driver
              JOIN asset_drivers ad ON ad.id=active_driver.driver_id AND ad.deleted_at IS NULL
              LEFT JOIN license_master lm ON lm.id=ad.license_master_id AND lm.deleted_at IS NULL
              ORDER BY active_driver.event_time DESC LIMIT 1
            ) driver ON TRUE
            LEFT JOIN LATERAL (
              SELECT telemetry.speed,telemetry.server_time,
                     COALESCE(telemetry.io_data->>'ignition',telemetry.io->>'ignition',telemetry.io_data->>'239',telemetry.io->>'239') ignition
              FROM telemetry WHERE telemetry.imei=d.imei ORDER BY telemetry.server_time DESC LIMIT 1
            ) latest ON TRUE
            LEFT JOIN telemetry_group_devices membership ON membership.device_id=d.id
            LEFT JOIN telemetry_groups g ON g.id=membership.group_id
            WHERE d.deleted_at IS NULL %s
            ORDER BY c.company_name,d.imei
            """.formatted(scope);
        return jdbc.query(sql, (rs,n)->new DeviceRow(
            rs.getLong("id"),rs.getString("imei"),rs.getString("brand"),rs.getString("model"),
            rs.getString("vehicle_model"),rs.getString("vehicle_type"),rs.getString("plate_number"),
            rs.getString("energy_type"),rs.getBigDecimal("reference_price"),
            rs.getString("driver_name"),rs.getString("driver_phone"),rs.getString("driver_license"),
            rs.getString("movement_status"),rs.getBoolean("tcp_enabled"),rs.getBoolean("connected"),
            rs.getObject("group_id",Long.class),rs.getString("group_name"),rs.getBoolean("group_deleted"),
            rs.getString("company_name"),rs.getString("last_updated")
        ), companyId);
    }

    public List<GroupFolder> folders(Long companyId,String role,boolean deleted) {
        String scope=scope(role,"g");
        String sql="""
            WITH RECURSIVE visible_companies AS (
              SELECT id FROM companies WHERE id=?
              UNION ALL SELECT child.id FROM companies child JOIN visible_companies parent ON child.parent_company_id=parent.id WHERE child.deleted_at IS NULL
            )
            SELECT g.id,g.group_name,COUNT(m.device_id) device_count
            FROM telemetry_groups g LEFT JOIN telemetry_group_devices m ON m.group_id=g.id
            WHERE g.deleted_at IS %s %s GROUP BY g.id,g.group_name ORDER BY g.group_name
            """.formatted(deleted?"NOT NULL":"NULL",scope);
        return jdbc.query(sql,(rs,n)->new GroupFolder(rs.getLong("id"),rs.getString("group_name"),rs.getInt("device_count"),deleted),companyId);
    }

    public Optional<String> imeiById(Long id,Long companyId,String role) {
        return list(companyId,role).stream().filter(row->row.id().equals(id)).map(DeviceRow::imei).findFirst();
    }
    public void setTcp(Long id,boolean enabled) {
        jdbc.update("UPDATE devices SET tcp_enabled=?,updated_at=NOW() WHERE id=? AND deleted_at IS NULL",enabled,id);
    }
    private String scope(String role,String alias) {
        String normalized=role==null?"":role.replaceAll("[\\s_-]+","").toUpperCase();
        if ("SUPERADMIN".equals(normalized)||"ADMIN".equals(normalized)) return "";
        if ("CLIENTUSER".equals(normalized)||"TECHUSER".equals(normalized)) return "AND "+alias+".company_id=?";
        return "AND "+alias+".company_id IN (SELECT id FROM visible_companies)";
    }
}
