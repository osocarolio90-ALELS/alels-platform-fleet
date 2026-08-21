package com.alels.gateway.service;

import com.alels.gateway.config.DatabaseConfig;
import com.alels.gateway.model.DictionaryAvlInfo;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.List;

public class DictionaryMappingGenerator {

    private DictionaryMappingGenerator() {
    }

    public static int generate(
            Long deviceModelId,
            String sourceProtocol,
            String dictionaryCode,
            List<DictionaryAvlInfo> avlList
    ) {
        if (deviceModelId == null || avlList == null || avlList.isEmpty()) {
            return 0;
        }

        int imported = 0;
        for (DictionaryAvlInfo avl : avlList) {
            if (avl == null || avl.getAvlId() == null || avl.getAvlId().isBlank()) {
                continue;
            }

            String fieldCode = NormalizedFieldMatcher.match(avl.getName());
            boolean persisted = fieldCode == null
                    ? upsertDictionaryMetadata(deviceModelId, sourceProtocol, dictionaryCode, avl)
                    : upsertNormalizedMapping(deviceModelId, sourceProtocol, dictionaryCode, avl, fieldCode);

            if (persisted) imported++;
        }
        return imported;
    }

    private static boolean upsertNormalizedMapping(
            Long deviceModelId,
            String sourceProtocol,
            String dictionaryCode,
            DictionaryAvlInfo avl,
            String fieldCode
    ) {
        String sql = """
                INSERT INTO device_io_mappings (
                    device_model_id,
                    source_protocol,
                    source_io_id,
                    normalized_field_id,
                    field_code,
                    source_name,
                    source_unit,
                    target_unit,
                    multiplier,
                    offset_value,
                    value_type,
                    status,
                    mapping_source,
                    dictionary_code,
                    category
                )
                SELECT
                    ?, ?, ?, nf.id, nf.field_code, ?, ?, nf.unit, ?, 0, ?,
                    'ACTIVE', 'AUTO_IMPORT', ?, ?
                FROM normalized_fields nf
                WHERE nf.field_code = ?
                ON CONFLICT (
                    device_model_id,
                    source_protocol,
                    source_io_id,
                    normalized_field_id
                )
                DO UPDATE SET
                    source_name = EXCLUDED.source_name,
                    source_unit = EXCLUDED.source_unit,
                    target_unit = EXCLUDED.target_unit,
                    multiplier = EXCLUDED.multiplier,
                    value_type = EXCLUDED.value_type,
                    category = EXCLUDED.category,
                    status = 'ACTIVE',
                    mapping_source = 'AUTO_IMPORT',
                    dictionary_code = EXCLUDED.dictionary_code
                """;

        try (Connection conn = DatabaseConfig.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, deviceModelId);
            ps.setString(2, sourceProtocol);
            ps.setString(3, avl.getAvlId());
            ps.setString(4, avl.getName());
            ps.setString(5, avl.getUnit());
            ps.setDouble(6, avl.getMultiplier() != null ? avl.getMultiplier() : 1.0);
            ps.setString(7, resolveValueType(avl));
            ps.setString(8, dictionaryCode);
            ps.setString(9, avl.getCategory());
            ps.setString(10, fieldCode);
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            throw mappingError(dictionaryCode, avl, e);
        }
    }

    private static boolean upsertDictionaryMetadata(
            Long deviceModelId,
            String sourceProtocol,
            String dictionaryCode,
            DictionaryAvlInfo avl
    ) {
        String sql = """
                INSERT INTO device_io_mappings (
                    device_model_id,
                    source_protocol,
                    source_io_id,
                    normalized_field_id,
                    field_code,
                    source_name,
                    source_unit,
                    target_unit,
                    multiplier,
                    offset_value,
                    value_type,
                    status,
                    mapping_source,
                    dictionary_code,
                    category
                ) VALUES (
                    ?, ?, ?, NULL, ?, ?, ?, ?, ?, 0, ?,
                    'ACTIVE', 'DICTIONARY_METADATA', ?, ?
                )
                ON CONFLICT (device_model_id, source_protocol, source_io_id)
                    WHERE normalized_field_id IS NULL AND mapping_source = 'DICTIONARY_METADATA'
                DO UPDATE SET
                    field_code = EXCLUDED.field_code,
                    source_name = EXCLUDED.source_name,
                    source_unit = EXCLUDED.source_unit,
                    target_unit = EXCLUDED.target_unit,
                    multiplier = EXCLUDED.multiplier,
                    value_type = EXCLUDED.value_type,
                    dictionary_code = EXCLUDED.dictionary_code,
                    category = EXCLUDED.category,
                    status = 'ACTIVE'
                """;

        try (Connection conn = DatabaseConfig.getConnection(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, deviceModelId);
            ps.setString(2, sourceProtocol);
            ps.setString(3, avl.getAvlId());
            ps.setString(4, "io." + avl.getAvlId());
            ps.setString(5, avl.getName());
            ps.setString(6, avl.getUnit());
            ps.setString(7, avl.getUnit());
            ps.setDouble(8, avl.getMultiplier() != null ? avl.getMultiplier() : 1.0);
            ps.setString(9, resolveValueType(avl));
            ps.setString(10, dictionaryCode);
            ps.setString(11, avl.getCategory());
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            throw mappingError(dictionaryCode, avl, e);
        }
    }

    private static IllegalStateException mappingError(String dictionaryCode, DictionaryAvlInfo avl, Exception e) {
        System.err.println(
                "[DICT MAPPING ERROR] dictionary=" + dictionaryCode
                        + " io=" + avl.getAvlId()
                        + " name=" + avl.getName()
                        + " error=" + e.getMessage()
        );
        return new IllegalStateException(
                "Failed to persist dictionary mapping " + dictionaryCode + "/" + avl.getAvlId(), e
        );
    }

    private static String resolveValueType(DictionaryAvlInfo avl) {
        if (avl != null && avl.getValueType() != null && !avl.getValueType().isBlank()) {
            return avl.getValueType().trim().toUpperCase();
        }
        String name = avl == null ? null : avl.getName();
        if (name == null) return "NUMBER";

        String n = name.toLowerCase();
        if (n.contains("ignition") || n.contains("movement") || n.contains("brake") || n.contains("pto")) {
            return "BOOLEAN";
        }
        if (n.contains("ibutton") || n.contains("rfid") || n.contains("vin") || n.contains("imei")) {
            return "TEXT";
        }
        return "NUMBER";
    }
}
