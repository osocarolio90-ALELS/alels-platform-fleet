package com.alels.gateway.repository;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import com.alels.gateway.config.DatabaseConfig;
import com.alels.gateway.detector.ProtocolType;
import com.alels.gateway.util.HexUtil;

/** Persists the exact inbound frame and its transport event in one transaction. */
public final class PacketAuditRepository {
    private PacketAuditRepository() {}

    public static Long insertReceived(String imei, ProtocolType protocol, String transport,
                                      byte[] packet, String remoteAddress) {
        return insert(imei, protocol, transport, packet, remoteAddress, "IN", "PACKET_RECEIVED");
    }

    public static Long insertSent(String imei, ProtocolType protocol, String transport,
                                  byte[] packet, String remoteAddress) {
        return insert(imei, protocol, transport, packet, remoteAddress, "OUT", "PACKET_SENT");
    }

    private static Long insert(String imei, ProtocolType protocol, String transport, byte[] packet,
                               String remoteAddress, String direction, String eventType) {
        if (imei == null || imei.isBlank()) throw new IllegalArgumentException("Packet IMEI is required");
        if (protocol == null || protocol == ProtocolType.UNKNOWN) throw new IllegalArgumentException("Packet protocol is required");
        if (packet == null || packet.length == 0) throw new IllegalArgumentException("Packet bytes are required");
        String text = new String(packet, StandardCharsets.UTF_8).trim();
        String rawJson = protocol == ProtocolType.ALELS_JSON && text.startsWith("{") ? text : null;
        String rawHex = rawJson == null ? HexUtil.toHex(packet) : null;
        String rawText = printableText(packet);

        try (Connection connection = DatabaseConfig.getConnection()) {
            connection.setAutoCommit(false);
            try {
                Long rawPacketId;
                try (PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO raw_packets
                            (imei, protocol, channel, remote_address, direction, raw_json, raw_hex, payload,
                             bytes_count, parse_status, received_at, created_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'RECEIVED', NOW(), NOW())
                        RETURNING id
                        """)) {
                    statement.setString(1, imei);
                    statement.setString(2, protocol.name());
                    statement.setString(3, transport);
                    statement.setString(4, remoteAddress);
                    statement.setString(5, direction);
                    statement.setString(6, rawJson);
                    statement.setString(7, rawHex);
                    statement.setString(8, rawText);
                    statement.setInt(9, packet.length);
                    try (ResultSet result = statement.executeQuery()) {
                        if (!result.next()) throw new IllegalStateException("Raw packet insert returned no id");
                        rawPacketId = result.getLong(1);
                    }
                }
                try (PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO tcp_logs
                            (imei, remote_address, channel, protocol, event_type, message, bytes_in, bytes_out)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        """)) {
                    statement.setString(1, imei);
                    statement.setString(2, remoteAddress);
                    statement.setString(3, transport);
                    statement.setString(4, protocol.name());
                    statement.setString(5, eventType);
                    statement.setString(6, direction.equals("OUT") ? "Packet sent to device" : "Packet received from device");
                    if (direction.equals("OUT")) statement.setNull(7, java.sql.Types.INTEGER); else statement.setInt(7, packet.length);
                    if (direction.equals("OUT")) statement.setInt(8, packet.length); else statement.setNull(8, java.sql.Types.INTEGER);
                    statement.executeUpdate();
                }
                connection.commit();
                return rawPacketId;
            } catch (Exception error) {
                connection.rollback();
                throw error;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (Exception error) {
            throw new IllegalStateException("Packet audit persistence failed", error);
        }
    }

    private static String printableText(byte[] packet) {
        String text = new String(packet, StandardCharsets.UTF_8);
        for (int index = 0; index < text.length(); index++) {
            char value = text.charAt(index);
            if (value == '\r' || value == '\n' || value == '\t') continue;
            if (Character.isISOControl(value) || value == '\uFFFD') return null;
        }
        return text;
    }
}
