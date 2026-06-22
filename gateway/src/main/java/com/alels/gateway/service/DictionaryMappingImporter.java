package com.alels.gateway.service;

import com.alels.gateway.model.DictionaryImportJobInfo;
import com.alels.gateway.repository.DictionaryImportRepository;

import java.util.List;

public class DictionaryMappingImporter {

    private static final DictionaryImportRepository repository =
            new DictionaryImportRepository();

    private DictionaryMappingImporter() {
    }

    public static void executePendingImports() {

        List<DictionaryImportJobInfo> jobs =
                repository.findPendingJobs();

        if (jobs.isEmpty()) {

            System.out.println(
                    "[DICT IMPORT] no pending jobs"
            );

            return;
        }

        for (DictionaryImportJobInfo job : jobs) {

            try {

                System.out.println(
                        "[DICT IMPORT] START "
                                + job.getDictionaryCode()
                                + " file="
                                + job.getDictionaryFile()
                );

                /*
                 next version:

                 load json
                 parse avl ids
                 map normalized fields
                 insert device_io_mappings
                 */

                System.out.println(
                        "[DICT IMPORT] READY "
                                + job.getDictionaryCode()
                );

            } catch (Exception e) {

                System.err.println(
                        "[DICT IMPORT ERROR] "
                                + job.getDictionaryCode()
                                + " "
                                + e.getMessage()
                );
            }
        }
    }
}