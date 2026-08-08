import { api } from "@/lib/api";
import type { TelemetryDeviceOverview } from "../types/telemetry-device";
const BASE="/api/telemetry/devices";
export type TelemetryDeviceQuery={afterId?:number;limit:number;search?:string;folder?:string};
export async function getTelemetryDevices(query:TelemetryDeviceQuery){return (await api.get<TelemetryDeviceOverview>(BASE,{params:query})).data;}
export async function setTelemetryDeviceTcp(id:number,enabled:boolean){return (await api.put(`${BASE}/${id}/tcp`,{enabled})).data;}
