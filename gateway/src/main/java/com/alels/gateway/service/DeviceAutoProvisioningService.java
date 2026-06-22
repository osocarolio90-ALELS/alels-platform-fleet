package com.alels.gateway.service;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import com.alels.gateway.config.DatabaseConfig;

public class DeviceAutoProvisioningService {

    private static final long DEFAULT_COMPANY_ID = 1L;

    private DeviceAutoProvisioningService() {
    }

    public static void autoProvision(
            String imei,
            String protocol
    ) {

        if (imei == null || imei.isBlank()) {
            return;
        }

        try (
                Connection conn =
                        DatabaseConfig.getConnection()
        ) {

            Long modelId =
                    resolveDefaultModelId(
                            conn,
                            protocol
                    );

            String modelCode =
                    resolveModelCode(
                            conn,
                            modelId
                    );

            String sql =
                    """
                    INSERT INTO devices(
                        imei,
                        company_id,
                        device_model_id,
                        device_model,
                        presence_status,
                        online
                    )
                    VALUES(
                        ?, ?, ?, ?, 'OFFLINE', false
                    )
                    ON CONFLICT (imei)
                    DO NOTHING
                    """;

            try (
                    PreparedStatement ps =
                            conn.prepareStatement(sql)
            ) {

                ps.setString(
                        1,
                        imei
                );

                ps.setLong(
                        2,
                        DEFAULT_COMPANY_ID
                );

                ps.setLong(
                        3,
                        modelId
                );

                ps.setString(
                        4,
                        modelCode
                );

                ps.executeUpdate();
            }

            System.out.println(
                    "[AUTO PROVISION] imei="
                            + imei
                            + " protocol="
                            + protocol
                            + " model="
                            + modelCode
            );

        } catch (Exception e) {

            System.err.println(
                    "[AUTO PROVISION ERROR] "
                            + e.getMessage()
            );
        }
    }

    private static Long resolveDefaultModelId(
            Connection conn,
            String protocol
    ) throws Exception {

        String modelCode;

        if (protocol != null
                && protocol.startsWith("ALELS")) {

            modelCode =
                    "ALELS_WIFI_HUB";

        } else {

            modelCode =
                    "FMC650";
        }

        try (
                PreparedStatement ps =
                        conn.prepareStatement(
                                """
                                SELECT id
                                FROM device_models
                                WHERE model_code=?
                                """
                        )
        ) {

            ps.setString(
                    1,
                    modelCode
            );

            try (
                    ResultSet rs =
                            ps.executeQuery()
            ) {

                if (rs.next()) {
                    return rs.getLong(
                            "id"
                    );
                }
            }
        }

        throw new RuntimeException(
                "Default model not found: "
                        + modelCode
        );
    }

    private static String resolveModelCode(
            Connection conn,
            Long modelId
    ) throws Exception {

        try (
                PreparedStatement ps =
                        conn.prepareStatement(
                                """
                                SELECT model_code
                                FROM device_models
                                WHERE id=?
                                """
                        )
        ) {

            ps.setLong(
                    1,
                    modelId
            );

            try (
                    ResultSet rs =
                            ps.executeQuery()
            ) {

                if (rs.next()) {

                    return rs.getString(
                            "model_code"
                    );
                }
            }
        }

        return "UNKNOWN";
    }
}