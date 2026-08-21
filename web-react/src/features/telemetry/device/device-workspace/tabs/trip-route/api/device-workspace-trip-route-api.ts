import{api}from"@/lib/api";
import type{DeleteDeviceHistoryResponse,DeviceLogPage,LatestDeviceLog,SelectedDeviceLogPage,SelectedTimeRange,TripDetailResponse,TripListResponse,TripLogRow,TripPlaybackTelemetryRow,TripEvent,SelectedRouteRange,SelectedRoutesResponse,TripInstrumentSourceMap,TripInstrumentSourceProfile,TripInstrumentSourceProfileList,TripInstrumentSourceOption}from"../types/device-workspace-trip-route";
const base=(deviceId:number)=>`/api/telemetry/devices/${deviceId}/workspace/trip-route`;
export async function getTrips(deviceId:number,from:string,to:string){return(await api.get<TripListResponse>(base(deviceId),{params:{from,to}})).data;}
export async function getTripDetail(deviceId:number,from:string,to:string){return(await api.get<TripDetailResponse>(`${base(deviceId)}/detail`,{params:{from,to}})).data;}
export async function getSelectedRoutes(deviceId:number,ranges:SelectedRouteRange[]){return(await api.post<SelectedRoutesResponse>(`${base(deviceId)}/routes/selection`,{ranges})).data;}
export async function getSelectedEvents(deviceId:number,ranges:SelectedTimeRange[]){return(await api.post<TripEvent[]>(`${base(deviceId)}/events/selection`,{ranges})).data;}
export async function getDeviceLogs(deviceId:number,from:string,to:string,limit:number,beforeTime?:string,beforeId?:number){return(await api.get<DeviceLogPage>(`${base(deviceId)}/logs`,{params:{from,to,limit,beforeTime,beforeId}})).data;}
export async function getLatestDeviceLog(deviceId:number,from:string,to:string){return(await api.get<LatestDeviceLog>(`${base(deviceId)}/logs/latest`,{params:{from,to}})).data;}
export async function deleteAllDeviceHistory(deviceId:number,imeiConfirmation:string){return(await api.delete<DeleteDeviceHistoryResponse>(`${base(deviceId)}/logs`,{data:{imeiConfirmation}})).data;}

export async function getSelectedDeviceLogs(deviceId:number,ranges:SelectedTimeRange[],page:number,size:number){
 return(await api.post<SelectedDeviceLogPage>(`${base(deviceId)}/logs/selection`,{ranges,page,size})).data;
}
export async function exportSelectedDeviceLogs(deviceId:number,ranges:SelectedTimeRange[]){
 return(await api.post<TripLogRow[]>(`${base(deviceId)}/logs/selection/export`,{ranges,page:0,size:100})).data;
}

export async function getSelectedPlaybackTelemetry(deviceId:number,ranges:SelectedTimeRange[],telemetryIds:number[]){return(await api.post<TripPlaybackTelemetryRow[]>(`${base(deviceId)}/playback/telemetry`,{ranges,telemetryIds,page:0,size:100})).data;}

export async function getInstrumentSourceOptions(deviceId:number){return(await api.get<TripInstrumentSourceOption[]>(`${base(deviceId)}/instrument-source-options`)).data;}
export async function getInstrumentSourceProfiles(deviceId:number){return(await api.get<TripInstrumentSourceProfileList>(`${base(deviceId)}/instrument-source-profiles`)).data;}
export async function saveInstrumentSourceProfile(deviceId:number,name:string,sources:TripInstrumentSourceMap,applyAll:boolean){return(await api.post<TripInstrumentSourceProfile>(`${base(deviceId)}/instrument-source-profiles`,{name,rpmSource:sources.rpm,speedSource:sources.speed,levelSource:sources.level,consumptionSource:sources.consumption,odometerSource:sources.odometer,applyAll})).data;}
export async function applyInstrumentSourceProfile(deviceId:number,profileId:number,applyAll:boolean){return(await api.post<TripInstrumentSourceProfile>(`${base(deviceId)}/instrument-source-profiles/apply`,{profileId,applyAll})).data;}
