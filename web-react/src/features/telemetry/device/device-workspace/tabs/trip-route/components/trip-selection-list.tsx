import { ChevronLeft, ChevronRight, MapPin } from "lucide-react";
import type { TripSummary } from "../types/device-workspace-trip-route";

type Props = {
  trips: TripSummary[];
  selectedId: string | null;
  checkedTripIds: Set<string>;
  allChecked: boolean;
  visible: boolean;
  fullscreen?: boolean;
  loading?: boolean;
  onToggleAll: () => void;
  onSelect: (tripId: string) => void;
  onToggle: (tripId: string) => void;
  onVisible: (visible: boolean) => void;
};

export function TripSelectionList({ trips, selectedId, checkedTripIds, allChecked, visible, fullscreen = false, loading = false,
  onToggleAll, onSelect, onToggle, onVisible }: Props) {
  if (!visible) {
    return <button type="button" className={`dw-trip-show ${fullscreen ? "dw-trip-fullscreen-show" : ""}`}
      onClick={() => onVisible(true)} title="Show Trips" aria-label="Show Trips">
      <ChevronRight/><span>Show Trips</span>
    </button>;
  }

  return <aside className={`dw-trip-list ${fullscreen ? "dw-trip-fullscreen-list" : ""}`}>
    <header>
      <strong>Trip, Idle &amp; Stop ({trips.length})</strong>
      <span className="dw-trip-list-header-actions">
        <button type="button" className={allChecked ? "active" : ""} onClick={onToggleAll} disabled={!trips.length}>All</button>
        <button type="button" onClick={() => onVisible(false)} title="Hide Trips" aria-label="Hide Trips"><ChevronLeft/></button>
      </span>
    </header>
    <div>
      {loading
        ? <p className="empty">Loading trip, idle and stop history...</p>
        : trips.length
          ? trips.map(trip => <article key={trip.id} className={`${selectedId === trip.id ? "selected " : ""}${trip.type.toLowerCase()}`}>
            <input type="checkbox" checked={checkedTripIds.has(trip.id)} onChange={() => onToggle(trip.id)} aria-label={`Show ${trip.type.toLowerCase()} ${trip.id}`}/>
            <button type="button" onClick={() => onSelect(trip.id)}>
              <span className="dw-trip-card-title"><span><MapPin/>{trip.type}</span><em>{trip.status.replace("_", " ")}</em></span>
              <span className="dw-trip-card-coordinate">{coordinate(trip.startLatitude, trip.startLongitude)}</span>
              <span className="dw-trip-card-data">
                <span><b>Duration</b><strong>{duration(trip.durationSeconds)}</strong></span>
                <span><b>Distance</b><strong>{trip.distanceKm.toFixed(1)} km</strong></span>
                <span className="wide"><b>Cost Operation</b><strong>{money(trip.operationCost, trip.costCurrency)}</strong></span>
                <span><b>Fuel Cost</b><strong>{money(trip.fuelCost, trip.costCurrency)}</strong></span>
                <span><b>Road Cost</b><strong>{money(trip.roadCost, trip.costCurrency)}</strong></span>
                <span className="wide"><b>Fuel Consumption</b><strong>{metric(trip.fuelConsumption, "L")}</strong></span>
                <span className="wide"><b>Fuel Level</b><strong>{fuelLevel(trip.fuelStart, trip.fuelFinish)}</strong></span>
                <span><b>Start</b><time>{dateTime(trip.startTime)}</time></span>
                <span><b>Finish</b><time>{dateTime(trip.endTime)}</time></span>
                <span><b>Driver</b><strong>{trip.driverName || "-"}</strong></span>
                <span><b>No. Vehicle</b><strong>{trip.vehiclePlateNumber || "-"}</strong></span>
              </span>
            </button>
          </article>)
        : <p className="empty">No trip, idle or stop history in this range.</p>}
    </div>
  </aside>;
}

function coordinate(lat?: number | null, lng?: number | null) {
  return Number.isFinite(lat) && Number.isFinite(lng) ? `${Number(lat).toFixed(5)}, ${Number(lng).toFixed(5)}` : "No location";
}
function dateTime(value: string) {
  return new Date(value).toLocaleString("id-ID", { day: "2-digit", month: "short", hour: "2-digit", minute: "2-digit", second: "2-digit" });
}
function metric(value?: number | null, unit?: string) {
  return Number.isFinite(value) ? `${Number(value).toFixed(2)}${unit ? ` ${unit}` : ""}` : "-";
}
function fuelLevel(start?: number | null, finish?: number | null) {
  if (!Number.isFinite(start) && !Number.isFinite(finish)) return "-";
  return `${metric(start, "%")} → ${metric(finish, "%")}`;
}
function money(value?: number | null, currency?: string | null) {
  if (!Number.isFinite(value)) return "-";
  const normalizedCurrency = currency && /^[A-Z]{3}$/.test(currency.toUpperCase()) ? currency.toUpperCase() : "IDR";
  return new Intl.NumberFormat("id-ID", { style: "currency", currency: normalizedCurrency, maximumFractionDigits: 2 }).format(Number(value));
}
function duration(seconds: number) {
  const hours = Math.floor(seconds / 3600);
  const minutes = Math.round((seconds % 3600) / 60);
  return hours ? `${hours}h ${minutes}m` : `${minutes} min`;
}
