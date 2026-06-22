package com.alels.gateway.service;

import java.util.Map;

public class VehicleStatusResolver {

    private static final String AVL_IGNITION = "239";
    private static final String AVL_MOVEMENT = "240";

    public static String resolve(Map<String, Object> ioData) {
        if (ioData == null || ioData.isEmpty()) {
            return "UNKNOWN";
        }

        Integer ignition = toInteger(ioData.get(AVL_IGNITION));
        Integer movement = toInteger(ioData.get(AVL_MOVEMENT));

        if (movement != null && movement == 1) {
            return "MOVING";
        }

        if (ignition != null && ignition == 1 && movement != null && movement == 0) {
            return "IDLE";
        }

        if (ignition != null && ignition == 0 && movement != null && movement == 0) {
            return "STOP";
        }

        return "UNKNOWN";
    }

    private static Integer toInteger(Object value) {
        if (value == null) {
            return null;
        }

        if (value instanceof Number number) {
            return number.intValue();
        }

        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception e) {
            return null;
        }
    }
}