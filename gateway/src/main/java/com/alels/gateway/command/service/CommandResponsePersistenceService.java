package com.alels.gateway.command.service;

import com.alels.gateway.command.CommandResponseHandler;
import com.alels.gateway.repository.CommandQueueRepository;
import com.alels.gateway.repository.CommandResponseRepository;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public final class CommandResponsePersistenceService {

    private static final int THREADS = Integer.parseInt(
            System.getenv().getOrDefault("ALELS_GATEWAY_COMMAND_DB_THREADS", "4")
    );
    private static final int QUEUE_CAPACITY = Integer.parseInt(
            System.getenv().getOrDefault("ALELS_GATEWAY_COMMAND_DB_QUEUE", "10000")
    );
    private static final ThreadPoolExecutor EXECUTOR = new ThreadPoolExecutor(
            THREADS,
            THREADS,
            0L,
            TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(QUEUE_CAPACITY),
            runnable -> {
                Thread thread = new Thread(runnable, "command-response-db");
                thread.setDaemon(true);
                return thread;
            },
            new ThreadPoolExecutor.AbortPolicy()
    );

    private CommandResponsePersistenceService() {
    }

    public static void persistAlels(byte[] packet) {
        submit(() -> CommandResponseHandler.handleAlelsJsonResponse(packet));
    }

    public static void persistTeltonika(
            String imei,
            String commandName,
            boolean valid,
            String rawHex
    ) {
        submit(() -> {
            CommandResponseRepository.insertResponse(
                    imei,
                    commandName,
                    valid ? "OK" : "FAILED",
                    valid ? "Teltonika Codec12 response received" : "Invalid Teltonika Codec12 response",
                    rawHex
            );
            if (valid) {
                CommandQueueRepository.markLatestAcked(
                        imei,
                        commandName.startsWith("UNKNOWN") ? null : commandName
                );
            }
        });
    }

    private static void submit(Runnable task) {
        try {
            EXECUTOR.execute(task);
        } catch (RuntimeException rejected) {
            System.err.println("[COMMAND RESPONSE] persistence queue full");
        }
    }
}
