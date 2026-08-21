package com.alels.gateway.repository;

import com.alels.gateway.config.DatabaseConfig;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class DictionaryImportHistoryRepository {

    private static final int MAPPING_SCHEMA_VERSION = 2;

    private DictionaryImportHistoryRepository() {
    }

    public static boolean alreadyImported(
            String dictionaryCode,
            String checksum
    ) {
        if (dictionaryCode == null || checksum == null) {
            return false;
        }

        String sql = """
                SELECT 1
                FROM dictionary_import_history
                WHERE dictionary_code = ?
                AND file_checksum = ?
                AND import_status = 'SUCCESS'
                AND imported_mapping_count > 0
                AND metadata ->> 'mappingSchemaVersion' = ?
                LIMIT 1
                """;

        try (
                Connection conn = DatabaseConfig.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)
        ) {
            ps.setString(1, dictionaryCode);
            ps.setString(2, checksum);
            ps.setString(3, String.valueOf(MAPPING_SCHEMA_VERSION));

            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }

        } catch (Exception e) {
            System.err.println(
                    "[DICT IMPORT HISTORY CHECK ERROR] dictionary="
                            + dictionaryCode
                            + " error="
                            + e.getMessage()
            );
            return false;
        }
    }

    public static void saveSuccess(
            Long dictionaryRegistryId,
            String dictionaryCode,
            String dictionaryFile,
            String checksum,
            int mappingCount
    ) {
        save(
                dictionaryRegistryId,
                dictionaryCode,
                dictionaryFile,
                checksum,
                "SUCCESS",
                mappingCount
        );
    }

    public static void saveFailed(
            Long dictionaryRegistryId,
            String dictionaryCode,
            String dictionaryFile,
            String checksum
    ) {
        save(
                dictionaryRegistryId,
                dictionaryCode,
                dictionaryFile,
                checksum,
                "FAILED",
                0
        );
    }

    private static void save(
            Long dictionaryRegistryId,
            String dictionaryCode,
            String dictionaryFile,
            String checksum,
            String status,
            int mappingCount
    ) {
        String sql = """
                INSERT INTO dictionary_import_history (
                    dictionary_registry_id,
                    dictionary_code,
                    dictionary_file,
                    file_checksum,
                    import_status,
                    imported_mapping_count,
                    metadata
                )
                VALUES (?, ?, ?, ?, ?, ?, jsonb_build_object('mappingSchemaVersion', ?))
                """;

        try (
                Connection conn = DatabaseConfig.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)
        ) {
            if (dictionaryRegistryId != null) {
                ps.setLong(1, dictionaryRegistryId);
            } else {
                ps.setObject(1, null);
            }

            ps.setString(2, dictionaryCode);
            ps.setString(3, dictionaryFile);
            ps.setString(4, checksum);
            ps.setString(5, status);
            ps.setInt(6, mappingCount);
            ps.setInt(7, MAPPING_SCHEMA_VERSION);

            ps.executeUpdate();

        } catch (Exception e) {
            System.err.println(
                    "[DICT IMPORT HISTORY SAVE ERROR] dictionary="
                            + dictionaryCode
                            + " status="
                            + status
                            + " error="
                            + e.getMessage()
            );
        }
    }
}
