import { useEffect, useRef } from "react";
import maplibregl from "maplibre-gl";
import "maplibre-gl/dist/maplibre-gl.css";

import { useThemeStore } from "@/stores/theme-store";

type Props={center:[number,number];valid:boolean;zoom:number;angle?:number|null};

export default function TerrainMap({center,valid,zoom,angle}:Props){
 const container=useRef<HTMLDivElement>(null),mapRef=useRef<maplibregl.Map|null>(null),markerRef=useRef<maplibregl.Marker|null>(null);
 const theme=useThemeStore(state=>state.theme);
 useEffect(()=>{
  if(!container.current)return;
  const map=new maplibregl.Map({container:container.current,style:`https://tiles.openfreemap.org/styles/${theme==="dark"?"fiord":"bright"}`,center:[center[1],center[0]],zoom,pitch:62,bearing:Number(angle)||-18,attributionControl:false,canvasContextAttributes:{antialias:true}});
  mapRef.current=map;
  map.on("load",()=>{
   map.addSource("alels-terrain",{type:"raster-dem",url:"https://tiles.mapterhorn.com/tilejson.json",tileSize:256});
   map.setTerrain({source:"alels-terrain",exaggeration:1.25});
   map.addSource("alels-buildings",{type:"vector",url:"https://tiles.openfreemap.org/planet"});
   const labelLayer=map.getStyle().layers.find(layer=>layer.type==="symbol"&&Boolean(layer.layout?.["text-field"]))?.id;
   map.addLayer({id:"alels-3d-buildings",source:"alels-buildings","source-layer":"building",type:"fill-extrusion",minzoom:14,filter:["!=",["get","hide_3d"],true],paint:{"fill-extrusion-color":["interpolate",["linear"],["get","render_height"],0,theme==="dark"?"#17385a":"#b9cee4",200,"#164a7b"],"fill-extrusion-height":["interpolate",["linear"],["zoom"],14,0,15.5,["coalesce",["get","render_height"],8]],"fill-extrusion-base":["coalesce",["get","render_min_height"],0],"fill-extrusion-opacity":.88}},labelLayer);
   if(valid&&!markerRef.current)markerRef.current=new maplibregl.Marker({color:"#164a7b",rotation:Number(angle)||0}).setLngLat([center[1],center[0]]).addTo(map);
  });
  return()=>{markerRef.current=null;mapRef.current=null;map.remove();};
 },[theme]);
 useEffect(()=>{const map=mapRef.current;if(!map)return;map.easeTo({center:[center[1],center[0]],zoom,pitch:62,bearing:Number(angle)||map.getBearing(),duration:500});if(valid){if(!markerRef.current)markerRef.current=new maplibregl.Marker({color:"#164a7b"}).addTo(map);markerRef.current.setLngLat([center[1],center[0]]).setRotation(Number(angle)||0);}else{markerRef.current?.remove();markerRef.current=null;}},[center[0],center[1],valid,zoom,angle]);
 return <div className="dw-map dw-maplibre-3d"><div ref={container} className="dw-maplibre-canvas"/>{!valid?<div className="dw-map-empty">Waiting for GPS position</div>:null}</div>;
}
