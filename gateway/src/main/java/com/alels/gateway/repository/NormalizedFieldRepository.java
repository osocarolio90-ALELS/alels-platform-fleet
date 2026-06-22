package com.alels.gateway.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import com.alels.gateway.config.DatabaseConfig;
import com.alels.gateway.model.NormalizedFieldInfo;

public class NormalizedFieldRepository {

    public NormalizedFieldInfo findMapping(
            Long deviceModelId,
            String sourceProtocol,
            String sourceIoId
    ) {
        if (deviceModelId == null || sourceIoId == null || sourceIoId.isBlank()) {
            return null;
        }

        String sql = """
                SELECT
                    m.id AS mapping_id,
                    m.device_model_id,
                    m.dictionary_code,
                    m.source_protocol,
                    m.source_io_id,
                    m.source_name,
                    m.source_unit,
                    m.target_unit,
                    m.multiplier,
                    m.offset_value,
                    m.value_type,
                    nf.id AS normalized_field_id,
                    nf.field_code,
                    nf.field_name,
                    nf.category,
                    nf.unit
                FROM device_io_mappings m
                JOIN normalized_fields nf
                    ON nf.id = m.normalized_field_id
                WHERE m.device_model_id = ?
                AND m.source_io_id = ?
                AND m.status = 'ACTIVE'
                AND (
                    m.source_protocol = ?
                    OR m.source_protocol = 'TELTONIKA_AUTO'
                    OR m.source_protocol = 'ANY'
                )
                ORDER BY
                    CASE
                        WHEN m.source_protocol = ? THEN 1
                        WHEN m.source_protocol = 'TELTONIKA_AUTO' THEN 2
                        ELSE 3
                    END
                LIMIT 1
                """;

        try (
                Connection conn = DatabaseConfig.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)
        ) {
            stmt.setLong(1, deviceModelId);
            stmt.setString(2, sourceIoId.trim());
            stmt.setString(3, sourceProtocol);
            stmt.setString(4, sourceProtocol);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    NormalizedFieldInfo info = new NormalizedFieldInfo();

                    info.setMappingId(rs.getLong("mapping_id"));
                    info.setDeviceModelId(rs.getLong("device_model_id"));
                    info.setDictionaryCode(rs.getString("dictionary_code"));
                    info.setSourceProtocol(rs.getString("source_protocol"));
                    info.setSourceIoId(rs.getString("source_io_id"));
                    info.setSourceName(rs.getString("source_name"));
                    info.setSourceUnit(rs.getString("source_unit"));
                    info.setTargetUnit(rs.getString("target_unit"));
                    info.setMultiplier(rs.getDouble("multiplier"));
                    info.setOffsetValue(rs.getDouble("offset_value"));
                    info.setValueType(rs.getString("value_type"));
                    info.setNormalizedFieldId(rs.getLong("normalized_field_id"));
                    info.setFieldCode(rs.getString("field_code"));
                    info.setFieldName(rs.getString("field_name"));
                    info.setCategory(rs.getString("category"));
                    info.setUnit(rs.getString("unit"));

                    return info;
                }
            }

        } catch (Exception e) {
            System.err.println(
                    "[NORMALIZED FIELD REPOSITORY ERROR] deviceModelId="
                            + deviceModelId
                            + " protocol="
                            + sourceProtocol
                            + " io="
                            + sourceIoId
                            + " error="
                            + e.getMessage()
            );
        }

        return null;
    }
}