package com.alels.gateway.observability.service;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/** Low-cardinality process metrics and admission state for one gateway instance. */
public final class GatewayRuntimeMetrics {
    private static final GatewayRuntimeMetrics INSTANCE = new GatewayRuntimeMetrics();

    private final long maxConnections = positiveEnv("ALELS_GATEWAY_MAX_CONNECTIONS", 100_000L);
    private final long maxPendingPublishes = positiveEnv("ALELS_GATEWAY_MAX_PENDING_PUBLISHES", 100_000L);
    private final AtomicBoolean accepting = new AtomicBoolean(false);
    private final AtomicLong activeConnections = new AtomicLong();
    private final AtomicLong acceptedConnections = new AtomicLong();
    private final AtomicLong rejectedConnections = new AtomicLong();
    private final AtomicLong receivedFrames = new AtomicLong();
    private final AtomicLong receivedBytes = new AtomicLong();
    private final AtomicLong pendingPublishes = new AtomicLong();
    private final AtomicLong successfulPublishes = new AtomicLong();
    private final AtomicLong failedPublishes = new AtomicLong();
    private final AtomicLong wrongCellRejections = new AtomicLong();
    private final AtomicLong sessionOwnershipRejections = new AtomicLong();

    private GatewayRuntimeMetrics() {
    }

    public static GatewayRuntimeMetrics instance() {
        return INSTANCE;
    }

    public boolean admitConnection() {
        if (!accepting.get()) {
            rejectedConnections.incrementAndGet();
            return false;
        }
        long current = activeConnections.incrementAndGet();
        if (current > maxConnections) {
            activeConnections.decrementAndGet();
            rejectedConnections.incrementAndGet();
            return false;
        }
        acceptedConnections.incrementAndGet();
        return true;
    }

    public void connectionClosed() {
        activeConnections.updateAndGet(current -> Math.max(0L, current - 1L));
    }

    public void frameReceived(int bytes) {
        receivedFrames.incrementAndGet();
        receivedBytes.addAndGet(Math.max(bytes, 0));
    }

    public boolean beginPublish() {
        long current = pendingPublishes.incrementAndGet();
        if (current > maxPendingPublishes) {
            pendingPublishes.decrementAndGet();
            failedPublishes.incrementAndGet();
            return false;
        }
        return true;
    }

    public void publishCompleted(boolean successful) {
        pendingPublishes.updateAndGet(current -> Math.max(0L, current - 1L));
        if (successful) {
            successfulPublishes.incrementAndGet();
        } else {
            failedPublishes.incrementAndGet();
        }
    }

    public void wrongCellRejected() {
        wrongCellRejections.incrementAndGet();
    }

    public void sessionOwnershipRejected() {
        sessionOwnershipRejections.incrementAndGet();
    }

    public void markAccepting() {
        accepting.set(true);
    }

    public void markDraining() {
        accepting.set(false);
    }

    public boolean isLive() {
        return true;
    }

    public boolean isReady() {
        return accepting.get()
                && activeConnections.get() < maxConnections
                && pendingPublishes.get() < maxPendingPublishes;
    }

    public String prometheus() {
        return """
                # TYPE alels_gateway_ready gauge
                alels_gateway_ready %d
                # TYPE alels_gateway_connections_active gauge
                alels_gateway_connections_active %d
                # TYPE alels_gateway_connections_accepted_total counter
                alels_gateway_connections_accepted_total %d
                # TYPE alels_gateway_connections_rejected_total counter
                alels_gateway_connections_rejected_total %d
                # TYPE alels_gateway_frames_received_total counter
                alels_gateway_frames_received_total %d
                # TYPE alels_gateway_bytes_received_total counter
                alels_gateway_bytes_received_total %d
                # TYPE alels_gateway_publishes_pending gauge
                alels_gateway_publishes_pending %d
                # TYPE alels_gateway_publishes_success_total counter
                alels_gateway_publishes_success_total %d
                # TYPE alels_gateway_publishes_failed_total counter
                alels_gateway_publishes_failed_total %d
                # TYPE alels_gateway_connections_limit gauge
                alels_gateway_connections_limit %d
                # TYPE alels_gateway_publishes_pending_limit gauge
                alels_gateway_publishes_pending_limit %d
                # TYPE alels_gateway_wrong_cell_rejections_total counter
                alels_gateway_wrong_cell_rejections_total %d
                # TYPE alels_gateway_session_ownership_rejections_total counter
                alels_gateway_session_ownership_rejections_total %d
                """.formatted(
                isReady() ? 1 : 0,
                activeConnections.get(), acceptedConnections.get(), rejectedConnections.get(),
                receivedFrames.get(), receivedBytes.get(), pendingPublishes.get(),
                successfulPublishes.get(), failedPublishes.get(),
                maxConnections, maxPendingPublishes, wrongCellRejections.get(),
                sessionOwnershipRejections.get()
        );
    }

    private static long positiveEnv(String name, long defaultValue) {
        long value = Long.parseLong(System.getenv().getOrDefault(name, Long.toString(defaultValue)));
        if (value <= 0L) {
            throw new IllegalStateException(name + " must be greater than zero");
        }
        return value;
    }
}
