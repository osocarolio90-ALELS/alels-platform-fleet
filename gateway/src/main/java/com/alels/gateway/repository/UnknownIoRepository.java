package com.alels.gateway.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;

import com.alels.gateway.config.DatabaseConfig;

public class UnknownIoRepository {

    private UnknownIoRepository() {
    }

    public static void upsert(
            String imei,
            Long deviceModelId,
            String dictionaryCode,
            String sourceProtocol,
            String sourceIoId,
            Object rawValue
    ) {
        if (sourceProtocol == null || sourceIoId == null) {
            return;
        }

        String sql = """
                INSERT INTO unknown_io_registry (
                    imei,
                    device_model_id,
                    dictionary_code,
                    source_protocol,
                    source_io_id,
                    raw_value
                )
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT (
                    COALESCE(device_model_id, 0),
                    source_protocol,
                    source_io_id
                )
                DO UPDATE SET
                    imei = EXCLUDED.imei,
                    dictionary_code = EXCLUDED.dictionary_code,
                    raw_value = EXCLUDED.raw_value,
                    last_seen_at = NOW(),
                    seen_count = unknown_io_registry.seen_count + 1
                """;

        try (
                Connection conn = DatabaseConfig.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)
        ) {
            ps.setString(1, imei);

            if (deviceModelId != null) {
                ps.setLong(2, deviceModelId);
            } else {
                ps.setObject(2, null);
            }

            ps.setString(3, dictionaryCode);
            ps.setString(4, sourceProtocol);
            ps.setString(5, sourceIoId);
            ps.setString(6, rawValue != null ? String.valueOf(rawValue) : null);

            ps.executeUpdate();

        } catch (Exception e) {
            System.err.println(
                    "[UNKNOWN IO ERROR] protocol="
                            + sourceProtocol
                            + " io="
                            + sourceIoId
                            + " error="
                            + e.getMessage()
            );
        }
    }
}