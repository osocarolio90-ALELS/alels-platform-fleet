package com.alels.gateway.admission.repository;

import com.alels.gateway.admission.model.DeviceAdmission;
import com.alels.gateway.config.DatabaseConfig;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.Map;

public final class DeviceAdmissionRepository {

    private static final String SQL = """
            SELECT d.imei,
                   COALESCE(d.tcp_enabled, TRUE)
                       AND UPPER(COALESCE(s.receive_status, 'ACTIVE')) <> 'SUSPENDED'
                       AS receive_allowed,
                   COALESCE(NULLIF(TRIM(dm.dictionary_code), ''), 'fmc650')
                       AS dictionary_code
            FROM devices d
            LEFT JOIN device_receive_status s ON s.imei = d.imei
            LEFT JOIN device_models dm
                   ON dm.id = d.device_model_id
                  AND COALESCE(dm.status, 'ACTIVE') = 'ACTIVE'
            WHERE d.imei IS NOT NULL
              AND TRIM(d.imei) <> ''
            """;

    public Map<String, DeviceAdmission> loadAll() throws Exception {
        Map<String, DeviceAdmission> admissions = new HashMap<>();

        try (Connection connection = DatabaseConfig.getConnection();
             PreparedStatement statement = connection.prepareStatement(SQL);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                String imei = resultSet.getString("imei").trim();
                admissions.put(imei, new DeviceAdmission(
                        imei,
                        resultSet.getBoolean("receive_allowed"),
                        resultSet.getString("dictionary_code")
                ));
            }
        }

        return admissions;
    }
}
