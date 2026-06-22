package com.alels.backend.serverops.auth.dto;

public record LoginResponse(boolean success, String message, String token, UserSession user) {
    public record UserSession(Long id, Long companyId, String companyName, String username, String fullName, String email, String role, String status, String profilePhotoUrl) {}
}
