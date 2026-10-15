package com.alels.backend.telemetry.device.deviceworkspace.tab.configuration.datasource.repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import com.alels.backend.telemetry.device.deviceworkspace.tab.configuration.datasource.dto.DeviceWorkspaceDataSourceDtos.Profile;
import com.alels.backend.telemetry.device.deviceworkspace.tab.configuration.datasource.dto.DeviceWorkspaceDataSourceDtos.SourceOption;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@Repository
public class DeviceWorkspaceDataSourceRepository {
    private final JdbcTemplate jdbc; private final ObjectMapper mapper;
    public DeviceWorkspaceDataSourceRepository(JdbcTemplate jdbc,ObjectMapper mapper){this.jdbc=jdbc;this.mapper=mapper;}
    public List<SourceOption> options(String imei){return jdbc.query("""
        WITH recent AS (
            SELECT id, server_time
            FROM telemetry
            WHERE imei=?
            ORDER BY id DESC
            LIMIT 2000
        ), ranked AS (
            SELECT n.field_code,n.field_name,n.unit,n.category,n.source_io_id,n.source_protocol,n.dictionary_code,n.source_name,
                   COALESCE(n.raw_value,n.numeric_value::text,n.text_value,n.boolean_value::text) AS last_value,
                   r.server_time,
                   COUNT(*) OVER (PARTITION BY n.field_code,COALESCE(n.source_io_id,'')) AS sample_count,
                   BOOL_OR(n.numeric_value IS NOT NULL) OVER (PARTITION BY n.field_code,COALESCE(n.source_io_id,'')) AS numeric_available,
                   ROW_NUMBER() OVER (
                       PARTITION BY n.field_code,COALESCE(n.source_io_id,'')
                       ORDER BY n.telemetry_id DESC,n.id DESC
                   ) AS rn
            FROM telemetry_normalized n
            JOIN recent r ON r.id=n.telemetry_id
            WHERE n.imei=?
              AND (n.numeric_value IS NOT NULL OR n.boolean_value IS NOT NULL OR n.text_value IS NOT NULL)
        )
        SELECT field_code,COALESCE(NULLIF(field_name,''),field_code) AS label,unit,category,source_io_id,
               source_protocol,dictionary_code,source_name,last_value,server_time,sample_count,numeric_available
        FROM ranked
        WHERE rn=1
        ORDER BY LOWER(COALESCE(NULLIF(field_name,''),field_code)),field_code,source_io_id NULLS FIRST
        """,(rs,n)->{
            String fieldCode=rs.getString("field_code");
            String sourceIoId=rs.getString("source_io_id");
            String selector=selector(fieldCode,sourceIoId);
            var received=rs.getTimestamp("server_time");
            return new SourceOption(selector,fieldCode,rs.getString("label"),rs.getString("unit"),rs.getString("category"),
                    sourceIoId,rs.getString("source_protocol"),rs.getString("dictionary_code"),rs.getString("source_name"),
                    rs.getString("last_value"),received==null?null:received.toInstant().toString(),rs.getLong("sample_count"),rs.getBoolean("numeric_available"));
        },imei,imei);}
    private static String selector(String fieldCode,String sourceIoId){
        String field=fieldCode==null?"":fieldCode.trim();
        String io=sourceIoId==null?"":sourceIoId.trim();
        return io.isBlank()?field:field+"::io::"+io;
    }
    public List<Profile> profiles(Long companyId){return jdbc.query("""
        SELECT id,profile_name,rpm_source,speed_source,level_source,consumption_source,odometer_source,is_company_default,
               workspace_configuration::text,unit_preferences::text FROM trip_route_instrument_source_profiles
        WHERE company_id=? ORDER BY is_company_default DESC,LOWER(profile_name),id
        """,(rs,n)->profile(rs),companyId);}
    public Optional<Profile> profile(Long companyId,Long id){return jdbc.query("""
        SELECT id,profile_name,rpm_source,speed_source,level_source,consumption_source,odometer_source,is_company_default,
               workspace_configuration::text,unit_preferences::text FROM trip_route_instrument_source_profiles WHERE company_id=? AND id=?
        """,(rs,n)->profile(rs),companyId,id).stream().findFirst();}
    public Long effectiveId(Long companyId,Long deviceId){List<Long> r=jdbc.query("""
        SELECT COALESCE(a.profile_id,d.id) profile_id FROM (SELECT 1) s
        LEFT JOIN trip_route_instrument_source_assignments a ON a.company_id=? AND a.device_id=?
        LEFT JOIN trip_route_instrument_source_profiles d ON d.company_id=? AND d.is_company_default=TRUE
        WHERE a.profile_id IS NOT NULL OR d.id IS NOT NULL LIMIT 1
        """,(rs,n)->rs.getLong(1),companyId,deviceId,companyId);return r.isEmpty()?null:r.getFirst();}
    public Long upsert(Long companyId,Long userId,String name,String rpm,String speed,String level,String consumption,String odometer,String workspace,String units){return jdbc.queryForObject("""
        INSERT INTO trip_route_instrument_source_profiles(company_id,profile_name,rpm_source,speed_source,level_source,consumption_source,odometer_source,workspace_configuration,unit_preferences,created_by,updated_by)
        VALUES(?,?,?,?,?,?,?,CAST(? AS jsonb),CAST(? AS jsonb),?,?)
        ON CONFLICT(company_id,LOWER(profile_name)) DO UPDATE SET rpm_source=EXCLUDED.rpm_source,speed_source=EXCLUDED.speed_source,
        level_source=EXCLUDED.level_source,consumption_source=EXCLUDED.consumption_source,odometer_source=EXCLUDED.odometer_source,
        workspace_configuration=EXCLUDED.workspace_configuration,unit_preferences=EXCLUDED.unit_preferences,updated_by=EXCLUDED.updated_by,updated_at=NOW()
        RETURNING id
        """,Long.class,companyId,name,rpm,speed,level,consumption,odometer,workspace,units,userId,userId);}
    public void applyDevice(Long companyId,Long deviceId,Long profileId,Long userId){jdbc.update("""
        INSERT INTO trip_route_instrument_source_assignments(company_id,device_id,profile_id,assigned_by) VALUES(?,?,?,?)
        ON CONFLICT(company_id,device_id) DO UPDATE SET profile_id=EXCLUDED.profile_id,assigned_by=EXCLUDED.assigned_by,updated_at=NOW()
        """,companyId,deviceId,profileId,userId);}
    public void applyAll(Long companyId,Long profileId){jdbc.update("UPDATE trip_route_instrument_source_profiles SET is_company_default=(id=?),updated_at=NOW() WHERE company_id=?",profileId,companyId);jdbc.update("DELETE FROM trip_route_instrument_source_assignments WHERE company_id=?",companyId);}
    public boolean deleteProfile(Long companyId,Long profileId){return jdbc.update("DELETE FROM trip_route_instrument_source_profiles WHERE company_id=? AND id=?",companyId,profileId)>0;}
    public boolean deviceInCompany(Long companyId,Long deviceId){Integer n=jdbc.queryForObject("SELECT COUNT(*) FROM devices WHERE id=? AND company_id=? AND deleted_at IS NULL",Integer.class,deviceId,companyId);return n!=null&&n>0;}
    private Profile profile(java.sql.ResultSet rs)throws java.sql.SQLException{Map<String,String> units=Map.of();try{units=mapper.readValue(rs.getString("unit_preferences"),new TypeReference<Map<String,String>>(){});}catch(Exception ignored){}return new Profile(rs.getLong("id"),rs.getString("profile_name"),rs.getString("rpm_source"),rs.getString("speed_source"),rs.getString("level_source"),rs.getString("consumption_source"),rs.getString("odometer_source"),rs.getBoolean("is_company_default"),rs.getString("workspace_configuration"),units);}
}
