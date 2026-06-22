package com.alels.gateway.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import com.alels.gateway.config.DatabaseConfig;
import com.alels.gateway.model.DeviceModelInfo;

public class DeviceModelResolverRepository {

    public DeviceModelInfo findByImei(String imei) {

        if (imei == null || imei.isBlank()) {
            return null;
        }

        String sql = """
                SELECT
                    dm.id AS model_id,

                    db.brand_code,
                    db.brand_name,

                    dm.model_code,
                    dm.model_name,

                    dm.protocol_code,
                    dm.parser_code,
                    dm.dictionary_code
                FROM devices d
                JOIN device_models dm
                    ON dm.id = d.device_model_id
                JOIN device_brands db
                    ON db.id = dm.brand_id
                WHERE d.imei = ?
                AND COALESCE(dm.status, 'ACTIVE') = 'ACTIVE'
                AND COALESCE(db.status, 'ACTIVE') = 'ACTIVE'
                LIMIT 1
                """;

        try (
                Connection conn = DatabaseConfig.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)
        ) {

            stmt.setString(1, imei);

            try (ResultSet rs = stmt.executeQuery()) {

                if (rs.next()) {
                    DeviceModelInfo info = new DeviceModelInfo();

                    info.setModelId(
                            rs.getLong("model_id")
                    );

                    info.setBrandCode(
                            rs.getString("brand_code")
                    );

                    info.setBrandName(
                            rs.getString("brand_name")
                    );

                    info.setModelCode(
                            rs.getString("model_code")
                    );

                    info.setModelName(
                            rs.getString("model_name")
                    );

                    info.setProtocolCode(
                            rs.getString("protocol_code")
                    );

                    info.setParserCode(
                            rs.getString("parser_code")
                    );

                    info.setDictionaryCode(
                            rs.getString("dictionary_code")
                    );

                    return info;
                }
            }

        } catch (Exception e) {
            System.err.println(
                    "[DEVICE MODEL RESOLVER REPOSITORY ERROR] imei="
                            + imei
                            + " error="
                            + e.getMessage()
            );
        }

        return null;
    }
}