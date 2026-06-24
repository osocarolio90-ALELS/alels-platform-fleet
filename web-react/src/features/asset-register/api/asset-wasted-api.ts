import { api } from "@/lib/api";

const BASE = "/api/asset-register/wasted";

export type AssetWastedType = "VEHICLE" | "DEVICE" | "DRIVER";

export type AssetWastedRow = {
  itemType: AssetWastedType | string;
  id: number;
  companyId?: number | null;
  companyName?: string | null;
  name: string;
  code?: string | null;
  extra?: string | null;
  status?: string | null;
  deletedAt?: string | null;
  deletedBy?: number | null;
  deletedByEmail?: string | null;
  deletePermanentAt?: string | null;
  remainingDays?: number | null;
  deletedReason?: string | null;
};

export async function getAssetWastedItems(type: AssetWastedType) {
  const response = await api.get<AssetWastedRow[]>(BASE, { params: { type } });
  return response.data;
}

export async function restoreAssetWasted(type: AssetWastedType, id: number) {
  const response = await api.post<{ success: boolean }>(`${BASE}/${pathFor(type)}/${id}/restore`);
  return response.data;
}

export async function permanentDeleteAssetWasted(type: AssetWastedType, id: number) {
  const response = await api.post<{ success: boolean }>(`${BASE}/${pathFor(type)}/${id}/permanent-delete`);
  return response.data;
}

function pathFor(type: AssetWastedType) {
  switch (type) {
    case "DEVICE": return "devices";
    case "DRIVER": return "drivers";
    default: return "vehicles";
  }
}
