package com.alels.ingestion.cell.config;

public record IngestionCellConfig(
        String cellId,
        int cellCount,
        boolean enforceOwnership,
        boolean topicPerCell
) {
    private static final IngestionCellConfig CURRENT = fromEnvironment();

    public IngestionCellConfig {
        if (cellId == null || !cellId.matches("[a-z0-9][a-z0-9-]{0,31}")) {
            throw new IllegalArgumentException("ALELS_CELL_ID must match [a-z0-9][a-z0-9-]{0,31}");
        }
        if (cellCount < 1 || cellCount > 1024) {
            throw new IllegalArgumentException("ALELS_CELL_COUNT must be between 1 and 1024");
        }
        if (cellCount > 1 && (!enforceOwnership || !topicPerCell)) {
            throw new IllegalArgumentException(
                    "Multi-cell ingestion requires ALELS_CELL_ENFORCE=true and ALELS_KAFKA_TOPIC_PER_CELL=true"
            );
        }
    }

    public static IngestionCellConfig current() {
        return CURRENT;
    }

    public String topic(String baseTopic) {
        return topicPerCell ? baseTopic + "." + cellId : baseTopic;
    }

    public String consumerGroup(String baseGroup) {
        return topicPerCell ? baseGroup + "." + cellId : baseGroup;
    }

    public boolean accepts(String messageCellId) {
        if (cellCount == 1 && !topicPerCell) return true;
        return cellId.equals(messageCellId);
    }

    private static IngestionCellConfig fromEnvironment() {
        return new IngestionCellConfig(
                env("ALELS_CELL_ID", "cell-0"),
                integer("ALELS_CELL_COUNT", 1),
                bool("ALELS_CELL_ENFORCE", true),
                bool("ALELS_KAFKA_TOPIC_PER_CELL", false)
        );
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
