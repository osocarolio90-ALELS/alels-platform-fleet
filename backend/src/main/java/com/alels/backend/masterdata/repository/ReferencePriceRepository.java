package com.alels.backend.masterdata.repository;

import org.springframework.stereotype.Repository;

/**
 * Marker repository for Master Data Reference Price domain.
 * Current implementation delegates to existing EnergyReferenceRepository to keep backward compatibility with migration 009/010.
 */
@Repository
public class ReferencePriceRepository {
}
