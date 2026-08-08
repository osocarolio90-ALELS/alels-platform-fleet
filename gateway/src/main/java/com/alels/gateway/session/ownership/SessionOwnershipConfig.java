package com.alels.gateway.session.ownership;

public record SessionOwnershipConfig(
        int replicaCount,
        String redisUri,
        long ttlSeconds,
        String keyPrefix
) {
    public SessionOwnershipConfig {
        if (replicaCount <= 0) {
            throw new IllegalArgumentException("replicaCount must be greater than zero");
        }
        if (ttlSeconds <= 0L) {
            throw new IllegalArgumentException("ttlSeconds must be greater than zero");
        }
        redisUri = redisUri == null ? "" : redisUri.trim();
        keyPrefix = keyPrefix == null ? "" : keyPrefix.trim();
        if (keyPrefix.isEmpty()) {
            throw new IllegalArgumentException("keyPrefix must not be blank");
        }
        if (replicaCount > 1 && redisUri.isEmpty()) {
            throw new IllegalArgumentException("redisUri is required for multiple replicas");
        }
    }

    public static SessionOwnershipConfig fromEnvironment() {
        int replicas = positiveInt("ALELS_GATEWAY_REPLICA_COUNT", 1);
        long ttl = positiveLong("ALELS_SESSION_OWNER_TTL_SECONDS", 15L);
        String uri = System.getenv().getOrDefault("ALELS_SESSION_OWNER_REDIS_URI", "").trim();
        String prefix = System.getenv().getOrDefault(
                "ALELS_SESSION_OWNER_KEY_PREFIX", "alels:gateway:session-owner:"
        ).trim();
        if (prefix.isEmpty()) {
            throw new IllegalStateException("ALELS_SESSION_OWNER_KEY_PREFIX must not be blank");
        }
        if (replicas > 1 && uri.isEmpty()) {
            throw new IllegalStateException(
                    "ALELS_SESSION_OWNER_REDIS_URI is required when ALELS_GATEWAY_REPLICA_COUNT > 1"
            );
        }
        return new SessionOwnershipConfig(replicas, uri, ttl, prefix);
    }

    public boolean distributed() {
        return replicaCount > 1;
    }

    private static int positiveInt(String name, int fallback) {
        int value = Integer.parseInt(System.getenv().getOrDefault(name, Integer.toString(fallback)));
        if (value <= 0) {
            throw new IllegalStateException(name + " must be greater than zero");
        }
        return value;
    }

    private static long positiveLong(String name, long fallback) {
        long value = Long.parseLong(System.getenv().getOrDefault(name, Long.toString(fallback)));
        if (value <= 0L) {
            throw new IllegalStateException(name + " must be greater than zero");
        }
        return value;
    }
}
