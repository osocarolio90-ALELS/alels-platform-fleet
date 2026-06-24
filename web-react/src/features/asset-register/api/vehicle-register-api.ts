import { api } from "@/lib/api";

const BASE = "/api/asset-register/vehicles";

export type VehicleRegisterRow = {
  id: number;
  companyId: number;
  companyName: string;
  vehicleCode: string;
  vehicleName?: string | null;
  plateNumber: string;
  vehicleTypeId?: number | null;
  vehicleType?: string | null;
  brandId?: number | null;
  brand?: string | null;
  modelId?: number | null;
  model?: string | null;
  yearManufacture?: number | null;
  energyCode?: string | null;
  energyName?: string | null;
  energyPriceSnapshot: number;
  energyCurrency: string;
  countryCode: string;
  countryName?: string | null;
  ownershipTypeId?: number | null;
  ownership?: string | null;
  capacityValue?: number | null;
  capacityUnitId?: number | null;
  capacityUnit?: string | null;
  operationalStatus: string;
  createdAt?: string | null;
  createdByEmail?: string | null;
  updatedAt?: string | null;
};

export type VehicleRegisterInput = {
  companyId?: number | null;
  vehicleCode?: string | null;
  vehicleName?: string | null;
  plateNumber: string;
  vehicleTypeId?: number | null;
  brandId?: number | null;
  modelId?: number | null;
  yearManufacture?: number | null;
  countryCode?: string | null;
  energyCode?: string | null;
  ownershipTypeId?: number | null;
  capacityValue?: number | null;
  capacityUnitId?: number | null;
  operationalStatus?: string | null;
  notes?: string | null;
};

export type VehicleLookupOption = {
  id: number;
  label: string;
  code?: string | null;
  extra?: string | null;
};

export async function getVehicles() {
  const response = await api.get<VehicleRegisterRow[]>(BASE);
  return response.data;
}

export async function getVehicleCompanyOptions() {
  const response = await api.get<VehicleLookupOption[]>(`${BASE}/company-options`);
  return response.data;
}

export async function createVehicle(input: VehicleRegisterInput) {
  const response = await api.post<{ success: boolean; id: number }>(BASE, input);
  return response.data;
}

export async function updateVehicle(id: number, input: VehicleRegisterInput) {
  const response = await api.put<{ success: boolean }>(`${BASE}/${id}`, input);
  return response.data;
}

export async function deleteVehicle(id: number) {
  const response = await api.delete<{ success: boolean }>(`${BASE}/${id}`);
  return response.data;
}

export async function setVehicleMaintenance(id: number, active: boolean) {
  const response = await api.post<{ success: boolean }>(`${BASE}/${id}/maintenance`, null, { params: { active } });
  return response.data;
}
