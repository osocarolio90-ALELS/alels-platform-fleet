import { useEffect, useMemo, useRef, useState } from "react";
import maplibregl from "maplibre-gl";
import "maplibre-gl/dist/maplibre-gl.css";

import type { WorkspaceTrackPoint } from "../types/device-workspace-telemetry";
import { vehicleMarkerSvg } from "./vehicle-map-marker";

type Props={center:[number,number];valid:boolean;zoom:number;angle?:number|null;vehicleType?:string|null;track:WorkspaceTrackPoint[]};
const PERFORMANCE_PITCH=48;
const FOLLOW_INTERVAL_MS=2000;
const FOLLOW_DEAD_ZONE_RATIO=.3;

export default function TerrainMap({center,valid,zoom,angle,vehicleType,track}:Props){
 const container=useRef<HTMLDivElement>(null),mapRef=useRef<maplibregl.Map|null>(null),markerRef=useRef<maplibregl.Marker|null>(null);
 const lastZoomRef=useRef(zoom);
 const lastFollowAtRef=useRef(0);
 const [startupError,setStartupError]=useState(false);
 const routeData=useMemo(()=>routeGeoJson(track),[track]);
 const routeDataRef=useRef(routeData);routeDataRef.current=routeData;
 useEffect(()=>{
  if(!container.current)return;
  let map:maplibregl.Map;
  let active=true;
  setStartupError(false);
  try{map=new maplibregl.Map({container:container.current,style:"https://tiles.openfreemap.org/styles/bright",center:[center[1],center[0]],zoom,pitch:PERFORMANCE_PITCH,bearing:-18,attributionControl:false,canvasContextAttributes:{antialias:false},pixelRatio:Math.min(window.devicePixelRatio||1,1.5),maxTileCacheSize:64,fadeDuration:0,refreshExpiredTiles:false});}
  catch(error){console.error("3D map could not initialize",error);setStartupError(true);return;}
  mapRef.current=map;
  map.on("load",()=>{
   if(!active)return;
   try{map.addSource("alels-buildings",{type:"vector",url:"https://tiles.openfreemap.org/planet"});const labelLayer=(map.getStyle().layers??[]).find(layer=>layer.type==="symbol"&&Boolean(layer.layout?.["text-field"]))?.id;map.addLayer({id:"alels-3d-buildings",source:"alels-buildings","source-layer":"building",type:"fill-extrusion",minzoom:15,filter:["!=",["get","hide_3d"],true],paint:{"fill-extrusion-color":"#b9cee4","fill-extrusion-height":["interpolate",["linear"],["zoom"],15,0,16,["coalesce",["get","render_height"],8]],"fill-extrusion-base":["coalesce",["get","render_min_height"],0],"fill-extrusion-opacity":.82}},labelLayer);}catch(error){console.warn("3D building layer unavailable",error);}
   addRouteLayers(map,routeDataRef.current);
   if(valid&&!markerRef.current)markerRef.current=createVehicleMarker(vehicleType,angle).setLngLat([center[1],center[0]]).addTo(map);
  });
  const resizeObserver=new ResizeObserver(()=>map.resize());resizeObserver.observe(container.current);
  const visibilityChanged=()=>{if(document.visibilityState==="hidden")map.stop();else map.resize();};document.addEventListener("visibilitychange",visibilityChanged);
  return()=>{active=false;resizeObserver.disconnect();document.removeEventListener("visibilitychange",visibilityChanged);markerRef.current=null;mapRef.current=null;map.remove();};
 },[]);
 useEffect(()=>{
  const map=mapRef.current,lng=center[1],lat=center[0];
  if(!map||!isValidCoordinate(lat,lng))return;
  if(lastZoomRef.current!==zoom){lastZoomRef.current=zoom;map.easeTo({zoom,pitch:PERFORMANCE_PITCH,duration:250});}
  if(valid){
   if(!markerRef.current)markerRef.current=createVehicleMarker(vehicleType,angle).setLngLat([lng,lat]).addTo(map);
   else markerRef.current.setLngLat([lng,lat]);
   markerRef.current.setRotation(Number(angle)||0);
   if(map.loaded()&&shouldFollowVehicle(map,lng,lat,lastFollowAtRef.current)){
    lastFollowAtRef.current=performance.now();
    map.easeTo({center:[lng,lat],duration:700,easing:value=>1-Math.pow(1-value,3)});
   }
  }else{markerRef.current?.remove();markerRef.current=null;}
 },[center[0],center[1],valid,zoom,angle,vehicleType]);
 useEffect(()=>{const map=mapRef.current;if(!map||!map.loaded())return;addRouteLayers(map,routeData);const source=map.getSource("alels-route") as maplibregl.GeoJSONSource|undefined;source?.setData(routeData);},[routeData]);
 if(startupError)return <div className="dw-map dw-map-empty">3D map is unavailable on this browser. Use 2D mode or enable hardware acceleration.</div>;
 return <div className="dw-map dw-maplibre-3d"><div ref={container} className="dw-maplibre-canvas"/>{!valid?<div className="dw-map-empty">Waiting for GPS position</div>:null}</div>;
}

function createVehicleMarker(vehicleType?:string|null,angle?:number|null){
 const element=document.createElement("div");
 element.className="dw-vehicle-marker";
 element.innerHTML=`<div>${vehicleMarkerSvg(vehicleType)}</div>`;
 return new maplibregl.Marker({element,rotation:Number(angle)||0,rotationAlignment:"map"});
}

function isValidCoordinate(latitude:number,longitude:number){return Number.isFinite(latitude)&&Number.isFinite(longitude)&&latitude>=-90&&latitude<=90&&longitude>=-180&&longitude<=180;}

function shouldFollowVehicle(map:maplibregl.Map,longitude:number,latitude:number,lastFollowAt:number){
 const now=performance.now();
 if(now-lastFollowAt<FOLLOW_INTERVAL_MS)return false;
 const canvas=map.getCanvas(),point=map.project([longitude,latitude]);
 const marginX=canvas.clientWidth*FOLLOW_DEAD_ZONE_RATIO,marginY=canvas.clientHeight*FOLLOW_DEAD_ZONE_RATIO;
 return point.x<marginX||point.x>canvas.clientWidth-marginX||point.y<marginY||point.y>canvas.clientHeight-marginY;
}

function routeGeoJson(track:WorkspaceTrackPoint[]){return {type:"Feature" as const,properties:{},geometry:{type:"LineString" as const,coordinates:track.filter(point=>Number.isFinite(point.latitude)&&Number.isFinite(point.longitude)&&!(point.latitude===0&&point.longitude===0)).map(point=>[point.longitude,point.latitude])}};}
function addRouteLayers(map:maplibregl.Map,data:ReturnType<typeof routeGeoJson>){
 if(data.geometry.coordinates.length<2)return;
 if(map.getSource("alels-route"))return;
 map.addSource("alels-route",{type:"geojson",data});
 map.addLayer({id:"alels-route-line",type:"line",source:"alels-route",layout:{"line-cap":"round","line-join":"round"},paint:{"line-color":"#0284c7","line-width":4,"line-opacity":.9}});
}
