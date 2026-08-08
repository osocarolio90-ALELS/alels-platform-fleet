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

        return new ParserResult(imei, ProtocolType.ALELS_JSON, 1, valid);
    }

    private String getText(JsonNode node, String key) {
        return node.has(key) ? node.get(key).asText() : "";
    }
}
