import { api } from "@/lib/api";
import type { WorkspaceConfiguration, WorkspaceEventPage, WorkspaceHistoricalRoutes, WorkspaceTelemetry } from "../types/device-workspace-telemetry";

function base(deviceId:number){return `/api/telemetry/devices/${deviceId}/workspace/telemetry`;}
export async function getDeviceWorkspaceTelemetry(deviceId:number){return (await api.get<WorkspaceTelemetry>(base(deviceId))).data;}
export async function getDeviceWorkspaceHistoricalRoutes(deviceId:number){return (await api.get<WorkspaceHistoricalRoutes>(`${base(deviceId)}/history-routes`)).data;}
export async function getDeviceWorkspaceEvents(deviceId:number,beforeId?:number|null,limit=50){return (await api.get<WorkspaceEventPage>(`${base(deviceId)}/events`,{params:{beforeId:beforeId||undefined,limit}})).data;}
export async function saveDeviceWorkspaceConfiguration(deviceId:number,configuration:WorkspaceConfiguration){return (await api.put<WorkspaceConfiguration>(`${base(deviceId)}/configuration`,configuration)).data;}
