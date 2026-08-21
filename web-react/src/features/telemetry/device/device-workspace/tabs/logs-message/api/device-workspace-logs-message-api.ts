import{api}from"@/lib/api";import type{Page,PacketDetail,PacketRow,PacketSummary,TrafficRow}from"../types/device-workspace-logs-message";
const base=(id:number)=>`/api/telemetry/devices/${id}/workspace/logs-message`;
export async function getTraffic(id:number,params:Record<string,string|number>){return(await api.get<Page<TrafficRow>>(`${base(id)}/traffic`,{params})).data;}
export async function getPackets(id:number,params:Record<string,string|number>){return(await api.get<Page<PacketRow>>(`${base(id)}/packets`,{params})).data;}
export async function getPacketSummary(id:number,from:string,to:string){return(await api.get<PacketSummary>(`${base(id)}/packets/summary`,{params:{from,to}})).data;}
export async function getPacketDetail(id:number,packetId:number){return(await api.get<PacketDetail>(`${base(id)}/packets/${packetId}`)).data;}
