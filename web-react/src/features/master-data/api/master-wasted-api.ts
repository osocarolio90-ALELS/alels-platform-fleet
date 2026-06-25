import { api } from "@/lib/api";

export type MasterWastedType = "LICENSE" | "VEHICLE" | "DEVICE" | "PRICE";

export type MasterWastedRow = {
  itemType: string;
  id: number;
  name?: string | null;
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

const BASE = "/api/master-data/wasted";

export async function getMasterWasted(type: MasterWastedType) {
  const response = await api.get<MasterWastedRow[]>(`${BASE}/${type}`);
  return response.data;
}

export async function restoreMasterWasted(type: MasterWastedType, id: number) {
  const response = await api.post<{ success: boolean }>(`${BASE}/${type}/${id}/restore`);
  return response.data;
}

export async function permanentDeleteMasterWasted(type: MasterWastedType, id: number) {
  const response = await api.post<{ success: boolean }>(`${BASE}/${type}/${id}/permanent-delete`);
  return response.data;
}
