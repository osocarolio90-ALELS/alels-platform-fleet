package com.alels.backend.organization.dto;

public final class OrganizationDtos {
    private OrganizationDtos() {}

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
            Long storageSize,
            Long storageUsedMb,
            String storageUnit,
            String country,
            String status,
            String subscriptionStatus,
            Boolean isInternal,
            String firstLoginAt,
            String startedAt,
            String expiredAt,
            String createdAt,
            Long createdBy,
            String createdByName,
            String createdByEmail,
            String updatedAt,
            String deletedAt,
            String deletePermanentAt,
            String deletedReason,
            Long activeUserCount
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
            String firstLoginAt,
            String lastLoginAt,
            String createdAt,
            Long createdBy,
            String createdByName,
            String createdByEmail,
            String updatedAt,
            String deletedAt,
            String deletePermanentAt,
            String deletedReason,
            String profilePhotoUrl
    ) {}

    public record WastedRow(
            String itemType,
            Long id,
            String name,
            String companyName,
            String roleOrType,
            String deletedAt,
            String deletePermanentAt,
            String deletedReason,
            Long deletedBy,
            String deletedByEmail
    ) {}

    public record OptionRow(Long id, String label) {}
    public record CheckNameResponse(boolean exists) {}

    public record CompanyRegisterRequest(
            Long parentCompanyId,
            String companyName,
            String companyType,
            String type,
            String plan,
            Integer monthPacket,
            Long storageSize,
            String storageUnit,
            String country,
            String adminFullName,
            String username,
            String adminEmail,
            String userEmail,
            String adminRole,
            String temporaryPassword
    ) {}

    public record CompanyRegisterResponse(
            Long companyId,
            Long userId,
            String companyName,
            String adminEmail,
            String adminRole,
            String generatedPassword,
            String status
    ) {}

    public record CompanyUpdateRequest(String companyName, Long parentCompanyId) {}
    public record UserCreateRequest(Long companyId, String username, String fullName, String email, String role, String temporaryPassword) {}
    public record UserUpdateRequest(String username, String fullName, String email, String temporaryPassword) {}
}
