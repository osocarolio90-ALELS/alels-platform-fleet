import axios from "axios";

import { api } from "@/lib/api";

export type AssetMoveType = "DEVICE" | "VEHICLE" | "DRIVER";

export async function moveAssets(assetType: AssetMoveType, assetIds: number[], targetCompanyId: number) {
  try {
    const response = await api.post<{ success: boolean; moved: number }>("/api/asset-register/move", {
      assetType,
      assetIds,
      targetCompanyId
    });
    return response.data;
  } catch (error) {
    if (axios.isAxiosError(error)) {
      const data = error.response?.data as { detail?: string; message?: string; error?: string } | undefined;
      throw new Error(data?.detail || data?.message || data?.error || "Asset gagal dipindahkan.");
    }
    throw error;
  }
}
