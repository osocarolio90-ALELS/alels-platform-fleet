package com.alels.gateway.command;

import com.alels.gateway.repository.CommandResponseRepository;
import com.alels.gateway.repository.CommandQueueRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class CommandResponseHandler {

    private static final ObjectMapper mapper = new ObjectMapper();

    public static void handleAlelsJsonResponse(byte[] packet) {
        try {
            JsonNode node = mapper.readTree(packet);

            String type = getText(node, "T");
            if (!"resp".equalsIgnoreCase(type)) {
                return;
            }

            String imei = getText(node, "imei");
            String cmd = getText(node, "cmd");
            String status = getText(node, "status");
            String message = getText(node, "message");
            String millis = getText(node, "millis");

            System.out.println("[COMMAND RESPONSE] imei=" + imei
                    + " cmd=" + cmd
                    + " status=" + status
                    + " message=" + message
                    + " millis=" + millis);

            CommandResponseRepository.insertResponse(
                    imei,
                    cmd,
                    status,
                    message,
                    node.toString()
            );

            CommandQueueRepository.markLatestAcked(
                    imei,
                    cmd
            );

        } catch (Exception e) {
            System.err.println("[COMMAND RESPONSE ERROR] " + e.getMessage());
        }
    }

    private static String getText(JsonNode node, String key) {
        return node.has(key) ? node.get(key).asText() : "";
    }
}
