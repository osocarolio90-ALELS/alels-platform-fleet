import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { ChevronDown, Columns3, Download, Search, Trash2, TriangleAlert } from "lucide-react";
import { useEffect, useMemo, useState } from "react";

import { Button } from "@/components/ui/button";
import { isAxiosError } from "axios";
import { normalizeRole } from "@/lib/role-access";
import { useAuthStore } from "@/stores/auth-store";
import { deleteAllDeviceHistory, exportSelectedDeviceLogs, getSelectedDeviceLogs } from "../api/device-workspace-trip-route-api";
import type { SelectedTimeRange, TripLogRow, TripSummary } from "../types/device-workspace-trip-route";
import { exportTripLog, type ExportFormat } from "../utils/trip-log-export";

type Props = { deviceId: number; imei: string; selectedSegments: TripSummary[]; rangeIdentity?: string };
type Column = { key: string; label: string; value: (row: TripLogRow) => string };

const PAGE_SIZES = [15, 25, 50, 75, 100] as const;
const BASE_CODES = new Set(["latitude", "longitude", "altitude", "angle", "speed", "satellites", "hdop", "protocol", "channel"]);

export function TripLogTable({ deviceId, imei, selectedSegments, rangeIdentity }: Props) {
  const queryClient = useQueryClient();
  const role = normalizeRole(useAuthStore(state => state.user?.role));
  const canDeleteAll = role === "SUPERADMIN" || role === "ADMIN";
  const [search, setSearch] = useState("");
  const [columnsOpen, setColumnsOpen] = useState(false);
  const [exportOpen, setExportOpen] = useState(false);
  const [hidden, setHidden] = useState<Set<string>>(new Set());
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState<(typeof PAGE_SIZES)[number]>(15);
  const [deleteOpen, setDeleteOpen] = useState(false);
  const [confirmation, setConfirmation] = useState("");
  const [deleteNotice, setDeleteNotice] = useState("");
  const [exporting, setExporting] = useState<ExportFormat | null>(null);

  const ranges = useMemo<SelectedTimeRange[]>(
    () => selectedSegments.map(segment => ({ from: segment.startTime, to: segment.endTime })),
    [selectedSegments],
  );
  const selectionKey = useMemo(
    () => selectedSegments.map(segment => `${segment.id}:${segment.startTime}:${segment.endTime}`).join("|"),
    [selectedSegments],
  );
  const resetKey = rangeIdentity ?? selectionKey;

  useEffect(() => {
    setPage(0);
    setSearch("");
  }, [deviceId, resetKey, pageSize]);

  const query = useQuery({
    queryKey: ["device-workspace", "device-log-selection", deviceId, resetKey, page, pageSize],
    queryFn: () => getSelectedDeviceLogs(deviceId, ranges, page, pageSize),
    enabled: deviceId > 0 && ranges.length > 0,
    placeholderData: previous => previous,
    refetchInterval: ranges.length ? 15_000 : false,
  });

  useEffect(() => {
    if (query.data && query.data.page !== page) setPage(query.data.page);
  }, [query.data, page]);

  const rows = query.data?.rows ?? [];
  const orderedRows = useMemo(() => [...rows].sort((left, right) =>
    Date.parse(right.occurredAt) - Date.parse(left.occurredAt) ||
    Date.parse(right.receivedAt) - Date.parse(left.receivedAt) ||
    right.telemetryId - left.telemetryId), [rows]);

  const dynamic = useMemo(() => {
    const map = new Map<string, string>();
    orderedRows.forEach(row => row.parameters.forEach(parameter => {
      if (!BASE_CODES.has(parameter.fieldCode.toLowerCase())) map.set(parameter.fieldCode, parameter.label);
    }));
    return [...map].map(([key, label]) => ({
      key: `parameter:${key}`,
      label,
      value: (row: TripLogRow) => {
        const parameter = row.parameters.find(item => item.fieldCode === key);
        return parameter ? `${formatTelemetryValue(parameter.value)}${parameter.unit && parameter.unit !== "-" ? ` ${parameter.unit}` : ""}` : "-";
      },
    }));
  }, [orderedRows]);

  const columns = useMemo<Column[]>(() => [
    { key: "imei", label: "Device IMEI", value: row => row.imei },
    { key: "timestamp", label: "Timestamp (Device/GPS)", value: row => formatDate(row.occurredAt) },
    { key: "receivedAt", label: "Received At (Server)", value: row => formatDate(row.receivedAt) },
    { key: "protocol", label: "Protocol", value: row => protocol(row.protocol) },
    { key: "source", label: "Source", value: row => (row.source || "-").toUpperCase() },
    { key: "driverName", label: "Driver Name", value: row => row.driverName || "-" },
    { key: "vehiclePlateNumber", label: "Vehicle Plate Number", value: row => row.vehiclePlateNumber || "-" },
    { key: "latitude", label: "Latitude", value: row => number(row.latitude, 7) },
    { key: "longitude", label: "Longitude", value: row => number(row.longitude, 7) },
    { key: "altitude", label: "Altitude (m)", value: row => number(row.altitude, 0) },
    { key: "angle", label: "Angle (°)", value: row => number(row.angle, 0) },
    { key: "speed", label: "Speed (km/h)", value: row => number(row.speed) },
    { key: "satellites", label: "Satellites", value: row => number(row.satellites, 0) },
    { key: "hdop", label: "HDOP", value: row => number(row.hdop, 2) },
    ...dynamic,
  ], [dynamic]);

  const visible = columns.filter(column => !hidden.has(column.key));
  const filtered = orderedRows.filter(row => !search || visible.some(column => column.value(row).toLowerCase().includes(search.toLowerCase())));
  const totalPages = query.data?.totalPages ?? 0;
  const totalRows = query.data?.totalRows ?? 0;

  const deleteMutation = useMutation({
    mutationFn: () => deleteAllDeviceHistory(deviceId, confirmation),
    onSuccess: async result => {
      setDeleteOpen(false);
      setConfirmation("");
      setPage(0);
      const total = result.telemetryRows + result.ioRows + result.normalizedRows + result.alertRows + result.eventRows + result.rawPacketRows + result.latestPositionRows + result.presenceRows;
      setDeleteNotice(`${total} database records were permanently deleted. New telemetry will start a new history.`);
      await queryClient.invalidateQueries({ queryKey: ["device-workspace"] });
    },
  });
  const deleteError = deleteMutation.isError ? apiErrorMessage(deleteMutation.error) : "";

  function toggle(key: string) {
    setHidden(current => {
      const next = new Set(current);
      next.has(key) ? next.delete(key) : next.add(key);
      return next;
    });
  }

  async function exportSelection(format: ExportFormat) {
    if (!ranges.length || exporting) return;
    setExporting(format);
    setExportOpen(false);
    try {
      const exportRows = await exportSelectedDeviceLogs(deviceId, ranges);
      const visibleExportColumns = exportColumns(exportRows).filter(column => !hidden.has(column.key));
      exportTripLog(format, exportRows, visibleExportColumns, imei, selectedSegments.length);
    } finally {
      setExporting(null);
    }
  }

  return <section className="dw-trip-log">
    <header>
      <div>
        <h2>Device Log History — Selected Trip, Idle &amp; Stop Data</h2>
        <p>{selectedSegments.length ? `${selectedSegments.length} selected Trip/Idle/Stop item(s). Only telemetry inside the selected intervals is shown.` : "Select at least one Trip, Idle or Stop to display device log history."}</p>
      </div>
      <div className="dw-trip-table-actions">
        <label><Search/><input value={search} onChange={event => setSearch(event.target.value)} placeholder="Search this page" disabled={!selectedSegments.length}/></label>
        <div className="dw-trip-columns">
          <button type="button" onClick={() => setColumnsOpen(value => !value)} disabled={!selectedSegments.length}><Columns3/>Columns</button>
          {columnsOpen ? <div className="dw-trip-columns-menu">{columns.map(column => <label key={column.key}><input type="checkbox" checked={!hidden.has(column.key)} onChange={() => toggle(column.key)}/><span>{column.label}</span></label>)}</div> : null}
        </div>
        <div className="dw-trip-export">
          <button type="button" onClick={() => setExportOpen(value => !value)} disabled={!selectedSegments.length || Boolean(exporting)}><Download/>{exporting ? `Exporting ${exporting.toUpperCase()}…` : "Export"}<ChevronDown/></button>
          {exportOpen ? <div className="dw-trip-export-menu">
            <button type="button" onClick={() => void exportSelection("xlsx")}>Excel (.xlsx)</button>
            <button type="button" onClick={() => void exportSelection("csv")}>CSV (.csv)</button>
          </div> : null}
        </div>
        {canDeleteAll ? <Button type="button" size="sm" variant="destructive" onClick={() => { setDeleteNotice(""); setDeleteOpen(true); }} disabled={!imei}><Trash2/>Delete All Data</Button> : null}
      </div>
    </header>

    {deleteNotice ? <div className="dw-trip-delete-notice" role="status">{deleteNotice}</div> : null}
    {query.isError ? <div className="dw-trip-error" role="alert">Selected device log history could not be loaded.</div> : null}

    <div className="dw-trip-table-scroll">
      <table>
        <thead><tr>{visible.map((column, index) => <th key={column.key} className={index < 4 ? `sticky sticky-${index}` : ""}>{column.label}</th>)}</tr></thead>
        <tbody>
          {!selectedSegments.length
            ? <tr><td className="dw-trip-log-empty" colSpan={Math.max(1, visible.length)}>No Trip/Idle/Stop selected.</td></tr>
            : query.isLoading
              ? <tr><td className="dw-trip-log-empty" colSpan={Math.max(1, visible.length)}>Loading selected telemetry…</td></tr>
              : filtered.length
                ? filtered.map(row => <tr key={row.telemetryId}>{visible.map((column, index) => <td key={column.key} className={index < 4 ? `sticky sticky-${index}` : ""}>{column.value(row)}</td>)}</tr>)
                : <tr><td className="dw-trip-log-empty" colSpan={Math.max(1, visible.length)}>No telemetry rows for the selected Trip/Idle/Stop interval.</td></tr>}
        </tbody>
      </table>
    </div>

    <footer>
      <span>{selectedSegments.length ? `Showing ${filtered.length} of ${totalRows} selected telemetry rows` : "Showing 0 selected telemetry rows"}</span>
      <div className="dw-trip-pagination">
        <label>Rows<select value={pageSize} onChange={event => setPageSize(Number(event.target.value) as (typeof PAGE_SIZES)[number])}>{PAGE_SIZES.map(size => <option key={size} value={size}>{size}</option>)}</select></label>
        <button type="button" disabled={page <= 0 || totalPages <= 1} onClick={() => setPage(0)} title="First page">1</button>
        <button type="button" disabled={page <= 0} onClick={() => setPage(value => Math.max(0, value - 1))}>‹</button>
        <strong>{totalPages ? `Page ${page + 1} of ${totalPages}` : "Page 0 of 0"}</strong>
        <button type="button" disabled={page + 1 >= totalPages} onClick={() => setPage(value => Math.min(Math.max(0, totalPages - 1), value + 1))}>›</button>
        <button type="button" disabled={totalPages <= 1 || page + 1 >= totalPages} onClick={() => setPage(Math.max(0, totalPages - 1))} title="Last page">{totalPages || 0}</button>
      </div>
    </footer>

    {deleteOpen ? <div className="dw-trip-delete-backdrop" role="presentation" onMouseDown={event => { if (event.target === event.currentTarget && !deleteMutation.isPending) setDeleteOpen(false); }}>
      <section className="dw-trip-delete-dialog" role="dialog" aria-modal="true" aria-labelledby="delete-device-history-title">
        <header><TriangleAlert/><div><h2 id="delete-device-history-title">Permanently Delete All Device History</h2><p>This action cannot be undone.</p></div></header>
        <div className="dw-trip-delete-body"><p>All GPS telemetry, device log parameters, generated telemetry alerts, raw packets, and the latest stored position for this device will be permanently deleted from the database.</p><p>The device, vehicle, driver, assignments, commands, and workspace configuration will remain unchanged. New incoming telemetry will be stored as a new history.</p><label>Type IMEI <strong>{imei}</strong> to confirm<input autoFocus value={confirmation} onChange={event => setConfirmation(event.target.value)} disabled={deleteMutation.isPending}/></label>{deleteError ? <div className="dw-trip-error" role="alert">Deletion failed: {deleteError}. No partial deletion was saved.</div> : null}</div>
        <footer><Button type="button" variant="outline" onClick={() => setDeleteOpen(false)} disabled={deleteMutation.isPending}>Cancel</Button><Button type="button" variant="destructive" onClick={() => deleteMutation.mutate()} disabled={confirmation.trim() !== imei || deleteMutation.isPending}><Trash2/>{deleteMutation.isPending ? "Deleting…" : "Permanently Delete"}</Button></footer>
      </section>
    </div> : null}
  </section>;
}

function exportColumns(rows: TripLogRow[]): Column[] {
  const parameterLabels = new Map<string, string>();
  rows.forEach(row => row.parameters.forEach(parameter => {
    if (!BASE_CODES.has(parameter.fieldCode.toLowerCase())) parameterLabels.set(parameter.fieldCode, parameter.label);
  }));
  return [
    { key: "imei", label: "Device IMEI", value: row => row.imei },
    { key: "timestamp", label: "Timestamp (Device/GPS)", value: row => formatDate(row.occurredAt) },
    { key: "receivedAt", label: "Received At (Server)", value: row => formatDate(row.receivedAt) },
    { key: "protocol", label: "Protocol", value: row => protocol(row.protocol) },
    { key: "source", label: "Source", value: row => (row.source || "-").toUpperCase() },
    { key: "driverName", label: "Driver Name", value: row => row.driverName || "-" },
    { key: "vehiclePlateNumber", label: "Vehicle Plate Number", value: row => row.vehiclePlateNumber || "-" },
    { key: "latitude", label: "Latitude", value: row => number(row.latitude, 7) },
    { key: "longitude", label: "Longitude", value: row => number(row.longitude, 7) },
    { key: "altitude", label: "Altitude (m)", value: row => number(row.altitude, 0) },
    { key: "angle", label: "Angle (°)", value: row => number(row.angle, 0) },
    { key: "speed", label: "Speed (km/h)", value: row => number(row.speed) },
    { key: "satellites", label: "Satellites", value: row => number(row.satellites, 0) },
    { key: "hdop", label: "HDOP", value: row => number(row.hdop, 2) },
    ...[...parameterLabels].map(([fieldCode, label]) => ({
      key: `parameter:${fieldCode}`,
      label,
      value: (row: TripLogRow) => {
        const parameter = row.parameters.find(item => item.fieldCode === fieldCode);
        return parameter ? `${formatTelemetryValue(parameter.value)}${parameter.unit && parameter.unit !== "-" ? ` ${parameter.unit}` : ""}` : "-";
      },
    })),
  ];
}

function protocol(value?: string | null) {
  const normalized = (value || "").toUpperCase();
  if (normalized.includes("8E")) return "CODEC8E";
  if (normalized.includes("CODEC8")) return "CODEC8";
  if (normalized.includes("ALELS")) return "ALELS";
  return normalized || "-";
}
function number(value?: number | null, digits?: number) {
  if (!Number.isFinite(value)) return "-";
  const numeric = Number(value);
  if (digits != null) return numeric.toFixed(digits);
  if (Number.isInteger(numeric)) return String(numeric);
  return numeric.toFixed(2);
}
function formatTelemetryValue(value: string) {
  const trimmed = value.trim();
  if (!/^-?\d+(?:\.\d+)?$/.test(trimmed)) return value;
  const numeric = Number(trimmed);
  if (!Number.isFinite(numeric)) return value;
  if (Number.isInteger(numeric)) return String(numeric);
  return numeric.toFixed(2);
}
function formatDate(value: string) { return new Date(value).toLocaleString("id-ID", { dateStyle: "medium", timeStyle: "medium" }); }
function apiErrorMessage(error: unknown) {
  if (!isAxiosError(error)) return "Unexpected application error";
  if (error.response?.status === 404 || error.response?.status === 405) return "The backend is still using an older version. Restart the backend service";
  const payload = error.response?.data as { message?: string; detail?: string; error?: string } | string | undefined;
  if (typeof payload === "string" && payload.trim()) return payload;
  if (payload && typeof payload === "object") return payload.detail || payload.message || payload.error || `Backend returned HTTP ${error.response?.status}`;
  return error.response?.status ? `Backend returned HTTP ${error.response.status}` : "Backend service is not reachable";
}
