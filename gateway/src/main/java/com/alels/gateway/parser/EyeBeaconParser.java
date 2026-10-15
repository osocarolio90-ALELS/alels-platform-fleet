package com.alels.gateway.parser;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Decodes the value of an EYE Beacon IO element, after the transport parser has extracted it. */
public final class EyeBeaconParser {
    private static final int MAX_BEACONS_PER_IO = 100;
    public static final int BASE_AVL_ID = 385;
    public static final int WIFI_ALELS_JSON_ID = 10500;
    public static final int ADVANCED_AVL_ID = 548;
    public static final int VISIBLE_OR_NEAR_AVL_ID = 10828;
    public static final int FOUND_OR_AWAY_AVL_ID = 10829;
    public static final int LOST_AVL_ID = 10831;

    private EyeBeaconParser() {
    }

    public record Beacon(String type, String id, Integer rssiDbm, Boolean lowBattery,
                         Integer batteryMillivolts, String mac, Map<Integer, String> otherParameters) {
        public Beacon {
            otherParameters = Map.copyOf(otherParameters);
        }
    }

    public record Result(int ioId, int part, int totalParts, List<Beacon> beacons,
                         String undecodedHex) {
        public Result {
            beacons = List.copyOf(beacons);
        }
    }

    /**
     * Accepts an IO value from Codec 8E or ALELS JSON. Unknown 10500 variants are
     * returned as undecoded hex rather than interpreted using undocumented offsets.
     */
    public static Result parse(int ioId, String hex) {
        if (ioId != BASE_AVL_ID && ioId != WIFI_ALELS_JSON_ID && ioId != ADVANCED_AVL_ID
                && ioId != VISIBLE_OR_NEAR_AVL_ID && ioId != FOUND_OR_AWAY_AVL_ID
                && ioId != LOST_AVL_ID) {
            throw new IllegalArgumentException("Not an EYE Beacon IO ID: " + ioId);
        }
        if (hex == null || (hex.length() & 1) != 0 || !hex.matches("[0-9a-fA-F]*")) {
            throw new IllegalArgumentException("EYE Beacon value must be even-length hexadecimal");
        }
        byte[] data = java.util.HexFormat.of().parseHex(hex);
        if (ioId == BASE_AVL_ID || ioId == WIFI_ALELS_JSON_ID) {
            return parseSimple(ioId, data);
        }
        if (ioId == ADVANCED_AVL_ID && data.length > 3
                && ((data[2] & 0xff) != 0 || (data[3] & 0xff) != 1)) {
            return parseLegacyAdvanced(data);
        }
        return parseAdvanced(ioId, data);
    }

    private static Result parseSimple(int ioId, byte[] data) {
        if (data.length == 0) {
            throw new IllegalArgumentException("Missing EYE Beacon data-part header");
        }
        int header = data[0] & 0xff;
        int part = header >>> 4;
        int total = header & 0x0f;
        if (part == 0 || total == 0 || part > total) {
            throw new IllegalArgumentException("Invalid EYE Beacon data-part header");
        }
        List<Beacon> beacons = new ArrayList<>();
        int offset = 1;
        while (offset < data.length) {
            if (beacons.size() >= MAX_BEACONS_PER_IO) {
                throw new IllegalArgumentException("Too many EYE Beacons in one IO value");
            }
            int flag = data[offset] & 0xff;
            // Only these two layouts are documented for AVL 385. WiFi IO 10500
            // can contain other flags, including 0x25, with a different layout.
            int idLength = flag == 0x21 ? 20 : flag == 0x01 ? 16 : 0;
            if (idLength == 0) {
                return new Result(ioId, part, total, beacons, hex(data, offset, data.length));
            }
            if (offset + 2 + idLength > data.length) {
                throw new IllegalArgumentException("Truncated EYE Beacon record");
            }
            String id = hex(data, offset + 1, offset + 1 + idLength);
            int rssi = data[offset + 1 + idLength];
            beacons.add(new Beacon(idLength == 20 ? "IBEACON" : "EDDYSTONE", id,
                    rssi, null, null, null, Map.of()));
            offset += 2 + idLength;
        }
        return new Result(ioId, part, total, beacons, "");
    }

    private static Result parseAdvanced(int ioId, byte[] data) {
        if (data.length == 0 || data[0] != 0x01) {
            throw new IllegalArgumentException("Invalid advanced EYE Beacon header");
        }
        List<Beacon> beacons = new ArrayList<>();
        int offset = 1;
        while (offset < data.length) {
            if (beacons.size() >= MAX_BEACONS_PER_IO) {
                throw new IllegalArgumentException("Too many EYE Beacons in one IO value");
            }
            int length = data[offset++] & 0xff;
            if (length == 0 || offset + length > data.length) {
                throw new IllegalArgumentException("Invalid advanced EYE Beacon record length");
            }
            int end = offset + length;
            Map<Integer, String> parameters = new LinkedHashMap<>();
            while (offset < end) {
                if (offset + 2 > end) {
                    throw new IllegalArgumentException("Truncated EYE Beacon parameter header");
                }
                int id = data[offset++] & 0xff;
                int parameterLength = data[offset++] & 0xff;
                if (offset + parameterLength > end || parameters.containsKey(id)) {
                    throw new IllegalArgumentException("Invalid EYE Beacon parameter");
                }
                parameters.put(id, hex(data, offset, offset + parameterLength));
                offset += parameterLength;
            }
            String id = parameters.remove(1);
            if (id == null || id.isEmpty()) {
                throw new IllegalArgumentException("Missing or invalid EYE Beacon ID");
            }
            String rssiHex = parameters.remove(0);
            String lowHex = parameters.remove(13);
            String voltageHex = parameters.remove(14);
            String mac = parameters.remove(15);
            Integer rssi = rssiHex == null ? null : signedByte(rssiHex);
            Boolean low = lowHex == null ? null : unsignedByte(lowHex) != 0;
            Integer voltage = voltageHex == null ? null : unsignedShort(voltageHex);
            if (mac != null && mac.length() != 12) {
                throw new IllegalArgumentException("Invalid EYE Beacon MAC length");
            }
            beacons.add(new Beacon(ioId == ADVANCED_AVL_ID ? "CUSTOM" : beaconType(id.length()), id,
                    rssi, low, voltage, mac, parameters));
        }
        return new Result(ioId, 1, 1, beacons, "");
    }

    // Older AVL 548 firmware uses a count followed by RSSI, ID length/ID,
    // additional-data length/additional data for each beacon.
    private static Result parseLegacyAdvanced(byte[] data) {
        if (data.length == 0) {
            throw new IllegalArgumentException("Missing advanced EYE Beacon count");
        }
        int count = data[0] & 0xff;
        if (count > MAX_BEACONS_PER_IO) {
            throw new IllegalArgumentException("Too many EYE Beacons in one IO value");
        }
        int offset = 1;
        List<Beacon> beacons = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            if (offset + 3 > data.length) {
                throw new IllegalArgumentException("Truncated advanced EYE Beacon record");
            }
            int rssi = data[offset++];
            int idLength = data[offset++] & 0xff;
            if (idLength == 0 || offset + idLength + 1 > data.length) {
                throw new IllegalArgumentException("Invalid advanced EYE Beacon ID length");
            }
            String id = hex(data, offset, offset + idLength);
            offset += idLength;
            int additionalLength = data[offset++] & 0xff;
            if (offset + additionalLength > data.length) {
                throw new IllegalArgumentException("Invalid advanced EYE Beacon additional-data length");
            }
            Map<Integer, String> extra = additionalLength == 0 ? Map.of()
                    : Map.of(2, hex(data, offset, offset + additionalLength));
            offset += additionalLength;
            beacons.add(new Beacon("CUSTOM", id, rssi, null, null, null, extra));
        }
        if (offset != data.length) {
            throw new IllegalArgumentException("Trailing advanced EYE Beacon bytes");
        }
        return new Result(ADVANCED_AVL_ID, 1, 1, beacons, "");
    }

    private static String beaconType(int hexLength) {
        return hexLength == 40 ? "IBEACON" : hexLength == 32 ? "EDDYSTONE" : "CUSTOM";
    }

    private static int signedByte(String hex) {
        return (byte) unsignedByte(hex);
    }

    private static int unsignedByte(String hex) {
        if (hex.length() != 2) {
            throw new IllegalArgumentException("Invalid one-byte EYE Beacon parameter");
        }
        return Integer.parseInt(hex, 16);
    }

    private static int unsignedShort(String hex) {
        if (hex.length() != 4) {
            throw new IllegalArgumentException("Invalid two-byte EYE Beacon parameter");
        }
        return Integer.parseInt(hex, 16);
    }

    private static String hex(byte[] data, int from, int to) {
        return java.util.HexFormat.of().withUpperCase().formatHex(Arrays.copyOfRange(data, from, to));
    }
}
