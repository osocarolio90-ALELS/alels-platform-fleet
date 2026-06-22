package com.alels.gateway.service;

import java.util.List;

import com.alels.gateway.model.DictionaryAvlInfo;
import com.alels.gateway.model.DictionaryFileInfo;
import com.alels.gateway.repository.DictionaryImportHistoryRepository;
import com.alels.gateway.repository.DictionaryStartupRepository;

public class DictionaryStartupImporter {

    private static final String RESOURCE_BASE =
            "device-dictionary/";

    private DictionaryStartupImporter() {
    }

    public static void runStartupCheck() {

        List<DictionaryFileInfo> dictionaries =
                DictionaryStartupRepository.findActiveDictionaries();

        if (dictionaries.isEmpty()) {
            System.out.println("[DICT STARTUP] no ACTIVE dictionaries");
            return;
        }

        System.out.println(
                "[DICT STARTUP] active dictionaries="
                        + dictionaries.size()
        );

        for (DictionaryFileInfo dictionary : dictionaries) {

            String resourcePath =
                    RESOURCE_BASE + dictionary.getDictionaryFile();

            String checksum =
                    DictionaryChecksumUtil.sha256FromResource(resourcePath);

            if (checksum == null || checksum.isBlank()) {

                System.err.println(
                        "[DICT STARTUP] file missing dictionary="
                                + dictionary.getDictionaryCode()
                                + " file="
                                + dictionary.getDictionaryFile()
                );

                DictionaryImportHistoryRepository.saveFailed(
                        dictionary.getDictionaryRegistryId(),
                        dictionary.getDictionaryCode(),
                        dictionary.getDictionaryFile(),
                        "MISSING_FILE"
                );

                continue;
            }

            System.out.println(
                    "[DICT STARTUP] dictionary="
                            + dictionary.getDictionaryCode()
                            + " file="
                            + dictionary.getDictionaryFile()
                            + " checksum="
                            + checksum.substring(0, 12)
            );

            if (
                    DictionaryImportHistoryRepository.alreadyImported(
                            dictionary.getDictionaryCode(),
                            checksum
                    )
            ) {
                System.out.println(
                        "[DICT STARTUP] SKIP "
                                + dictionary.getDictionaryCode()
                                + " checksum unchanged"
                );
                continue;
            }

            try {

                List<DictionaryAvlInfo> avlList =
                        DictionaryParser.parse(
                                dictionary.getDictionaryFile()
                        );

                String sourceProtocol =
                        resolveSourceProtocol(
                                dictionary.getDictionaryCode()
                        );

                int imported =
                        DictionaryMappingGenerator.generate(
                                dictionary.getDeviceModelId(),
                                sourceProtocol,
                                dictionary.getDictionaryCode(),
                                avlList
                        );

                DictionaryImportHistoryRepository.saveSuccess(
                        dictionary.getDictionaryRegistryId(),
                        dictionary.getDictionaryCode(),
                        dictionary.getDictionaryFile(),
                        checksum,
                        imported
                );

                System.out.println(
                        "[DICT AUTO IMPORT] dictionary="
                                + dictionary.getDictionaryCode()
                                + " protocol="
                                + sourceProtocol
                                + " mappings="
                                + imported
                );

            } catch (Exception e) {

                DictionaryImportHistoryRepository.saveFailed(
                        dictionary.getDictionaryRegistryId(),
                        dictionary.getDictionaryCode(),
                        dictionary.getDictionaryFile(),
                        checksum
                );

                System.err.println(
                        "[DICT AUTO IMPORT ERROR] dictionary="
                                + dictionary.getDictionaryCode()
                                + " error="
                                + e.getMessage()
                );
            }
        }
    }

    private static String resolveSourceProtocol(
            String dictionaryCode
    ) {
        if (dictionaryCode == null) {
            return "ANY";
        }

        String code =
                dictionaryCode.toLowerCase();

        if (code.startsWith("alels")) {
            return "ALELS_JSON";
        }

        if (code.startsWith("fmc")
                || code.startsWith("fmb")
                || code.startsWith("ftc")) {
            return "TELTONIKA_AUTO";
        }

        return "ANY";
    }
}