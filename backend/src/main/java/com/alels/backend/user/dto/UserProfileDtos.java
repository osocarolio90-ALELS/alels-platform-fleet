package com.alels.backend.user.dto;

public final class UserProfileDtos {
    private UserProfileDtos() {}

    public record UserProfileResponse(
            Long id,
            Long companyId,
            String companyName,
            String username,
            String fullName,
            String email,
            String role,
            String status,
            String createdAt,
            String profilePhotoUrl
    ) {}

    public record UserProfileUpdateResponse(boolean success, UserProfileResponse profile) {}
}
