package com.alels.gateway.repository;

import com.alels.gateway.config.DatabaseConfig;
import com.alels.gateway.model.ProtocolInfo;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class ProtocolRegistryRepository {

    public ProtocolInfo findByProtocolCode(String protocolCode) {

        if (protocolCode == null || protocolCode.isBlank()) {
            return null;
        }

        String sql = """
                SELECT
                    id,
                    protocol_code,
                    protocol_name,
                    brand_code,
                    protocol_family,
                    detector_code,
                    parser_code,
                    transport_type,
                    direction,
                    status
                FROM protocol_registry
                WHERE protocol_code = ?
                LIMIT 1
                """;

        try (
                Connection conn = DatabaseConfig.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)
        ) {
            stmt.setString(1, protocolCode.trim());

            try (ResultSet rs = stmt.executeQuery()) {

                if (rs.next()) {
                    ProtocolInfo info = new ProtocolInfo();

                    info.setId(rs.getLong("id"));
                    info.setProtocolCode(rs.getString("protocol_code"));
                    info.setProtocolName(rs.getString("protocol_name"));
                    info.setBrandCode(rs.getString("brand_code"));
                    info.setProtocolFamily(rs.getString("protocol_family"));
                    info.setDetectorCode(rs.getString("detector_code"));
                    info.setParserCode(rs.getString("parser_code"));
                    info.setTransportType(rs.getString("transport_type"));
                    info.setDirection(rs.getString("direction"));
                    info.setStatus(rs.getString("status"));

                    return info;
                }
            }

        } catch (Exception e) {
            System.err.println(
                    "[PROTOCOL REGISTRY REPOSITORY ERROR] protocol="
                            + protocolCode
                            + " error="
                            + e.getMessage()
            );
        }

        return null;
    }
}