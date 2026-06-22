package com.alels.gateway.parser;

import com.alels.gateway.detector.ProtocolType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class AlelsJsonParser implements PacketParser {
    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public ParserResult parse(byte[] packet) throws Exception {
        JsonNode node = mapper.readTree(packet);

        String type = getText(node, "T");

        String imei = "";
        if (node.has("A")) {
            imei = node.get("A").asText();
        } else if (node.has("imei")) {
            imei = node.get("imei").asText();
        }

        boolean valid = !type.isBlank();

        System.out.println("========== ALELS JSON PARSER ==========");
        System.out.println("T     : " + type);
        System.out.println("IMEI  : " + imei);

        if ("data".equalsIgnoreCase(type)) {
            System.out.println("Q     : " + getText(node, "Q"));
            System.out.println("TIME  : " + getText(node, "B"));
            System.out.println("HDOP  : " + getText(node, "C"));
            System.out.println("LON   : " + getText(node, "D"));
            System.out.println("LAT   : " + getText(node, "E"));
            System.out.println("ALT   : " + getText(node, "F"));
            System.out.println("ANGLE : " + getText(node, "G"));
            System.out.println("SAT   : " + getText(node, "H"));
            System.out.println("SPEED : " + getText(node, "I"));
            System.out.println("PRIO  : " + getText(node, "J"));
            System.out.println("EVENT : " + getText(node, "K"));
            System.out.println("SIZE  : " + getText(node, "S"));
        }

        if ("resp".equalsIgnoreCase(type)) {
            System.out.println("CMD   : " + getText(node, "cmd"));
            System.out.println("STATUS: " + getText(node, "status"));
            System.out.println("MSG   : " + getText(node, "message"));
        }

        System.out.println("=======================================");

        return new ParserResult(imei, ProtocolType.ALELS_JSON, 1, valid);
    }

    private String getText(JsonNode node, String key) {
        return node.has(key) ? node.get(key).asText() : "";
    }
}