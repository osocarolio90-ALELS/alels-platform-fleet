package com.alels.backend.masterdata.dto;

public final class MasterWastedDtos {
    private MasterWastedDtos() {}

    public record MasterWastedRow(
            Long id,
            String category,
            String masterType,
            String code,
            String name,
            String parentName,
            String status,
            String deletedAt,
            Integer remainingDays,
            String deletePermanentAt,
            String deletedBy,
            String reason
    ) {}
}
