import{useQuery}from"@tanstack/react-query";
import{ChevronLeft,ChevronRight,Download,Expand,Eye,EyeOff,LocateFixed,MapPin,Search}from"lucide-react";
import{useEffect,useMemo,useRef,useState}from"react";
import{TelemetryMap,type WorkspaceMapEvent}from"../../telemetry/components/telemetry-map";
import type{WorkspaceTrackPoint}from"../../telemetry/types/device-workspace-telemetry";
import{getTripDetail,getTrips}from"../api/device-workspace-trip-route-api";
import{TripLogTable}from"../components/trip-log-table";
import{TripPlayback}from"../components/trip-playback";
import{useTripPlayback}from"../hooks/use-trip-playback";
import type{TripSummary}from"../types/device-workspace-trip-route";
import"../trip-route.css";

type Props={deviceId:number;vehicleType?:string|null;refreshToken:number};
export function TripRouteWorkspaceTab({deviceId,vehicleType,refreshToken}:Props){
 const defaults=useMemo(()=>defaultRange(),[]),[from,setFrom]=useState(defaults.from),[to,setTo]=useState(defaults.to),[applied,setApplied]=useState(defaults);
 const[selectedId,setSelectedId]=useState<string|null>(null),[tripsVisible,setTripsVisible]=useState(true),[status,setStatus]=useState("ALL"),[driver,setDriver]=useState("ALL"),[location,setLocation]=useState("");
 const[checkedTripIds,setCheckedTripIds]=useState<Set<string>>(()=>new Set());
 const[validationError,setValidationError]=useState("");
 const[mode,setMode]=useState<"2d"|"3d">("2d"),[fitToken,setFitToken]=useState(1),[follow,setFollow]=useState(true),[showEvents,setShowEvents]=useState(true);
 const mapPanelRef=useRef<HTMLDivElement>(null);
 const listQuery=useQuery({queryKey:["device-workspace","trip-route",deviceId,applied.from,applied.to,refreshToken],queryFn:()=>getTrips(deviceId,new Date(applied.from).toISOString(),new Date(applied.to).toISOString()),enabled:deviceId>0});
 const trips=listQuery.data?.trips||[];
 useEffect(()=>{if(!trips.length){setSelectedId(null);return;}if(!trips.some(trip=>trip.id===selectedId))setSelectedId(trips[0].id);},[trips,selectedId]);
 const selected=trips.find(trip=>trip.id===selectedId)||null;
 const rangeDetail=!selected&&listQuery.isSuccess&&trips.length===0;
 const detailFrom=selected?.startTime??new Date(applied.from).toISOString(),detailTo=selected?.endTime??new Date(applied.to).toISOString();
 const detailQuery=useQuery({queryKey:["device-workspace","trip-route-detail",deviceId,selected?.id??"range",detailFrom,detailTo,refreshToken],queryFn:()=>getTripDetail(deviceId,detailFrom,detailTo),enabled:Boolean(selected)||rangeDetail});
 const detail=detailQuery.data,playback=useTripPlayback(detail?.track.length||0,selected?.id),current=detail?.track[playback.index];
 const drivers=[...new Set(trips.map(trip=>trip.driverName).filter(name=>name&&name!=="-"))];
 const filtered=trips.filter(trip=>(status==="ALL"||trip.status===status)&&(driver==="ALL"||trip.driverName===driver)&&(!location||tripLocation(trip).includes(location.toLowerCase())));
 const track:WorkspaceTrackPoint[]=(detail?.track||[]).map(point=>({latitude:point.latitude,longitude:point.longitude,angle:point.angle,speed:point.speed,occurredAt:point.occurredAt}));
 const visibleTrack=playback.started?track.slice(0,playback.index+1):track;
 const availableRoutes=listQuery.data?.routes||[],filteredTripIds=new Set(filtered.map(trip=>trip.id));
 const filteredRoutes=availableRoutes.filter(route=>filteredTripIds.has(route.tripId));
 const selectedRoutes=checkedTripIds.size?filteredRoutes.filter(route=>checkedTripIds.has(route.tripId)):filteredRoutes;
 const routeOverlays:WorkspaceTrackPoint[][]=selectedRoutes.map(route=>route.track.map(point=>({latitude:point.latitude,longitude:point.longitude,angle:point.angle,speed:point.speed,occurredAt:point.occurredAt})));
 const primaryTrack=playback.started?visibleTrack:routeOverlays.length?[]:track;
 const displayedOverlays=playback.started?[]:routeOverlays;
 const events:WorkspaceMapEvent[]=(detail?.events||[]).map(event=>({id:event.id,title:event.title,occurredAt:event.occurredAt,latitude:event.latitude,longitude:event.longitude,speed:event.speed,severity:event.severity}));
 const label=detail?`${formatDate(detail.trip.startTime)} — ${formatDate(detail.trip.endTime)} · ${coordinate(detail.trip.startLatitude,detail.trip.startLongitude)} → ${coordinate(detail.trip.endLatitude,detail.trip.endLongitude)}`:"Select a trip";
 function apply(){const start=new Date(from),end=new Date(to);if(!Number.isFinite(start.getTime())||!Number.isFinite(end.getTime())||start>=end){setValidationError("Date To must be later than Date From.");return;}if(end.getTime()-start.getTime()>31*86400000){setValidationError("Date range cannot exceed 31 days.");return;}setValidationError("");setApplied({from,to});setSelectedId(null);setCheckedTripIds(new Set());}
 function exportTrips(){const csv=["Type,Start GPS Time,Finish GPS Time,Status,Distance km,Duration seconds,Driver",...filtered.map(trip=>[trip.type,trip.startTime,trip.endTime,trip.status,trip.distanceKm,trip.durationSeconds,trip.driverName].map(value=>`"${String(value).replace(/"/g,'""')}"`).join(","))].join("\r\n");download(csv,"trip-stop-history.csv");}
 function toggleTrip(tripId:string){const willCheck=!checkedTripIds.has(tripId);setCheckedTripIds(current=>{const next=new Set(current);next.has(tripId)?next.delete(tripId):next.add(tripId);return next;});if(willCheck)setSelectedId(tripId);}
 return <section className="dw-trip-route-tab">
  <header className="dw-trip-filters"><label>Date From<input type="datetime-local" value={from} onChange={event=>setFrom(event.target.value)}/></label><label>Date To<input type="datetime-local" value={to} onChange={event=>setTo(event.target.value)}/></label><label>Trip Status<select value={status} onChange={event=>setStatus(event.target.value)}><option value="ALL">All Status</option><option value="COMPLETED">Completed</option><option value="IN_PROGRESS">In Progress</option></select></label><label>Driver<select value={driver} onChange={event=>setDriver(event.target.value)}><option value="ALL">All Driver</option>{drivers.map(name=><option key={name}>{name}</option>)}</select></label><label className="search">Search Location<span><Search/><input value={location} onChange={event=>setLocation(event.target.value)} placeholder="Coordinate or location"/></span></label><button type="button" className="primary" onClick={apply}>Apply</button><button type="button" onClick={exportTrips}><Download/>Export</button></header>
  {validationError?<div className="dw-trip-error" role="alert">{validationError}</div>:null}
  {listQuery.isError?<div className="dw-trip-error" role="alert">Trip history could not be loaded. Check the backend service and retry.</div>:null}
  <div className={`dw-trip-body ${tripsVisible?"":"trips-hidden"}`}>
   {tripsVisible?<aside className="dw-trip-list"><header><strong>Trips & Stops ({filtered.length})</strong><button type="button" onClick={()=>setTripsVisible(false)} title="Hide Trips" aria-label="Hide Trips"><ChevronLeft/></button></header><div>{listQuery.isLoading?<p className="empty">Loading trips and stops...</p>:filtered.length?filtered.map(trip=><article key={trip.id} className={`${selectedId===trip.id?"selected ":""}${trip.type.toLowerCase()}`}><input type="checkbox" checked={checkedTripIds.has(trip.id)} onChange={()=>toggleTrip(trip.id)} aria-label={`Show ${trip.type.toLowerCase()} ${trip.id}`}/><button type="button" onClick={()=>setSelectedId(trip.id)}><span><MapPin/>{trip.type} · {coordinate(trip.startLatitude,trip.startLongitude)}</span><em>{trip.status.replace("_"," ")}</em><small className="dw-trip-time"><b>Start</b> {dateTime(trip.startTime)}<b>Finish</b> {dateTime(trip.endTime)}</small><small><b>Duration</b> {duration(trip.durationSeconds)} · {trip.distanceKm.toFixed(1)} km · {trip.driverName}</small></button></article>):<p className="empty">No ignition trips or stops in this range.<br/>Available telemetry is shown on the map and log.</p>}</div></aside>:<button type="button" className="dw-trip-show" onClick={()=>setTripsVisible(true)} title="Show Trips" aria-label="Show Trips"><ChevronRight/><span>Show Trips</span></button>}
   <main><div ref={mapPanelRef} className="dw-trip-map-panel"><div className="dw-trip-map-toolbar"><button type="button" className={mode==="2d"?"active":""} onClick={()=>setMode("2d")}>2D</button><button type="button" className={mode==="3d"?"active":""} onClick={()=>setMode("3d")}>3D</button><button type="button" onClick={()=>setFitToken(value=>value+1)}><LocateFixed/>Fit Route</button><button type="button" className={follow?"active":""} onClick={()=>setFollow(value=>!value)}>{follow?<Eye/>:<EyeOff/>}Follow Vehicle</button><button type="button" className={showEvents?"active":""} onClick={()=>setShowEvents(value=>!value)}>Events</button><button type="button" onClick={()=>void mapPanelRef.current?.requestFullscreen()}><Expand/></button></div>{detail?<TelemetryMap latitude={current?.latitude??detail.track[0]?.latitude} longitude={current?.longitude??detail.track[0]?.longitude} angle={current?.angle} vehicleType={vehicleType} track={primaryTrack} routeOverlays={displayedOverlays} threeDimensional={mode==="3d"} zoom={playback.started?19:16} events={events} showEvents={showEvents} showStops={false} showTrackPoints follow={follow} fitToken={fitToken} resizeToken={tripsVisible} routeColor="#1d4ed8" routeWeight={playback.started?7:6} brightMap/>:<div className="dw-trip-map-empty">{detailQuery.isLoading?"Loading selected trip...":detailQuery.isError?"Selected trip could not be loaded.":"Select a trip to display its route."}</div>}</div>
    <TripPlayback length={detail?.track.length||0} index={playback.index} playing={playback.playing} rate={playback.rate} start={detail?.trip.startTime} end={detail?.trip.endTime} onIndex={playback.setIndex} onPlaying={playback.setPlaying} onRate={playback.setRate}/>
    <TripLogTable rows={detail?.logs||[]} events={detail?.events||[]} tripLabel={label}/>
   </main>
  </div>
 </section>;
}
function defaultRange(){const to=new Date(),from=new Date(to.getTime()-7*86400000);return{from:localInput(from),to:localInput(to)};}
function localInput(value:Date){const local=new Date(value.getTime()-value.getTimezoneOffset()*60000);return local.toISOString().slice(0,16);}
function coordinate(lat?:number|null,lng?:number|null){return Number.isFinite(lat)&&Number.isFinite(lng)?`${Number(lat).toFixed(5)}, ${Number(lng).toFixed(5)}`:"No location";}
function tripLocation(trip:TripSummary){return`${coordinate(trip.startLatitude,trip.startLongitude)} ${coordinate(trip.endLatitude,trip.endLongitude)}`.toLowerCase();}
function dateTime(value:string){return new Date(value).toLocaleString("id-ID",{day:"2-digit",month:"short",hour:"2-digit",minute:"2-digit",second:"2-digit"});}
function formatDate(value:string){return new Date(value).toLocaleDateString("id-ID",{day:"2-digit",month:"short",year:"numeric"});}
function duration(seconds:number){const hours=Math.floor(seconds/3600),minutes=Math.round((seconds%3600)/60);return hours?`${hours}h ${minutes}m`:`${minutes} min`;}
function download(content:string,name:string){const link=document.createElement("a");link.href=URL.createObjectURL(new Blob([content],{type:"text/csv;charset=utf-8"}));link.download=name;link.click();URL.revokeObjectURL(link.href);}
