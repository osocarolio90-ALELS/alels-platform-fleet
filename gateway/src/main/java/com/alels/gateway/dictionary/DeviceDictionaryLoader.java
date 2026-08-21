package com.alels.gateway.dictionary;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.alels.gateway.config.DatabaseConfig;

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

                if (inputStream == null) return loadFromDatabase(key);

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

    private static DeviceDictionary loadFromDatabase(String dictionaryCode) throws Exception {
        String sql = """
                SELECT dr.device_model, m.source_io_id, m.source_name, m.source_unit, m.value_type, m.multiplier, m.category
                FROM dictionary_registry dr
                JOIN device_io_mappings m ON m.device_model_id=dr.device_model_id AND m.dictionary_code=dr.dictionary_code
                WHERE LOWER(dr.dictionary_code)=? AND dr.status='ACTIVE' AND m.status='ACTIVE'
                ORDER BY m.source_io_id
                """;
        Map<String, AvlDefinition> definitions = new LinkedHashMap<>();
        String model = null;
        try (Connection connection = DatabaseConfig.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, dictionaryCode);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    model = rs.getString("device_model");
                    AvlDefinition definition = new AvlDefinition();
                    String id = rs.getString("source_io_id");
                    definition.setId(id);
                    definition.setName(rs.getString("source_name"));
                    definition.setUnit(rs.getString("source_unit"));
                    definition.setType(rs.getString("value_type"));
                    definition.setMultiplier(rs.getDouble("multiplier"));
                    definition.setCategory(rs.getString("category"));
                    definitions.put(id, definition);
                }
            }
        }
        if (definitions.isEmpty()) throw new RuntimeException("Active database dictionary has no verified AVL mapping: " + dictionaryCode);
        DeviceDictionary dictionary = new DeviceDictionary();
        dictionary.setModel(model);
        dictionary.setSource("ALELS_MASTER_DEVICE");
        dictionary.setTotal_avl(definitions.size());
        dictionary.setAvl(definitions);
        return dictionary;
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
