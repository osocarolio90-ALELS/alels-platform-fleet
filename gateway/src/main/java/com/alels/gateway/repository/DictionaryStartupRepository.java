package com.alels.gateway.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

import com.alels.gateway.config.DatabaseConfig;
import com.alels.gateway.model.DictionaryFileInfo;

public class DictionaryStartupRepository {

    private DictionaryStartupRepository() {
    }

    public static List<DictionaryFileInfo> findActiveDictionaries() {

        List<DictionaryFileInfo> result =
                new ArrayList<>();

        String sql = """
                SELECT
                    id,
                    device_model_id,
                    dictionary_code,
                    dictionary_file
                FROM dictionary_registry
                WHERE status = 'ACTIVE'
                ORDER BY dictionary_code
                """;

        try (
                Connection conn = DatabaseConfig.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()
        ) {
            while (rs.next()) {

                DictionaryFileInfo info =
                        new DictionaryFileInfo();

                info.setDictionaryRegistryId(
                        rs.getLong("id"));

                info.setDeviceModelId(
                        rs.getLong("device_model_id"));

                info.setDictionaryCode(
                        rs.getString("dictionary_code"));

                info.setDictionaryFile(
                        rs.getString("dictionary_file"));

                result.add(info);
            }

        } catch (Exception e) {
            System.err.println(
                    "[DICT STARTUP REPO ERROR] "
                            + e.getMessage()
            );
        }

        return result;
    }
}