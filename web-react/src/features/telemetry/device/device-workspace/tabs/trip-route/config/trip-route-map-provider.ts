import type { WorkspaceMapProviderOptions } from "../../telemetry/components/telemetry-map";

const env = import.meta.env;

export const TRIP_ROUTE_MAP_PROVIDER: WorkspaceMapProviderOptions = Object.freeze({
  tileUrl2d: env.VITE_TRIP_ROUTE_MAP_2D_TILE_URL?.trim() || "https://tile.openstreetmap.org/{z}/{x}/{y}.png",
  tileAttribution2d: env.VITE_TRIP_ROUTE_MAP_2D_ATTRIBUTION?.trim()
    || '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors',
  styleUrl3d: env.VITE_TRIP_ROUTE_MAP_3D_STYLE_URL?.trim() || "https://tiles.openfreemap.org/styles/bright",
  buildingsUrl3d: env.VITE_TRIP_ROUTE_MAP_3D_BUILDINGS_URL?.trim() || "https://tiles.openfreemap.org/planet",
  showAttribution: true,
});
