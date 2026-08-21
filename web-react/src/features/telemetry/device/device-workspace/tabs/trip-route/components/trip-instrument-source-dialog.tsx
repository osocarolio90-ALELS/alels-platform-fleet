import { Save, X } from "lucide-react";
import { Button } from "@/components/ui/button";
import { createPortal } from "react-dom";
import { useEffect, useMemo, useState } from "react";
import type { TripInstrumentSourceKey,TripInstrumentSourceMap,TripInstrumentSourceOption,TripInstrumentSourceProfile,TripParameter,TripPlaybackTelemetryRow } from "../types/device-workspace-trip-route";

type Option={key:string;label:string;unit?:string|null};
type Props={
  open:boolean;
  energyGroup?:string|null;
  rows:TripPlaybackTelemetryRow[];
  catalog:TripInstrumentSourceOption[];
  value:TripInstrumentSourceMap;
  profiles:TripInstrumentSourceProfile[];
  effectiveProfileId?:number|null;
  busy?:boolean;
  error?:string;
  success?:string;
  onChange:(next:TripInstrumentSourceMap)=>void;
  onSave:(name:string,applyAll:boolean)=>void;
  onApply:(profileId:number,applyAll:boolean)=>void;
  onClose:()=>void;
};
const slots:TripInstrumentSourceKey[]=["rpm","speed","level","odometer","consumption"];
export function TripInstrumentSourceDialog({open,energyGroup,rows,catalog,value,profiles,effectiveProfileId,busy,error,success,onChange,onSave,onApply,onClose}:Props){
 const options=useMemo(()=>buildOptions(rows,catalog),[rows,catalog]);
 const [selectedProfileId,setSelectedProfileId]=useState<number|0>(0);
 const [name,setName]=useState("");
 const [applyAll,setApplyAll]=useState(false);
 useEffect(()=>{if(!open)return;const id=effectiveProfileId||0;setSelectedProfileId(id);const profile=profiles.find(item=>item.id===id);setName(profile?.name||"");setApplyAll(Boolean(profile?.companyDefault));},[open,effectiveProfileId,profiles]);
 if(!open)return null;
 const target=document.fullscreenElement??document.querySelector<HTMLElement>(".dw-workspace")??document.body;
 function profileSources(profile:TripInstrumentSourceProfile):TripInstrumentSourceMap{return{rpm:profile.rpmSource||"",speed:profile.speedSource||"__gps_speed__",level:profile.levelSource||"",consumption:profile.consumptionSource||"",odometer:profile.odometerSource||""};}
 function chooseProfile(raw:string){const id=Number(raw)||0;setSelectedProfileId(id);const profile=profiles.find(item=>item.id===id);if(!profile)return;setName(profile.name);setApplyAll(profile.companyDefault);onChange(profileSources(profile));}
 return createPortal(<div className="dw-trip-source-backdrop" onMouseDown={e=>{if(e.currentTarget===e.target)onClose()}}><section className="dw-trip-source-dialog" role="dialog" aria-modal="true" aria-label="Playback Instrument Sources"><header><div><h2>Playback Instrument Sources</h2><p>Save and reuse source mappings inside your company only.</p></div><button type="button" onClick={onClose} aria-label="Close source configuration"><X/></button></header>
 <div className="dw-trip-source-profile-tools">
   <label><span>Saved Configuration</span><select value={selectedProfileId} onChange={e=>chooseProfile(e.target.value)}><option value="0">Select saved configuration</option>{profiles.map(profile=><option key={profile.id} value={profile.id}>{profile.name}{profile.companyDefault?" (All IMEIs)":""}</option>)}</select></label>
   <Button type="button" size="sm" disabled={!selectedProfileId||busy} onClick={()=>selectedProfileId&&onApply(selectedProfileId,applyAll)}>Apply</Button>
   <label className="dw-trip-source-name"><span>Configuration Name</span><input value={name} onChange={e=>setName(e.target.value)} maxLength={120} placeholder="Example: FMC650 Fuel Standard"/></label>
   <label className="dw-trip-source-apply-all"><input type="checkbox" checked={applyAll} onChange={e=>setApplyAll(e.target.checked)}/><span>All IMEIs in this company</span></label>
 </div>
 <div className="dw-trip-source-grid">{slots.map(slot=><label key={slot}><span>{slotLabel(slot,energyGroup)}</span><select value={value[slot]} onChange={e=>onChange({...value,[slot]:e.target.value})}>{slot==="speed"?<option value="__gps_speed__">GPS Speed (telemetry.speed)</option>:null}<option value="">Auto detect</option>{options.map(option=><option key={option.key} value={option.key}>{option.label}{option.unit?` (${option.unit})`:""}</option>)}</select></label>)}</div>
 {success?<div className="dw-trip-source-success" role="status">{success}</div>:null}
 {error?<div className="dw-trip-source-error" role="alert">{error}</div>:null}
 <footer><Button type="button" size="sm" onClick={onClose}>Cancel</Button><Button type="button" size="sm" disabled={busy||!name.trim()} onClick={()=>onSave(name.trim(),applyAll)}><Save/> {busy?"Saving...":"Save Configuration"}</Button></footer></section></div>,target);
}
function buildOptions(rows:TripPlaybackTelemetryRow[],catalog:TripInstrumentSourceOption[]){const map=new Map<string,Option>();for(const p of catalog||[]){const key=p.fieldCode;if(!key||map.has(key))continue;map.set(key,{key,label:p.label||p.fieldCode,unit:p.unit});}for(const row of rows)for(const p of row.parameters||[]){if(!isNumeric(p))continue;const key=p.fieldCode;if(!key||map.has(key))continue;map.set(key,{key,label:p.label||p.fieldCode,unit:p.unit});}return [...map.values()].sort((a,b)=>a.label.localeCompare(b.label));}
function isNumeric(p:TripParameter){const n=Number(String(p.value??"").replace(",","."));return Number.isFinite(n)}
function slotLabel(slot:TripInstrumentSourceKey,energy?:string|null){const group=String(energy||"FUEL").toUpperCase();if(slot==="rpm")return"Engine RPM";if(slot==="speed")return"Speed";if(slot==="odometer")return"Odometer";if(slot==="level")return group==="ELECTRIC"?"SOC / Battery Level":group==="GAS"?"Gas Level":"Fuel Level";return group==="ELECTRIC"?"Battery Consumption":group==="GAS"?"Gas Consumption":"Fuel Consumption";}
