import { api } from "@/lib/api";

export type DeviceBrandRow = {
  id: number;
  brandCode: string;
  brandName: string;
  description?: string | null;
  active: boolean;
  system: boolean;
  sortOrder: number;
  createdAt?: string | null;
  createdBy?: string | null;
  updatedAt?: string | null;
};

export type DeviceModelRow = {
  id: number;
  brandId?: number | null;
  brandCode?: string | null;
  brandName?: string | null;
  modelCode: string;
  modelName: string;
  protocolCode?: string | null;
  parserCode?: string | null;
  dictionaryCode?: string | null;
  description?: string | null;
  active: boolean;
  system: boolean;
  sortOrder: number;
  createdAt?: string | null;
  createdBy?: string | null;
  updatedAt?: string | null;
};

export type DeviceMasterInput = {
  brandId?: number | null;
  brandCode?: string | null;
  brandName?: string | null;
  modelCode?: string | null;
  modelName?: string | null;
  protocolCode?: string | null;
  parserCode?: string | null;
  dictionaryCode?: string | null;
  description?: string | null;
  active?: boolean | null;
  sortOrder?: number | null;
};

const BASE = "/api/master-data/device-master";

export async function getDeviceBrands() {
  const response = await api.get<DeviceBrandRow[]>(`${BASE}/brands`);
  return response.data;
}

export async function createDeviceBrand(input: DeviceMasterInput) {
  const response = await api.post<{ success: boolean; id: number }>(`${BASE}/brands`, input);
  return response.data;
}

export async function updateDeviceBrand(id: number, input: DeviceMasterInput) {
  const response = await api.put<{ success: boolean }>(`${BASE}/brands/${id}`, input);
  return response.data;
}

export async function deleteDeviceBrand(id: number) {
  const response = await api.delete<{ success: boolean }>(`${BASE}/brands/${id}`);
  return response.data;
}

export async function getDeviceModels(brandId?: number | null) {
  const response = await api.get<DeviceModelRow[]>(`${BASE}/models`, { params: brandId ? { brandId } : undefined });
  return response.data;
}

export async function createDeviceModel(input: DeviceMasterInput) {
  const response = await api.post<{ success: boolean; id: number }>(`${BASE}/models`, input);
  return response.data;
}

export async function updateDeviceModel(id: number, input: DeviceMasterInput) {
  const response = await api.put<{ success: boolean }>(`${BASE}/models/${id}`, input);
  return response.data;
}

export async function deleteDeviceModel(id: number) {
  const response = await api.delete<{ success: boolean }>(`${BASE}/models/${id}`);
  return response.data;
}
