package com.alels.backend.assetregister.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.alels.backend.assetregister.repository.AssetWastedRepository;

@Service
public class AssetWastedRetentionService {
    private static final Logger log = LoggerFactory.getLogger(AssetWastedRetentionService.class);
    private final AssetWastedRepository repository;

    public AssetWastedRetentionService(AssetWastedRepository repository) {
        this.repository = repository;
    }

    @Scheduled(cron = "0 0 2 * * *")
    public void purgeExpiredAssetWasted() {
        long deletedRows = repository.purgeExpiredWasted();
        if (deletedRows > 0) {
            log.info("Purged {} expired asset wasted rows.", deletedRows);
        }
    }
}
