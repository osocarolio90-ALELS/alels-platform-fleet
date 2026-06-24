export type AlelsRole = "SUPERADMIN" | "ADMIN" | "OWNER" | "MANAGER" | "TECHUSER" | "CLIENTUSER";

export const ALL_ROLES: AlelsRole[] = ["SUPERADMIN", "ADMIN", "OWNER", "MANAGER", "TECHUSER", "CLIENTUSER"];
export const SERVER_MONITOR_ROLES: AlelsRole[] = ["SUPERADMIN"];
export const ORGANIZATION_ROLES: AlelsRole[] = ["SUPERADMIN", "ADMIN", "OWNER", "MANAGER"];
export const ENERGY_REFERENCE_ROLES: AlelsRole[] = ["SUPERADMIN", "ADMIN"];
export const ENERGY_REFERENCE_EDIT_ROLES: AlelsRole[] = ["SUPERADMIN"];
export const ENERGY_PRICE_EDIT_ROLES: AlelsRole[] = ["SUPERADMIN", "ADMIN"];

export function normalizeRole(role?: string | null): AlelsRole | null {
  const normalized = (role || "").trim().toUpperCase().replace(/[\s_-]+/g, "");
  if (normalized === "TECHUSER") return "TECHUSER";
  if (normalized === "CLIENTUSER") return "CLIENTUSER";
  if (normalized === "ALELSSUPERADMIN") return "SUPERADMIN";
  return ALL_ROLES.includes(normalized as AlelsRole) ? (normalized as AlelsRole) : null;
}

export function hasRole(role: string | null | undefined, allowedRoles: readonly AlelsRole[]): boolean {
  const normalized = normalizeRole(role);
  return Boolean(normalized && allowedRoles.includes(normalized));
}

export function getDefaultRouteForRole(_role?: string | null): string {
  const normalized = normalizeRole(_role);
  if (normalized && ORGANIZATION_ROLES.includes(normalized)) return "/organization/company-list";
  return "/server-monitor/overview";
}

export const MASTER_DATA_ROLES: AlelsRole[] = ["SUPERADMIN", "ADMIN"];
export const MASTER_DATA_EDIT_ROLES: AlelsRole[] = ["SUPERADMIN"];
