package com.alels.gateway.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;

import com.alels.gateway.config.DatabaseConfig;
import com.alels.gateway.model.NormalizedFieldInfo;

public class TelemetryNormalizedRepository {

    public void insert(
            Long telemetryId,
            Long rawPacketId,
            Long companyId,
            Long vehicleId,
            String imei,
            NormalizedFieldInfo fieldInfo,
            String rawValue,
            Double numericValue,
            String textValue,
            Boolean booleanValue,
            String sourceProtocol,
            String dictionaryCode
    ) {
        if (telemetryId == null || fieldInfo == null) {
            return;
        }

        String sql = """
                INSERT INTO telemetry_normalized (
                    telemetry_id,
                    raw_packet_id,
                    company_id,
                    vehicle_id,
                    imei,
                    field_code,
                    field_name,
                    category,
                    raw_value,
                    numeric_value,
                    text_value,
                    boolean_value,
                    unit,
                    source_protocol,
                    source_io_id,
                    value_type,
                    source_name,
                    dictionary_code,
                    device_model_id,
                    mapping_id
                )
                VALUES (
                    ?, ?, ?, ?, ?,
                    ?, ?, ?,
                    ?, ?, ?, ?,
                    ?, ?, ?,
                    ?, ?, ?, ?, ?
                )
                """;

        try (
                Connection conn = DatabaseConfig.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)
        ) {
            stmt.setLong(1, telemetryId);

            if (rawPacketId != null) stmt.setLong(2, rawPacketId);
            else stmt.setObject(2, null);

            if (companyId != null) stmt.setLong(3, companyId);
            else stmt.setObject(3, null);

            if (vehicleId != null) stmt.setLong(4, vehicleId);
            else stmt.setObject(4, null);

            stmt.setString(5, imei);

            stmt.setString(6, fieldInfo.getFieldCode());
            stmt.setString(7, fieldInfo.getFieldName());
            stmt.setString(8, fieldInfo.getCategory());

            stmt.setString(9, rawValue);

            if (numericValue != null) stmt.setDouble(10, numericValue);
            else stmt.setObject(10, null);

            stmt.setString(11, textValue);

            if (booleanValue != null) stmt.setBoolean(12, booleanValue);
            else stmt.setObject(12, null);

            stmt.setString(13, fieldInfo.getTargetUnit() != null ? fieldInfo.getTargetUnit() : fieldInfo.getUnit());
            stmt.setString(14, sourceProtocol);
            stmt.setString(15, fieldInfo.getSourceIoId());

            stmt.setString(16, fieldInfo.getValueType());
            stmt.setString(17, fieldInfo.getSourceName());
            stmt.setString(18, dictionaryCode);

            if (fieldInfo.getDeviceModelId() != null) stmt.setLong(19, fieldInfo.getDeviceModelId());
            else stmt.setObject(19, null);

            if (fieldInfo.getMappingId() != null) stmt.setLong(20, fieldInfo.getMappingId());
            else stmt.setObject(20, null);

            stmt.executeUpdate();

        } catch (Exception e) {
            System.err.println(
                    "[TELEMETRY NORMALIZED INSERT ERROR] telemetryId="
                            + telemetryId
                            + " field="
                            + fieldInfo.getFieldCode()
                            + " error="
                            + e.getMessage()
            );
        }
    }
}