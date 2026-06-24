package com.alels.backend.organization.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.alels.backend.organization.repository.OrganizationRepository;

@Service
public class OrganizationWastedRetentionService {
    private static final Logger log = LoggerFactory.getLogger(OrganizationWastedRetentionService.class);
    private final OrganizationRepository repository;

    public OrganizationWastedRetentionService(OrganizationRepository repository) {
        this.repository = repository;
    }

    @Scheduled(cron = "0 5 2 * * *")
    public void purgeExpiredOrganizationWasted() {
        try {
            long deletedRows = repository.purgeExpiredWasted();
            if (deletedRows > 0) {
                log.info("Purged {} expired organization wasted rows.", deletedRows);
            }
        } catch (DataAccessException ex) {
            log.warn("Organization wasted purge skipped: {}", ex.getMessage());
        }
    }
}
