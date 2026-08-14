import{api}from"@/lib/api";
import type{TripDetailResponse,TripListResponse}from"../types/device-workspace-trip-route";
const base=(deviceId:number)=>`/api/telemetry/devices/${deviceId}/workspace/trip-route`;
export async function getTrips(deviceId:number,from:string,to:string){return(await api.get<TripListResponse>(base(deviceId),{params:{from,to}})).data;}
export async function getTripDetail(deviceId:number,from:string,to:string){return(await api.get<TripDetailResponse>(`${base(deviceId)}/detail`,{params:{from,to}})).data;}
