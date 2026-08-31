import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import html2canvas from "html2canvas";
import { Download, Expand, Eye, EyeOff, LocateFixed, Search, Settings2, Shrink } from "lucide-react";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";

import { TelemetryMap, type MapViewportPadding, type WorkspaceMapEvent } from "../../telemetry/components/telemetry-map";
import type { WorkspaceTrackPoint } from "../../telemetry/types/device-workspace-telemetry";
import { applyInstrumentSourceProfile, getInstrumentSourceOptions, getInstrumentSourceProfiles, getSelectedEvents, getSelectedPlaybackTelemetry, getSelectedRoutes, getTrips, saveInstrumentSourceProfile } from "../api/device-workspace-trip-route-api";
import { TripLogTable } from "../components/trip-log-table";
import { TripPlayback } from "../components/trip-playback";
import { TripSelectionList } from "../components/trip-selection-list";
import { TripFullscreenInstruments } from "../components/trip-fullscreen-instruments";
import { TripInstrumentSourceDialog } from "../components/trip-instrument-source-dialog";
import { TRIP_ROUTE_MAP_PROVIDER } from "../config/trip-route-map-provider";
import { exportTripHistory } from "../utils/trip-log-export";
import { useTripPlayback } from "../hooks/use-trip-playback";
import type { TripEvent, TripInstrumentSourceMap, TripInstrumentSourceProfile, TripParameter, TripPlaybackTelemetryRow, TripPoint, TripSummary } from "../types/device-workspace-trip-route";
import "../trip-route.css";

type Props = { deviceId: number; imei: string; vehicleType?: string | null; refreshToken: number };
type PlaybackPoint = { routeIndex: number; point: TripPoint };
const EMPTY_MAP_TRACK: WorkspaceTrackPoint[] = [];

export function TripRouteWorkspaceTab({ deviceId, imei, vehicleType, refreshToken }: Props) {
  const defaults = useMemo(() => defaultRange(), []);
  const queryClient = useQueryClient();
  const [from, setFrom] = useState(defaults.from);
  const [to, setTo] = useState(defaults.to);
  const [applied, setApplied] = useState(defaults);
  const [liveRange, setLiveRange] = useState(true);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [tripsVisible, setTripsVisible] = useState(true);
  const [status, setStatus] = useState("ALL");
  const [driver, setDriver] = useState("ALL");
  const [location, setLocation] = useState("");
  const [followLatest, setFollowLatest] = useState(true);
  const [checkedTripIds, setCheckedTripIds] = useState<Set<string>>(() => new Set());
  const [validationError, setValidationError] = useState("");
  const [mode, setMode] = useState<"2d" | "3d">("2d");
  const [fitToken, setFitToken] = useState(1);
  const [follow, setFollow] = useState(true);
  const [showEvents, setShowEvents] = useState(true);
  const [mapFullscreen, setMapFullscreen] = useState(false);
  const [mapViewportPadding, setMapViewportPadding] = useState<MapViewportPadding>({ top: 0, right: 0, bottom: 0, left: 0 });
  const [instrumentSourceOpen, setInstrumentSourceOpen] = useState(false);
  const [instrumentSources, setInstrumentSources] = useState<TripInstrumentSourceMap>(AUTO_SOURCES);
  const [instrumentProfileError, setInstrumentProfileError] = useState("");
  const [instrumentProfileSuccess, setInstrumentProfileSuccess] = useState("");
  const mapPanelRef = useRef<HTMLDivElement>(null);
  const playbackRunningRef = useRef(false);

  const listQuery = useQuery({
    queryKey: ["device-workspace", "trip-route", deviceId, applied.from, applied.to, liveRange, refreshToken],
    queryFn: () => getTrips(deviceId, new Date(applied.from).toISOString(), liveRange ? new Date().toISOString() : new Date(applied.to).toISOString()),
    enabled: deviceId > 0,
    refetchInterval: () => playbackRunningRef.current ? false : 15_000,
  });
  const trips = listQuery.data?.trips || [];

  useEffect(() => {
    if (!trips.length) {
      setSelectedId(null);
      return;
    }
    if ((liveRange && followLatest) || !trips.some(trip => trip.id === selectedId)) setSelectedId(trips[0].id);
  }, [trips, selectedId, liveRange, followLatest]);

  const measureMapViewportPadding = useCallback(() => {
    const panel = mapPanelRef.current;
    if (!panel || document.fullscreenElement !== panel) {
      setMapViewportPadding(previous => samePadding(previous, ZERO_MAP_PADDING) ? previous : ZERO_MAP_PADDING);
      return;
    }

    const panelRect = panel.getBoundingClientRect();
    const visibleRect = (selector: string) => {
      const element = panel.querySelector<HTMLElement>(selector);
      if (!element || element.getClientRects().length === 0) return null;
      return element.getBoundingClientRect();
    };
    const listRect = tripsVisible ? visibleRect(".dw-trip-fullscreen-list") : null;
    const instrumentsRect = visibleRect(".dw-trip-fullscreen-instruments");
    const playbackRect = visibleRect(".dw-trip-fullscreen-playback");
    const toolbarRect = visibleRect(".dw-trip-map-toolbar");
    const next: MapViewportPadding = {
      left: listRect ? Math.max(0, listRect.right - panelRect.left + 10) : 0,
      right: instrumentsRect ? Math.max(0, panelRect.right - instrumentsRect.left + 10) : 0,
      bottom: playbackRect ? Math.max(0, panelRect.bottom - playbackRect.top + 8) : 0,
      top: toolbarRect ? Math.max(0, toolbarRect.bottom - panelRect.top + 8) : 0,
    };
    setMapViewportPadding(previous => samePadding(previous, next) ? previous : next);
  }, [tripsVisible]);

  useEffect(() => {
    const panel = mapPanelRef.current;
    if (!panel) return;
    const resizeObserver = new ResizeObserver(measureMapViewportPadding);
    resizeObserver.observe(panel);
    const frame = window.requestAnimationFrame(measureMapViewportPadding);
    window.addEventListener("resize", measureMapViewportPadding);
    return () => {
      resizeObserver.disconnect();
      window.cancelAnimationFrame(frame);
      window.removeEventListener("resize", measureMapViewportPadding);
    };
  }, [measureMapViewportPadding, mapFullscreen]);

  useEffect(() => {
    const onFullscreenChange = () => {
      const active = document.fullscreenElement === mapPanelRef.current;
      setMapFullscreen(active);
      window.requestAnimationFrame(() => {
        measureMapViewportPadding();
        window.requestAnimationFrame(() => setFitToken(value => value + 1));
      });
    };
    document.addEventListener("fullscreenchange", onFullscreenChange);
    return () => document.removeEventListener("fullscreenchange", onFullscreenChange);
  }, [measureMapViewportPadding]);

  const instrumentProfilesQuery = useQuery({
    queryKey: ["device-workspace", "trip-route-instrument-source-profiles", deviceId],
    queryFn: () => getInstrumentSourceProfiles(deviceId),
    enabled: deviceId > 0,
    staleTime: 30_000,
  });
  const instrumentSourceOptionsQuery = useQuery({
    queryKey: ["device-workspace", "trip-route-instrument-source-options", deviceId],
    queryFn: () => getInstrumentSourceOptions(deviceId),
    enabled: deviceId > 0 && instrumentSourceOpen,
    staleTime: 60_000,
  });
  useEffect(() => {
    const data = instrumentProfilesQuery.data;
    if (!data) return;
    const profile = data.profiles.find(item => item.id === data.effectiveProfileId);
    setInstrumentSources(profile ? profileToSources(profile) : AUTO_SOURCES);
  }, [deviceId, instrumentProfilesQuery.data]);
  const saveInstrumentProfile = useMutation({
    mutationFn: ({name,applyAll}:{name:string;applyAll:boolean}) => saveInstrumentSourceProfile(deviceId,name,instrumentSources,applyAll),
    onMutate: () => { setInstrumentProfileError(""); setInstrumentProfileSuccess(""); },
    onSuccess: async (profile, variables) => {
      setInstrumentSources(profileToSources(profile));
      await queryClient.invalidateQueries({queryKey:["device-workspace","trip-route-instrument-source-profiles",deviceId]});
      await instrumentProfilesQuery.refetch();
      setInstrumentProfileSuccess(variables.applyAll
        ? `Configuration "${profile.name}" saved and applied to all IMEIs in this company.`
        : `Configuration "${profile.name}" saved and applied to IMEI ${imei}.`);
      setInstrumentSourceOpen(false);
    },
    onError: error => setInstrumentProfileError(error instanceof Error ? error.message : "Source configuration could not be saved."),
  });
  const applyInstrumentProfile = useMutation({
    mutationFn: ({profileId,applyAll}:{profileId:number;applyAll:boolean}) => applyInstrumentSourceProfile(deviceId,profileId,applyAll),
    onMutate: () => { setInstrumentProfileError(""); setInstrumentProfileSuccess(""); },
    onSuccess: async (profile, variables) => {
      setInstrumentSources(profileToSources(profile));
      await queryClient.invalidateQueries({queryKey:["device-workspace","trip-route-instrument-source-profiles",deviceId]});
      await instrumentProfilesQuery.refetch();
      setInstrumentProfileSuccess(variables.applyAll
        ? `Configuration "${profile.name}" applied to all IMEIs in this company.`
        : `Configuration "${profile.name}" applied to IMEI ${imei}.`);
    },
    onError: error => setInstrumentProfileError(error instanceof Error ? error.message : "Saved configuration could not be applied."),
  });

  const selected = trips.find(trip => trip.id === selectedId) || null;
  const drivers = useMemo(() => [...new Set(trips.map(trip => trip.driverName).filter(name => name && name !== "-"))], [trips]);
  const filtered = useMemo(() => trips.filter(trip =>
    (status === "ALL" || trip.status === status)
    && (driver === "ALL" || trip.driverName === driver)
    && (!location || tripLocation(trip).includes(location.toLowerCase()))
  ), [trips, status, driver, location]);

  const selectedSegments = useMemo(
    () => trips.filter(trip => checkedTripIds.has(trip.id)).sort((a, b) => Date.parse(a.startTime) - Date.parse(b.startTime)),
    [trips, checkedTripIds],
  );
  const selectedRanges = useMemo(() => selectedSegments.map(segment => ({ from: segment.startTime, to: segment.endTime })), [selectedSegments]);
  const selectedRouteRanges = useMemo(() => selectedSegments.map(segment => ({
    tripId: segment.id, from: segment.startTime, to: segment.endTime,
  })), [selectedSegments]);
  const selectedRouteKey = useMemo(
    () => selectedRouteRanges.map(range => `${range.tripId}:${range.from}:${range.to}`).join("|"),
    [selectedRouteRanges],
  );
  const hasLiveSelection = selectedSegments.some(segment => segment.status === "IN_PROGRESS");
  const selectedRoutesQuery = useQuery({
    queryKey: ["device-workspace", "trip-route-routes-selection", deviceId, selectedRouteKey, refreshToken],
    queryFn: () => getSelectedRoutes(deviceId, selectedRouteRanges),
    enabled: selectedRouteRanges.length > 0,
    placeholderData: previous => previous,
    refetchInterval: () => playbackRunningRef.current ? false : hasLiveSelection ? 15_000 : false,
  });
  const availableRoutes = selectedRoutesQuery.data?.routes || [];
  const selectedRoutePoints = useMemo(
    () => selectedSegments.map(segment => availableRoutes.find(candidate => candidate.tripId === segment.id)?.track || []),
    [selectedSegments, availableRoutes],
  );
  const mapRouteTracks = useMemo(
    () => selectedRoutePoints.map(route => route.map(point => ({
      latitude: point.latitude,
      longitude: point.longitude,
      angle: point.angle,
      speed: point.speed,
      occurredAt: point.occurredAt,
    } satisfies WorkspaceTrackPoint))),
    [selectedRoutePoints],
  );
  const playbackPoints = useMemo<PlaybackPoint[]>(
    () => selectedRoutePoints.flatMap((route, routeIndex) => route.map(point => ({ routeIndex, point }))),
    [selectedRoutePoints],
  );
  const playbackKey = selectedSegments.map(segment => segment.id).join("|");
  const playbackTimeline = useMemo(() => playbackPoints.map(item => item.point.occurredAt), [playbackPoints]);
  const playback = useTripPlayback(playbackTimeline, playbackKey);
  playbackRunningRef.current = playback.playing;
  const currentPlayback = playbackPoints[playback.index]?.point;
  const playbackTelemetryIds = useMemo(() => playbackPoints.map(item => item.point.telemetryId), [playbackPoints]);
  const playbackTelemetryQuery = useQuery({
    queryKey: ["device-workspace", "trip-route-playback-telemetry", deviceId, selectedRouteKey, refreshToken],
    queryFn: () => getSelectedPlaybackTelemetry(deviceId, selectedRanges, playbackTelemetryIds),
    enabled: selectedRanges.length > 0 && playbackTelemetryIds.length > 0 && (mapFullscreen || instrumentSourceOpen),
    staleTime: 30_000,
  });
  const playbackTelemetryMap = useMemo(() => new Map((playbackTelemetryQuery.data || []).map(row => [row.telemetryId, row])), [playbackTelemetryQuery.data]);
  const currentTelemetry = currentPlayback ? playbackTelemetryMap.get(currentPlayback.telemetryId) : undefined;
  const sourceOptions = useMemo(() => collectPlaybackParameters(playbackTelemetryQuery.data || []), [playbackTelemetryQuery.data]);
  const effectiveSources = useMemo(() => resolveInstrumentSources(instrumentSources, sourceOptions, listQuery.data?.energyGroup), [instrumentSources, sourceOptions, listQuery.data?.energyGroup]);
  const instrumentValues = useMemo(() => resolveInstrumentValues(currentTelemetry, currentPlayback?.speed, effectiveSources, listQuery.data?.energyGroup), [currentTelemetry, currentPlayback?.speed, effectiveSources, listQuery.data?.energyGroup]);

  const selectedEventsQuery = useQuery({
    queryKey: ["device-workspace", "trip-route-events-selection", deviceId, selectedRouteKey, refreshToken],
    queryFn: () => getSelectedEvents(deviceId, selectedRanges),
    enabled: showEvents && selectedRanges.length > 0,
    placeholderData: previous => previous,
    refetchInterval: () => playbackRunningRef.current ? false : hasLiveSelection ? 15_000 : false,
  });
  const events = useMemo<WorkspaceMapEvent[]>(() => groupReportsByTelemetry(selectedEventsQuery.data || []), [selectedEventsQuery.data]);

  const firstTrackPoint = mapRouteTracks.find(route => route.length > 0)?.[0];
  const mapLatitude = currentPlayback?.latitude ?? firstTrackPoint?.latitude;
  const mapLongitude = currentPlayback?.longitude ?? firstTrackPoint?.longitude;
  const mapAngle = currentPlayback?.angle ?? firstTrackPoint?.angle;
  const allFilteredChecked = filtered.length > 0 && filtered.every(trip => checkedTripIds.has(trip.id));

  function apply() {
    const start = new Date(from);
    const end = new Date(to);
    if (!Number.isFinite(start.getTime()) || !Number.isFinite(end.getTime()) || start >= end) {
      setValidationError("Date To must be later than Date From.");
      return;
    }
    if (end.getTime() - start.getTime() > 31 * 86400000) {
      setValidationError("Date range cannot exceed 31 days.");
      return;
    }
    setValidationError("");
    setLiveRange(false);
    setFollowLatest(false);
    setApplied({ from, to });
    setSelectedId(null);
    setCheckedTripIds(new Set());
  }

  async function exportTrips() {
    if (!filtered.length) {
      setValidationError("No Trip/Stop data is available to export for the current filters.");
      return;
    }
    if (!selectedSegments.length) {
      setValidationError("Check at least one Trip/Stop so the export can include its route map snapshot.");
      return;
    }
    if (!mapRouteTracks.some(route => route.length > 1)) {
      setValidationError("Selected route data is still loading or unavailable. Retry after the route is visible on the map.");
      return;
    }

    const mapElement = mapPanelRef.current?.querySelector<HTMLElement>(".dw-map");
    if (!mapElement) {
      setValidationError("The selected route map is not available for export.");
      return;
    }

    setValidationError("");
    try {
      const mapPng = await captureTripMapSnapshot(mapElement, mapRouteTracks);
      await exportTripHistory(filtered, imei, selectedSegments.length, mapPng);
    } catch (error) {
      console.error("Trip export could not be created", error);
      setValidationError("Trip export could not be created. Please retry after the selected route is fully loaded.");
    }
  }

  function selectTrip(tripId: string) {
    setFollowLatest(false);
    setSelectedId(tripId);
  }

  function toggleTrip(tripId: string) {
    const willCheck = !checkedTripIds.has(tripId);
    setCheckedTripIds(current => {
      const next = new Set(current);
      next.has(tripId) ? next.delete(tripId) : next.add(tripId);
      return next;
    });
    if (willCheck) selectTrip(tripId);
  }

  function toggleAllVisible() {
    setFollowLatest(false);
    setCheckedTripIds(current => {
      const next = new Set(current);
      if (allFilteredChecked) filtered.forEach(trip => next.delete(trip.id));
      else filtered.forEach(trip => next.add(trip.id));
      return next;
    });
    if (!allFilteredChecked && filtered.length) setSelectedId(filtered[0].id);
  }

  const stopFollowingForManualView = useCallback(() => setFollow(false), []);

  function fitSelectedRoute() {
    if (!selectedSegments.length) return;
    setFollow(false);
    setFitToken(value => value + 1);
  }

  function setPlaybackRunning(value: boolean) {
    if (value) setFollow(true);
    playback.setPlaying(value);
  }

  async function toggleMapFullscreen() {
    const panel = mapPanelRef.current;
    if (!panel) return;
    if (document.fullscreenElement === panel) await document.exitFullscreen();
    else await panel.requestFullscreen();
  }

  const playbackStart = selectedSegments[0]?.startTime;
  const playbackEnd = selectedSegments.at(-1)?.endTime;

  return <section className="dw-trip-route-tab">
    <header className="dw-trip-filters">
      <label>Date From<input type="datetime-local" value={from} onChange={event => setFrom(event.target.value)}/></label>
      <label>Date To<input type="datetime-local" value={to} onChange={event => setTo(event.target.value)}/></label>
      <label>Trip Status<select value={status} onChange={event => setStatus(event.target.value)}><option value="ALL">All Status</option><option value="COMPLETED">Completed</option><option value="IN_PROGRESS">In Progress</option></select></label>
      <label>Driver<select value={driver} onChange={event => setDriver(event.target.value)}><option value="ALL">All Driver</option>{drivers.map(name => <option key={name}>{name}</option>)}</select></label>
      <label className="search">Search Location<span><Search/><input value={location} onChange={event => setLocation(event.target.value)} placeholder="Coordinate or location"/></span></label>
      <button type="button" className="primary" onClick={apply}>Apply</button>
      <button type="button" onClick={() => void exportTrips()}><Download/>Export</button>
    </header>
    {validationError ? <div className="dw-trip-error" role="alert">{validationError}</div> : null}
    {listQuery.isError ? <div className="dw-trip-error" role="alert">Trip history could not be loaded. Check the backend service and retry.</div> : null}
    <div className={`dw-trip-body ${tripsVisible ? "" : "trips-hidden"}`}>
      <TripSelectionList trips={filtered} loading={listQuery.isLoading} selectedId={selectedId} checkedTripIds={checkedTripIds}
        allChecked={allFilteredChecked} visible={tripsVisible} onToggleAll={toggleAllVisible} onSelect={selectTrip}
        onToggle={toggleTrip} onVisible={setTripsVisible}/>
      <main>
        <div ref={mapPanelRef} className="dw-trip-map-panel">
          <div className="dw-trip-map-toolbar">
            <button type="button" className="dw-trip-source-toolbar-button" onClick={() => setInstrumentSourceOpen(true)} title="Configure playback instrument sources"><Settings2/>Sources</button>
            <button type="button" className={mode === "2d" ? "active" : ""} onClick={() => setMode("2d")}>2D</button>
            <button type="button" className={mode === "3d" ? "active" : ""} onClick={() => setMode("3d")}>3D</button>
            <button type="button" onClick={fitSelectedRoute} disabled={!selectedSegments.length}><LocateFixed/>Fit Route</button>
            <button type="button" className={follow ? "active" : ""} onClick={() => setFollow(value => !value)}>{follow ? <Eye/> : <EyeOff/>}Follow Vehicle</button>
            <button type="button" className={showEvents ? "active" : ""} onClick={() => setShowEvents(value => !value)}>Events</button>
            <button type="button" onClick={() => void toggleMapFullscreen()} title={mapFullscreen ? "Exit fullscreen" : "Fullscreen"} aria-label={mapFullscreen ? "Exit fullscreen" : "Fullscreen"}>{mapFullscreen ? <Shrink/> : <Expand/>}</button>
          </div>
          <TripFullscreenInstruments rpm={instrumentValues.rpm} speed={instrumentValues.speed}
            level={instrumentValues.level} consumption={instrumentValues.consumption} odometer={instrumentValues.odometer}/>
          <TripSelectionList trips={filtered} loading={listQuery.isLoading} selectedId={selectedId} checkedTripIds={checkedTripIds}
            allChecked={allFilteredChecked} visible={tripsVisible} fullscreen onToggleAll={toggleAllVisible} onSelect={selectTrip}
            onToggle={toggleTrip} onVisible={setTripsVisible}/>
          <div className={`dw-trip-fullscreen-playback ${tripsVisible ? "with-trip-list" : ""}`}>
            <TripPlayback length={playbackPoints.length} index={playback.index} playing={playback.playing} rate={playback.rate}
              start={playbackStart} end={playbackEnd} onIndex={playback.setIndex} onPlaying={setPlaybackRunning} onRate={playback.setRate}/>
          </div>
          {selectedSegments.length
            ? <TelemetryMap latitude={mapLatitude} longitude={mapLongitude} angle={mapAngle} vehicleType={vehicleType}
                track={EMPTY_MAP_TRACK} routeOverlays={mapRouteTracks} fitRouteOverlays={mapRouteTracks}
                playbackProgress={playback.started ? playback.index + 1 : null} threeDimensional={mode === "3d"} zoom={playback.started ? 19 : 16}
                events={events} showEvents={showEvents} compactEvents showStops={false} follow={follow}
                fitToken={fitToken} resizeToken={`${tripsVisible}-${mapFullscreen}`} routeColor="#168bff"
                routeHaloColor="#ffffff" routeWeight={playback.started ? 8 : 7} brightMap viewportPadding={mapViewportPadding}
                provider={TRIP_ROUTE_MAP_PROVIDER} onUserViewChange={stopFollowingForManualView}/>
            : <div className="dw-trip-map-empty">Check one or more Trip/Stop items to display route data.</div>}
        </div>
        <div className="dw-trip-normal-playback"><TripPlayback length={playbackPoints.length} index={playback.index} playing={playback.playing} rate={playback.rate}
          start={playbackStart} end={playbackEnd} onIndex={playback.setIndex} onPlaying={setPlaybackRunning} onRate={playback.setRate}/></div>
        <TripLogTable deviceId={deviceId} imei={imei} selectedSegments={selectedSegments}/>
      </main>
    </div>
    <TripInstrumentSourceDialog open={instrumentSourceOpen} energyGroup={listQuery.data?.energyGroup}
      rows={playbackTelemetryQuery.data || []} catalog={instrumentSourceOptionsQuery.data || []} value={instrumentSources}
      profiles={instrumentProfilesQuery.data?.profiles || []} effectiveProfileId={instrumentProfilesQuery.data?.effectiveProfileId}
      busy={saveInstrumentProfile.isPending || applyInstrumentProfile.isPending} error={instrumentProfileError} success={instrumentProfileSuccess}
      onChange={setInstrumentSources}
      onSave={(name,applyAll)=>saveInstrumentProfile.mutate({name,applyAll})}
      onApply={(profileId,applyAll)=>applyInstrumentProfile.mutate({profileId,applyAll})}
      onClose={() => { setInstrumentSourceOpen(false); setInstrumentProfileError(""); setInstrumentProfileSuccess(""); }}/>
  </section>;
}


const AUTO_SOURCES:TripInstrumentSourceMap={rpm:"",speed:"__gps_speed__",level:"",consumption:"",odometer:""};
function profileToSources(profile:TripInstrumentSourceProfile):TripInstrumentSourceMap{return{rpm:profile.rpmSource||"",speed:profile.speedSource||"__gps_speed__",level:profile.levelSource||"",consumption:profile.consumptionSource||"",odometer:profile.odometerSource||""};}
function collectPlaybackParameters(rows:TripPlaybackTelemetryRow[]){const map=new Map<string,TripParameter>();for(const row of rows)for(const parameter of row.parameters||[]){if(!parameter.fieldCode||map.has(parameter.fieldCode)||numeric(parameter.value)==null)continue;map.set(parameter.fieldCode,parameter);}return [...map.values()];}
function resolveInstrumentSources(current:TripInstrumentSourceMap,options:TripParameter[],energyGroup?:string|null):TripInstrumentSourceMap{const group=String(energyGroup||"FUEL").toUpperCase();return{rpm:current.rpm||findSource(options,["engine_rpm","engine rpm","engine speed","rpm"]),speed:current.speed||"__gps_speed__",level:current.level||findSource(options,group==="ELECTRIC"?["battery_soc","state of charge","battery level"]:group==="GAS"?["gas level","cng level","lpg level","gas pressure"]:["fuel_level","fuel level"]),consumption:current.consumption||findSource(options,group==="ELECTRIC"?["battery consumption","battery power","high voltage battery current"]:group==="GAS"?["gas consumption","cng used","lpg used","gas used"]:["fuel_rate","fuel consumption","fuel used"]),odometer:current.odometer||findSource(options,["total_odometer","odometer","total mileage","mileage"])};}

function groupReportsByTelemetry(events: TripEvent[]): WorkspaceMapEvent[] {
  const groups = new Map<string, TripEvent[]>();
  for (const event of events) {
    if (!Number.isFinite(event.latitude) || !Number.isFinite(event.longitude)) continue;
    const key = event.telemetryId != null
      ? `telemetry:${event.telemetryId}`
      : `gps:${Number(event.latitude).toFixed(6)}:${Number(event.longitude).toFixed(6)}:${event.occurredAt}`;
    const group = groups.get(key);
    if (group) group.push(event); else groups.set(key, [event]);
  }
  return [...groups.values()].map(group => {
    const event = group[0];
    const combined = group.length > 1;
    return {
      id: event.id,
      telemetryId: event.telemetryId,
      title: combined ? `${group.length} Reports` : event.title,
      occurredAt: event.occurredAt,
      latitude: event.latitude,
      longitude: event.longitude,
      speed: event.speed,
      severity: highestSeverity(group),
      locationSource: event.locationSource,
      message: combined
        ? group.map(item => `${item.title}${item.message?.trim() ? `: ${item.message.trim()}` : ""}`).join(" · ")
        : event.message,
    };
  });
}

function highestSeverity(events: TripEvent[]) {
  const rank = (severity: string) => severity.toUpperCase().includes("CRITICAL") ? 3
    : severity.toUpperCase().includes("HIGH") ? 2
      : severity.toUpperCase().includes("WARN") ? 1 : 0;
  return events.reduce((selected, event) => rank(event.severity) > rank(selected) ? event.severity : selected, events[0]?.severity || "INFO");
}
function findSource(options:TripParameter[],needles:string[]){const normalized=needles.map(value=>value.toLowerCase());return options.find(option=>{const text=`${option.fieldCode} ${option.label}`.toLowerCase();return normalized.some(needle=>text.includes(needle));})?.fieldCode||"";}
function resolveInstrumentValues(row:TripPlaybackTelemetryRow|undefined,routeSpeed:number|null|undefined,sources:TripInstrumentSourceMap,energyGroup?:string|null){const group=String(energyGroup||"FUEL").toUpperCase();const source=(key:string)=>row?.parameters?.find(parameter=>parameter.fieldCode===key);const value=(key:string)=>numeric(source(key)?.value);const metric=(key:string,label:string,fallbackUnit:string)=>({label,value:value(key),unit:source(key)?.unit||fallbackUnit});return{rpm:value(sources.rpm),speed:sources.speed==="__gps_speed__"?(row?.speed??routeSpeed??null):value(sources.speed),level:metric(sources.level,group==="ELECTRIC"?"SOC / Battery Level":group==="GAS"?"Gas Level":"Fuel Level","%"),consumption:metric(sources.consumption,group==="ELECTRIC"?"Battery Consumption":group==="GAS"?"Gas Consumption":"Fuel Consumption",group==="ELECTRIC"?"kW":group==="GAS"?"kg/h":"l/h"),odometer:metric(sources.odometer,"Odometer / Total Mileage","km")};}
function numeric(value?:string|null){if(value==null)return null;const parsed=Number(String(value).trim().replace(",","."));return Number.isFinite(parsed)?parsed:null;}

function defaultRange() {
  const to = new Date();
  const from = new Date(to.getTime() - 7 * 86400000);
  return { from: localInput(from), to: localInput(to) };
}
function localInput(value: Date) {
  const local = new Date(value.getTime() - value.getTimezoneOffset() * 60000);
  return local.toISOString().slice(0, 16);
}
function coordinate(lat?: number | null, lng?: number | null) {
  return Number.isFinite(lat) && Number.isFinite(lng) ? `${Number(lat).toFixed(5)}, ${Number(lng).toFixed(5)}` : "No location";
}
function tripLocation(trip: TripSummary) {
  return `${coordinate(trip.startLatitude, trip.startLongitude)} ${coordinate(trip.endLatitude, trip.endLongitude)}`.toLowerCase();
}
async function captureTripMapSnapshot(element: HTMLElement, routes: WorkspaceTrackPoint[][]) {
  try {
    const canvas = await html2canvas(element, {
      useCORS: true,
      allowTaint: false,
      logging: false,
      backgroundColor: null,
      scale: Math.min(window.devicePixelRatio || 1, 1.5),
    });
    if (snapshotHasVisibleContent(canvas)) {
      const png = dataUrlBytes(canvas.toDataURL("image/png"));
      if (png.length > 1024) return png;
    }
  } catch (error) {
    console.warn("Map snapshot was blocked; using selected-route export fallback.", error);
  }
  return routeSnapshotPng(routes, element);
}

function snapshotHasVisibleContent(canvas: HTMLCanvasElement) {
  const context = canvas.getContext("2d", { willReadFrequently: true });
  if (!context || canvas.width < 2 || canvas.height < 2) return false;
  const colors = new Set<number>();
  const xStep = Math.max(1, Math.floor(canvas.width / 16));
  const yStep = Math.max(1, Math.floor(canvas.height / 10));
  for (let y = yStep / 2; y < canvas.height; y += yStep) {
    for (let x = xStep / 2; x < canvas.width; x += xStep) {
      const pixel = context.getImageData(Math.floor(x), Math.floor(y), 1, 1).data;
      colors.add((pixel[0] << 24) | (pixel[1] << 16) | (pixel[2] << 8) | pixel[3]);
      if (colors.size >= 4) return true;
    }
  }
  return false;
}

function routeSnapshotPng(routes: WorkspaceTrackPoint[][], element: HTMLElement) {
  const points = routes.flat().filter(point => Number.isFinite(point.latitude) && Number.isFinite(point.longitude));
  if (points.length < 2) throw new Error("Selected route does not contain enough coordinates for export.");

  const canvas = document.createElement("canvas");
  canvas.width = 1200;
  canvas.height = 430;
  const context = canvas.getContext("2d");
  if (!context) throw new Error("Canvas is not available for trip route export.");

  const styles = getComputedStyle(element);
  context.fillStyle = styles.backgroundColor && styles.backgroundColor !== "rgba(0, 0, 0, 0)" ? styles.backgroundColor : "#ffffff";
  context.fillRect(0, 0, canvas.width, canvas.height);

  const meanLatitude = points.reduce((sum, point) => sum + point.latitude, 0) / points.length;
  const longitudeScale = Math.max(0.01, Math.cos(meanLatitude * Math.PI / 180));
  const projected = points.map(point => ({ x: point.longitude * longitudeScale, y: point.latitude }));
  const minX = Math.min(...projected.map(point => point.x));
  const maxX = Math.max(...projected.map(point => point.x));
  const minY = Math.min(...projected.map(point => point.y));
  const maxY = Math.max(...projected.map(point => point.y));
  const padding = 36;
  const usableWidth = canvas.width - padding * 2;
  const usableHeight = canvas.height - padding * 2;
  const spanX = Math.max(maxX - minX, 0.000001);
  const spanY = Math.max(maxY - minY, 0.000001);
  const scale = Math.min(usableWidth / spanX, usableHeight / spanY);
  const routeWidth = spanX * scale;
  const routeHeight = spanY * scale;
  const offsetX = padding + (usableWidth - routeWidth) / 2;
  const offsetY = padding + (usableHeight - routeHeight) / 2;
  const project = (point: WorkspaceTrackPoint) => ({
    x: offsetX + (point.longitude * longitudeScale - minX) * scale,
    y: offsetY + (maxY - point.latitude) * scale,
  });

  context.lineCap = "round";
  context.lineJoin = "round";
  context.strokeStyle = "#168bff";
  context.lineWidth = 7;
  for (const route of routes) {
    const valid = route.filter(point => Number.isFinite(point.latitude) && Number.isFinite(point.longitude));
    if (valid.length < 2) continue;
    context.beginPath();
    valid.forEach((point, index) => {
      const projected = project(point);
      if (index === 0) context.moveTo(projected.x, projected.y);
      else context.lineTo(projected.x, projected.y);
    });
    context.stroke();
  }

  return dataUrlBytes(canvas.toDataURL("image/png"));
}

function dataUrlBytes(dataUrl: string) {
  const comma = dataUrl.indexOf(",");
  if (comma < 0) throw new Error("Invalid PNG data URL.");
  const binary = atob(dataUrl.slice(comma + 1));
  const bytes = new Uint8Array(binary.length);
  for (let index = 0; index < binary.length; index++) bytes[index] = binary.charCodeAt(index);
  return bytes;
}

const ZERO_MAP_PADDING:MapViewportPadding={top:0,right:0,bottom:0,left:0};
function samePadding(a:MapViewportPadding,b:MapViewportPadding){return a.top===b.top&&a.right===b.right&&a.bottom===b.bottom&&a.left===b.left;}
