import { lazy, Suspense, useEffect, useMemo } from "react";
import L from "leaflet";
import { CircleMarker, MapContainer, Marker, Polyline, TileLayer, useMap } from "react-leaflet";
import "leaflet/dist/leaflet.css";
import type { WorkspaceTrackPoint } from "../types/device-workspace-telemetry";
import { vehicleMarkerSvg } from "./vehicle-map-marker";

const TerrainMap=lazy(()=>import("./terrain-map"));

export type WorkspaceMapEvent={id:number;title:string;occurredAt:string;latitude?:number|null;longitude?:number|null;speed?:number|null;severity?:string|null};
type Props={latitude?:number|null;longitude?:number|null;angle?:number|null;vehicleType?:string|null;track:WorkspaceTrackPoint[];routeOverlays?:WorkspaceTrackPoint[][];threeDimensional:boolean;zoom:number;events?:WorkspaceMapEvent[];showEvents?:boolean;showStops?:boolean;showTrackPoints?:boolean;follow?:boolean;fitToken?:number;resizeToken?:unknown;routeColor?:string;routeWeight?:number;brightMap?:boolean};

export function TelemetryMap({latitude,longitude,angle,vehicleType,track,routeOverlays=[],threeDimensional,zoom,events=[],showEvents=false,showStops=false,showTrackPoints=false,follow=true,fitToken=0,resizeToken,routeColor,routeWeight=4,brightMap=false}:Props){
 const valid=hasValidPosition(latitude,longitude);
 const center=valid?[latitude as number,longitude as number] as [number,number]:[-6.2088,106.8456] as [number,number];
 const icon=useMemo(()=>vehicleIcon(vehicleType,angle),[vehicleType,angle]);
 const route=track.filter(point=>hasValidPosition(point.latitude,point.longitude)).map(point=>[point.latitude,point.longitude] as [number,number]);
 const overlayRoutes=routeOverlays.map(overlay=>overlay.filter(point=>hasValidPosition(point.latitude,point.longitude)).map(point=>[point.latitude,point.longitude] as [number,number])).filter(overlay=>overlay.length>1);
 const boundsRoute=[...route,...overlayRoutes.flat()];
 const routePoints=showTrackPoints?sampleRoutePoints([route,...overlayRoutes],600):[];
 if(threeDimensional)return <Suspense fallback={<div className="dw-map dw-map-empty">Loading 3D terrain...</div>}><TerrainMap center={center} valid={valid} zoom={zoom} angle={angle} vehicleType={vehicleType} track={track} routeOverlays={routeOverlays} events={events} showEvents={showEvents} showTrackPoints={showTrackPoints} follow={follow} fitToken={fitToken} routeColor={routeColor} routeWeight={routeWeight} brightMap={brightMap}/></Suspense>;
 return <div className={`dw-map${brightMap?" dw-map-bright":""}`}>
  <MapContainer center={center} zoom={zoom} zoomControl={false} attributionControl={false} keyboard={false}>
   <MapSync center={center} zoom={zoom} route={boundsRoute} follow={follow} fitToken={fitToken} resizeToken={resizeToken}/>
   <TileLayer url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"/>
   {route.length>1?<><Polyline positions={route} pathOptions={{color:routeColor??"#0b5ed7",weight:routeWeight+5,opacity:.24,lineCap:"round",lineJoin:"round"}}/><Polyline positions={route} pathOptions={{color:routeColor??"#38bdf8",weight:routeWeight,opacity:1,lineCap:"round",lineJoin:"round"}}/></>:null}
   {overlayRoutes.map((overlay,index)=><Polyline key={`route-overlay-${index}`} positions={overlay} pathOptions={{color:routeColor??"#38bdf8",weight:routeWeight,opacity:1,lineCap:"round",lineJoin:"round"}}/>)}
   {routePoints.map((position,index)=><CircleMarker key={`route-point-${index}`} center={position} radius={2.5} pathOptions={{color:"#ffffff",weight:1,fillColor:routeColor??"#1d4ed8",fillOpacity:1,opacity:.9}}/>)}
   {showStops?track.filter(point=>hasValidPosition(point.latitude,point.longitude)&&(point.speed??0)<=1).map((point,index)=><Marker key={`stop-${point.occurredAt}-${index}`} position={[point.latitude,point.longitude]} icon={mapPin("•","#f59e0b")}><span/></Marker>):null}
   {showEvents?events.filter(event=>hasValidPosition(event.latitude,event.longitude)).map((event,index)=><Marker key={event.id} position={[event.latitude!,event.longitude!]} icon={mapPin(String(index+1),eventColor(event.severity))}/>):null}
   {valid?<Marker position={center} icon={icon}/>:null}
  </MapContainer>
  {!valid?<div className="dw-map-empty">Waiting for GPS position</div>:null}
 </div>;
}

function sampleRoutePoints(routes:[number,number][][],maximum:number){const points=routes.flat();if(points.length<=maximum)return points;const step=Math.ceil(points.length/maximum);return points.filter((_,index)=>index%step===0||index===points.length-1);}

export function hasValidPosition(latitude?:number|null,longitude?:number|null){
 if(!Number.isFinite(latitude)||!Number.isFinite(longitude))return false;
 const lat=latitude as number,lng=longitude as number;
 return lat>=-90&&lat<=90&&lng>=-180&&lng<=180&&!(lat===0&&lng===0);
}

function MapSync({center,zoom,route,follow,fitToken,resizeToken}:{center:[number,number];zoom:number;route:[number,number][];follow:boolean;fitToken:number;resizeToken:unknown}){const map=useMap();useEffect(()=>{if(fitToken&&route.length>1)map.fitBounds(route,{padding:[40,40],animate:true});},[map,fitToken]);useEffect(()=>{if(follow)map.setView(center,zoom,{animate:true});},[map,center[0],center[1],zoom,follow]);useEffect(()=>{const frame=window.requestAnimationFrame(()=>map.invalidateSize({pan:false}));const timer=window.setTimeout(()=>map.invalidateSize({pan:false}),280);return()=>{window.cancelAnimationFrame(frame);window.clearTimeout(timer);};},[map,resizeToken]);return null;}

function vehicleIcon(type?:string|null,angle?:number|null){
 return L.divIcon({className:"dw-vehicle-marker",html:`<div style="transform:rotate(${Number(angle)||0}deg)">${vehicleMarkerSvg(type)}</div>`,iconSize:[58,72],iconAnchor:[29,36]});
}
function mapPin(label:string,color:string){return L.divIcon({className:"dw-trip-map-pin",html:`<span style="--trip-pin:${color}">${label}</span>`,iconSize:[28,34],iconAnchor:[14,28]});}
function eventColor(severity?:string|null){const value=(severity||"").toUpperCase();return value.includes("CRITICAL")||value.includes("HIGH")?"#dc2626":value.includes("WARN")?"#f59e0b":"#2563eb";}
