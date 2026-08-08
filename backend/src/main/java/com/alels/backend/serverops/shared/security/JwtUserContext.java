package com.alels.backend.serverops.shared.security;

public record JwtUserContext(Long userId, Long companyId, String role, String email, long sessionVersion) {
    public boolean isSuperAdmin() {
        return "SUPERADMIN".equals(normalizedRole());
    }

    public String normalizedRole() {
        return RoleNormalizer.normalize(role);
    }
}
