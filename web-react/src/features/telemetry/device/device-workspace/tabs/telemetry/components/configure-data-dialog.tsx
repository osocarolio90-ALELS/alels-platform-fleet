import { Plus, Save, Trash2, X } from "lucide-react";
import { useEffect, useMemo, useState } from "react";

import { Button } from "@/components/ui/button";
import type { InstrumentMapping, WorkspaceConfiguration, WorkspaceDataParameter } from "../types/device-workspace-telemetry";

type Props = {
  open:boolean;
  configuration:WorkspaceConfiguration;
  parameters:WorkspaceDataParameter[];
  saving:boolean;
  error?:string;
  onClose:()=>void;
  onSave:(value:WorkspaceConfiguration)=>void;
};

export function ConfigureDataDialog({open,configuration,parameters,saving,error,onClose,onSave}:Props){
 const [draft,setDraft]=useState<WorkspaceConfiguration>(()=>clone(configuration));
 useEffect(()=>{if(open)setDraft(clone(configuration));},[open,configuration]);
 const options=useMemo(()=>parameters.map(item=>({key:parameterKey(item),label:`${item.label}${item.parameterId?` · ID ${item.parameterId}`:""}`,item})),[parameters]);
 if(!open)return null;

 function update(target:"primary"|"fallback",key:string,bottomIndex:number){
  const parameter=options.find(item=>item.key===key)?.item;
  setDraft(current=>({...current,bottomItems:current.bottomItems.map((item,index)=>index===bottomIndex?withParameter(item,target,parameter):item)}));
 }
 function addBottom(){setDraft(current=>{if(current.bottomItems.length>=7)return current;const index=current.bottomItems.length+1;return {...current,bottomItems:[...current.bottomItems,{slot:`CUSTOM_${Date.now()}_${index}`,label:`Data ${index}`,primaryFieldCode:null,primaryParameterId:null,fallbackFieldCode:null,fallbackParameterId:null,unit:null,icon:"activity"}]};});}
 function removeBottom(index:number){setDraft(current=>current.bottomItems.length<=1?current:{...current,bottomItems:current.bottomItems.filter((_,itemIndex)=>itemIndex!==index)});}

 return <div className="dw-modal-backdrop" role="dialog" aria-modal="true" aria-label="Configure cluster data">
  <form className="dw-config-dialog" onSubmit={event=>{event.preventDefault();onSave(draft);}}>
   <header><div><h2>Configure Bottom Data</h2><p>Choose the device data shown in the seven bottom instrument slots. RPM and Speed gauges are fixed.</p></div><Button type="button" size="icon" variant="ghost" onClick={onClose} aria-label="Close"><X className="h-4 w-4"/></Button></header>
   <section><div className="dw-config-section-title"><div><h3>Bottom Cluster Instruments</h3><small>{draft.bottomItems.length} of 7 slots</small></div><Button type="button" size="sm" variant="outline" disabled={draft.bottomItems.length>=7} onClick={addBottom}><Plus className="h-4 w-4"/>Add Data</Button></div><div className="dw-config-list">{draft.bottomItems.slice(0,7).map((item,index)=><div className="dw-config-row" key={item.slot}><span>{index+1}</span><MappingFields compact item={item} options={options} onChange={(target,key)=>update(target,key,index)}/><Button type="button" size="icon" variant="destructive" disabled={draft.bottomItems.length<=1} aria-label={`Remove ${item.label||`data ${index+1}`}`} onClick={()=>removeBottom(index)}><Trash2 className="h-4 w-4"/></Button></div>)}</div></section>
   {error?<div className="dw-config-error" role="alert">{error}</div>:null}
   <footer><Button type="button" variant="outline" onClick={onClose}>Cancel</Button><Button type="submit" disabled={saving}><Save className="h-4 w-4"/>{saving?"Saving...":"Save Configuration"}</Button></footer>
  </form>
 </div>;
}

function MappingFields({item,options,onChange,compact=false}:{item:InstrumentMapping;options:{key:string;label:string;item:WorkspaceDataParameter}[];onChange:(target:"primary"|"fallback",key:string)=>void;compact?:boolean}){
 return <label className={compact?"dw-config-mapping compact":"dw-config-mapping"}><span>{item.label||item.slot}</span><select aria-label={`${item.label||item.slot} primary source`} value={mappingKey(item,"primary")} onChange={event=>onChange("primary",event.target.value)}><option value="">Select primary data</option>{options.map(option=><option key={option.key} value={option.key}>{option.label}</option>)}</select><select aria-label={`${item.label||item.slot} fallback source`} value={mappingKey(item,"fallback")} onChange={event=>onChange("fallback",event.target.value)}><option value="">No fallback</option>{options.map(option=><option key={option.key} value={option.key}>{option.label}</option>)}</select></label>;
}
function parameterKey(value:WorkspaceDataParameter){return `${value.fieldCode}::${value.parameterId||""}`;}
function mappingKey(value:InstrumentMapping,target:"primary"|"fallback"){const code=target==="primary"?value.primaryFieldCode:value.fallbackFieldCode;const id=target==="primary"?value.primaryParameterId:value.fallbackParameterId;return code?`${code}::${id||""}`:"";}
function withParameter(current:InstrumentMapping,target:"primary"|"fallback",parameter?:WorkspaceDataParameter):InstrumentMapping{
 if(target==="primary")return {...current,label:parameter?.label||current.label,primaryFieldCode:parameter?.fieldCode||null,primaryParameterId:parameter?.parameterId||null,unit:parameter?.unit||current.unit};
 return {...current,fallbackFieldCode:parameter?.fieldCode||null,fallbackParameterId:parameter?.parameterId||null};
}
function clone(value:WorkspaceConfiguration):WorkspaceConfiguration{return {instruments:value.instruments.map(item=>({...item})),bottomItems:value.bottomItems.map(item=>({...item}))};}
