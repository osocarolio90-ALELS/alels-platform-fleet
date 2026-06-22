package com.alels.gateway.repository;

import com.alels.gateway.config.DatabaseConfig;
import com.alels.gateway.model.DictionaryInfo;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class DictionaryRegistryRepository {

    public DictionaryInfo findActiveByDeviceModelId(Long deviceModelId) {

        if (deviceModelId == null) {
            return null;
        }

        String sql = """
                SELECT
                    id,
                    device_model_id,
                    dictionary_code,
                    dictionary_file,
                    dictionary_version,
                    source_type,
                    source_path,
                    status
                FROM dictionary_registry
                WHERE device_model_id = ?
                AND status = 'ACTIVE'
                ORDER BY id DESC
                LIMIT 1
                """;

        try (
                Connection conn = DatabaseConfig.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)
        ) {
            stmt.setLong(1, deviceModelId);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    DictionaryInfo info = new DictionaryInfo();

                    info.setId(rs.getLong("id"));
                    info.setDeviceModelId(rs.getLong("device_model_id"));
                    info.setDictionaryCode(rs.getString("dictionary_code"));
                    info.setDictionaryFile(rs.getString("dictionary_file"));
                    info.setDictionaryVersion(rs.getString("dictionary_version"));
                    info.setSourceType(rs.getString("source_type"));
                    info.setSourcePath(rs.getString("source_path"));
                    info.setStatus(rs.getString("status"));

                    return info;
                }
            }

        } catch (Exception e) {
            System.err.println(
                    "[DICTIONARY REGISTRY REPOSITORY ERROR] deviceModelId="
                            + deviceModelId
                            + " error="
                            + e.getMessage()
            );
        }

        return null;
    }
}