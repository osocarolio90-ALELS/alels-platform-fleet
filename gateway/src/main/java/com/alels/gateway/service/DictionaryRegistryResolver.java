package com.alels.gateway.service;

import com.alels.gateway.model.DeviceModelInfo;
import com.alels.gateway.model.DictionaryInfo;
import com.alels.gateway.repository.DictionaryRegistryRepository;

import java.util.Map;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

public class DictionaryRegistryResolver {

    private static final String FALLBACK_DICTIONARY = "fmc650";

    private static final DictionaryRegistryRepository repository =
            new DictionaryRegistryRepository();

    private static final Map<Long, DictionaryInfo> cache =
            new ConcurrentHashMap<>();

    private DictionaryRegistryResolver() {
    }

    public static DictionaryInfo resolveByDeviceModel(DeviceModelInfo deviceModelInfo) {

        if (deviceModelInfo == null || deviceModelInfo.getModelId() == null) {
            return null;
        }

        Long modelId = deviceModelInfo.getModelId();

        DictionaryInfo cached = cache.get(modelId);
        if (cached != null) {
            return cached;
        }

        DictionaryInfo info =
                repository.findActiveByDeviceModelId(modelId);

        if (info == null) {
            System.out.println(
                    "[DICTIONARY REGISTRY] NOT FOUND model="
                            + deviceModelInfo.getBrandCode()
                            + "/"
                            + deviceModelInfo.getModelCode()
                            + " fallback="
                            + FALLBACK_DICTIONARY
            );

            return null;
        }

        cache.put(modelId, info);

        System.out.println(
                "[DICTIONARY REGISTRY] model="
                        + deviceModelInfo.getBrandCode()
                        + "/"
                        + deviceModelInfo.getModelCode()
                        + " dictionary="
                        + info.getDictionaryCode()
                        + " file="
                        + info.getDictionaryFile()
        );

        return info;
    }

    public static String resolveDictionaryCode(DeviceModelInfo deviceModelInfo) {

        if (usesAlelsHubFmc650Contract(deviceModelInfo)) {
            return FALLBACK_DICTIONARY;
        }

        DictionaryInfo info =
                resolveByDeviceModel(deviceModelInfo);

        if (info != null
                && info.getDictionaryCode() != null
                && !info.getDictionaryCode().isBlank()) {
            return info.getDictionaryCode();
        }

        if (deviceModelInfo != null
                && deviceModelInfo.getDictionaryCode() != null
                && !deviceModelInfo.getDictionaryCode().isBlank()) {
            return deviceModelInfo.getDictionaryCode();
        }

        return FALLBACK_DICTIONARY;
    }

    static boolean usesAlelsHubFmc650Contract(DeviceModelInfo deviceModelInfo) {
        if (deviceModelInfo == null) return false;
        String identity = ((deviceModelInfo.getBrandCode() == null ? "" : deviceModelInfo.getBrandCode()) + " "
                + (deviceModelInfo.getModelCode() == null ? "" : deviceModelInfo.getModelCode()) + " "
                + (deviceModelInfo.getModelName() == null ? "" : deviceModelInfo.getModelName())).toUpperCase(Locale.ROOT);
        return identity.contains("ALELS") && (identity.contains("HUB") || deviceModelInfo.isAlelsJsonParser());
    }

    public static void clearCache() {
        cache.clear();
        System.out.println("[DICTIONARY REGISTRY] cache cleared");
    }
}
