import { api } from "@/lib/api";

export type Company = {
  id: number;
  parentCompanyId?: number | null;
  parentCompanyName?: string | null;
  companyName: string;
  companyCode?: string | null;
  companyType?: string | null;
  type?: string | null;
  plan?: string | null;
  monthPacket?: number | null;
  storageQuotaMb?: number | null;
  storageUsedMb?: number | null;
  storageSize?: number | null;
  storageUnit?: string | null;
  country?: string | null;
  status?: string | null;
  subscriptionStatus?: string | null;
  isInternal?: boolean | null;
  firstLoginAt?: string | null;
  startedAt?: string | null;
  startAt?: string | null;
  expiredAt?: string | null;
  createdAt?: string | null;
  createdBy?: number | null;
  createdByName?: string | null;
  createdByEmail?: string | null;
  updatedAt?: string | null;
  deletedAt?: string | null;
  deletePermanentAt?: string | null;
  deletedReason?: string | null;
};

export type User = {
  id: number;
  companyId?: number | null;
  companyName?: string | null;
  parentCompanyName?: string | null;
  username?: string | null;
  fullName?: string | null;
  email?: string | null;
  role?: string | null;
  status?: string | null;
  firstLoginAt?: string | null;
  lastLoginAt?: string | null;
  createdAt?: string | null;
  createdBy?: number | null;
  createdByName?: string | null;
  createdByEmail?: string | null;
  updatedAt?: string | null;
  deletedAt?: string | null;
  deletePermanentAt?: string | null;
  deletedReason?: string | null;
  profilePhotoUrl?: string | null;
};

export type OrganizationWastedItem = {
  itemType: "COMPANY" | "USER" | string;
  id: number;
  name: string;
  companyName?: string | null;
  roleOrType?: string | null;
  deletedAt?: string | null;
  deletePermanentAt?: string | null;
  deletedReason?: string | null;
  deletedBy?: number | null;
  deletedByEmail?: string | null;
};

export type OptionRow = { id?: number; label: string };

export type CompanyRegisterInput = {
  parentCompanyId?: number | null;
  companyName: string;
  companyType: string;
  type?: string;
  plan: string;
  monthPacket?: number | null;
  storageSize?: number | null;
  storageUnit?: string | null;
  country: string;
  adminFullName: string;
  username?: string;
  adminEmail: string;
  userEmail?: string;
  adminRole: string;
  temporaryPassword: string;
};

export type CompanyRegisterResponse = {
  companyId: number;
  userId: number;
  companyName: string;
  adminEmail: string;
  adminRole: string;
  generatedPassword: string;
  status: string;
};

export type UpdateUserResponse = {
  success: boolean;
  generatedPassword?: string;
};

const ORGANIZATION_BASE = "/api/organizations";

export async function getCompanies() {
  const response = await api.get<Company[]>(`${ORGANIZATION_BASE}/companies`);
  return response.data;
}

export async function getUsers() {
  const response = await api.get<User[]>(`${ORGANIZATION_BASE}/users`);
  return response.data;
}

export async function getOrganizationWastedItems() {
  const response = await api.get<OrganizationWastedItem[]>(`${ORGANIZATION_BASE}/wasted`);
  return response.data;
}

export async function getParentCompanyOptions() {
  const response = await api.get<OptionRow[]>(`${ORGANIZATION_BASE}/parent-options`);
  return response.data;
}

export async function checkCompanyName(name: string, excludeId?: number | null) {
  const response = await api.get<{ exists: boolean; available: boolean }>(
    `${ORGANIZATION_BASE}/company/check-name`,
    { params: { name, excludeId } }
  );

  return response.data;
}

export async function createCompany(input: CompanyRegisterInput) {
  const response = await api.post<CompanyRegisterResponse>(`${ORGANIZATION_BASE}/company-registration`, input);
  return response.data;
}

export async function updateCompany(id: number, input: { companyName: string; parentCompanyId?: number | null }) {
  const response = await api.put<{ success: boolean }>(`${ORGANIZATION_BASE}/companies/${id}`, input);
  return response.data;
}

export async function updateUser(id: number, input: { username: string; fullName: string; email: string; temporaryPassword?: string }) {
  const response = await api.put<UpdateUserResponse>(`${ORGANIZATION_BASE}/users/${id}`, input);
  return response.data;
}

export async function companyAction(id: number, action: "delete" | "suspend" | "activate" | "restore" | "permanent-delete") {
  const response = await api.post<{ success: boolean }>(`${ORGANIZATION_BASE}/companies/${id}/${action}`);
  return response.data;
}

export async function userAction(id: number, action: "delete" | "suspend" | "activate" | "restore" | "permanent-delete") {
  const response = await api.post<{ success: boolean }>(`${ORGANIZATION_BASE}/users/${id}/${action}`);
  return response.data;
}

export async function getCompanyOptions() {
  const response = await api.get<OptionRow[]>(`${ORGANIZATION_BASE}/company-options`);
  return response.data;
}

export async function createUser(input: { companyId?: number | null; username: string; fullName: string; email: string; role: string; temporaryPassword: string }) {
  const response = await api.post<{ success: boolean; userId: number }>(`${ORGANIZATION_BASE}/users`, input);
  return response.data;
}
