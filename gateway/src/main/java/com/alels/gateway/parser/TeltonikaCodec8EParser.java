package com.alels.gateway.parser;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.alels.gateway.detector.ProtocolType;
import com.alels.gateway.model.TelemetryData;
import com.alels.gateway.util.ByteUtil;

public class TeltonikaCodec8EParser implements PacketParser {

    @Override
    public ParserResult parse(byte[] packet) {
        if (packet == null || packet.length < 15) {
            System.out.println("[CODEC8E] Invalid packet: too short");
            return new ParserResult(null, ProtocolType.TELTONIKA_CODEC8E, 0, false);
        }

        try {
            int codecId = packet[8] & 0xFF;

            if (codecId != 0x8E) {
                System.out.println("[CODEC8E] Invalid codec id: 0x" + String.format("%02X", codecId));
                return new ParserResult(null, ProtocolType.TELTONIKA_CODEC8E, 0, false);
            }

            int recordCount = packet[9] & 0xFF;
            List<TelemetryData> telemetryList = new ArrayList<>();

            System.out.println("========== TELTONIKA CODEC8E PARSER ==========");
            System.out.println("CODEC ID     : 0x8E");
            System.out.println("RECORD COUNT : " + recordCount);

            ByteUtil reader = new ByteUtil(packet, 10);

            for (int recordIndex = 1; recordIndex <= recordCount; recordIndex++) {
                long timestampMs = reader.readInt64();
                int priority = reader.readUInt8();

                int longitudeRaw = reader.readInt32();
                int latitudeRaw = reader.readInt32();

                int altitude = reader.readUInt16();
                int angle = reader.readUInt16();
                int satellites = reader.readUInt8();
                int speed = reader.readUInt16();

                int eventIoId = reader.readUInt16();
                int totalIo = reader.readUInt16();

                Map<String, Object> ioData = new LinkedHashMap<>();

                int n1 = reader.readUInt16();
                for (int i = 0; i < n1; i++) {
                    int ioId = reader.readUInt16();
                    int value = reader.readUInt8();
                    ioData.put(String.valueOf(ioId), value);
                }

                int n2 = reader.readUInt16();
                for (int i = 0; i < n2; i++) {
                    int ioId = reader.readUInt16();
                    int value = reader.readUInt16();
                    ioData.put(String.valueOf(ioId), value);
                }

                int n4 = reader.readUInt16();
                for (int i = 0; i < n4; i++) {
                    int ioId = reader.readUInt16();
                    long value = reader.readUInt32();
                    ioData.put(String.valueOf(ioId), value);
                }

                int n8 = reader.readUInt16();
                for (int i = 0; i < n8; i++) {
                    int ioId = reader.readUInt16();
                    long value = reader.readInt64();
                    ioData.put(String.valueOf(ioId), value);
                }

                int nx = reader.readUInt16();
                for (int i = 0; i < nx; i++) {
                    int ioId = reader.readUInt16();
                    int length = reader.readUInt16();
                    byte[] valueBytes = reader.readBytes(length);

                    StringBuilder hexValue = new StringBuilder();
                    for (byte b : valueBytes) {
                        hexValue.append(String.format("%02X", b));
                    }

                    ioData.put(String.valueOf(ioId), hexValue.toString());
                }

                double longitude = longitudeRaw / 10_000_000.0;
                double latitude = latitudeRaw / 10_000_000.0;

                LocalDateTime gpsTime = LocalDateTime.ofInstant(
                        Instant.ofEpochMilli(timestampMs),
                        ZoneOffset.UTC
                );

                TelemetryData telemetryData = new TelemetryData();
                telemetryData.setSourceProtocol(ProtocolType.TELTONIKA_CODEC8E);
                telemetryData.setDeviceTime(gpsTime.toString());
                telemetryData.setPacketSequence(recordIndex);
                telemetryData.setLongitude(longitude);
                telemetryData.setLatitude(latitude);
                telemetryData.setAltitude(altitude);
                telemetryData.setAngle(angle);
                telemetryData.setSatellites(satellites);
                telemetryData.setSpeed(speed);
                telemetryData.setPriority(priority);
                telemetryData.setEventIoId(eventIoId);
                telemetryData.setHdop(0.0);
                telemetryData.setIoData(ioData);

                telemetryList.add(telemetryData);

                System.out.println();
                System.out.println("RECORD #" + recordIndex);
                System.out.println("TIME        : " + gpsTime + " UTC");
                System.out.println("TIMESTAMP   : " + timestampMs);
                System.out.println("PRIORITY    : " + priority);
                System.out.println("LON RAW     : " + longitudeRaw);
                System.out.println("LAT RAW     : " + latitudeRaw);
                System.out.println("LON         : " + longitude);
                System.out.println("LAT         : " + latitude);
                System.out.println("ALTITUDE    : " + altitude);
                System.out.println("ANGLE       : " + angle);
                System.out.println("SATELLITES  : " + satellites);
                System.out.println("SPEED       : " + speed);
                System.out.println("EVENT IO    : " + eventIoId);
                System.out.println("TOTAL IO    : " + totalIo);
                System.out.println("N1          : " + n1);
                System.out.println("N2          : " + n2);
                System.out.println("N4          : " + n4);
                System.out.println("N8          : " + n8);
                System.out.println("NX          : " + nx);
                System.out.println("IO DATA     : " + ioData);
            }

            int recordCount2 = reader.readUInt8();

            long crc = 0;
            if (packet.length - reader.position() >= 4) {
                crc = reader.readUInt32();
            }

            System.out.println();
            System.out.println("RECORD COUNT 2 : " + recordCount2);
            System.out.println("CRC            : " + String.format("0x%08X", crc));
            System.out.println("END POSITION   : " + reader.position() + " / " + packet.length);
            System.out.println("=============================================");

            boolean valid = recordCount == recordCount2;

            if (!valid) {
                System.out.println("[CODEC8E] WARNING: record count mismatch. count1="
                        + recordCount + " count2=" + recordCount2);
            }

            return new ParserResult(
                    null,
                    ProtocolType.TELTONIKA_CODEC8E,
                    recordCount,
                    valid,
                    telemetryList
            );

        } catch (Exception e) {
            System.err.println("[CODEC8E PARSER ERROR] " + e.getMessage());
            return new ParserResult(null, ProtocolType.TELTONIKA_CODEC8E, 0, false);
        }
    }
}