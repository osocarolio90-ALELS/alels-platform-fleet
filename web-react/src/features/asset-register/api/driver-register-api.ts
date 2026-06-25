import { api } from "@/lib/api";

export type DriverRegisterRow = {
  id: number;
  companyId: number;
  companyName?: string | null;
  driverId: string;
  employeeId?: string | null;
  driverName: string;
  licenseNumber: string;
  countryCode: string;
  countryName?: string | null;
  licenseMasterId: number;
  licenseType?: string | null;
  phoneNumber: string;
  rfidIbutton?: string | null;
  status: string;
  createdAt?: string | null;
  createdBy?: string | null;
};

export type DriverLookupOption = {
  id: number;
  label: string;
  value: string;
  extra?: string | null;
};

export type DriverRegisterInput = {
  companyId?: number | null;
  driverId: string;
  employeeId?: string | null;
  driverName: string;
  licenseNumber: string;
  countryCode: string;
  licenseMasterId?: number | null;
  phoneNumber: string;
  rfidIbutton?: string | null;
  status?: string | null;
};

const BASE = "/api/asset-register/drivers";

export async function getDrivers() {
  const response = await api.get<DriverRegisterRow[]>(BASE);
  return response.data;
}

export async function getDriverCompanyOptions() {
  const response = await api.get<DriverLookupOption[]>(`${BASE}/company-options`);
  return response.data;
}

export async function createDriver(input: DriverRegisterInput) {
  const response = await api.post<{ success: boolean; id: number }>(BASE, input);
  return response.data;
}

export async function updateDriver(id: number, input: DriverRegisterInput) {
  const response = await api.put<{ success: boolean }>(`${BASE}/${id}`, input);
  return response.data;
}

export async function deleteDriver(id: number) {
  const response = await api.delete<{ success: boolean }>(`${BASE}/${id}`);
  return response.data;
}

export async function suspendDriver(id: number) {
  const response = await api.put<{ success: boolean }>(`${BASE}/${id}/suspend`);
  return response.data;
}

export async function activateDriver(id: number) {
  const response = await api.put<{ success: boolean }>(`${BASE}/${id}/activate`);
  return response.data;
}
