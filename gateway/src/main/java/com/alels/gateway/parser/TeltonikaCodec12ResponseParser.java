package com.alels.gateway.parser;

import java.nio.charset.StandardCharsets;

import com.alels.gateway.detector.ProtocolType;

public class TeltonikaCodec12ResponseParser implements PacketParser {

    @Override
    public ParserResult parse(byte[] packet) {
        if (packet == null || packet.length < 16) {
            System.out.println("[CODEC12 RESPONSE] Invalid packet length");
            return new ParserResult(null, ProtocolType.TELTONIKA_CODEC12_RESPONSE, 0, false);
        }

        try {
            int index = 0;

            long preamble = readUInt32(packet, index);
            index += 4;

            long dataLength = readUInt32(packet, index);
            index += 4;

            int codecId = readUInt8(packet, index);
            index += 1;

            int recordCount1 = readUInt8(packet, index);
            index += 1;

            int responseType = readUInt8(packet, index);
            index += 1;

            long responseSize = readUInt32(packet, index);
            index += 4;

            if (responseSize < 0 || index + responseSize > packet.length) {
                System.out.println("[CODEC12 RESPONSE] Invalid response size=" + responseSize);
                return new ParserResult(null, ProtocolType.TELTONIKA_CODEC12_RESPONSE, 0, false);
            }

            byte[] responseBytes = new byte[(int) responseSize];
            System.arraycopy(packet, index, responseBytes, 0, (int) responseSize);
            index += (int) responseSize;

            String responseText = new String(responseBytes, StandardCharsets.US_ASCII);

            int recordCount2 = readUInt8(packet, index);
            index += 1;

            long crc = readUInt32(packet, index);
            index += 4;

            boolean valid =
                    preamble == 0
                            && codecId == 0x0C
                            && recordCount1 == recordCount2
                            && recordCount1 > 0;

            System.out.println("========== TELTONIKA CODEC12 RESPONSE ==========");
            System.out.println("PREAMBLE      : " + preamble);
            System.out.println("DATA LENGTH   : " + dataLength);
            System.out.println("CODEC ID      : 0x" + String.format("%02X", codecId));
            System.out.println("RECORD COUNT  : " + recordCount1);
            System.out.println("RESPONSE TYPE : 0x" + String.format("%02X", responseType));
            System.out.println("RESPONSE SIZE : " + responseSize);
            System.out.println("RESPONSE TEXT : " + responseText);
            System.out.println("RECORD COUNT2 : " + recordCount2);
            System.out.println("CRC           : 0x" + String.format("%08X", crc));
            System.out.println("END POSITION  : " + index + " / " + packet.length);
            System.out.println("VALID         : " + valid);
            System.out.println("===============================================");

            return new ParserResult(
                    null,
                    ProtocolType.TELTONIKA_CODEC12_RESPONSE,
                    recordCount1,
                    valid
            );

        } catch (Exception e) {
            System.out.println("[CODEC12 RESPONSE] Parse error: " + e.getMessage());
            return new ParserResult(null, ProtocolType.TELTONIKA_CODEC12_RESPONSE, 0, false);
        }
    }

    private int readUInt8(byte[] data, int index) {
        return data[index] & 0xFF;
    }

    private long readUInt32(byte[] data, int index) {
        return ((long) (data[index] & 0xFF) << 24)
                | ((long) (data[index + 1] & 0xFF) << 16)
                | ((long) (data[index + 2] & 0xFF) << 8)
                | ((long) (data[index + 3] & 0xFF));
    }
}