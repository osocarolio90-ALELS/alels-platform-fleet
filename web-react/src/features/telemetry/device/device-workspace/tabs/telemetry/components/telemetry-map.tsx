import { lazy, Suspense, useEffect, useMemo } from "react";
import L from "leaflet";
import { MapContainer, Marker, TileLayer, useMap } from "react-leaflet";
import "leaflet/dist/leaflet.css";

const TerrainMap=lazy(()=>import("./terrain-map"));

type Props={latitude?:number|null;longitude?:number|null;angle?:number|null;vehicleType?:string|null;threeDimensional:boolean;zoom:number};

export function TelemetryMap({latitude,longitude,angle,vehicleType,threeDimensional,zoom}:Props){
 const valid=Number.isFinite(latitude)&&Number.isFinite(longitude);
 const center=valid?[latitude as number,longitude as number] as [number,number]:[-6.2088,106.8456] as [number,number];
 const icon=useMemo(()=>vehicleIcon(vehicleType,angle),[vehicleType,angle]);
 if(threeDimensional)return <Suspense fallback={<div className="dw-map dw-map-empty">Loading 3D terrain...</div>}><TerrainMap center={center} valid={valid} zoom={zoom} angle={angle}/></Suspense>;
 return <div className={threeDimensional?"dw-map dw-map-3d":"dw-map"}>
  <MapContainer center={center} zoom={zoom} zoomControl={false} attributionControl={false} keyboard={false}>
   <MapSync center={center} zoom={zoom}/>
   <TileLayer url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"/>
   {valid?<Marker position={center} icon={icon}/>:null}
  </MapContainer>
  {!valid?<div className="dw-map-empty">Waiting for GPS position</div>:null}
 </div>;
}

function MapSync({center,zoom}:{center:[number,number];zoom:number}){const map=useMap();useEffect(()=>{map.setView(center,zoom,{animate:true});},[map,center[0],center[1],zoom]);return null;}

function vehicleIcon(type?:string|null,angle?:number|null){
 const normalized=(type||"").toLowerCase();
 const kind=normalized.includes("truck")||normalized.includes("bus")?"truck":normalized.includes("motor")?"motorcycle":"car";
 const svg=kind==="truck"
  ?'<path d="M3 8h11v8H3zM14 11h4l3 3v2h-7z"/><circle cx="7" cy="18" r="2"/><circle cx="18" cy="18" r="2"/>'
  :kind==="motorcycle"
   ?'<circle cx="6" cy="17" r="3"/><circle cx="18" cy="17" r="3"/><path d="M8 17l3-7h4l3 7M9 12h7M12 10l-2-3"/>'
   :'<path d="M4 14l2-6h12l2 6v4H4z"/><circle cx="7" cy="18" r="2"/><circle cx="17" cy="18" r="2"/>';
 return L.divIcon({className:"dw-vehicle-marker",html:`<div style="transform:rotate(${Number(angle)||0}deg)"><svg data-preserve-tone viewBox="0 0 24 24" aria-hidden="true">${svg}</svg></div>`,iconSize:[52,52],iconAnchor:[26,26]});
}
