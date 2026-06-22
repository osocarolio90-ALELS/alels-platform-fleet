package com.alels.gateway.service;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.alels.gateway.repository.DevicePresenceRepository;

public class DevicePresenceScheduler {

    private static final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor();

    public static void start() {

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
}