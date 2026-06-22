import { api } from "@/lib/api";

const ENERGY_PRICE_BASE = "/api/asset-register/energy-prices";

export type EnergyCountryOption = {
  countryCode: string;
  countryName: string;
  currency: string;
  usdToLocalRate?: number | null;
  sourceName?: string | null;
  sourceUrl?: string | null;
};

export type EnergyPriceRow = {
  id: number;
  companyId: number;
  companyName?: string | null;
  energyId: number;
  energyCode: string;
  energyName: string;
  energyGroup: string;
  unit: string;
  vehicleUsage?: string | null;
  countryCode?: string | null;
  country: string;
  currency: string;
  priceEnergy: number;
  referencePriceCountryIdr?: number | null;
  referencePriceGlobalUsd?: number | null;
  fxRateToIdr?: number | null;
  priceSource?: string | null;
  referenceSource?: string | null;
  sourceUrl?: string | null;
  lastReferenceUpdateAt?: string | null;
  manualOverride?: boolean | null;
  updatedAt?: string | null;
  updatedByEmail?: string | null;
};

export type EnergyPriceUpdateInput = {
  countryCode?: string | null;
  country?: string | null;
  currency?: string | null;
  priceEnergy?: number | null;
};

export async function getEnergyPrices(countryCode?: string | null) {
  const response = await api.get<EnergyPriceRow[]>(ENERGY_PRICE_BASE, {
    params: countryCode ? { countryCode } : undefined
  });
  return response.data;
}

export async function getEnergyPriceCountries() {
  const response = await api.get<EnergyCountryOption[]>(`${ENERGY_PRICE_BASE}/countries`);
  return response.data;
}

export async function updateEnergyPrice(id: number, input: EnergyPriceUpdateInput) {
  const response = await api.put<{ success: boolean }>(`${ENERGY_PRICE_BASE}/${id}`, input);
  return response.data;
}
