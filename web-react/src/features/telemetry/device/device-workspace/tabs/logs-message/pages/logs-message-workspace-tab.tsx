import { useQuery } from "@tanstack/react-query";
import { useEffect, useMemo, useState, type ReactNode } from "react";

import { TripLogTable } from "../../trip-route/components/trip-log-table";
import type { TripSummary } from "../../trip-route/types/device-workspace-trip-route";
import { getPacketDetail, getPackets, getPacketSummary, getTraffic } from "../api/device-workspace-logs-message-api";

type Mode = "TRAFFIC" | "DEVICE";
const LIVE_REFRESH_MS = 5_000;
const PAGE_SIZES = [15, 25, 50, 75, 100] as const;

export function LogsMessageWorkspaceTab({ deviceId, imei, refreshToken }: { deviceId: number; imei: string; refreshToken: number }) {
  const [mode, setMode] = useState<Mode>("TRAFFIC");
  const [from, setFrom] = useState(() => localDateTime(startOfToday()));
  const [to, setTo] = useState(() => localDateTime(new Date()));
  const [live, setLive] = useState(true);
  const [liveNow, setLiveNow] = useState(() => Date.now());
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(15);
  const [search, setSearch] = useState("");
  const [eventType, setEventType] = useState("");
  const [transport, setTransport] = useState("");
  const [direction, setDirection] = useState("");
  const [selected, setSelected] = useState<number | null>(null);
  const fromIso = iso(from);
  const toIso = live ? new Date(liveNow).toISOString() : iso(to);
  const toQueryKey = live ? "live" : toIso;

  useEffect(() => { setPage(0); setSelected(null); }, [mode, from, size, eventType, transport, direction]);
  useEffect(() => {
    if (!live) return;
    setFrom(localDateTime(startOfToday()));
    const update = () => { const now = new Date(); setLiveNow(now.getTime()); setTo(localDateTime(now)); };
    update();
    const timer = window.setInterval(update, LIVE_REFRESH_MS);
    return () => window.clearInterval(timer);
  }, [live]);

  const traffic = useQuery({
    queryKey: ["dw-traffic", deviceId, fromIso, toQueryKey, page, size, search, eventType, transport, refreshToken],
    queryFn: () => getTraffic(deviceId, { from: fromIso, to: toIso, page, size, search, eventType, transport }),
    enabled: deviceId > 0 && mode === "TRAFFIC", placeholderData: previous => previous,
    refetchInterval: live ? LIVE_REFRESH_MS : false, refetchIntervalInBackground: true,
  });
  const packets = useQuery({
    queryKey: ["dw-packets", deviceId, fromIso, toQueryKey, page, size, direction, refreshToken],
    queryFn: () => getPackets(deviceId, { from: fromIso, to: toIso, page, size, direction }),
    enabled: deviceId > 0 && mode === "DEVICE", placeholderData: previous => previous,
    refetchInterval: live ? LIVE_REFRESH_MS : false, refetchIntervalInBackground: true,
  });
  const summary = useQuery({
    queryKey: ["dw-packet-summary", deviceId, fromIso, toQueryKey, refreshToken],
    queryFn: () => getPacketSummary(deviceId, fromIso, toIso),
    enabled: deviceId > 0 && mode === "DEVICE", placeholderData: previous => previous,
    refetchInterval: live ? LIVE_REFRESH_MS : false, refetchIntervalInBackground: true,
  });
  const detail = useQuery({
    queryKey: ["dw-packet-detail", deviceId, selected], queryFn: () => getPacketDetail(deviceId, selected!),
    enabled: deviceId > 0 && mode === "DEVICE" && selected != null,
  });
  const historySegments = useMemo<TripSummary[]>(() => [{
    id: `logs-message:${fromIso}:${toIso}`, type: "TRIP", status: "COMPLETED", startTime: fromIso, endTime: toIso,
    durationSeconds: Math.max(0, (Date.parse(toIso) - Date.parse(fromIso)) / 1_000), distanceKm: 0,
    averageSpeed: 0, maximumSpeed: 0, driverName: "-", startLatitude: null, startLongitude: null,
    endLatitude: null, endLongitude: null,
  }], [fromIso, toIso]);
  const current = mode === "TRAFFIC" ? traffic.data : packets.data;
  const currentError = mode === "TRAFFIC" ? traffic.isError : packets.isError || summary.isError;

  return <section className="dw-log-workspace">
    <header className="dw-log-toolbar">
      <div className="dw-mode-toggle"><button className={mode === "TRAFFIC" ? "active" : ""} onClick={() => setMode("TRAFFIC")}>TRAFFIC</button><button className={mode === "DEVICE" ? "active" : ""} onClick={() => setMode("DEVICE")}>DEVICE</button></div>
      <label>From<input type="datetime-local" value={from} onChange={event => setFrom(event.target.value)} /></label>
      <label>To<input type="datetime-local" value={to} onChange={event => { setTo(event.target.value); setLive(false); }} /></label>
      <button type="button" className={`dw-live-toggle ${live ? "active" : ""}`} onClick={() => setLive(value => !value)}>{live ? "LIVE" : "LIVE OFF"}</button>
      {mode === "TRAFFIC" ? <><input aria-label="Search logs" placeholder="Search logs" value={search} onChange={event => { setSearch(event.target.value); setPage(0); }} /><select aria-label="Event type" value={eventType} onChange={event => setEventType(event.target.value)}><option value="">All events</option><option value="PACKET_RECEIVED">Packet received</option></select><select aria-label="Transport" value={transport} onChange={event => setTransport(event.target.value)}><option value="">All transport</option><option>TCP</option><option>UDP</option></select></> : <select aria-label="Direction" value={direction} onChange={event => setDirection(event.target.value)}><option value="">RX &amp; TX</option><option>RX</option><option>TX</option></select>}
    </header>
    {currentError ? <p className="dw-log-error" role="alert">Data Log &amp; Message gagal dimuat dari server. Data tidak diganti atau disamarkan sebagai tabel kosong.</p> : null}

    {mode === "TRAFFIC" ? <>
      <Panel title="TCP Logs"><table><thead><tr><th>Time</th><th>Event</th><th>Transport</th><th>Source</th><th>Protocol</th><th>Status</th><th>RX</th><th>TX</th><th>Remote</th></tr></thead><tbody>{traffic.isLoading ? <tr><td className="dw-log-empty" colSpan={9}>Loading actual TCP gateway events…</td></tr> : traffic.data?.rows.length ? traffic.data.rows.map(row => <tr key={row.id}><td>{time(row.timestamp)}</td><td><strong>{row.title}</strong><small>{row.details || row.eventType}</small></td><td>{row.transport || "-"}</td><td>{row.source || "-"}</td><td>{row.protocol || "-"}</td><td><span className={`dw-log-status ${row.status.toLowerCase()}`}>{row.status}</span></td><td>{bytes(row.receivedBytes)}</td><td>{bytes(row.sentBytes)}</td><td>{row.remoteAddress || "-"}</td></tr>) : <tr><td className="dw-log-empty" colSpan={9}>No actual TCP/UDP gateway events were stored for this device in the selected interval.</td></tr>}</tbody></table></Panel>
      <div className="dw-trip-route-tab"><TripLogTable deviceId={deviceId} imei={imei} selectedSegments={historySegments} rangeIdentity={`${deviceId}:${from}:${live ? "live" : to}`} /></div>
    </> : <>
      <div className="dw-packet-summary"><article><small>RX bytes</small><strong>{bytes(summary.data?.receivedBytes)}</strong><span>{summary.data?.rxPacketCount || 0} packets</span></article><article><small>TX bytes</small><strong>{bytes(summary.data?.transmittedBytes)}</strong><span>{summary.data?.txPacketCount || 0} packets</span></article><article><small>Total</small><strong>{bytes(summary.data?.totalBytes)}</strong></article></div>
      <div className="dw-packet-grid">
        <Panel title="Raw packets"><div className="dw-packet-list">{packets.isLoading ? <p className="dw-log-empty">Loading actual raw packets…</p> : packets.data?.rows.length ? packets.data.rows.map(row => <button key={row.id} className={`${row.direction.toLowerCase()} ${selected === row.id ? "active" : ""}`} onClick={() => setSelected(row.id)}><span>{row.direction}</span><time>{time(row.timestamp)}</time><strong>{bytes(row.sizeBytes)}</strong><small><span>{row.transport || "-"}</span><span>Protocol: {row.protocol || "-"}</span></small></button>) : <p className="dw-log-empty">No actual raw packets were stored in the selected interval.</p>}</div></Panel>
        <Panel title="Packet detail">{detail.isError ? <p className="dw-log-error" role="alert">Stored packet detail could not be loaded.</p> : detail.data ? <dl className="dw-packet-detail"><dt>Direction</dt><dd>{detail.data.direction}</dd><dt>Time</dt><dd>{time(detail.data.timestamp)}</dd><dt>Remote</dt><dd>{detail.data.remoteAddress || "-"}</dd><dt>Size</dt><dd>{bytes(detail.data.sizeBytes)}</dd><dt>Raw hex</dt><dd><pre>{detail.data.rawHex || "Not stored"}</pre></dd><dt>Raw text</dt><dd><pre>{detail.data.rawText || "Not stored"}</pre></dd></dl> : <p className="dw-log-empty">Select a packet to inspect its stored bytes.</p>}</Panel>
      </div>
    </>}
    <footer className="dw-log-pagination"><span>{current?.totalRows || 0} records</span><button disabled={page === 0} onClick={() => setPage(value => value - 1)}>Previous</button><span>Page {page + 1} / {Math.max(1, current?.totalPages || 0)}</span><button disabled={!current || page + 1 >= current.totalPages} onClick={() => setPage(value => value + 1)}>Next</button><select value={size} onChange={event => setSize(Number(event.target.value))}>{PAGE_SIZES.map(value => <option key={value}>{value}</option>)}</select></footer>
  </section>;
}

function Panel({ title, children }: { title: string; children: ReactNode }) { return <section className="dw-panel dw-log-panel"><h2>{title}</h2><div className="dw-log-table">{children}</div></section>; }
function localDateTime(date: Date) { const offset = date.getTimezoneOffset() * 60_000; return new Date(date.getTime() - offset).toISOString().slice(0, 16); }
function startOfToday() { const date = new Date(); date.setHours(0, 0, 0, 0); return date; }
function iso(value: string) { return new Date(value).toISOString(); }
function time(value: string) { return new Date(value).toLocaleString(); }
function bytes(value: number | null | undefined) { if (value == null) return "-"; if (value < 1_024) return `${value} B`; if (value < 1_048_576) return `${(value / 1_024).toFixed(1)} KB`; return `${(value / 1_048_576).toFixed(1)} MB`; }
