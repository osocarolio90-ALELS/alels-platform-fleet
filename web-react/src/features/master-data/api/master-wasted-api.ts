import { api } from "@/lib/api";

export type MasterWastedCategory = "vehicle" | "device" | "harga";

export type MasterWastedRow = {
  id: number;
  category: MasterWastedCategory;
  masterType: string;
  code: string;
  name: string;
  parentName?: string | null;
  status?: string | null;
  deletedAt?: string | null;
  remainingDays?: number | null;
  deletePermanentAt?: string | null;
  deletedBy?: string | null;
  reason?: string | null;
};

const BASE = "/api/master-data/wasted";

export async function getMasterWasted(category: MasterWastedCategory) {
  const response = await api.get<MasterWastedRow[]>(BASE, { params: { category } });
  return response.data;
}

export async function restoreMasterWasted(row: MasterWastedRow) {
  const response = await api.post<{ success: boolean }>(`${BASE}/${row.category}/${encodeURIComponent(row.masterType)}/${row.id}/restore`);
  return response.data;
}

export async function permanentDeleteMasterWasted(row: MasterWastedRow) {
  const response = await api.delete<{ success: boolean }>(`${BASE}/${row.category}/${encodeURIComponent(row.masterType)}/${row.id}/permanent`);
  return response.data;
}
