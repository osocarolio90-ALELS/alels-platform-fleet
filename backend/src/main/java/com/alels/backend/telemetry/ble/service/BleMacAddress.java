package com.alels.backend.telemetry.ble.service;

import java.util.Locale;

/** Asset/display validation only; does not reinterpret or modify advertisement decoding. */
public final class BleMacAddress {
    private BleMacAddress() {}

    public static String normalizeMac(String value) {
        if (value == null) throw new IllegalArgumentException("MAC address is required");
        String input = value.trim();
        if (!input.matches("(?i)([0-9a-f]{12}|([0-9a-f]{2}:){5}[0-9a-f]{2}|([0-9a-f]{2}-){5}[0-9a-f]{2})"))
            throw new IllegalArgumentException("A valid 48-bit MAC address is required");
        String hex = input.replace(":", "").replace("-", "").toUpperCase(Locale.ROOT);
        return java.util.HexFormat.ofDelimiter(":").withUpperCase()
                .formatHex(java.util.HexFormat.of().parseHex(hex));
    }

    /** Formatting explicit physical MAC evidence never derives an address from an advertising ID. */
    public static String verifiedDisplayMac(String parsedMac) {
        if(parsedMac==null || parsedMac.isBlank())return null;
        try{return normalizeMac(parsedMac);}catch(IllegalArgumentException invalid){return null;}
    }

    /** User policy: EyeBeacon display MAC comes from Instance ID; EyeSensor keeps its verified MAC. */
    public static String displayMac(String type,String instanceId,String verifiedMac) {
        String instance="EyeBeacon".equals(type)?verifiedDisplayMac(instanceId):null;
        return instance!=null?instance:verifiedDisplayMac(verifiedMac);
    }

    /** Preserve the existing public API; advertisement hints are not identity evidence. */
    public static String verifiedDisplayMac(String parsedMac,String protocol,String namespace,String instance,String receiverImei) {
        return verifiedDisplayMac(parsedMac);
    }
}
