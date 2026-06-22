package com.alels.gateway.dictionary;

import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.databind.ObjectMapper;

public class DeviceDictionaryLoader {

    private static final ObjectMapper mapper = new ObjectMapper();

    private static final Map<String, DeviceDictionary> cache =
            new ConcurrentHashMap<>();

    private static final String RESOURCE_BASE =
            "device-dictionary/";

    private DeviceDictionaryLoader() {
    }

    public static DeviceDictionary load(String dictionaryCode) {

        if (dictionaryCode == null || dictionaryCode.isBlank()) {
            throw new RuntimeException("[DICTIONARY] dictionaryCode is empty");
        }

        String normalizedCode =
                dictionaryCode.trim().toLowerCase();

        return cache.computeIfAbsent(normalizedCode, key -> {
            try {
                String path =
                        RESOURCE_BASE + key + ".json";

                InputStream inputStream =
                        DeviceDictionaryLoader.class
                                .getClassLoader()
                                .getResourceAsStream(path);

                if (inputStream == null) {
                    throw new RuntimeException(
                            "Dictionary file not found: " + path
                    );
                }

                DeviceDictionary dictionary =
                        mapper.readValue(
                                inputStream,
                                DeviceDictionary.class
                        );

                if (dictionary == null) {
                    throw new RuntimeException(
                            "Dictionary parse result is null: " + path
                    );
                }

                System.out.println(
                        "[DICTIONARY] Loaded code="
                                + key
                                + " model="
                                + dictionary.getModel()
                                + " totalAvl="
                                + dictionary.getTotal_avl()
                );

                return dictionary;

            } catch (Exception e) {
                throw new RuntimeException(
                        "[DICTIONARY] Failed to load code="
                                + key
                                + " error="
                                + e.getMessage(),
                        e
                );
            }
        });
    }

    public static boolean isLoaded(String dictionaryCode) {
        if (dictionaryCode == null || dictionaryCode.isBlank()) {
            return false;
        }

        return cache.containsKey(
                dictionaryCode.trim().toLowerCase()
        );
    }

    public static void reload(String dictionaryCode) {
        if (dictionaryCode == null || dictionaryCode.isBlank()) {
            return;
        }

        cache.remove(
                dictionaryCode.trim().toLowerCase()
        );

        System.out.println(
                "[DICTIONARY] Reload requested code="
                        + dictionaryCode.trim().toLowerCase()
        );
    }

    public static void clearCache() {
        cache.clear();
        System.out.println("[DICTIONARY] Cache cleared");
    }

    public static int cacheSize() {
        return cache.size();
    }
}