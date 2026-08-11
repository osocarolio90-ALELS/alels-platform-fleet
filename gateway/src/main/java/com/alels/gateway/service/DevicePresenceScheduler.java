package com.alels.gateway.service;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import com.alels.gateway.repository.DevicePresenceRepository;

public class DevicePresenceScheduler {

    private static final AtomicBoolean STARTED = new AtomicBoolean();
    private static final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(task -> {
                Thread thread = new Thread(task, "device-presence-scheduler");
                thread.setDaemon(true);
                return thread;
            });

    public static void start() {
        if (!STARTED.compareAndSet(false, true)) {
            return;
        }

        System.out.println("[PRESENCE SCHEDULER] Started");

        scheduler.scheduleAtFixedRate(() -> {
            try {

                int updated =
                        DevicePresenceRepository.refreshPresenceStatus();

                System.out.println(
                        "[PRESENCE SCHEDULER] refreshed devices="
                                + updated
                );

            } catch (Exception e) {

                System.err.println(
                        "[PRESENCE SCHEDULER ERROR] "
                                + e.getMessage()
                );
            }

        }, 5, 30, TimeUnit.SECONDS);

    }

    public static void stop() {
        scheduler.shutdownNow();
    }
}
