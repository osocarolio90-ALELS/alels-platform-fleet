import { useState } from "react";
import { Folder, FolderOpen, RadioTower, Search, Trash2 } from "lucide-react";
import { Input } from "@/components/ui/input";
import { t } from "@/lib/i18n";
import { cn } from "@/lib/utils";
import { useLanguageStore } from "@/stores/language-store";
import type { TelemetryDeviceGroup } from "../types/telemetry-device";

export type DeviceFolder = "ALL"|"UNGROUP"|`GROUP:${number}`|`WASTED:${number}`;
export function DeviceFolderTree({groups,wasted,total,ungroup,value,onChange}:{groups:TelemetryDeviceGroup[];wasted:TelemetryDeviceGroup[];total:number;ungroup:number;value:DeviceFolder;onChange:(value:DeviceFolder)=>void}){
 const language=useLanguageStore(state=>state.language),[search,setSearch]=useState(""),term=search.trim().toLowerCase();
 const visibleGroups=groups.filter(group=>group.name.toLowerCase().includes(term)),visibleWasted=wasted.filter(group=>group.name.toLowerCase().includes(term));
 return <aside className="rounded-xl border border-border bg-card p-4 text-card-foreground">
  <h2 className="mb-4 text-sm font-extrabold uppercase tracking-wide">{t(language,"groups")}</h2>
  <div className="relative mb-4"><Search className="absolute left-3 top-3 h-4 w-4 text-muted-foreground"/><Input className="pl-9" value={search} onChange={event=>setSearch(event.target.value)} placeholder={t(language,"searchGroup")}/></div>
  <nav className="space-y-1">
   <FolderButton active={value==="ALL"} icon={<RadioTower className="h-4 w-4"/>} label={t(language,"allDevices")} count={total} onClick={()=>onChange("ALL")}/>
   <FolderButton active={value==="UNGROUP"} icon={<FolderOpen className="h-4 w-4"/>} label={t(language,"ungroup")} count={ungroup} onClick={()=>onChange("UNGROUP")}/>
   {visibleGroups.map(group=><FolderButton key={group.id} active={value===`GROUP:${group.id}`} icon={<Folder className="h-4 w-4"/>} label={group.name} count={group.deviceCount} onClick={()=>onChange(`GROUP:${group.id}`)}/>)}
   {visibleWasted.length?<div className="pt-3"><p className="mb-1 px-3 text-xs font-bold uppercase text-muted-foreground">{t(language,"wasted")}</p>{visibleWasted.map(group=><FolderButton key={group.id} active={value===`WASTED:${group.id}`} icon={<Trash2 className="h-4 w-4"/>} label={group.name} count={group.deviceCount} onClick={()=>onChange(`WASTED:${group.id}`)}/>)}</div>:null}
  </nav>
 </aside>;
}
function FolderButton({active,icon,label,count,onClick}:{active:boolean;icon:React.ReactNode;label:string;count:number;onClick:()=>void}){
 return <button type="button" onClick={onClick} className={cn("flex w-full items-center gap-3 rounded-lg px-3 py-2.5 text-left text-sm font-semibold transition-colors hover:bg-muted",active&&"bg-primary/15 text-primary")}><span>{icon}</span><span className="min-w-0 flex-1 truncate">{label}</span><span className="text-xs font-bold">{count}</span></button>;
}
