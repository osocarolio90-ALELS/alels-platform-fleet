package com.alels.backend.serverops.shared.security;

import java.util.Locale;

/**
 * Central role normalizer used by JWT, RBAC, and feature modules.
 * Keep this class as the single source of truth to avoid SUPER_ADMIN /
 * ALELS_SUPER_ADMIN / CLIENT_USER mismatches across backend APIs.
 */
public final class RoleNormalizer {
    private RoleNormalizer() {}

    public static String normalize(String role) {
        if (role == null || role.isBlank()) {
            return "GUEST";
        }

        String compact = role.trim()
                .toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9]", "");

        return switch (compact) {
            case "ALELSSUPERADMIN", "SUPERADMIN", "SUPER" -> "SUPERADMIN";
            case "ALELSADMIN", "ADMINISTRATOR", "ADMIN" -> "ADMIN";
            case "OWNER", "COMPANYOWNER" -> "OWNER";
            case "MANAGER", "COMPANYMANAGER" -> "MANAGER";
            case "TECHUSER", "TECH", "TECHNICIAN", "TECHNICALUSER" -> "TECHUSER";
            case "CLIENTUSER", "CLIENT", "CUSTOMERUSER" -> "CLIENTUSER";
            default -> compact;
        };
    }
}
