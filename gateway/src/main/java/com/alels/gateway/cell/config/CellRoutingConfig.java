package com.alels.gateway.cell.config;

import java.nio.charset.StandardCharsets;

public record CellRoutingConfig(
        String cellId,
        int cellIndex,
        int cellCount,
        boolean enforceOwnership,
        boolean topicPerCell
) {
    private static final CellRoutingConfig CURRENT = fromEnvironment();

    public CellRoutingConfig {
        if (cellId == null || !cellId.matches("[a-z0-9][a-z0-9-]{0,31}")) {
            throw new IllegalArgumentException("ALELS_CELL_ID must match [a-z0-9][a-z0-9-]{0,31}");
        }
        if (cellCount < 1 || cellCount > 1024) {
            throw new IllegalArgumentException("ALELS_CELL_COUNT must be between 1 and 1024");
        }
        if (cellIndex < 0 || cellIndex >= cellCount) {
            throw new IllegalArgumentException("ALELS_CELL_INDEX must be within the configured cell count");
        }
        if (cellCount > 1 && (!enforceOwnership || !topicPerCell)) {
            throw new IllegalArgumentException(
                    "Multi-cell mode requires ALELS_CELL_ENFORCE=true and ALELS_KAFKA_TOPIC_PER_CELL=true"
            );
        }
    }

    public static CellRoutingConfig current() {
        return CURRENT;
    }

    public boolean owns(String deviceId) {
        return !enforceOwnership || assignedCell(deviceId) == cellIndex;
    }

    public int assignedCell(String deviceId) {
        if (deviceId == null || deviceId.isBlank()) return -1;
        return jumpConsistentHash(fnv1a64(deviceId), cellCount);
    }

    public String topic(String baseTopic) {
        return topicPerCell ? baseTopic + "." + cellId : baseTopic;
    }

    private static CellRoutingConfig fromEnvironment() {
        return new CellRoutingConfig(
                env("ALELS_CELL_ID", "cell-0"),
                integer("ALELS_CELL_INDEX", 0),
                integer("ALELS_CELL_COUNT", 1),
                bool("ALELS_CELL_ENFORCE", true),
                bool("ALELS_KAFKA_TOPIC_PER_CELL", false)
        );
    }

    private static int jumpConsistentHash(long key, int buckets) {
        long current = -1;
        long next = 0;
        while (next < buckets) {
            current = next;
            key = key * 2862933555777941757L + 1;
            next = (long) ((current + 1) * (double) (1L << 31) / (double) ((key >>> 33) + 1));
        }
        return (int) current;
    }

    private static long fnv1a64(String value) {
        long hash = 0xcbf29ce484222325L;
        for (byte item : value.getBytes(StandardCharsets.UTF_8)) {
            hash ^= item & 0xffL;
            hash *= 0x100000001b3L;
        }
        return hash;
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static int integer(String name, int fallback) {
        return Integer.parseInt(env(name, Integer.toString(fallback)));
    }

    private static boolean bool(String name, boolean fallback) {
        return Boolean.parseBoolean(env(name, Boolean.toString(fallback)));
    }
}
