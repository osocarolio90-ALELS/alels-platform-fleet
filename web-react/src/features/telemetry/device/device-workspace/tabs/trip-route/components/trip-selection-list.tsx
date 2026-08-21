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
      <strong>Trips &amp; Stops ({trips.length})</strong>
      <span className="dw-trip-list-header-actions">
        <button type="button" className={allChecked ? "active" : ""} onClick={onToggleAll} disabled={!trips.length}>All</button>
        <button type="button" onClick={() => onVisible(false)} title="Hide Trips" aria-label="Hide Trips"><ChevronLeft/></button>
      </span>
    </header>
    <div>
      {loading
        ? <p className="empty">Loading trips and stops...</p>
        : trips.length
          ? trips.map(trip => <article key={trip.id} className={`${selectedId === trip.id ? "selected " : ""}${trip.type.toLowerCase()}`}>
            <input type="checkbox" checked={checkedTripIds.has(trip.id)} onChange={() => onToggle(trip.id)} aria-label={`Show ${trip.type.toLowerCase()} ${trip.id}`}/>
            <button type="button" onClick={() => onSelect(trip.id)}>
              <span className="dw-trip-card-title"><span><MapPin/>{trip.type}</span><em>{trip.status.replace("_", " ")}</em></span>
              <span className="dw-trip-card-coordinate">{coordinate(trip.startLatitude, trip.startLongitude)}</span>
              <span className="dw-trip-time"><span><b>Start</b><time>{dateTime(trip.startTime)}</time></span><span><b>Finish</b><time>{dateTime(trip.endTime)}</time></span></span>
              <span className="dw-trip-card-summary"><b>Duration</b><span>{duration(trip.durationSeconds)} · {trip.distanceKm.toFixed(1)} km</span><span>{trip.driverName}</span></span>
            </button>
          </article>)
        : <p className="empty">No ignition trips or stops in this range.</p>}
    </div>
  </aside>;
}

function coordinate(lat?: number | null, lng?: number | null) {
  return Number.isFinite(lat) && Number.isFinite(lng) ? `${Number(lat).toFixed(5)}, ${Number(lng).toFixed(5)}` : "No location";
}
function dateTime(value: string) {
  return new Date(value).toLocaleString("id-ID", { day: "2-digit", month: "short", hour: "2-digit", minute: "2-digit", second: "2-digit" });
}
function duration(seconds: number) {
  const hours = Math.floor(seconds / 3600);
  const minutes = Math.round((seconds % 3600) / 60);
  return hours ? `${hours}h ${minutes}m` : `${minutes} min`;
}
