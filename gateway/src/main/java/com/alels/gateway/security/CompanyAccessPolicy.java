package com.alels.gateway.security;

public class CompanyAccessPolicy {

    public static final String ALELS_SUPER_ADMIN = "ALELS_SUPER_ADMIN";
    public static final String COMPANY_ADMIN = "COMPANY_ADMIN";
    public static final String COMPANY_USER = "COMPANY_USER";

    public static boolean isAlelsSuperAdmin(String role) {
        return ALELS_SUPER_ADMIN.equalsIgnoreCase(role);
    }

    public static boolean isCompanyAdmin(String role) {
        return COMPANY_ADMIN.equalsIgnoreCase(role);
    }

    public static boolean isCompanyUser(String role) {
        return COMPANY_USER.equalsIgnoreCase(role);
    }

    public static boolean canManageCompany(String role) {
        return isAlelsSuperAdmin(role);
    }

    public static boolean canManageUsers(String role) {
        return isAlelsSuperAdmin(role) || isCompanyAdmin(role);
    }

    public static boolean canAccessAllCompanies(String role) {
        return isAlelsSuperAdmin(role);
    }

    public static boolean canAccessCompany(
            String role,
            Long userCompanyId,
            Long targetCompanyId
    ) {
        if (isAlelsSuperAdmin(role)) {
            return true;
        }

        if (userCompanyId == null || targetCompanyId == null) {
            return false;
        }

        return userCompanyId.equals(targetCompanyId);
    }
}