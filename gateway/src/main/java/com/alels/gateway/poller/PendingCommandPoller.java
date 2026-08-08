package com.alels.gateway.poller;

import com.alels.gateway.cell.config.CellRoutingConfig;
import com.alels.gateway.command.CommandDispatcher;
import com.alels.gateway.repository.CommandQueueRepository;
import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Claims commands only after confirming that this gateway owns the live device channel. */
public final class PendingCommandPoller implements Runnable {
    private static final Logger log = LoggerFactory.getLogger(PendingCommandPoller.class);
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final int limit = positiveEnv("ALELS_GATEWAY_COMMAND_POLL_LIMIT", 100);
    private final int leaseSeconds = positiveEnv("ALELS_GATEWAY_COMMAND_LEASE_SECONDS", 30);
    private final long intervalMs = positiveEnv("ALELS_GATEWAY_COMMAND_POLL_INTERVAL_MS", 2_000);
    private final String owner = ownerId();

    @Override
    public void run() {
        log.info("event=command_poller_start owner={} cell={}", owner,
                CellRoutingConfig.current().cellId());
        while (running.get()) {
            try {
                poll();
            } catch (Exception error) {
                log.warn("event=command_poll_failed owner={} error={}", owner,
                        error.getClass().getSimpleName());
            }
            sleep();
        }
    }

    public void stop() {
        running.set(false);
    }

    private void poll() {
        List<PendingCommandDto> candidates = CommandQueueRepository.findDispatchCandidates(limit);
        for (PendingCommandDto command : candidates) {
            if (command == null || command.id == null || command.imei == null) continue;
            if (!CellRoutingConfig.current().owns(command.imei)) continue;
            if (!CommandDispatcher.hasActiveChannel(command.imei)) continue;
            if (!CommandQueueRepository.tryLease(command.id, owner, leaseSeconds)) continue;

            boolean sent = false;
            try {
                sent = CommandDispatcher.sendQueuedCommand(
                        command.imei, command.commandName, command.route
                );
            } finally {
                if (!CommandQueueRepository.completeLease(command.id, owner, sent)) {
                    log.error("event=command_lease_completion_lost id={} owner={}", command.id, owner);
                }
            }
        }
    }

    private void sleep() {
        try {
            Thread.sleep(intervalMs);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            running.set(false);
        }
    }

    private String ownerId() {
        String configured = System.getenv("ALELS_GATEWAY_INSTANCE_ID");
        if (configured != null && configured.matches("[A-Za-z0-9._-]{1,120}")) return configured;
        try {
            String generated = CellRoutingConfig.current().cellId() + "-"
                    + InetAddress.getLocalHost().getHostName() + "-"
                    + ManagementFactory.getRuntimeMXBean().getName();
            generated = generated.replaceAll("[^A-Za-z0-9._-]", "-");
            return generated.substring(0, Math.min(generated.length(), 120));
        } catch (Exception ignored) {
            return CellRoutingConfig.current().cellId() + "-gateway-process";
        }
    }

    private static int positiveEnv(String name, int fallback) {
        int value = Integer.parseInt(System.getenv().getOrDefault(name, Integer.toString(fallback)));
        if (value <= 0) throw new IllegalStateException(name + " must be greater than zero");
        return value;
    }
}
