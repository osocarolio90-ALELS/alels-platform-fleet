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

            String fieldCode =
                    NormalizedFieldMatcher.match(avl.getName());

            if (fieldCode == null) {
                continue;
            }

            boolean inserted =
                    upsertMapping(
                            deviceModelId,
                            sourceProtocol,
                            dictionaryCode,
                            avl,
                            fieldCode
                    );

            if (inserted) {
                imported++;
            }
        }

        return imported;
    }

    private static boolean upsertMapping(
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
                    dictionary_code
                )
                SELECT
                    ?,
                    ?,
                    ?,
                    nf.id,
                    nf.field_code,
                    ?,
                    ?,
                    nf.unit,
                    ?,
                    0,
                    ?,
                    'ACTIVE',
                    'AUTO_IMPORT',
                    ?
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
                    status = 'ACTIVE',
                    mapping_source = 'AUTO_IMPORT',
                    dictionary_code = EXCLUDED.dictionary_code
                """;

        try (
                Connection conn = DatabaseConfig.getConnection();
                PreparedStatement ps = conn.prepareStatement(sql)
        ) {
            ps.setLong(1, deviceModelId);
            ps.setString(2, sourceProtocol);
            ps.setString(3, avl.getAvlId());
            ps.setString(4, avl.getName());
            ps.setString(5, avl.getUnit());
            ps.setDouble(6, avl.getMultiplier() != null ? avl.getMultiplier() : 1.0);
            ps.setString(7, resolveValueType(avl.getName()));
            ps.setString(8, dictionaryCode);
            ps.setString(9, fieldCode);

            return ps.executeUpdate() > 0;

        } catch (Exception e) {
            System.err.println(
                    "[DICT MAPPING ERROR] dictionary="
                            + dictionaryCode
                            + " io="
                            + avl.getAvlId()
                            + " name="
                            + avl.getName()
                            + " error="
                            + e.getMessage()
            );
            throw new IllegalStateException(
                    "Failed to persist dictionary mapping " + dictionaryCode + "/" + avl.getAvlId(), e
            );
        }
    }

    private static String resolveValueType(String name) {
        if (name == null) {
            return "NUMBER";
        }

        String n = name.toLowerCase();

        if (n.contains("ignition")
                || n.contains("movement")
                || n.contains("brake")
                || n.contains("pto")) {
            return "BOOLEAN";
        }

        if (n.contains("ibutton")
                || n.contains("rfid")
                || n.contains("vin")
                || n.contains("imei")) {
            return "TEXT";
        }

        return "NUMBER";
    }
}
