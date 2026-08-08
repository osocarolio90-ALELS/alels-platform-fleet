package com.alels.gateway.parser;

import java.nio.charset.StandardCharsets;

import com.alels.gateway.detector.ProtocolType;
import com.alels.gateway.util.TeltonikaCrc16;

public class TeltonikaCodec12ResponseParser implements PacketParser {

    @Override
    public ParserResult parse(byte[] packet) {
        if (packet == null || packet.length < 16) {
            return new ParserResult(null, ProtocolType.TELTONIKA_CODEC12_RESPONSE, 0, false);
        }

        try {
            int index = 0;

            long preamble = readUInt32(packet, index);
            index += 4;

            long dataLength = readUInt32(packet, index);
            index += 4;
            if (dataLength <= 0 || dataLength > 1_048_576 || packet.length != dataLength + 12) {
                return new ParserResult(null, ProtocolType.TELTONIKA_CODEC12_RESPONSE, 0, false);
            }

            int codecId = readUInt8(packet, index);
            index += 1;

            int recordCount1 = readUInt8(packet, index);
            index += 1;

            int responseType = readUInt8(packet, index);
            index += 1;

            long responseSize = readUInt32(packet, index);
            index += 4;

            if (responseSize < 0 || index + responseSize > packet.length) {
                return new ParserResult(null, ProtocolType.TELTONIKA_CODEC12_RESPONSE, 0, false);
            }

            byte[] responseBytes = new byte[(int) responseSize];
            System.arraycopy(packet, index, responseBytes, 0, (int) responseSize);
            index += (int) responseSize;

            int recordCount2 = readUInt8(packet, index);
            index += 1;

            long crc = readUInt32(packet, index);
            index += 4;

            boolean valid =
                    preamble == 0
                            && codecId == 0x0C
                            && responseType == 0x06
                            && recordCount1 == recordCount2
                            && recordCount1 > 0
                            && index == packet.length
                            && (crc & 0xFFFF) == TeltonikaCrc16.calculate(packet, 8, (int) dataLength);

            return new ParserResult(
                    null,
                    ProtocolType.TELTONIKA_CODEC12_RESPONSE,
                    recordCount1,
                    valid
            );

        } catch (Exception e) {
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
