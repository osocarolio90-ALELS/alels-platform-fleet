import { api } from "@/lib/api";
import type { EyeBeaconPage } from "../types/eyebeacon";

export async function getEyeBeacons(deviceId: number, beforeId: number, signal?: AbortSignal): Promise<EyeBeaconPage> {
  return (await api.get<EyeBeaconPage>(`/api/ble/gateways/${deviceId}/received-beacons`, {
    params: { beforeId, limit: 50 }, signal,
  })).data;
}
