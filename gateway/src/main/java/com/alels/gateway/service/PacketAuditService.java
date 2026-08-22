package com.alels.gateway.service;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import com.alels.gateway.detector.ProtocolType;
import com.alels.gateway.repository.PacketAuditRepository;

/** Bounded off-event-loop persistence. Rejected/failed writes fail the frame so the device can retry. */
public final class PacketAuditService {
    private static final int WORKERS = positiveEnvironment("ALELS_PACKET_AUDIT_WORKERS", 4);
    private static final int QUEUE_CAPACITY = positiveEnvironment("ALELS_PACKET_AUDIT_QUEUE_CAPACITY", 8192);
    private static final ThreadFactory THREADS = runnable -> {
        Thread thread = new Thread(runnable, "packet-audit-writer");
        thread.setDaemon(true);
        return thread;
    };
    private static final ThreadPoolExecutor EXECUTOR = new ThreadPoolExecutor(
            WORKERS, WORKERS, 0L, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(QUEUE_CAPACITY), THREADS, new ThreadPoolExecutor.AbortPolicy()
    );

    private PacketAuditService() {}

    public static CompletionStage<Long> persistReceived(String imei, ProtocolType protocol,
                                                        String transport, byte[] packet,
                                                        String remoteAddress) {
        try {
            return CompletableFuture.supplyAsync(
                    () -> PacketAuditRepository.insertReceived(
                            imei, protocol, transport, packet.clone(), remoteAddress
                    ), EXECUTOR
            );
        } catch (RuntimeException error) {
            return CompletableFuture.failedFuture(error);
        }
    }

    public static CompletionStage<Long> persistSent(String imei, ProtocolType protocol,
                                                    String transport, byte[] packet,
                                                    String remoteAddress) {
        try {
            return CompletableFuture.supplyAsync(
                    () -> PacketAuditRepository.insertSent(
                            imei, protocol, transport, packet.clone(), remoteAddress
                    ), EXECUTOR
            );
        } catch (RuntimeException error) {
            return CompletableFuture.failedFuture(error);
        }
    }

    public static void shutdown() {
        EXECUTOR.shutdown();
    }

    private static int positiveEnvironment(String name, int fallback) {
        try {
            int value = Integer.parseInt(System.getenv().getOrDefault(name, String.valueOf(fallback)));
            return value > 0 ? value : fallback;
        } catch (NumberFormatException error) {
            return fallback;
        }
    }
}
