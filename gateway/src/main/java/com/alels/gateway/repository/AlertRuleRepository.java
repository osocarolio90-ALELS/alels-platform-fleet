package com.alels.gateway.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

import com.alels.gateway.config.DatabaseConfig;
import com.alels.gateway.model.AlertRule;

public class AlertRuleRepository {

    public static List<AlertRule> findMatchingRules(
            Long companyId,
            Long deviceId,
            String imei,
            String dictionaryCode,
            String protocol
    ) {
        String sql = """
                SELECT
                    id,
                    company_id,
                    device_id,
                    imei,
                    dictionary_code,
                    protocol,
                    io_id,
                    alert_type,
                    title,
                    severity,
                    condition_operator,
                    threshold_value,
                    match_value
                FROM alert_rules
                WHERE enabled = TRUE
                  AND (company_id IS NULL OR company_id = ?)
                  AND (device_id IS NULL OR device_id = ?)
                  AND (imei IS NULL OR imei = ?)
                  AND (dictionary_code IS NULL OR LOWER(dictionary_code) = LOWER(?))
                  AND (protocol IS NULL OR LOWER(protocol) = LOWER(?))
                ORDER BY
                    CASE WHEN company_id IS NULL THEN 1 ELSE 0 END,
                    CASE WHEN device_id IS NULL THEN 1 ELSE 0 END,
                    id ASC
                """;

        List<AlertRule> rules =
                new ArrayList<>();

        try (
                Connection conn = DatabaseConfig.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)
        ) {
            setLongOrNull(stmt, 1, companyId);
            setLongOrNull(stmt, 2, deviceId);
            stmt.setString(3, imei);
            stmt.setString(4, dictionaryCode);
            stmt.setString(5, protocol);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    rules.add(map(rs));
                }
            }

        } catch (Exception e) {
            System.err.println("[ALERT RULE ERROR] " + e.getMessage());
        }

        return rules;
    }

    private static AlertRule map(ResultSet rs) throws Exception {
        AlertRule rule =
                new AlertRule();

        rule.setId(rs.getLong("id"));

        long companyId = rs.getLong("company_id");
        rule.setCompanyId(rs.wasNull() ? null : companyId);

        long deviceId = rs.getLong("device_id");
        rule.setDeviceId(rs.wasNull() ? null : deviceId);

        rule.setImei(rs.getString("imei"));
        rule.setDictionaryCode(rs.getString("dictionary_code"));
        rule.setProtocol(rs.getString("protocol"));
        rule.setIoId(rs.getString("io_id"));
        rule.setAlertType(rs.getString("alert_type"));
        rule.setTitle(rs.getString("title"));
        rule.setSeverity(rs.getString("severity"));
        rule.setConditionOperator(rs.getString("condition_operator"));

        double threshold = rs.getDouble("threshold_value");
        rule.setThresholdValue(rs.wasNull() ? null : threshold);

        rule.setMatchValue(rs.getString("match_value"));

        return rule;
    }

    private static void setLongOrNull(
            PreparedStatement stmt,
            int index,
            Long value
    ) throws Exception {
        if (value == null) {
            stmt.setNull(index, java.sql.Types.BIGINT);
        } else {
            stmt.setLong(index, value);
        }
    }
}
