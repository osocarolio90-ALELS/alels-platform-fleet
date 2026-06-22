package com.alels.gateway.command;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

public class TeltonikaCodec12CommandBuilder {

    private static final int CODEC_ID = 0x0C;
    private static final int COMMAND_TYPE = 0x05;
    private static final int RECORD_COUNT = 0x01;

    public static byte[] build(String command) {
        if (command == null || command.isBlank()) {
            throw new IllegalArgumentException("Command cannot be empty");
        }

        String normalizedCommand = normalizeCommand(command);
        byte[] commandBytes = normalizedCommand.getBytes(StandardCharsets.US_ASCII);

        int dataSize =
                1 + // codec id
                1 + // record count 1
                1 + // command type
                4 + // command size
                commandBytes.length +
                1;  // record count 2

        byte[] avlData = new byte[dataSize];

        int index = 0;

        avlData[index++] = (byte) CODEC_ID;
        avlData[index++] = (byte) RECORD_COUNT;
        avlData[index++] = (byte) COMMAND_TYPE;

        writeInt32(avlData, index, commandBytes.length);
        index += 4;

        System.arraycopy(commandBytes, 0, avlData, index, commandBytes.length);
        index += commandBytes.length;

        avlData[index] = (byte) RECORD_COUNT;

        int crc = crc16Ibm(avlData);

        byte[] packet = new byte[4 + 4 + avlData.length + 4];

        index = 0;

        // preamble
        writeInt32(packet, index, 0);
        index += 4;

        // data field length
        writeInt32(packet, index, avlData.length);
        index += 4;

        // AVL data
        System.arraycopy(avlData, 0, packet, index, avlData.length);
        index += avlData.length;

        // CRC
        writeInt32(packet, index, crc);

        return packet;
    }

    public static String buildHex(String command) {
        return bytesToHex(build(command));
    }

    private static String normalizeCommand(String command) {
        return command.trim().toLowerCase(Locale.ROOT);
    }

    private static void writeInt32(byte[] data, int index, int value) {
        data[index] = (byte) ((value >> 24) & 0xFF);
        data[index + 1] = (byte) ((value >> 16) & 0xFF);
        data[index + 2] = (byte) ((value >> 8) & 0xFF);
        data[index + 3] = (byte) (value & 0xFF);
    }

    private static int crc16Ibm(byte[] data) {
        int crc = 0x0000;

        for (byte b : data) {
            crc ^= (b & 0xFF);

            for (int i = 0; i < 8; i++) {
                if ((crc & 0x0001) != 0) {
                    crc = (crc >> 1) ^ 0xA001;
                } else {
                    crc >>= 1;
                }
            }
        }

        return crc & 0xFFFF;
    }

    private static String bytesToHex(byte[] data) {
        StringBuilder sb = new StringBuilder();

        for (byte b : data) {
            sb.append(String.format("%02X", b));
        }

        return sb.toString();
    }
}