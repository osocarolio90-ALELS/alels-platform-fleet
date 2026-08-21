import { lazy, Suspense, useEffect, useMemo, useRef } from "react";
import L from "leaflet";
import { MapContainer, Marker, Pane, TileLayer, useMap } from "react-leaflet";
import "leaflet/dist/leaflet.css";

import type { WorkspaceTrackPoint } from "../types/device-workspace-telemetry";
import { vehicleMarkerSvg } from "./vehicle-map-marker";

const TerrainMap = lazy(() => import("./terrain-map"));
const EMPTY_ROUTES: WorkspaceTrackPoint[][] = [];
const FOLLOW_MIN_INTERVAL_MS = 650;
const FOLLOW_DEAD_ZONE_RATIO = 0.28;

type LatLng = [number, number];

export type WorkspaceMapEvent = {
  id: number;
  title: string;
  occurredAt: string;
  latitude?: number | null;
  longitude?: number | null;
  speed?: number | null;
  severity?: string | null;
};

export type MapViewportPadding = { top: number; right: number; bottom: number; left: number };
export type WorkspaceMapProviderOptions = {
  tileUrl2d?: string;
  tileAttribution2d?: string;
  styleUrl3d?: string;
  buildingsUrl3d?: string;
  showAttribution?: boolean;
};

const ZERO_VIEWPORT_PADDING: MapViewportPadding = { top: 0, right: 0, bottom: 0, left: 0 };

type Props = {
  latitude?: number | null;
  longitude?: number | null;
  angle?: number | null;
  vehicleType?: string | null;
  track: WorkspaceTrackPoint[];
  routeOverlays?: WorkspaceTrackPoint[][];
  fitRouteOverlays?: WorkspaceTrackPoint[][];
  playbackProgress?: number | null;
  threeDimensional: boolean;
  zoom: number;
  events?: WorkspaceMapEvent[];
  showEvents?: boolean;
  showStops?: boolean;
  showTrackPoints?: boolean;
  follow?: boolean;
  fitToken?: number;
  resizeToken?: unknown;
  routeColor?: string;
  routeWeight?: number;
  routeHaloColor?: string;
  brightMap?: boolean;
  viewportPadding?: MapViewportPadding;
  provider?: WorkspaceMapProviderOptions;
  onUserViewChange?: () => void;
};

export function TelemetryMap({
  latitude,
  longitude,
  angle,
  vehicleType,
  track,
  routeOverlays,
  fitRouteOverlays,
  playbackProgress = null,
  threeDimensional,
  zoom,
  events = [],
  showEvents = false,
  showStops = false,
  showTrackPoints = false,
  follow = true,
  fitToken = 0,
  resizeToken,
  routeColor = "#168bff",
  routeWeight = 4,
  routeHaloColor,
  brightMap = false,
  viewportPadding = ZERO_VIEWPORT_PADDING,
  provider,
  onUserViewChange,
}: Props) {
  const overlays = routeOverlays ?? EMPTY_ROUTES;
  const fitOverlays = fitRouteOverlays ?? overlays;
  const valid = hasValidPosition(latitude, longitude);
  const center: LatLng = valid ? [latitude as number, longitude as number] : [-6.2088, 106.8456];

  const route = useMemo(() => toLeafletRoute(track), [track]);
  const overlayRoutes = useMemo(
    () => overlays.map(toLeafletRoute).filter(candidate => candidate.length > 1),
    [overlays],
  );
  const renderRoutes = useMemo(() => [route, ...overlayRoutes].filter(candidate => candidate.length > 0), [route, overlayRoutes]);
  const fitBoundsRoute = useMemo(() => {
    const fitRoutes = fitOverlays.map(toLeafletRoute).filter(candidate => candidate.length > 1);
    const primary = route.length > 1 ? [route] : [];
    return [...primary, ...fitRoutes].flat();
  }, [route, fitOverlays]);

  if (threeDimensional) {
    return <Suspense fallback={<div className="dw-map dw-map-empty">Loading 3D terrain...</div>}>
      <TerrainMap
        center={center}
        valid={valid}
        zoom={zoom}
        angle={angle}
        vehicleType={vehicleType}
        track={track}
        routeOverlays={overlays}
        fitRouteOverlays={fitOverlays}
        playbackProgress={playbackProgress}
        events={events}
        showEvents={showEvents}
        showTrackPoints={showTrackPoints}
        follow={follow}
        fitToken={fitToken}
        routeColor={routeColor}
        routeWeight={routeWeight}
        routeHaloColor={routeHaloColor}
        brightMap={brightMap}
        viewportPadding={viewportPadding}
        provider={provider}
        onUserViewChange={onUserViewChange}
      />
    </Suspense>;
  }

  return <div className={`dw-map${brightMap ? " dw-map-bright" : ""}`}>
    <MapContainer center={center} zoom={zoom} zoomControl={false} attributionControl={provider?.showAttribution ?? false} keyboard={false}>
      <MapSync
        center={center}
        zoom={zoom}
        route={fitBoundsRoute}
        follow={follow}
        fitToken={fitToken}
        resizeToken={resizeToken}
        viewportPadding={viewportPadding}
      />
      <MapManualInteraction onUserViewChange={onUserViewChange}/>
      <PreserveLeafletVectorTone/>
      <TileLayer
        url={provider?.tileUrl2d ?? "https://tile.openstreetmap.org/{z}/{x}/{y}.png"}
        attribution={provider?.tileAttribution2d}
      />
      <Pane name="alels-route-pane" style={{ zIndex: 460, pointerEvents: "none" }}/>
      <LeafletRouteLayers
        routes={renderRoutes}
        playbackProgress={playbackProgress}
        routeColor={routeColor}
        routeWeight={routeWeight}
        routeHaloColor={routeHaloColor}
        showTrackPoints={showTrackPoints}
      />
      {showStops ? track.filter(point => hasValidPosition(point.latitude, point.longitude) && (point.speed ?? 0) <= 1)
        .map((point, index) => <Marker key={`stop-${point.occurredAt}-${index}`} position={[point.latitude, point.longitude]} icon={mapPin("•", "#f59e0b")}><span/></Marker>) : null}
      {showEvents ? events.filter(event => hasValidPosition(event.latitude, event.longitude))
        .map(event => <Marker key={event.id} position={[event.latitude!, event.longitude!]} icon={eventMapPin(event.title, eventColor(event.severity))}/>) : null}
      <LeafletVehicleMarker center={center} valid={valid} angle={angle} vehicleType={vehicleType}/>
    </MapContainer>
    {!valid ? <div className="dw-map-empty">Waiting for GPS position</div> : null}
  </div>;
}

function MapManualInteraction({ onUserViewChange }: { onUserViewChange?: () => void }) {
  const map = useMap();
  const callbackRef = useRef(onUserViewChange);
  callbackRef.current = onUserViewChange;

  useEffect(() => {
    const container = map.getContainer();
    const notify = () => callbackRef.current?.();
    container.addEventListener("pointerdown", notify);
    container.addEventListener("wheel", notify, { passive: true });
    return () => {
      container.removeEventListener("pointerdown", notify);
      container.removeEventListener("wheel", notify);
    };
  }, [map]);
  return null;
}

function PreserveLeafletVectorTone() {
  const map = useMap();
  useEffect(() => {
    const container = map.getContainer();
    const preserve = () => container.querySelectorAll("svg").forEach(svg => svg.setAttribute("data-preserve-tone", "true"));
    preserve();
    const observer = new MutationObserver(preserve);
    observer.observe(container, { childList: true, subtree: true });
    return () => observer.disconnect();
  }, [map]);
  return null;
}

function LeafletRouteLayers({
  routes,
  playbackProgress,
  routeColor,
  routeWeight,
  routeHaloColor,
  showTrackPoints,
}: {
  routes: LatLng[][];
  playbackProgress: number | null;
  routeColor: string;
  routeWeight: number;
  routeHaloColor?: string;
  showTrackPoints: boolean;
}) {
  const map = useMap();
  const layersRef = useRef<Array<{ halo: L.Polyline; line: L.Polyline }>>([]);
  const pointLayersRef = useRef<L.CircleMarker[]>([]);
  const previousProgressRef = useRef<number | null>(null);

  useEffect(() => () => {
    clearRouteLayers(map, layersRef.current, pointLayersRef.current);
    layersRef.current = [];
    pointLayersRef.current = [];
    previousProgressRef.current = null;
  }, [map]);

  useEffect(() => {
    while (layersRef.current.length < routes.length) {
      layersRef.current.push(createLeafletRouteLayer(map, routeColor, routeWeight, routeHaloColor));
    }
    while (layersRef.current.length > routes.length) {
      const layer = layersRef.current.pop();
      if (layer) {
        layer.halo.removeFrom(map);
        layer.line.removeFrom(map);
      }
    }

    applyRouteProgress(layersRef.current, routes, playbackProgress, null);
    previousProgressRef.current = playbackProgress;
  }, [map, routes]);

  useEffect(() => {
    for (const layer of layersRef.current) {
      layer.halo.setStyle({ color: routeHaloColor ?? "#ffffff", weight: routeWeight + 5, opacity: 0.96 });
      layer.line.setStyle({ color: routeColor, weight: routeWeight, opacity: 1 });
    }
  }, [routeColor, routeWeight, routeHaloColor]);

  useEffect(() => {
    const previous = previousProgressRef.current;
    applyRouteProgress(layersRef.current, routes, playbackProgress, previous);
    previousProgressRef.current = playbackProgress;
  }, [routes, playbackProgress]);

  useEffect(() => {
    pointLayersRef.current.forEach(layer => layer.removeFrom(map));
    pointLayersRef.current = [];
    if (!showTrackPoints) return;

    const visibleRoutes = visibleRouteSlices(routes, playbackProgress);
    const sampled = sampleRoutePoints(visibleRoutes, 600);
    pointLayersRef.current = sampled.map(position => L.circleMarker(position, {
      pane: "alels-route-pane",
      radius: 2.5,
      color: "#ffffff",
      weight: 1,
      fillColor: routeColor,
      fillOpacity: 1,
      opacity: 1,
      interactive: false,
    }).addTo(map));

    return () => {
      pointLayersRef.current.forEach(layer => layer.removeFrom(map));
      pointLayersRef.current = [];
    };
  }, [map, routes, playbackProgress, showTrackPoints, routeColor]);

  return null;
}

function createLeafletRouteLayer(map: L.Map, routeColor: string, routeWeight: number, routeHaloColor?: string) {
  return {
    halo: L.polyline([], {
      pane: "alels-route-pane",
      color: routeHaloColor ?? "#ffffff",
      weight: routeWeight + 5,
      opacity: 0.96,
      lineCap: "round",
      lineJoin: "round",
      interactive: false,
    }).addTo(map),
    line: L.polyline([], {
      pane: "alels-route-pane",
      color: routeColor,
      weight: routeWeight,
      opacity: 1,
      lineCap: "round",
      lineJoin: "round",
      interactive: false,
    }).addTo(map),
  };
}

function LeafletVehicleMarker({
  center,
  valid,
  angle,
  vehicleType,
}: {
  center: LatLng;
  valid: boolean;
  angle?: number | null;
  vehicleType?: string | null;
}) {
  const map = useMap();
  const markerRef = useRef<L.Marker | null>(null);
  const vehicleTypeRef = useRef<string | null | undefined>(undefined);

  useEffect(() => () => {
    markerRef.current?.removeFrom(map);
    markerRef.current = null;
  }, [map]);

  useEffect(() => {
    if (!valid) {
      markerRef.current?.removeFrom(map);
      markerRef.current = null;
      return;
    }

    if (!markerRef.current) {
      markerRef.current = L.marker(center, { icon: vehicleIcon(vehicleType) }).addTo(map);
      vehicleTypeRef.current = vehicleType;
    } else if (vehicleTypeRef.current !== vehicleType) {
      markerRef.current.setIcon(vehicleIcon(vehicleType));
      vehicleTypeRef.current = vehicleType;
    }
  }, [map, valid, vehicleType]);

  useEffect(() => {
    if (!valid || !markerRef.current) return;
    markerRef.current.setLatLng(center);
  }, [center[0], center[1], valid]);

  useEffect(() => {
    const rotation = markerRef.current?.getElement()?.querySelector<HTMLElement>("[data-vehicle-rotation]");
    if (rotation) rotation.style.transform = `rotate(${Number(angle) || 0}deg)`;
  }, [angle, vehicleType, valid]);

  return null;
}

function MapSync({
  center,
  zoom,
  route,
  follow,
  fitToken,
  resizeToken,
  viewportPadding,
}: {
  center: LatLng;
  zoom: number;
  route: LatLng[];
  follow: boolean;
  fitToken: number;
  resizeToken: unknown;
  viewportPadding: MapViewportPadding;
}) {
  const map = useMap();
  const lastFitTokenRef = useRef(0);
  const lastFollowAtRef = useRef(0);
  const lastZoomRef = useRef(zoom);
  const lastFollowRef = useRef(follow);

  useEffect(() => {
    if (!fitToken || fitToken === lastFitTokenRef.current || route.length < 2) return;
    lastFitTokenRef.current = fitToken;
    map.stop();
    map.fitBounds(route, {
      paddingTopLeft: [viewportPadding.left + 28, viewportPadding.top + 28],
      paddingBottomRight: [viewportPadding.right + 28, viewportPadding.bottom + 28],
      animate: true,
      duration: 0.45,
    });
    lastFollowAtRef.current = performance.now();
  }, [map, fitToken, route]);

  useEffect(() => {
    const zoomChanged = lastZoomRef.current !== zoom;
    const followActivated = follow && !lastFollowRef.current;
    if (follow && (zoomChanged || followActivated)) {
      const targetZoom = zoomChanged ? zoom : map.getZoom();
      map.stop();
      map.setView(visibleCameraCenter(map, center, viewportPadding, targetZoom), targetZoom, { animate: true });
      lastFollowAtRef.current = performance.now();
    }
    lastZoomRef.current = zoom;
    lastFollowRef.current = follow;
  }, [map, center[0], center[1], zoom, follow, viewportPadding.top, viewportPadding.right, viewportPadding.bottom, viewportPadding.left]);

  useEffect(() => {
    if (!follow) return;
    const now = performance.now();
    if (now - lastFollowAtRef.current < FOLLOW_MIN_INTERVAL_MS) return;
    if (isInsideFollowDeadZone(map, center, viewportPadding)) return;

    lastFollowAtRef.current = now;
    map.stop();
    map.panTo(visibleCameraCenter(map, center, viewportPadding, map.getZoom()), {
      animate: true,
      duration: 0.35,
      easeLinearity: 0.25,
      noMoveStart: true,
    });
  }, [map, center[0], center[1], follow, viewportPadding.top, viewportPadding.right, viewportPadding.bottom, viewportPadding.left]);

  useEffect(() => {
    const frame = window.requestAnimationFrame(() => map.invalidateSize({ pan: false }));
    const timer = window.setTimeout(() => map.invalidateSize({ pan: false }), 280);
    return () => {
      window.cancelAnimationFrame(frame);
      window.clearTimeout(timer);
    };
  }, [map, resizeToken]);

  return null;
}

function isInsideFollowDeadZone(map: L.Map, center: LatLng, padding: MapViewportPadding) {
  const point = map.latLngToContainerPoint(center);
  const size = map.getSize();
  const safeLeft = Math.min(size.x, Math.max(0, padding.left));
  const safeRight = Math.max(safeLeft, size.x - Math.max(0, padding.right));
  const safeTop = Math.min(size.y, Math.max(0, padding.top));
  const safeBottom = Math.max(safeTop, size.y - Math.max(0, padding.bottom));
  const safeWidth = Math.max(1, safeRight - safeLeft);
  const safeHeight = Math.max(1, safeBottom - safeTop);
  const marginX = safeWidth * FOLLOW_DEAD_ZONE_RATIO;
  const marginY = safeHeight * FOLLOW_DEAD_ZONE_RATIO;
  return point.x >= safeLeft + marginX && point.x <= safeRight - marginX
    && point.y >= safeTop + marginY && point.y <= safeBottom - marginY;
}

function visibleCameraCenter(map: L.Map, vehicle: LatLng, padding: MapViewportPadding, zoom: number) {
  const size = map.getSize();
  const safeLeft = Math.min(size.x, Math.max(0, padding.left));
  const safeRight = Math.max(safeLeft, size.x - Math.max(0, padding.right));
  const safeTop = Math.min(size.y, Math.max(0, padding.top));
  const safeBottom = Math.max(safeTop, size.y - Math.max(0, padding.bottom));
  const visibleCenter = L.point((safeLeft + safeRight) / 2, (safeTop + safeBottom) / 2);
  const mapCenter = L.point(size.x / 2, size.y / 2);
  const projectedVehicle = map.project(vehicle, zoom);
  return map.unproject(projectedVehicle.add(mapCenter.subtract(visibleCenter)), zoom);
}

function applyRouteProgress(
  layers: Array<{ halo: L.Polyline; line: L.Polyline }>,
  routes: LatLng[][],
  progress: number | null,
  previousProgress: number | null,
) {
  if (progress != null && previousProgress != null && progress === previousProgress + 1) {
    const nextPoint = locateRoutePoint(routes, progress - 1);
    if (nextPoint) {
      layers[nextPoint.routeIndex]?.halo.addLatLng(nextPoint.point);
      layers[nextPoint.routeIndex]?.line.addLatLng(nextPoint.point);
      return;
    }
  }

  const visible = visibleRouteSlices(routes, progress);
  layers.forEach((layer, index) => {
    const coordinates = visible[index] ?? [];
    layer.halo.setLatLngs(coordinates);
    layer.line.setLatLngs(coordinates);
  });
}

function visibleRouteSlices(routes: LatLng[][], progress: number | null) {
  if (progress == null) return routes;
  let remaining = Math.max(0, progress);
  return routes.map(route => {
    const length = Math.min(remaining, route.length);
    remaining = Math.max(0, remaining - route.length);
    return length > 0 ? route.slice(0, length) : [];
  });
}

function locateRoutePoint(routes: LatLng[][], globalIndex: number) {
  let remaining = globalIndex;
  for (let routeIndex = 0; routeIndex < routes.length; routeIndex += 1) {
    const route = routes[routeIndex];
    if (remaining < route.length) return { routeIndex, point: route[remaining] };
    remaining -= route.length;
  }
  return null;
}

function clearRouteLayers(
  map: L.Map,
  layers: Array<{ halo: L.Polyline; line: L.Polyline }>,
  points: L.CircleMarker[],
) {
  layers.forEach(layer => {
    layer.halo.removeFrom(map);
    layer.line.removeFrom(map);
  });
  points.forEach(layer => layer.removeFrom(map));
}

function toLeafletRoute(points: WorkspaceTrackPoint[]): LatLng[] {
  return points
    .filter(point => hasValidPosition(point.latitude, point.longitude))
    .map(point => [point.latitude, point.longitude]);
}

function sampleRoutePoints(routes: LatLng[][], maximum: number) {
  const points = routes.flat();
  if (points.length <= maximum) return points;
  const step = Math.ceil(points.length / maximum);
  return points.filter((_, index) => index % step === 0 || index === points.length - 1);
}

export function hasValidPosition(latitude?: number | null, longitude?: number | null) {
  if (!Number.isFinite(latitude) || !Number.isFinite(longitude)) return false;
  const lat = latitude as number;
  const lng = longitude as number;
  return lat >= -90 && lat <= 90 && lng >= -180 && lng <= 180 && !(lat === 0 && lng === 0);
}

function vehicleIcon(type?: string | null) {
  return L.divIcon({
    className: "dw-vehicle-marker",
    html: `<div data-vehicle-rotation>${vehicleMarkerSvg(type)}</div>`,
    iconSize: [58, 72],
    iconAnchor: [29, 36],
  });
}

function mapPin(label: string, color: string) {
  return L.divIcon({
    className: "dw-trip-map-pin",
    html: `<span style="--trip-pin:${color}">${label}</span>`,
    iconSize: [28, 34],
    iconAnchor: [14, 28],
  });
}

function eventMapPin(title: string, color: string) {
  return L.divIcon({
    className: "dw-trip-map-pin dw-trip-event-pin",
    html: `<span style="--trip-pin:${color}">${escapeHtml(title || "Telemetry event")}</span>`,
    iconSize: [160, 34],
    iconAnchor: [80, 28],
  });
}

function escapeHtml(value: string) {
  return value.replace(/[&<>"']/g, character => ({
    "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;",
  })[character] || character);
}

function eventColor(severity?: string | null) {
  const value = (severity || "").toUpperCase();
  return value.includes("CRITICAL") || value.includes("HIGH") ? "#dc2626" : value.includes("WARN") ? "#f59e0b" : "#2563eb";
}
