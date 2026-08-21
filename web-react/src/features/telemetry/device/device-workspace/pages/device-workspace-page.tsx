import { useQuery } from "@tanstack/react-query";
import html2canvas from "html2canvas";
import { Camera, Moon, RefreshCw, Sun } from "lucide-react";
import { useEffect, useRef, useState } from "react";
import { useParams } from "react-router-dom";

import { Button } from "@/components/ui/button";
import { cn } from "@/lib/utils";
import { heartbeatSession } from "@/lib/api";
import { useThemeStore } from "@/stores/theme-store";
import { DeviceWorkspaceHeader } from "../components/device-workspace-header";
import { getDeviceWorkspaceTelemetry } from "../tabs/telemetry/api/device-workspace-telemetry-api";
import { TelemetryWorkspaceTab } from "../tabs/telemetry/pages/telemetry-workspace-tab";
import { useDevicePresence } from "../tabs/telemetry/hooks/use-device-presence";
import { TripRouteWorkspaceTab } from "../tabs/trip-route/pages/trip-route-workspace-tab";
import "../device-workspace.css";

export function DeviceWorkspacePage(){
 const params=useParams(),deviceId=Number(params.deviceId),theme=useThemeStore(state=>state.theme),toggleTheme=useThemeStore(state=>state.toggleTheme);
 const live=true;
 const [activeTab,setActiveTab]=useState<"telemetry"|"trip-route">("telemetry");
 const [refreshToken,setRefreshToken]=useState(0),[notice,setNotice]=useState("");
 const workspaceRef=useRef<HTMLDivElement>(null),clusterRef=useRef<HTMLDivElement>(null);
 const headerQuery=useQuery({queryKey:["device-workspace","telemetry",deviceId],queryFn:()=>getDeviceWorkspaceTelemetry(deviceId),enabled:Number.isSafeInteger(deviceId)&&deviceId>0});
 const online=useDevicePresence(headerQuery.data);
 useEffect(()=>{let stopped=false;const heartbeat=()=>{if(!stopped)void heartbeatSession().catch(()=>undefined);};heartbeat();const interval=window.setInterval(heartbeat,2_000);return()=>{stopped=true;window.clearInterval(interval);};},[]);
 if(!Number.isSafeInteger(deviceId)||deviceId<=0)return <div className="dw-state"><h2>Invalid device workspace</h2><p>The device identifier in this URL is invalid.</p></div>;

 async function snapshot(){
  if(!workspaceRef.current)return;
  setNotice("Creating snapshot...");
  try{
   const canvas=await html2canvas(workspaceRef.current,{backgroundColor:theme==="dark"?"#06142b":"#f8fbff",scale:1.5,useCORS:true,logging:false});
   const link=document.createElement("a");
   link.download=`device-workspace-${headerQuery.data?.device.imei||deviceId}-${new Date().toISOString().replace(/[:.]/g,"-")}.png`;
   link.href=canvas.toDataURL("image/png");link.click();setNotice("Snapshot downloaded.");
  }catch{setNotice("Snapshot could not be created. Please retry after the map has loaded.");}
 }

 const data=headerQuery.data,device=data?.device;
 const vehiclePlate=data?.vehicle.paired?data.vehicle.plateNumber||"-":"NOT PAIRING";
 const location=data?.position.latitude!=null&&data.position.longitude!=null?`${data.position.latitude.toFixed(5)}, ${data.position.longitude.toFixed(5)}`:"No location";
 return <div className={cn("legacy-shell min-h-screen p-5",theme==="light"?"theme-light":"theme-dark")}><div ref={workspaceRef} className="legacy-content dw-workspace">
  <DeviceWorkspaceHeader imei={device?.imei} model={device?`${device.brand} ${device.model}`:"Loading device..."} online={online} vehiclePlate={vehiclePlate} location={location} actions={<>
   <Button type="button" variant="outline" className="dw-theme-toggle" onClick={toggleTheme} aria-label={`Switch to ${theme==="dark"?"light":"dark"} mode`} title={`Switch to ${theme==="dark"?"light":"dark"} mode`}><Sun data-preserve-tone className={cn("h-4 w-4",theme==="light"&&"active")}/><Moon data-preserve-tone className={cn("h-4 w-4",theme==="dark"&&"active")}/></Button>
   <Button type="button" variant="outline" onClick={()=>{setRefreshToken(value=>value+1);setNotice("Data refreshed.");}} title="Refresh now"><RefreshCw className="h-4 w-4"/>Refresh</Button>
   <Button type="button" variant="outline" onClick={()=>void snapshot()}><Camera className="h-4 w-4"/>Snapshot</Button>
  </>}/>
  {notice?<div className="dw-notice" role="status">{notice}<button type="button" onClick={()=>setNotice("")} aria-label="Dismiss">×</button></div>:null}
  <nav className="dw-tabs" aria-label="Device workspace tabs"><button type="button" className={activeTab==="telemetry"?"active":""} onClick={()=>setActiveTab("telemetry")}>Telemetry</button><button type="button" className={activeTab==="trip-route"?"active":""} onClick={()=>setActiveTab("trip-route")}>Trip &amp; Route</button><button type="button" disabled>Log &amp; Message</button><button type="button" disabled>Command</button><button type="button" disabled>Analytics</button></nav>
  {activeTab==="telemetry"?<TelemetryWorkspaceTab deviceId={deviceId} live={live} refreshToken={refreshToken} clusterRef={clusterRef}/>:<TripRouteWorkspaceTab deviceId={deviceId} imei={device?.imei||""} vehicleType={data?.vehicle.type} refreshToken={refreshToken}/>}
 </div></div>;
}
