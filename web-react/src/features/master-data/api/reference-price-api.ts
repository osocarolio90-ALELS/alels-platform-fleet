import { api } from "@/lib/api";

const BASE = "/api/asset-register/energy-reference-prices";

export type EnergyReferenceCountryRow = {
  id: number;
  countryCode: string;
  countryName: string;
  currency: string;
  usdToLocalRate: number;
  sourceName?: string | null;
  sourceUrl?: string | null;
  providerStatus?: string | null;
  providerLastUpdateAt?: string | null;
  status?: string | null;
  updatedAt?: string | null;
};

export type EnergyReferenceCountryInput = {
  countryCode: string;
  countryName: string;
  currency: string;
  usdToLocalRate: number | null;
  sourceName?: string | null;
  sourceUrl?: string | null;
};

export type EnergyReferenceRow = {
  id: number;
  energyId: number;
  energyCode: string;
  energyName: string;
  energyGroup: string;
  unit: string;
  countryCode: string;
  countryName: string;
  currency: string;
  referencePriceCountry: number;
  referencePriceGlobalUsd: number;
  providerReferencePriceCountry: number;
  providerReferencePriceGlobalUsd: number;
  sourceName?: string | null;
  sourceDetail?: string | null;
  sourceUrl?: string | null;
  providerStatus?: string | null;
  providerLastUpdateAt?: string | null;
  lastSyncAt?: string | null;
  updatedAt?: string | null;
  updatedByEmail?: string | null;
};

export type EnergyReferenceUpdateInput = {
  referencePriceCountry?: number | null;
  referencePriceGlobalUsd?: number | null;
  providerReferencePriceCountry?: number | null;
  providerReferencePriceGlobalUsd?: number | null;
  sourceName?: string | null;
  sourceDetail?: string | null;
  sourceUrl?: string | null;
  providerStatus?: string | null;
};

export async function getEnergyReferenceCountries() {
  const response = await api.get<EnergyReferenceCountryRow[]>(`${BASE}/countries`);
  return response.data;
}

export async function createEnergyReferenceCountry(input: EnergyReferenceCountryInput) {
  const response = await api.post<{ success: boolean }>(`${BASE}/countries`, input);
  return response.data;
}

export async function getEnergyReferences(countryCode?: string | null) {
  const response = await api.get<EnergyReferenceRow[]>(BASE, { params: countryCode ? { countryCode } : undefined });
  return response.data;
}

export async function updateEnergyReferenceProvider(countryCode?: string | null) {
  const response = await api.post<{ success: boolean; updatedRows: number; message: string }>(`${BASE}/update-provider`, { countryCode: countryCode || null });
  return response.data;
}

export async function updateEnergyReference(id: number, input: EnergyReferenceUpdateInput) {
  const response = await api.put<{ success: boolean }>(`${BASE}/${id}`, input);
  return response.data;
}


export async function deleteEnergyReference(id: number) {
  const response = await api.delete<{ success: boolean }>(`${BASE}/${id}`);
  return response.data;
}
