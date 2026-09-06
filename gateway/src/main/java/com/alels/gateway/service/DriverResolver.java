package com.alels.gateway.service;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Map;

import com.alels.gateway.config.DatabaseConfig;
import com.alels.gateway.model.DriverInfo;

public class DriverResolver {

    private static final String AVL_IBUTTON = "78";

    public static DriverInfo resolve(
            Long companyId,
            Map<String, Object> ioData
    ) {

        if (companyId == null) {
            return null;
        }

        if (ioData == null || ioData.isEmpty()) {
            return null;
        }

        Object value = ioData.get(AVL_IBUTTON);

        if (value == null) {
            return null;
        }

        String rfid =
                String.valueOf(value).trim();

        if (rfid.isBlank()
                || rfid.equals("0")
                || rfid.equals("0x0000000000000000")) {

            return null;
        }

        String sql = """
                SELECT
                    d.id,
                    d.driver_name,
                    a.rfid_id
                FROM driver_rfid_assignments a
                JOIN drivers d
                    ON d.id = a.driver_id
                WHERE a.company_id = ?
                AND a.rfid_id = ?
                AND a.status = 'ACTIVE'
                LIMIT 1
                """;

        try (
                Connection conn =
                        DatabaseConfig.getConnection();

                PreparedStatement stmt =
                        conn.prepareStatement(sql)
        ) {

            stmt.setLong(1, companyId);
            stmt.setString(2, rfid);

            try (ResultSet rs = stmt.executeQuery()) {

                if (rs.next()) {

                    DriverInfo info =
                            new DriverInfo();

                    info.setDriverId(
                            rs.getLong("id")
                    );

                    info.setDriverName(
                            rs.getString("driver_name")
                    );

                    info.setDriverRfid(
                            rs.getString("rfid_id")
                    );

                    System.out.println(
                            "[DRIVER RESOLVER] companyId="
                                    + companyId
                                    + " rfid="
                                    + rfid
                                    + " driver="
                                    + info.getDriverName()
                    );

                    return info;
                }
            }

            System.out.println(
                    "[DRIVER RESOLVER] driver not found companyId="
                            + companyId
                            + " rfid="
                            + rfid
            );

        } catch (Exception e) {

            System.err.println(
                    "[DRIVER RESOLVER ERROR] "
                            + e.getMessage()
            );
        }

        return null;
    }
}
