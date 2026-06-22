package com.alels.gateway.normalizer;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import com.alels.gateway.detector.ProtocolType;
import com.alels.gateway.model.TelemetryData;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class AlelsJsonTelemetryNormalizer {

    private static final ObjectMapper mapper = new ObjectMapper();

    public static TelemetryData normalize(byte[] packet) throws Exception {
        JsonNode node = mapper.readTree(packet);

        String type = getText(node, "T");
        if (!"data".equalsIgnoreCase(type)) {
            return null;
        }

        TelemetryData data = new TelemetryData();

        data.setSourceProtocol(ProtocolType.ALELS_JSON);
        data.setImei(getText(node, "A"));
        data.setPacketSequence(getLong(node, "Q"));
        data.setDeviceTime(getText(node, "B"));

        // ALELS JSON memakai skala Teltonika 1e7
        data.setLongitude(getDouble(node, "D") / 10_000_000.0);
        data.setLatitude(getDouble(node, "E") / 10_000_000.0);

        data.setHdop(getDouble(node, "C"));
        data.setAltitude(getInt(node, "F"));
        data.setAngle(getInt(node, "G"));
        data.setSatellites(getInt(node, "H"));
        data.setSpeed(getInt(node, "I"));
        data.setPriority(getInt(node, "J"));
        data.setEventIoId(getInt(node, "K"));

        Map<String, Object> io = new LinkedHashMap<>();

        Iterator<String> fields = node.fieldNames();
        while (fields.hasNext()) {
            String key = fields.next();

            if (isReservedKey(key)) {
                continue;
            }

            JsonNode value = node.get(key);
            if (value.isNumber()) {
                io.put(key, value.numberValue());
            } else if (value.isBoolean()) {
                io.put(key, value.asBoolean());
            } else {
                io.put(key, value.asText());
            }
        }

        data.setIoData(io);

        return data;
    }

    private static boolean isReservedKey(String key) {
        return key.equals("T")
                || key.equals("Q")
                || key.equals("A")
                || key.equals("B")
                || key.equals("C")
                || key.equals("D")
                || key.equals("E")
                || key.equals("F")
                || key.equals("G")
                || key.equals("H")
                || key.equals("I")
                || key.equals("J")
                || key.equals("K")
                || key.equals("S");
    }

    private static String getText(JsonNode node, String key) {
        return node.has(key) ? node.get(key).asText() : "";
    }

    private static int getInt(JsonNode node, String key) {
        return node.has(key) ? node.get(key).asInt() : 0;
    }

    private static long getLong(JsonNode node, String key) {
        return node.has(key) ? node.get(key).asLong() : 0;
    }

    private static double getDouble(JsonNode node, String key) {
        return node.has(key) ? node.get(key).asDouble() : 0.0;
    }
}