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
import com.alels.gateway.util.TeltonikaCrc16;

public class TeltonikaCodec8Parser implements PacketParser {

    @Override
    public ParserResult parse(byte[] packet) {
        if (packet == null || packet.length < 15) {
            return new ParserResult(null, ProtocolType.TELTONIKA_CODEC8, 0, false);
        }

        try {
            int declaredDataLength = java.nio.ByteBuffer.wrap(packet, 4, 4).getInt();
            if (declaredDataLength <= 0 || declaredDataLength > 1_048_576
                    || packet.length != declaredDataLength + 12) {
                return new ParserResult(null, ProtocolType.TELTONIKA_CODEC8, 0, false);
            }
            int codecId = packet[8] & 0xFF;

            if (codecId != 0x08) {
                return new ParserResult(null, ProtocolType.TELTONIKA_CODEC8, 0, false);
            }

            int recordCount = packet[9] & 0xFF;
            List<TelemetryData> telemetryList = new ArrayList<>();
            boolean ioCountsValid = true;

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

                int eventIoId = reader.readUInt8();
                int totalIo = reader.readUInt8();

                Map<String, Object> ioData = new LinkedHashMap<>();

                int n1 = reader.readUInt8();
                for (int i = 0; i < n1; i++) {
                    int ioId = reader.readUInt8();
                    int value = reader.readUInt8();
                    ioData.put(String.valueOf(ioId), value);
                }

                int n2 = reader.readUInt8();
                for (int i = 0; i < n2; i++) {
                    int ioId = reader.readUInt8();
                    int value = reader.readUInt16();
                    ioData.put(String.valueOf(ioId), value);
                }

                int n4 = reader.readUInt8();
                for (int i = 0; i < n4; i++) {
                    int ioId = reader.readUInt8();
                    long value = reader.readUInt32();
                    ioData.put(String.valueOf(ioId), value);
                }

                int n8 = reader.readUInt8();
                for (int i = 0; i < n8; i++) {
                    int ioId = reader.readUInt8();
                    long value = reader.readInt64();
                    ioData.put(String.valueOf(ioId), value);
                }

                ioCountsValid &= totalIo == n1 + n2 + n4 + n8;

                double longitude = longitudeRaw / 10_000_000.0;
                double latitude = latitudeRaw / 10_000_000.0;

                LocalDateTime gpsTime = LocalDateTime.ofInstant(
                        Instant.ofEpochMilli(timestampMs),
                        ZoneOffset.UTC
                );

                TelemetryData telemetryData = new TelemetryData();
                telemetryData.setSourceProtocol(ProtocolType.TELTONIKA_CODEC8);
                telemetryData.setDeviceTime(Instant.ofEpochMilli(timestampMs).toString());
                telemetryData.setPacketSequence(timestampMs * 256L + recordIndex);
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

            }

            int recordCount2 = reader.readUInt8();

            long crc = 0;
            if (packet.length - reader.position() >= 4) {
                crc = reader.readUInt32();
            }

            int expectedCrc = (int) (crc & 0xFFFF);
            int actualCrc = TeltonikaCrc16.calculate(packet, 8, declaredDataLength);
            boolean valid = recordCount == recordCount2
                    && reader.position() == packet.length
                    && ioCountsValid
                    && expectedCrc == actualCrc;

            return new ParserResult(
                    null,
                    ProtocolType.TELTONIKA_CODEC8,
                    recordCount,
                    valid,
                    telemetryList
            );

        } catch (Exception e) {
            System.err.println("[CODEC8 PARSER ERROR] " + e.getMessage());
            return new ParserResult(null, ProtocolType.TELTONIKA_CODEC8, 0, false);
        }
    }
}
