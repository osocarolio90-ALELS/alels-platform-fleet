import{Columns3,Download,Search}from"lucide-react";
import{useMemo,useState}from"react";
import type{TripEvent,TripLogRow}from"../types/device-workspace-trip-route";

type Props={rows:TripLogRow[];events:TripEvent[];tripLabel:string};
type Column={key:string;label:string;value:(row:TripLogRow)=>string};
const PAGE_SIZE=8;
const BASE_CODES=new Set(["latitude","longitude","altitude","angle","speed","satellites","hdop","protocol","channel"]);

export function TripLogTable({rows,events,tripLabel}:Props){
 const[search,setSearch]=useState(""),[eventsOnly,setEventsOnly]=useState(false),[columnsOpen,setColumnsOpen]=useState(false),[hidden,setHidden]=useState<Set<string>>(new Set()),[page,setPage]=useState(0);
 const eventIds=useMemo(()=>new Set(events.map(event=>event.telemetryId).filter(Boolean)),[events]);
 const dynamic=useMemo(()=>{const map=new Map<string,string>();rows.forEach(row=>row.parameters.forEach(parameter=>{if(!BASE_CODES.has(parameter.fieldCode.toLowerCase()))map.set(parameter.fieldCode,parameter.label);}));return[...map].map(([key,label])=>({key:`parameter:${key}`,label,value:(row:TripLogRow)=>{const parameter=row.parameters.find(item=>item.fieldCode===key);return parameter?`${parameter.value}${parameter.unit&&parameter.unit!=="-"?` ${parameter.unit}`:""}`:"-";}}));},[rows]);
 const columns=useMemo<Column[]>(()=>[
  {key:"imei",label:"Device IMEI",value:row=>row.imei},{key:"timestamp",label:"Timestamp (Device/GPS)",value:row=>formatDate(row.occurredAt)},
  {key:"protocol",label:"Protocol",value:row=>protocol(row.protocol)},{key:"source",label:"Source",value:row=>(row.source||"-").toUpperCase()},
  {key:"latitude",label:"Latitude",value:row=>number(row.latitude,7)},{key:"longitude",label:"Longitude",value:row=>number(row.longitude,7)},
  {key:"altitude",label:"Altitude (m)",value:row=>number(row.altitude,0)},{key:"angle",label:"Angle (°)",value:row=>number(row.angle,0)},
  {key:"speed",label:"Speed (km/h)",value:row=>number(row.speed,1)},{key:"satellites",label:"Satellites",value:row=>number(row.satellites,0)},
  {key:"hdop",label:"HDOP",value:row=>number(row.hdop,2)},...dynamic],[dynamic]);
 const visible=columns.filter(column=>!hidden.has(column.key));
 const filtered=rows.filter(row=>(!eventsOnly||eventIds.has(row.telemetryId))&&(!search||visible.some(column=>column.value(row).toLowerCase().includes(search.toLowerCase()))));
 const pageCount=Math.max(1,Math.ceil(filtered.length/PAGE_SIZE)),safePage=Math.min(page,pageCount-1),shown=filtered.slice(safePage*PAGE_SIZE,(safePage+1)*PAGE_SIZE);
 function toggle(key:string){setHidden(current=>{const next=new Set(current);next.has(key)?next.delete(key):next.add(key);return next;});}
 function exportCsv(){const quote=(value:string)=>`"${value.replace(/"/g,'""')}"`;const csv=[visible.map(column=>quote(column.label)).join(","),...filtered.map(row=>visible.map(column=>quote(column.value(row))).join(","))].join("\r\n");const link=document.createElement("a");link.href=URL.createObjectURL(new Blob([csv],{type:"text/csv;charset=utf-8"}));link.download=`trip-log-${rows[0]?.imei||"device"}.csv`;link.click();URL.revokeObjectURL(link.href);}
 return <section className="dw-trip-log"><header><div><h2>Device Log History — Selected Trip</h2><p>{tripLabel}</p></div><div className="dw-trip-table-actions"><label><Search/><input value={search} onChange={event=>{setSearch(event.target.value);setPage(0);}} placeholder="Search in table"/></label><button type="button" className={eventsOnly?"active":""} onClick={()=>{setEventsOnly(value=>!value);setPage(0);}}>Events Only</button><div className="dw-trip-columns"><button type="button" onClick={()=>setColumnsOpen(value=>!value)}><Columns3/>Columns</button>{columnsOpen?<div className="dw-trip-columns-menu">{columns.map(column=><label key={column.key}><input type="checkbox" checked={!hidden.has(column.key)} onChange={()=>toggle(column.key)}/><span>{column.label}</span></label>)}</div>:null}</div><button type="button" onClick={exportCsv}><Download/>Export CSV</button></div></header>
  <div className="dw-trip-table-scroll"><table><thead><tr>{visible.map((column,index)=><th key={column.key} className={index<4?`sticky sticky-${index}`:""}>{column.label}</th>)}</tr></thead><tbody>{shown.map(row=><tr key={row.telemetryId}>{visible.map((column,index)=><td key={column.key} className={index<4?`sticky sticky-${index}`:""}>{column.value(row)}</td>)}</tr>)}</tbody></table></div>
  <footer><span>Showing {filtered.length?`${safePage*PAGE_SIZE+1}–${Math.min((safePage+1)*PAGE_SIZE,filtered.length)}`:"0"} of {filtered.length}</span><div><button type="button" disabled={safePage===0} onClick={()=>setPage(value=>Math.max(0,value-1))}>‹</button><strong>{safePage+1}</strong><button type="button" disabled={safePage>=pageCount-1} onClick={()=>setPage(value=>Math.min(pageCount-1,value+1))}>›</button></div></footer>
 </section>;
}
function protocol(value?:string|null){const normalized=(value||"").toUpperCase();if(normalized.includes("8E"))return"CODEC8E";if(normalized.includes("CODEC8"))return"CODEC8";if(normalized.includes("ALELS"))return"ALELS";return normalized||"-";}
function number(value?:number|null,digits=2){return Number.isFinite(value)?Number(value).toFixed(digits):"-";}
function formatDate(value:string){return new Date(value).toLocaleString("id-ID",{dateStyle:"medium",timeStyle:"medium"});}
