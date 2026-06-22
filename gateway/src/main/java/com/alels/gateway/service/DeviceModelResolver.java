package com.alels.gateway.service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.alels.gateway.model.DeviceModelInfo;
import com.alels.gateway.repository.DeviceModelResolverRepository;

public class DeviceModelResolver {

    private static final String FALLBACK_DICTIONARY = "fmc650";

    private static final DeviceModelResolverRepository repository =
            new DeviceModelResolverRepository();

    private static final Map<String, DeviceModelInfo> cache =
            new ConcurrentHashMap<>();

    private DeviceModelResolver() {
    }

    public static DeviceModelInfo resolve(String imei) {

        if (imei == null || imei.isBlank()) {
            return null;
        }

        String normalizedImei =
                imei.trim();

        DeviceModelInfo cached =
                cache.get(normalizedImei);

        if (cached != null) {
            return cached;
        }

        DeviceModelInfo info =
                repository.findByImei(normalizedImei);

        if (info == null) {
            System.out.println(
                    "[DEVICE MODEL RESOLVER] NOT FOUND imei="
                            + normalizedImei
                            + " -> AUTO PROVISION REQUIRED"
            );

            return null;
        }

        cache.put(
                normalizedImei,
                info
        );

        String dictionaryCode =
                DictionaryRegistryResolver.resolveDictionaryCode(info);

        System.out.println(
                "[DEVICE MODEL RESOLVER] imei="
                        + normalizedImei
                        + " brand="
                        + info.getBrandCode()
                        + " model="
                        + info.getModelCode()
                        + " parser="
                        + info.getParserCode()
                        + " dictionary="
                        + dictionaryCode
        );

        return info;
    }

    public static String resolveDictionaryCode(String imei) {

        DeviceModelInfo info =
                resolve(imei);

        if (info == null) {
            return FALLBACK_DICTIONARY;
        }

        return DictionaryRegistryResolver.resolveDictionaryCode(info);
    }

    public static String resolveModelCode(String imei) {

        DeviceModelInfo info =
                resolve(imei);

        if (info == null) {
            return FALLBACK_DICTIONARY;
        }

        if (info.getModelCode() == null || info.getModelCode().isBlank()) {
            return FALLBACK_DICTIONARY;
        }

        return info.getModelCode();
    }

    public static void clearCache() {
        cache.clear();

        DictionaryRegistryResolver.clearCache();

        System.out.println(
                "[DEVICE MODEL RESOLVER] cache cleared"
        );
    }
}