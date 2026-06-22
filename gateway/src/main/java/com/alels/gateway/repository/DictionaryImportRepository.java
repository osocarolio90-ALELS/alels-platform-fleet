package com.alels.gateway.repository;

import com.alels.gateway.config.DatabaseConfig;
import com.alels.gateway.model.DictionaryImportJobInfo;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

public class DictionaryImportRepository {

    public List<DictionaryImportJobInfo> findPendingJobs() {

        List<DictionaryImportJobInfo> jobs =
                new ArrayList<>();

        String sql = """
            SELECT
                id,
                dictionary_registry_id,
                device_model_id,
                dictionary_code,
                dictionary_file,
                import_status
            FROM dictionary_import_jobs
            WHERE import_status='PENDING'
            ORDER BY id
            """;

        try (
                Connection conn =
                        DatabaseConfig.getConnection();

                PreparedStatement stmt =
                        conn.prepareStatement(sql);

                ResultSet rs =
                        stmt.executeQuery()
        ) {

            while (rs.next()) {

                DictionaryImportJobInfo job =
                        new DictionaryImportJobInfo();

                job.setId(
                        rs.getLong("id"));

                job.setDictionaryRegistryId(
                        rs.getLong("dictionary_registry_id"));

                job.setDeviceModelId(
                        rs.getLong("device_model_id"));

                job.setDictionaryCode(
                        rs.getString("dictionary_code"));

                job.setDictionaryFile(
                        rs.getString("dictionary_file"));

                job.setImportStatus(
                        rs.getString("import_status"));

                jobs.add(job);
            }

        } catch (Exception e) {

            System.err.println(
                    "[DICT IMPORT REPOSITORY ERROR] "
                            + e.getMessage()
            );
        }

        return jobs;
    }
}