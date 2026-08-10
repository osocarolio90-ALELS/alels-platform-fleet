import { useDeferredValue, useEffect, useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Eye, Power, RadioTower } from "lucide-react";

import { DataTable, type DataTableColumn } from "@/components/data-table";
import { Button } from "@/components/ui/button";
import { PageHeader } from "@/components/ui/page-header";
import { OrganizationTableCard } from "@/features/organization/components/organization-ui";
import { t } from "@/lib/i18n";
import { normalizeRole } from "@/lib/role-access";
import { useAuthStore } from "@/stores/auth-store";
import { useLanguageStore } from "@/stores/language-store";
import { cn } from "@/lib/utils";
import { getTelemetryDevices,setTelemetryDeviceTcp } from "../api/telemetry-device-api";
import { DeviceFolderTree,type DeviceFolder } from "../components/device-folder-tree";
import type { TelemetryDeviceRow } from "../types/telemetry-device";
import { openDeviceWorkspace } from "../device-workspace/session/services/device-workspace-session-handoff";

export function TelemetryDevicePage(){
 const client=useQueryClient(),user=useAuthStore(state=>state.user),language=useLanguageStore(state=>state.language),viewOnly=normalizeRole(user?.role)==="TECHUSER";
 const [folder,setFolder]=useState<DeviceFolder>("ALL"),[notice,setNotice]=useState(""),[search,setSearch]=useState(""),[page,setPage]=useState(1),[pageSize,setPageSize]=useState(10),[cursors,setCursors]=useState<number[]>([0]);
 const deferredSearch=useDeferredValue(search.trim());
 useEffect(()=>{setPage(1);setCursors([0]);},[folder,deferredSearch,pageSize]);
 const cursor=cursors[page-1]??0;
 const query=useQuery({queryKey:["telemetry-devices",folder,deferredSearch,pageSize,cursor],queryFn:()=>getTelemetryDevices({afterId:cursor,limit:pageSize,search:deferredSearch,folder}),placeholderData:previous=>previous,refetchInterval:15_000});
 const toggle=useMutation({mutationFn:({id,enabled}:{id:number;enabled:boolean})=>setTelemetryDeviceTcp(id,enabled),onSuccess:()=>client.invalidateQueries({queryKey:["telemetry-devices"]}),onError:error=>setNotice(error instanceof Error?error.message:"TCP update failed.")});
 const rows=query.data?.devices||[];
 const columns=useMemo<DataTableColumn<TelemetryDeviceRow>[]>(()=>[
  {key:"device",label:t(language,"device"),value:r=>`${r.imei} ${r.brand} ${r.model}`,render:r=><Cell lines={[r.imei,r.brand,r.model]} strong/>},
  {key:"vehicle",label:t(language,"vehicle"),value:r=>`${r.vehicleModel} ${r.vehicleType} ${r.plateNumber}`,render:r=><Cell lines={[r.vehicleModel,r.vehicleType,r.plateNumber]}/>},
  {key:"energy",label:t(language,"energy"),value:r=>`${r.energyType} ${r.referencePrice}`,render:r=><Cell lines={[r.energyType,formatCurrency(r.referencePrice)]}/>},
  {key:"driver",label:t(language,"driver"),value:r=>`${r.driverName} ${r.driverPhone} ${r.driverLicense}`,render:r=><Cell lines={[r.driverName,r.driverPhone,r.driverLicense]}/>},
  {key:"status",label:t(language,"status"),value:r=>r.movementStatus,render:r=><MovementStatus status={r.movementStatus}/>},
  {key:"tcp",label:"TCP",value:r=>r.tcpEnabled?"Enabled":"Disabled",render:r=><Button type="button" size="sm" variant={r.tcpEnabled?"default":"destructive"} disabled={viewOnly||toggle.isPending} onClick={()=>toggle.mutate({id:r.id,enabled:!r.tcpEnabled})} className={cn("gap-2",r.tcpEnabled&&"bg-primary text-primary-foreground hover:bg-primary/90")}><Power className="h-4 w-4"/>{r.tcpEnabled?"Enabled":"Disabled"}</Button>},
  {key:"group",label:t(language,"groupDevice"),value:r=>r.groupName||t(language,"ungroup")},
  {key:"company",label:t(language,"company"),value:r=>r.company},
  {key:"updated",label:t(language,"lastUpdated"),value:r=>r.lastUpdated||"",render:r=>relativeTime(r.lastUpdated)}
 ],[language,toggle.isPending,viewOnly]);
 function changePage(nextPage:number){
  if(nextPage===page+1&&query.data?.hasMore&&query.data.nextCursor){setCursors(current=>{const next=[...current];next[page]=query.data!.nextCursor!;return next;});setPage(nextPage);}
  else if(nextPage<page&&nextPage>=1)setPage(nextPage);
 }
 function openWorkspace(row:TelemetryDeviceRow){
  const session=useAuthStore.getState();
  if(!session.token||!session.user){setNotice("Your session is no longer active. Please sign in again.");return;}
  if(!openDeviceWorkspace(row.id,row.imei,{token:session.token,user:session.user,rememberMe:session.rememberMe}))setNotice("The browser blocked the new workspace tab. Allow pop-ups for this site and retry.");
 }
 return <section className="space-y-5 text-foreground"><PageHeader icon={<RadioTower className="h-5 w-5"/>} title={t(language,"telemetryDevice")}/>{notice?<div className="rounded-lg border border-border bg-muted p-3 text-sm">{notice}</div>:null}<div className="grid gap-4 xl:grid-cols-[220px_minmax(0,1fr)]"><DeviceFolderTree groups={query.data?.groups||[]} wasted={query.data?.wastedGroups||[]} total={query.data?.totalDevices||0} ungroup={query.data?.ungroupedDevices||0} value={folder} onChange={setFolder}/><OrganizationTableCard><DataTable data={rows} columns={columns} rowKey={row=>row.id} rowClassName={row=>row.connected?"bg-primary/15 text-foreground":undefined} onRowDoubleClick={openWorkspace} actions={row=><Button type="button" size="sm" variant="outline" onClick={event=>{event.stopPropagation();openWorkspace(row);}} onDoubleClick={event=>event.stopPropagation()} title={`Open workspace ${row.imei}`}><Eye className="h-4 w-4"/>Details</Button>} searchPlaceholder="Search IMEI, vehicle, driver..." emptyMessage={query.isLoading?t(language,"loading"):"No device found."} remote={{page,pageSize,totalRows:query.data?.filteredDevices||0,search,onPageChange:changePage,onPageSizeChange:setPageSize,onSearchChange:setSearch}}/></OrganizationTableCard></div></section>;
}
function Cell({lines,strong=false}:{lines:(string|number|null|undefined)[];strong?:boolean}){return <div className="space-y-1">{lines.map((line,index)=><div key={index} className={cn("whitespace-nowrap text-xs",strong&&index===0&&"font-bold")}>{line||"-"}</div>)}</div>;}
function MovementStatus({status}:{status:string}){const normalized=status.toUpperCase();return <span className={cn("inline-flex items-center gap-2 text-sm font-semibold",normalized==="MOVING"?"text-primary":normalized==="IDLE"?"text-amber-500":"text-destructive")}><span className="h-2 w-2 rounded-full bg-current"/>{normalized==="MOVING"?"Moving":normalized==="IDLE"?"Idle":"Stop"}</span>;}
function formatCurrency(value:number){return new Intl.NumberFormat("id-ID",{style:"currency",currency:"IDR",maximumFractionDigits:0}).format(value||0);}
function relativeTime(value?:string|null){if(!value)return"-";const delta=Math.max(0,Date.now()-new Date(value).getTime()),seconds=Math.floor(delta/1000);if(seconds<60)return`${seconds} seconds ago`;const minutes=Math.floor(seconds/60);if(minutes<60)return`${minutes} minutes ago`;const hours=Math.floor(minutes/60);return`${hours} hours ago`;}
