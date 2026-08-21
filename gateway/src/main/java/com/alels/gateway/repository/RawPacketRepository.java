package com.alels.gateway.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import com.alels.gateway.config.DatabaseConfig;

public class RawPacketRepository {

    public static Long insert(
            String imei,
            String protocol,
            String channel,
            String remoteAddress,
            String direction,
            String rawJson,
            String rawHex,
            Integer bytesCount
    ) {
        String sql = """
                INSERT INTO raw_packets (
                    imei,
                    protocol,
                    channel,
                    remote_address,
                    direction,
                    raw_json,
                    raw_hex,
                    bytes_count
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                RETURNING id
                """;

        try (
                Connection conn = DatabaseConfig.getConnection();
                PreparedStatement stmt = conn.prepareStatement(sql)
        ) {
            stmt.setString(1, imei);
            stmt.setString(2, protocol);
            stmt.setString(3, channel);
            stmt.setString(4, remoteAddress);
            stmt.setString(5, direction);
            stmt.setString(6, rawJson);
            stmt.setString(7, rawHex);

            if (bytesCount == null) {
                stmt.setNull(8, java.sql.Types.INTEGER);
            } else {
                stmt.setInt(8, bytesCount);
            }

            try (ResultSet result = stmt.executeQuery()) {
                if (result.next()) return result.getLong("id");
            }

        } catch (Exception e) {
            throw new IllegalStateException("Raw packet persistence failed", e);
        }
        throw new IllegalStateException("Raw packet persistence did not return an id");
    }
}
