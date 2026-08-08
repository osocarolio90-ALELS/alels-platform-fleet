package com.alels.gateway.session.ownership;

import io.lettuce.core.RedisClient;
import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.SetArgs;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.async.RedisAsyncCommands;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** Distributed, non-blocking device-session ownership with compare-and-renew fencing. */
public final class SessionOwnershipService implements AutoCloseable {
    private static final String RENEW_SCRIPT = """
            if redis.call('get', KEYS[1]) == ARGV[1] then
              return redis.call('pexpire', KEYS[1], ARGV[2])
            end
            return 0
            """;
    private static final String RELEASE_SCRIPT = """
            if redis.call('get', KEYS[1]) == ARGV[1] then
              return redis.call('del', KEYS[1])
            end
            return 0
            """;

    private final SessionOwnershipConfig config;
    private final RedisClient client;
    private final StatefulRedisConnection<String, String> connection;
    private final RedisAsyncCommands<String, String> commands;

    public SessionOwnershipService(SessionOwnershipConfig config) {
        this.config = Objects.requireNonNull(config, "config");
        if (config.distributed()) {
            client = RedisClient.create(config.redisUri());
            connection = client.connect();
            commands = connection.async();
        } else {
            client = null;
            connection = null;
            commands = null;
        }
    }

    public CompletionStage<Boolean> acquireOrRenew(String imei, String fencingToken) {
        if (!config.distributed()) {
            return CompletableFuture.completedFuture(true);
        }
        String key = key(imei);
        long ttlMillis = config.ttlSeconds() * 1_000L;
        return commands.set(key, fencingToken, SetArgs.Builder.nx().px(ttlMillis))
                .thenCompose(result -> result != null
                        ? CompletableFuture.completedFuture(true)
                        : commands.<Long>eval(
                                RENEW_SCRIPT,
                                ScriptOutputType.INTEGER,
                                new String[]{key},
                                fencingToken,
                                Long.toString(ttlMillis)
                        ).thenApply(value -> value != null && value == 1L));
    }

    public CompletionStage<Boolean> release(String imei, String fencingToken) {
        if (!config.distributed() || imei == null || imei.isBlank()) {
            return CompletableFuture.completedFuture(true);
        }
        return commands.<Long>eval(
                RELEASE_SCRIPT,
                ScriptOutputType.INTEGER,
                new String[]{key(imei)},
                fencingToken
        ).thenApply(value -> value != null && value == 1L);
    }

    public boolean distributed() {
        return config.distributed();
    }

    private String key(String imei) {
        if (imei == null || imei.isBlank()) {
            throw new IllegalArgumentException("IMEI is required for session ownership");
        }
        return config.keyPrefix() + imei;
    }

    @Override
    public void close() {
        if (connection != null) {
            connection.close();
        }
        if (client != null) {
            client.shutdown();
        }
    }
}
