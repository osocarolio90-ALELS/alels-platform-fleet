package com.alels.gateway.admission.service;

import com.alels.gateway.admission.model.DeviceAdmission;
import com.alels.gateway.admission.repository.DeviceAdmissionRepository;

import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public final class DeviceAdmissionRegistry {

    private static final String FALLBACK_DICTIONARY = "fmc650";
    private static final DeviceAdmissionRepository REPOSITORY = new DeviceAdmissionRepository();
    private static final AtomicReference<Map<String, DeviceAdmission>> SNAPSHOT =
            new AtomicReference<>(Map.of());
    private static final boolean ALLOW_UNKNOWN = Boolean.parseBoolean(
            System.getenv().getOrDefault("ALELS_ALLOW_UNKNOWN_DEVICES", "false")
    );
    private static final long REFRESH_SECONDS = Long.parseLong(
            System.getenv().getOrDefault("ALELS_GATEWAY_ADMISSION_REFRESH_SECONDS", "60")
    );
    private static final ScheduledExecutorService REFRESHER =
            Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable, "device-admission-refresh");
                thread.setDaemon(true);
                return thread;
            });

    private DeviceAdmissionRegistry() {
    }

    public static void start() throws Exception {
        refreshOrThrow();
        REFRESHER.scheduleWithFixedDelay(
                DeviceAdmissionRegistry::refreshSafely,
                REFRESH_SECONDS,
                REFRESH_SECONDS,
                TimeUnit.SECONDS
        );
    }

    public static boolean isReceiveAllowed(String imei) {
        if (imei == null || imei.isBlank()) {
            return false;
        }
        DeviceAdmission admission = SNAPSHOT.get().get(imei);
        return admission != null ? admission.receiveAllowed() : ALLOW_UNKNOWN;
    }

    public static String dictionaryCode(String imei) {
        DeviceAdmission admission = imei == null ? null : SNAPSHOT.get().get(imei);
        if (admission == null || admission.dictionaryCode() == null
                || admission.dictionaryCode().isBlank()) {
            return FALLBACK_DICTIONARY;
        }
        return admission.dictionaryCode();
    }

    public static int size() {
        return SNAPSHOT.get().size();
    }

    static void replaceForTest(Map<String, DeviceAdmission> snapshot) {
        SNAPSHOT.set(Map.copyOf(snapshot));
    }

    private static void refreshOrThrow() throws Exception {
        Map<String, DeviceAdmission> loaded = Map.copyOf(REPOSITORY.loadAll());
        SNAPSHOT.set(loaded);
        System.out.println("[ADMISSION] snapshot loaded devices=" + loaded.size());
    }

    private static void refreshSafely() {
        try {
            refreshOrThrow();
        } catch (Exception error) {
            System.err.println("[ADMISSION] refresh failed; previous snapshot retained: "
                    + error.getMessage());
        }
    }
}
