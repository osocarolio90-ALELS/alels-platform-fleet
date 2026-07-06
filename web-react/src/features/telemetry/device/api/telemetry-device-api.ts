import { api } from "@/lib/api";
import type { TelemetryDeviceOverview } from "../types/telemetry-device";
const BASE="/api/telemetry/devices";
export async function getTelemetryDevices(){return (await api.get<TelemetryDeviceOverview>(BASE)).data;}
export async function setTelemetryDeviceTcp(id:number,enabled:boolean){return (await api.put(`${BASE}/${id}/tcp`,{enabled})).data;}
