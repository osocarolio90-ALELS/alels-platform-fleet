import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Activity, BatteryCharging, CircleParking, Fuel, Gauge, Maximize, Minimize2, Minus, Plus, Settings2, UserRound } from "lucide-react";
import { useEffect, useMemo, useRef, useState } from "react";

import { StatusIndicator } from "@/components/ui/status-indicator";
import { Button } from "@/components/ui/button";
import { getDriverPhoto } from "@/features/asset-register/api/driver-register-api";
import { getDeviceWorkspaceEvents, getDeviceWorkspaceTelemetry, saveDeviceWorkspaceConfiguration } from "../api/device-workspace-telemetry-api";
import { ConfigureDataDialog } from "../components/configure-data-dialog";
import { InstrumentGauge } from "../components/instrument-gauge";
import { TelemetryMap } from "../components/telemetry-map";
import type { InstrumentMapping, WorkspaceDataParameter, WorkspaceEvent, WorkspaceTelemetry } from "../types/device-workspace-telemetry";

type Props={deviceId:number;live:boolean;refreshToken:number;clusterRef:React.RefObject<HTMLDivElement>};

export function TelemetryWorkspaceTab({deviceId,live,refreshToken,clusterRef}:Props){
 const client=useQueryClient();
 const [mapMode,setMapMode]=useState<"2d"|"3d">("2d"),[zoom,setZoom]=useState(15),[configure,setConfigure]=useState(false),[configurationError,setConfigurationError]=useState(""),[fullscreen,setFullscreen]=useState(false);
 const validDeviceId=Number.isSafeInteger(deviceId)&&deviceId>0;
 const query=useQuery({queryKey:["device-workspace","telemetry",deviceId],queryFn:()=>getDeviceWorkspaceTelemetry(deviceId),enabled:validDeviceId,refetchInterval:live?1_000:false,refetchIntervalInBackground:false});
 const events=useInfiniteQuery({queryKey:["device-workspace","events",deviceId],queryFn:({pageParam})=>getDeviceWorkspaceEvents(deviceId,pageParam,50),enabled:validDeviceId,initialPageParam:null as number|null,getNextPageParam:last=>last.hasMore?last.nextBeforeId:undefined,refetchInterval:live?5_000:false});
 const save=useMutation({mutationFn:(value:WorkspaceTelemetry["configuration"])=>saveDeviceWorkspaceConfiguration(deviceId,value),onMutate:()=>setConfigurationError(""),onSuccess:value=>{client.setQueryData<WorkspaceTelemetry>(["device-workspace","telemetry",deviceId],current=>current?{...current,configuration:value}:current);setConfigure(false);},onError:error=>setConfigurationError(error instanceof Error?error.message:"Configuration could not be saved.")});
 useEffect(()=>{if(refreshToken>0){void query.refetch();void events.refetch();}},[refreshToken]);
 useEffect(()=>{const update=()=>setFullscreen(document.fullscreenElement===clusterRef.current);document.addEventListener("fullscreenchange",update);return()=>document.removeEventListener("fullscreenchange",update);},[clusterRef]);
 if(query.isLoading)return <WorkspaceState title="Loading device workspace..."/>;
 if(query.isError||!query.data)return <WorkspaceState title="Workspace cannot be loaded" message={errorMessage(query.error)}/>;
 const data=query.data;
 const rpm=resolveNumeric(data,undefined,["engine_rpm","rpm"]);
 const speed=resolveNumeric(data,undefined,["gps.speed","gnss_speed","speed"])??data.position.speed??0;
 const toggleFullscreen=async()=>{const target=clusterRef.current;if(!target||!document.fullscreenEnabled)return;try{if(document.fullscreenElement===target)await document.exitFullscreen();else await target.requestFullscreen();}catch(error){console.warn("Device workspace fullscreen request failed",error);}};
 const eventRows=uniqueEvents(events.data?.pages.flatMap(page=>page.events)||[]);
 return <div className="dw-telemetry-layout">
  <div className="dw-left-column">
   <section ref={clusterRef} className="dw-cluster dw-sticky-cluster">
    <TelemetryMap latitude={data.position.latitude} longitude={data.position.longitude} angle={data.position.angle} vehicleType={data.vehicle.type} threeDimensional={mapMode==="3d"} zoom={zoom}/>
    <div className="dw-cluster-toolbar">
     <div className="dw-map-actions"><Button variant="outline" onClick={()=>setConfigure(true)}><Settings2 className="h-4 w-4"/>Configure Data</Button><ModeButton active={mapMode==="2d"} onClick={()=>setMapMode("2d")}>2D</ModeButton><ModeButton active={mapMode==="3d"} onClick={()=>setMapMode("3d")}>3D</ModeButton><Button size="icon" variant="outline" title={fullscreen?"Exit fullscreen":"Fullscreen cluster"} aria-pressed={fullscreen} disabled={!document.fullscreenEnabled} onClick={()=>void toggleFullscreen()}>{fullscreen?<Minimize2 className="h-4 w-4"/>:<Maximize className="h-4 w-4"/>}</Button><Button size="icon" variant="outline" title="Zoom in" disabled={zoom>=19} onClick={()=>setZoom(value=>Math.min(19,value+1))}><Plus className="h-4 w-4"/></Button><Button size="icon" variant="outline" title="Zoom out" disabled={zoom<=3} onClick={()=>setZoom(value=>Math.max(3,value-1))}><Minus className="h-4 w-4"/></Button></div>
     <div className="dw-connectivity"><ConnectionStat label="Signal Strength" value={data.connection.signalStrength==null?"– / 5":`${data.connection.signalStrength} / 5`} bars={data.connection.signalStrength}/><ConnectionStat label="Satellites Used" value={String(data.connection.satellitesUsed??"–")}/><ConnectionStat label="GNSS Status" value={data.connection.gnssStatus} blue/><ConnectionStat label="TCP Status" value={data.connection.tcpStatus} blue/></div>
    </div>
    <div className="dw-instrument-stage"><InstrumentGauge label="RPM" value={rpm??0} unit="rpm" max={6000} major={[0,1,2,3,4,5,6]}/><div className="dw-map-center-label">{data.position.latitude!=null&&data.position.longitude!=null?<><strong>{data.position.latitude.toFixed(5)}, {data.position.longitude.toFixed(5)}</strong><span>Live vehicle position</span></>:<span>Waiting for location</span>}</div><InstrumentGauge label="SPEED" value={speed} unit="km/h" max={160} major={[0,20,40,60,80,100,120,140,160]}/></div>
    <div className="dw-bottom-instruments">{data.configuration.bottomItems.slice(0,7).map(item=><BottomInstrument key={item.slot} item={item} parameter={resolveParameter(data,item)}/>)}</div>
   </section>
   <DataReceivedPanel data={data}/>
  </div>
  <aside className="dw-right-column"><DriverPanel data={data}/><VehiclePanel data={data}/><RecentEventsPanel events={eventRows} hasMore={Boolean(events.hasNextPage)} loading={events.isFetchingNextPage} onMore={()=>void events.fetchNextPage()}/></aside>
  <ConfigureDataDialog key={`${configure}-${data.packet?.id||0}`} open={configure} configuration={data.configuration} parameters={data.dataReceived} saving={save.isPending} error={configurationError} onClose={()=>setConfigure(false)} onSave={value=>save.mutate(value)}/>
 </div>;
}

function DataReceivedPanel({data}:{data:WorkspaceTelemetry}){return <section className="dw-panel dw-data-panel"><header><h2>Data Received</h2><div><StatusIndicator status={data.device.online?"LIVE":"OFFLINE"}/>{data.packet?<><span>Packet #{data.packet.sequence??data.packet.id}</span><i/><span>Received {formatDate(data.packet.receivedAt)}</span></>:<span>Waiting for first packet</span>}</div></header><div className="dw-data-scroll">{data.dataReceived.length?data.dataReceived.map((parameter,index)=><article className="dw-data-card" key={`${parameter.fieldCode}-${parameter.parameterId||index}`}><span>{parameter.label}</span><strong>{formatParameter(parameter)}</strong>{parameter.parameterId?<small>Parameter ID<br/><b>{parameter.parameterId}</b></small>:null}</article>):<div className="dw-empty-card">No packet data received for this IMEI yet.</div>}</div></section>;}

function DriverPanel({data}:{data:WorkspaceTelemetry}){const photo=useSecurePhoto(data.driver.photoUrl);return <section className="dw-panel dw-info-panel"><h2>Driver Information</h2>{!data.driver.paired?<NotPairing label="Driver"/>:<div className="dw-driver-content"><div className="dw-driver-photo">{photo?<img src={photo} alt={data.driver.driverName||"Driver"}/>:<UserRound/>}</div><dl>{infoRows([["Driver Name",data.driver.driverName],["Driver ID",data.driver.driverId],["Employee ID",data.driver.employeeId],["License Number",data.driver.licenseNumber],["License Type",data.driver.licenseType],["Country",data.driver.country],["Phone Number",data.driver.phoneNumber],["RFID / iButton",data.driver.rfidIbutton]])}</dl></div>}</section>;}
function VehiclePanel({data}:{data:WorkspaceTelemetry}){return <section className="dw-panel dw-info-panel"><h2>Vehicle Information</h2>{!data.vehicle.paired?<NotPairing label="Vehicle"/>:<dl className="dw-vehicle-info">{infoRows([["Vehicle Name",data.vehicle.vehicleName],["Vehicle Code",data.vehicle.vehicleCode],["Plate Number",data.vehicle.plateNumber],["Type",data.vehicle.type],["Brand",data.vehicle.brand],["Model",data.vehicle.model],["Year",data.vehicle.year],["Energy",data.vehicle.energy],["Ownership",data.vehicle.ownership],["Tank Capacity",data.vehicle.capacity],["Country",data.vehicle.country]])}<dt>Operational Status</dt><dd><Status value={data.vehicle.operationalStatus}/></dd></dl>}</section>;}
function RecentEventsPanel({events,hasMore,loading,onMore}:{events:WorkspaceEvent[];hasMore:boolean;loading:boolean;onMore:()=>void}){return <section className="dw-panel dw-events-panel"><h2>Recent Events</h2><div className="dw-events-scroll">{events.length?events.map(event=><article key={event.id}><time>{formatDate(event.occurredAt)}</time><div><strong>{event.title}</strong><span>{event.message}</span></div><Status value={event.severity}/></article>):<div className="dw-not-pairing">No event received for this IMEI.</div>}{hasMore?<Button type="button" variant="ghost" disabled={loading} onClick={onMore}>{loading?"Loading...":"Load previous events"}</Button>:null}</div></section>;}

function BottomInstrument({item,parameter}:{item:InstrumentMapping;parameter?:WorkspaceDataParameter}){const Icon=iconFor(item.icon);return <article><Icon/><div><small>{parameter?.label||"Waiting for data"}</small><strong>{parameter?formatParameter(parameter):"–"}</strong></div></article>;}
function ConnectionStat({label,value,blue,bars}:{label:string;value:string;blue?:boolean;bars?:number|null}){return <div className="dw-connection-stat">{bars!=null?<span className="dw-signal-bars">{[1,2,3,4,5].map(level=><i key={level} className={level<=bars?"active":""}/>)}</span>:null}<span><small>{label}</small><strong data-preserve-tone={blue?"info":undefined} className={blue?"dw-blue-status":""}>{value}</strong></span></div>;}
function ModeButton({active,onClick,children}:{active:boolean;onClick:()=>void;children:React.ReactNode}){return <Button variant={active?"default":"outline"} aria-pressed={active} onClick={onClick}>{children}</Button>;}
function NotPairing({label}:{label:string}){return <div className="dw-not-pairing"><strong>NOT PAIRING</strong><span>{label} has not been assigned to this device.</span></div>;}
function Status({value}:{value?:string|null}){return <StatusIndicator status={value||"UNKNOWN"}/>;}
function WorkspaceState({title,message}:{title:string;message?:string}){return <div className="dw-state"><Gauge/><h2>{title}</h2>{message?<p>{message}</p>:null}</div>;}

function resolveParameter(data:WorkspaceTelemetry,mapping?:InstrumentMapping){if(!mapping)return undefined;return findMapped(data,mapping.primaryFieldCode,mapping.primaryParameterId)||findMapped(data,mapping.fallbackFieldCode,mapping.fallbackParameterId);}
function findMapped(data:WorkspaceTelemetry,fieldCode?:string|null,parameterId?:string|null){if(!fieldCode&&!parameterId)return undefined;return data.dataReceived.find(item=>(!fieldCode||item.fieldCode.toLowerCase()===fieldCode.toLowerCase())&&(!parameterId||item.parameterId===parameterId));}
function resolveNumeric(data:WorkspaceTelemetry,mapping:InstrumentMapping|undefined,aliases:string[]){const mapped=resolveParameter(data,mapping);if(mapped?.numericValue!=null)return mapped.numericValue;const normalizedAliases=aliases.map(value=>value.toLowerCase());return data.dataReceived.find(item=>normalizedAliases.some(alias=>item.fieldCode.toLowerCase().includes(alias)))?.numericValue;}
function formatParameter(parameter:WorkspaceDataParameter){let value=parameter.value??(parameter.booleanValue==null?"–":parameter.booleanValue?"ON":"OFF");if(parameter.booleanValue!=null)value=parameter.booleanValue?"ON":"OFF";return `${value}${parameter.unit?` ${parameter.unit}`:""}`;}
function formatDate(value?:string|null){if(!value)return"–";const date=new Date(value);return Number.isNaN(date.getTime())?value:new Intl.DateTimeFormat("en-GB",{timeZone:"Asia/Jakarta",day:"2-digit",month:"short",year:"numeric",hour:"2-digit",minute:"2-digit",second:"2-digit",hour12:false}).format(date)+" WIB";}
function errorMessage(error:unknown){return error instanceof Error?error.message:"Please retry or verify access to this IMEI.";}
function uniqueEvents(rows:WorkspaceEvent[]){const ids=new Set<number>();return rows.filter(row=>!ids.has(row.id)&&Boolean(ids.add(row.id)));}
function infoRows(rows:[string,string|number|null|undefined][]) {return rows.map(([label,value])=><><dt key={`${label}-label`}>{label}</dt><dd key={`${label}-value`}>{value??"–"}</dd></>);}
function iconFor(value?:string|null){if(value==="fuel")return Fuel;if(value==="battery")return BatteryCharging;if(value==="brake")return CircleParking;if(value==="odometer")return Gauge;return Activity;}
function useSecurePhoto(url?:string|null){const [source,setSource]=useState<string|null>(null),previous=useRef<string|null>(null);useEffect(()=>{if(!url){setSource(null);return;}let active=true;void getDriverPhoto(url).then(blob=>{if(!active)return;const objectUrl=URL.createObjectURL(blob);if(previous.current)URL.revokeObjectURL(previous.current);previous.current=objectUrl;setSource(objectUrl);}).catch(()=>setSource(null));return()=>{active=false;};},[url]);useEffect(()=>()=>{if(previous.current)URL.revokeObjectURL(previous.current);},[]);return source;}
