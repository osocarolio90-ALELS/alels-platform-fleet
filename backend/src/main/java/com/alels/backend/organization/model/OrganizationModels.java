package com.alels.backend.organization.model;

import java.time.OffsetDateTime;

public final class OrganizationModels {
    private OrganizationModels() {}

    public record CompanyRow(
            Long id,
            Long parentCompanyId,
            String parentCompanyName,
            String companyName,
            String companyCode,
            String companyType,
            String type,
            String plan,
            Integer monthPacket,
            Long storageQuotaMb,
            Long storageUsedMb,
            String country,
            String status,
            String subscriptionStatus,
            Boolean isInternal,
            OffsetDateTime firstLoginAt,
            OffsetDateTime startedAt,
            OffsetDateTime expiredAt,
            OffsetDateTime createdAt,
            Long createdBy,
            String createdByName,
            String createdByEmail,
            OffsetDateTime updatedAt,
            OffsetDateTime deletedAt,
            OffsetDateTime deletePermanentAt,
            String deletedReason
    ) {}

    public record UserRow(
            Long id,
            Long companyId,
            String companyName,
            String parentCompanyName,
            String username,
            String fullName,
            String email,
            String role,
            String status,
            OffsetDateTime firstLoginAt,
            OffsetDateTime lastLoginAt,
            OffsetDateTime createdAt,
            Long createdBy,
            String createdByName,
            String createdByEmail,
            OffsetDateTime updatedAt,
            OffsetDateTime deletedAt,
            OffsetDateTime deletePermanentAt,
            String deletedReason
    ) {}

    public record OptionRow(Long id, String label) {}
    public record ExistsResponse(boolean exists) {}
}
