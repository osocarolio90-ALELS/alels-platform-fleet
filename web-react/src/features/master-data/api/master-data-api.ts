import { api } from "@/lib/api";

export type MasterDataRow = {
  id: number;
  code: string;
  name: string;
  description?: string | null;
  status: string;
  isSystem: boolean;
  sortOrder: number;
  createdAt?: string | null;
  updatedAt?: string | null;
};

export type VehicleModelRow = MasterDataRow & {
  brandId?: number | null;
  brandName?: string | null;
  modelCode?: string | null;
  modelName?: string | null;
  active?: boolean | null;
  system?: boolean | null;
};

export type MasterDataInput = {
  code?: string | null;
  name: string;
  description?: string | null;
  status?: string | null;
  sortOrder?: number | null;
};

export type VehicleModelInput = MasterDataInput & {
  brandId?: number | null;
};

const BASE = "/api/master-data";

export async function getMasterData(resource: string) {
  const response = await api.get<MasterDataRow[]>(`${BASE}/${resource}`);
  return response.data;
}

export async function createMasterData(resource: string, input: MasterDataInput) {
  const response = await api.post<{ success: boolean; id: number }>(`${BASE}/${resource}`, input);
  return response.data;
}

export async function updateMasterData(resource: string, id: number, input: MasterDataInput) {
  const response = await api.put<{ success: boolean }>(`${BASE}/${resource}/${id}`, input);
  return response.data;
}

export async function deleteMasterData(resource: string, id: number) {
  const response = await api.delete<{ success: boolean }>(`${BASE}/${resource}/${id}`);
  return response.data;
}

export async function getVehicleModels(brandId?: number | null) {
  const response = await api.get<VehicleModelRow[]>(`${BASE}/vehicle-models`, {
    params: brandId ? { brandId } : undefined
  });
  return response.data.map((row) => ({
    ...row,
    code: row.modelCode ?? row.code,
    name: row.modelName ?? row.name,
    status: row.active === false ? "INACTIVE" : "ACTIVE",
    isSystem: row.system ?? row.isSystem ?? false,
    modelCode: row.modelCode ?? row.code,
    modelName: row.modelName ?? row.name
  }));
}

export async function createVehicleModel(input: VehicleModelInput) {
  const response = await api.post<{ success: boolean; id: number }>(`${BASE}/vehicle-models`, input);
  return response.data;
}

export async function updateVehicleModel(id: number, input: VehicleModelInput) {
  const response = await api.put<{ success: boolean }>(`${BASE}/vehicle-models/${id}`, input);
  return response.data;
}

export async function deleteVehicleModel(id: number) {
  const response = await api.delete<{ success: boolean }>(`${BASE}/vehicle-models/${id}`);
  return response.data;
}


export type MasterOptionRow = MasterDataRow & { active: boolean };

export type MasterCountryRow = {
  id: number;
  countryCode: string;
  countryName: string;
  currency: string;
  usdToLocalRate?: number | null;
  sourceName?: string | null;
  sourceUrl?: string | null;
  status?: string | null;
  updatedAt?: string | null;
};

const RESOURCE_ALIASES: Record<string, string> = {
  vehicleTypes: "vehicle-types",
  vehicleBrands: "vehicle-brands",
  ownershipTypes: "ownership-types",
  capacityUnits: "capacity-units"
};

function normalizeResource(resource: string) {
  return RESOURCE_ALIASES[resource] ?? resource;
}

export async function getMasterOptions(resource: string) {
  const rows = await getMasterData(normalizeResource(resource));
  return rows.map((row) => ({
    ...row,
    active: row.status ? row.status.toUpperCase() === "ACTIVE" : true
  })) as MasterOptionRow[];
}

export async function getMasterCountries() {
  const response = await api.get<MasterCountryRow[]>(`${BASE}/countries`);
  return response.data;
}
