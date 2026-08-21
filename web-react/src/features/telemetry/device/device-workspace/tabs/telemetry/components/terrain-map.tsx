import { useEffect, useMemo, useRef, useState } from "react";
import maplibregl from "maplibre-gl";
import "maplibre-gl/dist/maplibre-gl.css";

import type { WorkspaceTrackPoint } from "../types/device-workspace-telemetry";
import { vehicleMarkerSvg } from "./vehicle-map-marker";
import type { MapViewportPadding, WorkspaceMapEvent, WorkspaceMapProviderOptions } from "./telemetry-map";

const EMPTY_ROUTES: WorkspaceTrackPoint[][] = [];
const PERFORMANCE_PITCH = 48;
const FOLLOW_MIN_INTERVAL_MS = 650;
const FOLLOW_DEAD_ZONE_RATIO = 0.28;

type Coordinate = [number, number];
type RouteGeoJson = ReturnType<typeof routeGeoJsonFromLines>;

type Props = {
  center: [number, number];
  valid: boolean;
  zoom: number;
  angle?: number | null;
  vehicleType?: string | null;
  track: WorkspaceTrackPoint[];
  routeOverlays?: WorkspaceTrackPoint[][];
  fitRouteOverlays?: WorkspaceTrackPoint[][];
  playbackProgress?: number | null;
  events?: WorkspaceMapEvent[];
  showEvents?: boolean;
  showTrackPoints?: boolean;
  follow?: boolean;
  fitToken?: number;
  routeColor?: string;
  routeWeight?: number;
  routeHaloColor?: string;
  brightMap?: boolean;
  viewportPadding: MapViewportPadding;
  provider?: WorkspaceMapProviderOptions;
  onUserViewChange?: () => void;
};

export default function TerrainMap({
  center,
  valid,
  zoom,
  angle,
  vehicleType,
  track,
  routeOverlays,
  fitRouteOverlays,
  playbackProgress = null,
  events = [],
  showEvents = false,
  showTrackPoints = false,
  follow = true,
  fitToken = 0,
  routeColor = "#0284c7",
  routeWeight = 4,
  routeHaloColor,
  brightMap = false,
  viewportPadding,
  provider,
  onUserViewChange,
}: Props) {
  const overlays = routeOverlays ?? EMPTY_ROUTES;
  const fitOverlays = fitRouteOverlays ?? overlays;
  const container = useRef<HTMLDivElement>(null);
  const mapRef = useRef<maplibregl.Map | null>(null);
  const markerRef = useRef<maplibregl.Marker | null>(null);
  const markerVehicleTypeRef = useRef<string | null | undefined>(undefined);
  const eventMarkersRef = useRef<maplibregl.Marker[]>([]);
  const onUserViewChangeRef = useRef(onUserViewChange);
  const lastZoomRef = useRef(zoom);
  const lastFollowRef = useRef(follow);
  const lastFitTokenRef = useRef(0);
  const lastFollowAtRef = useRef(0);
  const [startupError, setStartupError] = useState(false);
  const [mapReady, setMapReady] = useState(false);

  const routeLines = useMemo(() => routeCoordinateLines(track, overlays), [track, overlays]);
  const routeData = useMemo(
    () => routeGeoJsonFromLines(visibleCoordinateLines(routeLines, playbackProgress)),
    [routeLines, playbackProgress],
  );
  const fitRouteLines = useMemo(() => routeCoordinateLines(track, fitOverlays), [track, fitOverlays]);
  const fitRouteData = useMemo(() => routeGeoJsonFromLines(fitRouteLines), [fitRouteLines]);
  const routeDataRef = useRef<RouteGeoJson>(routeData);
  routeDataRef.current = routeData;
  onUserViewChangeRef.current = onUserViewChange;

  useEffect(() => {
    if (!container.current) return;
    let map: maplibregl.Map;
    let active = true;
    setStartupError(false);

    try {
      map = new maplibregl.Map({
        container: container.current,
        style: provider?.styleUrl3d ?? "https://tiles.openfreemap.org/styles/bright",
        center: [center[1], center[0]],
        zoom,
        pitch: PERFORMANCE_PITCH,
        bearing: -18,
        attributionControl: provider?.showAttribution ? { compact: true } : false,
        canvasContextAttributes: { antialias: false },
        pixelRatio: Math.min(window.devicePixelRatio || 1, 1.5),
        maxTileCacheSize: 64,
        fadeDuration: 0,
        refreshExpiredTiles: false,
      });
    } catch (error) {
      console.error("3D map could not initialize", error);
      setStartupError(true);
      return;
    }

    mapRef.current = map;
    map.on("load", () => {
      if (!active) return;
      setMapReady(true);
      try {
        map.addSource("alels-buildings", { type: "vector", url: provider?.buildingsUrl3d ?? "https://tiles.openfreemap.org/planet" });
        const labelLayer = (map.getStyle().layers ?? []).find(layer => layer.type === "symbol" && Boolean(layer.layout?.["text-field"]))?.id;
        map.addLayer({
          id: "alels-3d-buildings",
          source: "alels-buildings",
          "source-layer": "building",
          type: "fill-extrusion",
          minzoom: 15,
          filter: ["!=", ["get", "hide_3d"], true],
          paint: {
            "fill-extrusion-color": "#b9cee4",
            "fill-extrusion-height": ["interpolate", ["linear"], ["zoom"], 15, 0, 16, ["coalesce", ["get", "render_height"], 8]],
            "fill-extrusion-base": ["coalesce", ["get", "render_min_height"], 0],
            "fill-extrusion-opacity": 0.82,
          },
        }, labelLayer);
      } catch (error) {
        console.warn("3D building layer unavailable", error);
      }

      addRouteLayers(map, routeDataRef.current, routeColor, routeWeight, showTrackPoints, routeHaloColor);
      if (valid && !markerRef.current) {
        markerRef.current = createVehicleMarker(vehicleType, angle).setLngLat([center[1], center[0]]).addTo(map);
        markerVehicleTypeRef.current = vehicleType;
      }
    });

    const resizeObserver = new ResizeObserver(() => map.resize());
    resizeObserver.observe(container.current);

    const canvasContainer = map.getCanvasContainer();
    const manualViewChange = () => onUserViewChangeRef.current?.();
    canvasContainer.addEventListener("pointerdown", manualViewChange);
    canvasContainer.addEventListener("wheel", manualViewChange, { passive: true });

    const visibilityChanged = () => {
      if (document.visibilityState === "hidden") map.stop();
      else map.resize();
    };
    document.addEventListener("visibilitychange", visibilityChanged);

    return () => {
      active = false;
      resizeObserver.disconnect();
      canvasContainer.removeEventListener("pointerdown", manualViewChange);
      canvasContainer.removeEventListener("wheel", manualViewChange);
      document.removeEventListener("visibilitychange", visibilityChanged);
      eventMarkersRef.current.forEach(eventMarker => eventMarker.remove());
      eventMarkersRef.current = [];
      markerRef.current = null;
      markerVehicleTypeRef.current = undefined;
      mapRef.current = null;
      map.remove();
    };
  }, []);

  useEffect(() => {
    const map = mapRef.current;
    const longitude = center[1];
    const latitude = center[0];
    if (!map || !isValidCoordinate(latitude, longitude)) return;

    if (valid) {
      if (!markerRef.current || markerVehicleTypeRef.current !== vehicleType) {
        markerRef.current?.remove();
        markerRef.current = createVehicleMarker(vehicleType, angle).setLngLat([longitude, latitude]).addTo(map);
        markerVehicleTypeRef.current = vehicleType;
      } else {
        markerRef.current.setLngLat([longitude, latitude]);
        markerRef.current.setRotation(Number(angle) || 0);
      }
    } else {
      markerRef.current?.remove();
      markerRef.current = null;
      markerVehicleTypeRef.current = undefined;
    }

    const zoomChanged = lastZoomRef.current !== zoom;
    const followActivated = follow && !lastFollowRef.current;
    if (follow && (zoomChanged || followActivated) && map.loaded()) {
      const targetZoom = zoomChanged ? zoom : map.getZoom();
      map.stop();
      map.easeTo({
        center: [longitude, latitude],
        zoom: targetZoom,
        pitch: PERFORMANCE_PITCH,
        offset: viewportOffset(viewportPadding),
        duration: 450,
        easing: value => 1 - Math.pow(1 - value, 3),
      });
      lastFollowAtRef.current = performance.now();
    } else if (follow && map.loaded() && shouldFollowVehicle(map, longitude, latitude, lastFollowAtRef.current, viewportPadding)) {
      lastFollowAtRef.current = performance.now();
      map.stop();
      map.easeTo({
        center: [longitude, latitude],
        offset: viewportOffset(viewportPadding),
        duration: 450,
        easing: value => 1 - Math.pow(1 - value, 3),
      });
    }

    lastZoomRef.current = zoom;
    lastFollowRef.current = follow;
  }, [center[0], center[1], valid, zoom, angle, vehicleType, follow, viewportPadding.top, viewportPadding.right, viewportPadding.bottom, viewportPadding.left]);

  useEffect(() => {
    const map = mapRef.current;
    if (!map || !map.loaded()) return;

    addRouteLayers(map, routeData, routeColor, routeWeight, showTrackPoints, routeHaloColor);
    const source = map.getSource("alels-route") as maplibregl.GeoJSONSource | undefined;
    source?.setData(routeData);
  }, [routeData]);

  useEffect(() => {
    const map = mapRef.current;
    if (!map || !map.loaded()) return;

    if (map.getLayer("alels-route-halo")) {
      map.setPaintProperty("alels-route-halo", "line-color", routeHaloColor ?? routeColor);
      map.setPaintProperty("alels-route-halo", "line-width", routeWeight + 5);
      map.setPaintProperty("alels-route-halo", "line-opacity", routeHaloColor ? 0.92 : 0.24);
    }
    if (map.getLayer("alels-route-line")) {
      map.setPaintProperty("alels-route-line", "line-color", routeColor);
      map.setPaintProperty("alels-route-line", "line-width", routeWeight);
    }
    if (map.getLayer("alels-route-points")) map.setPaintProperty("alels-route-points", "circle-color", routeColor);
  }, [routeColor, routeWeight, routeHaloColor]);

  useEffect(() => {
    const map = mapRef.current;
    if (!map || !map.loaded() || showTrackPoints) return;
    const points = map.getSource("alels-route-points") as maplibregl.GeoJSONSource | undefined;
    points?.setData(emptyRoutePointGeoJson());
  }, [showTrackPoints]);

  useEffect(() => {
    if (!showTrackPoints) return;
    const map = mapRef.current;
    if (!map || !map.loaded()) return;
    const points = map.getSource("alels-route-points") as maplibregl.GeoJSONSource | undefined;
    points?.setData(routePointGeoJson(routeData, true));
  }, [routeData, showTrackPoints]);

  useEffect(() => {
    const map = mapRef.current;
    if (!map || !mapReady || !fitToken || fitToken === lastFitTokenRef.current) return;
    const coordinates = fitRouteData.geometry.coordinates.flat();
    if (coordinates.length < 2) return;

    lastFitTokenRef.current = fitToken;
    const bounds = new maplibregl.LngLatBounds(coordinates[0] as Coordinate, coordinates[0] as Coordinate);
    coordinates.slice(1).forEach(coordinate => bounds.extend(coordinate as Coordinate));
    map.stop();
    map.fitBounds(bounds, {
      padding: {
        top: viewportPadding.top + 28,
        right: viewportPadding.right + 28,
        bottom: viewportPadding.bottom + 28,
        left: viewportPadding.left + 28,
      },
      duration: 500,
    });
    lastFollowAtRef.current = performance.now();
  }, [fitToken, mapReady, fitRouteData, viewportPadding.top, viewportPadding.right, viewportPadding.bottom, viewportPadding.left]);

  useEffect(() => {
    const map = mapRef.current;
    if (!map || !mapReady) return;

    eventMarkersRef.current.forEach(eventMarker => eventMarker.remove());
    eventMarkersRef.current = [];
    if (!showEvents) return;

    events.filter(event => isValidCoordinate(Number(event.latitude), Number(event.longitude))).forEach(event => {
      const element = document.createElement("div");
      element.className = "dw-trip-map-pin dw-trip-event-pin";
      const label = document.createElement("span");
      label.style.setProperty("--trip-pin", eventColor(event.severity));
      label.textContent = event.title || "Telemetry event";
      element.appendChild(label);
      element.title = `${event.title} · ${new Date(event.occurredAt).toLocaleString("id-ID")}`;
      eventMarkersRef.current.push(new maplibregl.Marker({ element }).setLngLat([Number(event.longitude), Number(event.latitude)]).addTo(map));
    });
  }, [events, showEvents, mapReady]);

  if (startupError) return <div className="dw-map dw-map-empty">3D map is unavailable on this browser. Use 2D mode or enable hardware acceleration.</div>;
  return <div className={`dw-map dw-maplibre-3d${brightMap ? " dw-map-bright" : ""}`}>
    <div ref={container} className="dw-maplibre-canvas"/>
    {!valid ? <div className="dw-map-empty">Waiting for GPS position</div> : null}
  </div>;
}

function createVehicleMarker(vehicleType?: string | null, angle?: number | null) {
  const element = document.createElement("div");
  element.className = "dw-vehicle-marker";
  element.innerHTML = `<div>${vehicleMarkerSvg(vehicleType)}</div>`;
  return new maplibregl.Marker({ element, rotation: Number(angle) || 0, rotationAlignment: "map" });
}

function isValidCoordinate(latitude: number, longitude: number) {
  return Number.isFinite(latitude) && Number.isFinite(longitude) && latitude >= -90 && latitude <= 90 && longitude >= -180 && longitude <= 180;
}

function shouldFollowVehicle(
  map: maplibregl.Map,
  longitude: number,
  latitude: number,
  lastFollowAt: number,
  padding: MapViewportPadding,
) {
  const now = performance.now();
  if (now - lastFollowAt < FOLLOW_MIN_INTERVAL_MS) return false;
  const canvas = map.getCanvas();
  const point = map.project([longitude, latitude]);
  const safeLeft = Math.min(canvas.clientWidth, Math.max(0, padding.left));
  const safeRight = Math.max(safeLeft, canvas.clientWidth - Math.max(0, padding.right));
  const safeTop = Math.min(canvas.clientHeight, Math.max(0, padding.top));
  const safeBottom = Math.max(safeTop, canvas.clientHeight - Math.max(0, padding.bottom));
  const safeWidth = Math.max(1, safeRight - safeLeft);
  const safeHeight = Math.max(1, safeBottom - safeTop);
  const marginX = safeWidth * FOLLOW_DEAD_ZONE_RATIO;
  const marginY = safeHeight * FOLLOW_DEAD_ZONE_RATIO;
  return point.x < safeLeft + marginX || point.x > safeRight - marginX
    || point.y < safeTop + marginY || point.y > safeBottom - marginY;
}

function viewportOffset(padding: MapViewportPadding): [number, number] {
  return [(padding.left - padding.right) / 2, (padding.top - padding.bottom) / 2];
}

function routeCoordinateLines(track: WorkspaceTrackPoint[], overlays: WorkspaceTrackPoint[][]): Coordinate[][] {
  return [track, ...overlays]
    .map(line => line
      .filter(point => Number.isFinite(point.latitude) && Number.isFinite(point.longitude) && !(point.latitude === 0 && point.longitude === 0))
      .map(point => [point.longitude, point.latitude] as Coordinate))
    .filter(line => line.length > 1);
}

function visibleCoordinateLines(lines: Coordinate[][], progress: number | null) {
  if (progress == null) return lines;
  let remaining = Math.max(0, progress);
  return lines.map(line => {
    const length = Math.min(remaining, line.length);
    remaining = Math.max(0, remaining - line.length);
    return length > 1 ? line.slice(0, length) : [];
  }).filter(line => line.length > 1);
}

function routeGeoJsonFromLines(lines: Coordinate[][]) {
  return {
    type: "Feature" as const,
    properties: {},
    geometry: { type: "MultiLineString" as const, coordinates: lines },
  };
}

function addRouteLayers(
  map: maplibregl.Map,
  data: RouteGeoJson,
  routeColor: string,
  routeWeight: number,
  showTrackPoints: boolean,
  routeHaloColor?: string,
) {
  if (!data.geometry.coordinates.length || map.getSource("alels-route")) return;

  map.addSource("alels-route", { type: "geojson", data });
  map.addLayer({
    id: "alels-route-halo",
    type: "line",
    source: "alels-route",
    layout: { "line-cap": "round", "line-join": "round" },
    paint: {
      "line-color": routeHaloColor ?? routeColor,
      "line-width": routeWeight + 5,
      "line-opacity": routeHaloColor ? 0.92 : 0.24,
    },
  });
  map.addLayer({
    id: "alels-route-line",
    type: "line",
    source: "alels-route",
    layout: { "line-cap": "round", "line-join": "round" },
    paint: { "line-color": routeColor, "line-width": routeWeight, "line-opacity": 1 },
  });
  map.addSource("alels-route-points", { type: "geojson", data: routePointGeoJson(data, showTrackPoints) });
  map.addLayer({
    id: "alels-route-points",
    type: "circle",
    source: "alels-route-points",
    paint: {
      "circle-radius": 3,
      "circle-color": routeColor,
      "circle-stroke-color": "#ffffff",
      "circle-stroke-width": 1,
      "circle-opacity": 1,
    },
  });
}

function emptyRoutePointGeoJson() {
  return {
    type: "Feature" as const,
    properties: {},
    geometry: { type: "MultiPoint" as const, coordinates: [] as Coordinate[] },
  };
}

function routePointGeoJson(data: RouteGeoJson, visible: boolean) {
  const all = visible ? data.geometry.coordinates.flat() : [];
  const step = Math.max(1, Math.ceil(all.length / 600));
  const coordinates = all.filter((_, index) => index % step === 0 || index === all.length - 1);
  return {
    type: "Feature" as const,
    properties: {},
    geometry: { type: "MultiPoint" as const, coordinates },
  };
}

function eventColor(severity?: string | null) {
  const value = (severity || "").toUpperCase();
  return value.includes("CRITICAL") || value.includes("HIGH") ? "#dc2626" : value.includes("WARN") ? "#f59e0b" : "#2563eb";
}
