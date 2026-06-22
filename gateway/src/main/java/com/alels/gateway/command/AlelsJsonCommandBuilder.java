package com.alels.gateway.command;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class AlelsJsonCommandBuilder {

    private static final ObjectMapper mapper = new ObjectMapper();

    public static String build(String imei, String command) {
        ObjectNode node = mapper.createObjectNode();

        node.put("T", "cmd");
        node.put("imei", imei);
        node.put("cmd", command);

        return node.toString();
    }

    public static String buildWithValue(String imei, String command, String key, String value) {
        ObjectNode node = mapper.createObjectNode();

        node.put("T", "cmd");
        node.put("imei", imei);
        node.put("cmd", command);
        node.put(key, value);

        return node.toString();
    }

    public static String buildSetCommand(String imei, Integer records, String server, Integer port) {
        ObjectNode node = mapper.createObjectNode();

        node.put("T", "cmd");
        node.put("imei", imei);
        node.put("cmd", "set");

        if (records != null) node.put("records", records);
        if (server != null && !server.isBlank()) node.put("server", server);
        if (port != null) node.put("port", port);

        return node.toString();
    }
}