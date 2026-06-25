import { api } from "@/lib/api";

export type LicenseMasterRow = {
  id: number;
  countryCode: string;
  countryName: string;
  code: string;
  name: string;
  active: boolean;
  status: string;
  createdAt?: string | null;
  createdBy?: string | null;
  updatedAt?: string | null;
};

export type LicenseMasterInput = {
  countryCode?: string | null;
  countryName: string;
  name: string;
  active?: boolean | null;
};

const BASE = "/api/master-data/license-master";

export async function getLicenseMaster() {
  const response = await api.get<LicenseMasterRow[]>(BASE);
  return response.data;
}

export async function getLicenseOptions(countryCode?: string | null) {
  const response = await api.get<LicenseMasterRow[]>(`${BASE}/options`, { params: countryCode ? { countryCode } : undefined });
  return response.data;
}

export async function createLicenseMaster(input: LicenseMasterInput) {
  const response = await api.post<{ success: boolean; id: number }>(BASE, input);
  return response.data;
}

export async function updateLicenseMaster(id: number, input: LicenseMasterInput) {
  const response = await api.put<{ success: boolean }>(`${BASE}/${id}`, input);
  return response.data;
}

export async function deleteLicenseMaster(id: number) {
  const response = await api.delete<{ success: boolean }>(`${BASE}/${id}`);
  return response.data;
}

export async function setLicenseMasterActive(id: number, active: boolean) {
  const response = await api.put<{ success: boolean }>(`${BASE}/${id}/active`, { active });
  return response.data;
}
