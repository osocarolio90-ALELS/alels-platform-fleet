import { lazy, Suspense, useEffect, useMemo } from "react";
import L from "leaflet";
import { MapContainer, Marker, Polyline, TileLayer, useMap } from "react-leaflet";
import "leaflet/dist/leaflet.css";
import type { WorkspaceTrackPoint } from "../types/device-workspace-telemetry";
import { vehicleMarkerSvg } from "./vehicle-map-marker";

const TerrainMap=lazy(()=>import("./terrain-map"));

type Props={latitude?:number|null;longitude?:number|null;angle?:number|null;vehicleType?:string|null;track:WorkspaceTrackPoint[];threeDimensional:boolean;zoom:number};

export function TelemetryMap({latitude,longitude,angle,vehicleType,track,threeDimensional,zoom}:Props){
 const valid=hasValidPosition(latitude,longitude);
 const center=valid?[latitude as number,longitude as number] as [number,number]:[-6.2088,106.8456] as [number,number];
 const icon=useMemo(()=>vehicleIcon(vehicleType,angle),[vehicleType,angle]);
 const route=track.filter(point=>hasValidPosition(point.latitude,point.longitude)).map(point=>[point.latitude,point.longitude] as [number,number]);
 if(threeDimensional)return <Suspense fallback={<div className="dw-map dw-map-empty">Loading 3D terrain...</div>}><TerrainMap center={center} valid={valid} zoom={zoom} angle={angle} vehicleType={vehicleType} track={track}/></Suspense>;
 return <div className={threeDimensional?"dw-map dw-map-3d":"dw-map"}>
  <MapContainer center={center} zoom={zoom} zoomControl={false} attributionControl={false} keyboard={false}>
   <MapSync center={center} zoom={zoom}/>
   <TileLayer url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"/>
   {route.length>1?<><Polyline positions={route} pathOptions={{color:"#0b5ed7",weight:8,opacity:.25,lineCap:"round",lineJoin:"round"}}/><Polyline positions={route} pathOptions={{color:"#38bdf8",weight:4,opacity:.95,lineCap:"round",lineJoin:"round"}}/></>:null}
   {valid?<Marker position={center} icon={icon}/>:null}
  </MapContainer>
  {!valid?<div className="dw-map-empty">Waiting for GPS position</div>:null}
 </div>;
}

export function hasValidPosition(latitude?:number|null,longitude?:number|null){
 if(!Number.isFinite(latitude)||!Number.isFinite(longitude))return false;
 const lat=latitude as number,lng=longitude as number;
 return lat>=-90&&lat<=90&&lng>=-180&&lng<=180&&!(lat===0&&lng===0);
}

function MapSync({center,zoom}:{center:[number,number];zoom:number}){const map=useMap();useEffect(()=>{map.setView(center,zoom,{animate:true});},[map,center[0],center[1],zoom]);return null;}

function vehicleIcon(type?:string|null,angle?:number|null){
 return L.divIcon({className:"dw-vehicle-marker",html:`<div style="transform:rotate(${Number(angle)||0}deg)">${vehicleMarkerSvg(type)}</div>`,iconSize:[58,72],iconAnchor:[29,36]});
}
