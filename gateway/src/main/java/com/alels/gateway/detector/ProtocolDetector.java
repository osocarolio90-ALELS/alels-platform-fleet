package com.alels.gateway.detector;

public class ProtocolDetector {

    public static ProtocolType detect(byte[] packet) {
        if (packet == null || packet.length == 0) return ProtocolType.UNKNOWN;

        // ALELS JSON: starts with '{'
        if (packet[0] == '{') {
            return ProtocolType.ALELS_JSON;
        }

        // Teltonika IMEI handshake:
        // 2 bytes length + ASCII IMEI, usually 15 digits.
        if (packet.length >= 17 && packet[0] == 0x00 && packet[1] == 0x0F) {
            return ProtocolType.TELTONIKA_IMEI;
        }

        // Teltonika AVL:
        // 4 bytes preamble + 4 bytes data length + codec id at index 8.
        if (packet.length > 8) {
            int codecId = packet[8] & 0xFF;
            if (codecId == 0x08) return ProtocolType.TELTONIKA_CODEC8;
            if (codecId == 0x8E) return ProtocolType.TELTONIKA_CODEC8E;
            if (codecId == 0x0C) return ProtocolType.TELTONIKA_CODEC12_RESPONSE;
        }

        return ProtocolType.UNKNOWN;
    }
}
