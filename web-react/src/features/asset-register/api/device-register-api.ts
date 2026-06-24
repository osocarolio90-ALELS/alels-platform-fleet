import { api } from "@/lib/api";

export type DeviceLookupOption = { id: number; label: string; code?: string | null; extra?: string | null };

export type DeviceRegisterRow = {
  id: number;
  companyId: number;
  companyName: string;
  deviceBrandId?: number | null;
  deviceBrand?: string | null;
  deviceModelId?: number | null;
  deviceModel?: string | null;
  imei: string;
  gsmNumber?: string | null;
  tcpHost?: string | null;
  tcpPort?: number | null;
  protocolCode?: string | null;
  parserCode?: string | null;
  dictionaryCode?: string | null;
  status: "ONLINE" | "OFFLINE" | string;
  createdAt?: string | null;
  createdBy?: string | null;
  updatedAt?: string | null;
};

export type DeviceRegisterInput = {
  companyId?: number | null;
  deviceBrandId?: number | null;
  deviceModelId?: number | null;
  imei: string;
  gsmNumber?: string | null;
  tcpHost?: string | null;
  tcpPort?: number | null;
  registerStatus?: string | null;
  notes?: string | null;
};

const BASE = "/api/asset-register/devices";

export async function getDevices() {
  const response = await api.get<DeviceRegisterRow[]>(BASE);
  return response.data;
}
export async function createDevice(input: DeviceRegisterInput) {
  const response = await api.post<{ success: boolean; id: number }>(BASE, input);
  return response.data;
}
export async function updateDevice(id: number, input: DeviceRegisterInput) {
  const response = await api.put<{ success: boolean }>(`${BASE}/${id}`, input);
  return response.data;
}
export async function deleteDevice(id: number) {
  const response = await api.delete<{ success: boolean }>(`${BASE}/${id}`);
  return response.data;
}
export async function getDeviceCompanyOptions() {
  const response = await api.get<DeviceLookupOption[]>(`${BASE}/company-options`);
  return response.data;
}
export async function getDeviceBrandOptions() {
  const response = await api.get<DeviceLookupOption[]>(`${BASE}/brand-options`);
  return response.data;
}
export async function getDeviceModelOptions(brandId?: number | null) {
  const response = await api.get<DeviceLookupOption[]>(`${BASE}/model-options`, { params: brandId ? { brandId } : undefined });
  return response.data;
}
