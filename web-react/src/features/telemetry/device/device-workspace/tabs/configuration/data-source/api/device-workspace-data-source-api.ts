import{api}from"@/lib/api";import type{WorkspaceConfiguration}from"../../../telemetry/types/device-workspace-telemetry";import type{DataSourceOption,DataSourceProfile,DataSourceProfileList}from"../types/device-workspace-data-source";
const base=(id:number)=>`/api/telemetry/devices/${id}/workspace/configuration/data-source`;
export async function getDataSourceOptions(id:number){return(await api.get<DataSourceOption[]>(`${base(id)}/options`)).data;}
export async function getDataSourceProfiles(id:number){return(await api.get<DataSourceProfileList>(`${base(id)}/profiles`)).data;}
export async function saveDataSourceProfile(id:number,payload:{name:string;rpmSource:string;speedSource:string;levelSource:string;consumptionSource:string;odometerSource:string;workspaceConfiguration:WorkspaceConfiguration;unitPreferences:Record<string,string>;deviceIds:number[];applyAll:boolean}){return(await api.post<DataSourceProfile>(`${base(id)}/profiles`,{...payload,workspaceConfiguration:JSON.stringify(payload.workspaceConfiguration)})).data;}
export async function applyDataSourceProfile(id:number,profileId:number,deviceIds:number[],applyAll:boolean){return(await api.post<DataSourceProfile>(`${base(id)}/profiles/apply`,{profileId,deviceIds,applyAll})).data;}

export async function deleteDataSourceProfile(id:number,profileId:number){await api.delete(`${base(id)}/profiles/${profileId}`);}
