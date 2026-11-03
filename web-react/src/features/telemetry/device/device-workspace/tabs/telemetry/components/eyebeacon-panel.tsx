import { useInfiniteQuery } from "@tanstack/react-query";

import axios from "axios";

import { useEffect, useId, useState } from "react";

import { ChevronDown, ChevronUp } from "lucide-react";



import { Button } from "@/components/ui/button";

import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";

import { getEyeBeacons } from "../api/eyebeacon-api";

import type { EyeBeaconCard } from "../types/eyebeacon";

import { beaconTime, beaconValue, bleAssetCards } from "../utils/eyebeacon-cards";

import { eyeBeaconPresentation } from "../utils/eyebeacon-presentation";



type Props = { deviceId: number; live: boolean; refreshToken: number; assetType?: "EyeBeacon" | "EyeSensor" };



function useReceivedBle(deviceId: number, live: boolean, refreshToken: number) {

  const query = useInfiniteQuery({

    queryKey: ["device-workspace", "received-eyebeacons", deviceId],

    queryFn: ({ pageParam, signal }) => getEyeBeacons(deviceId, pageParam, signal),

    initialPageParam: 0,

    getNextPageParam: page => page.hasMore ? page.nextBeforeId ?? undefined : undefined,

    enabled: Number.isSafeInteger(deviceId) && deviceId > 0,

    refetchInterval: live ? 5_000 : false,

    refetchIntervalInBackground: false,

    retry: 2,

  });

  const { refetch } = query;

  useEffect(() => { if (refreshToken > 0) void refetch({ cancelRefetch: false }); }, [refreshToken, refetch]);

  return query;

}



export function EyeBeaconPanel({ deviceId, live, refreshToken, assetType = "EyeBeacon" }: Props) {

  const query = useReceivedBle(deviceId, live, refreshToken);

  const { refetch } = query;

  const forbidden = axios.isAxiosError(query.error) && [401, 403, 404].includes(query.error.response?.status ?? 0);

  const pages = forbidden ? [] : query.data?.pages ?? [];

  const cards = bleAssetCards(pages, assetType);



  return <div className="mt-3 space-y-3" aria-label={`${assetType} observations`} aria-busy={query.isFetching}>

    <p className="text-xs text-muted-foreground">Parsed from this device's stored BLE telemetry. RECENT and STALE describe observation age. Load older packets to discover earlier beacons.</p>

    {query.isPending ? <p role="status" className="text-sm text-muted-foreground">Loading BLE observations...</p> : null}

    {query.isError ? <div role="alert" className="space-y-2 text-sm text-muted-foreground">

      <p>{forbidden ? "EyeBeacon history is unavailable for this device." : cards.length ? "EyeBeacon history could not be refreshed. Previously loaded cards are retained." : "EyeBeacon history could not be loaded."}</p>

      <Button size="sm" variant="outline" disabled={query.isFetching} onClick={() => void refetch({ cancelRefetch: false })}>Retry</Button>

    </div> : null}

    {!query.isPending && !query.isError && !cards.length ? <p className="text-sm text-muted-foreground">No parsed BLE data of this type is available for this device.</p> : null}

    {pages.some(page => page.hasUnresolvedObservations) ? <p role="status" className="text-xs text-muted-foreground">Some observations have no verified physical MAC. Captured IDs remain separate from MAC addresses.</p> : null}

    {pages.some(page => page.errors?.length) ? <details className="text-xs text-muted-foreground">

      <summary>BLE parsing diagnostics</summary>

      {pages.flatMap(page => page.errors ?? []).map((error, index) => <p key={`${error.telemetryId}:${error.ioId}:${error.byteOffset}:${index}`}>

        Telemetry {error.telemetryId} · IO {error.ioId} · {error.code} · byte {error.byteOffset}<br />

        <span className="break-all">{error.rawHex}</span>

      </p>)}

    </details> : null}

    <div className="grid max-h-[650px] grid-cols-1 gap-3 overflow-y-auto pr-1 lg:grid-cols-2">

      {cards.map(card => <BeaconCard key={card.beaconId} card={card} />)}

    </div>

    {query.hasNextPage && !forbidden ? <Button size="sm" variant="outline" disabled={query.isFetching} onClick={() => void query.fetchNextPage({ cancelRefetch: false })}>

      {query.isFetchingNextPage ? "Loading..." : "Load older BLE packets"}

    </Button> : null}

  </div>;

}



export function BeaconCard({ card }: { card: EyeBeaconCard }) {

  const [expanded, setExpanded] = useState(false);

  const detailsId = useId();

  const { measurement, source } = card;

  const display = eyeBeaconPresentation(card);

  const isSensor = card.sensor?.type === "EyeSensor";

  const sensor = measurement.sensorData;

  const measurementSource = (field: string, value?: unknown) => {

    const id=sensor?.sourceIoIds?.[field];

    const error=sensor?.fieldErrors?.[field];

    const provenance = id != null && display.ioIds.length > 1 ? `AVL ID ${id} · ${source.observedAt ? "Device" : "Server"} ${beaconTime(source.observedAt ?? source.receivedAt)}` : null;

    const unavailable = value == null ? error ?? (measurement.sensorCapture?.status !== "DECODED"

      ? measurement.sensorCapture?.reason : null) ?? "Not provided by a verified field in this observation." : error;

    return [provenance, unavailable].filter(Boolean).join(" · ") || undefined;

  };

  return <Card className="min-w-0 self-start" data-beacon-id={card.beaconId}>

    <CardHeader>

      <div className="flex items-start justify-between gap-2">

        <CardTitle className="min-w-0 break-all text-sm leading-snug">{display.name}</CardTitle>

        <Button type="button" variant="ghost" size="icon" className="h-7 w-7 shrink-0"

          aria-label={expanded ? "Collapse EyeBeacon details" : "Expand EyeBeacon details"}

          aria-expanded={expanded} aria-controls={detailsId} onClick={() => setExpanded(value => !value)}>

          {expanded ? <ChevronUp className="h-4 w-4" /> : <ChevronDown className="h-4 w-4" />}

        </Button>

      </div>

      {expanded ? <p className="break-all text-xs text-muted-foreground">{display.identifier} · {card.recency}</p> : null}

    </CardHeader>

    <CardContent className="space-y-4 text-xs">

      <dl className="space-y-1" hidden={expanded}>

        <Field label="MAC Address" value={display.mac} />

        <Field label="RSSI" value={beaconValue(measurement.rssiDbm, " dBm")} />

        <Field label="Estimated Distance (m)" value={display.distance} description={display.distanceReason} />

      </dl>

      <div id={detailsId} hidden={!expanded} className="space-y-4">

      <CardSection title="BLE Sensor Information">
        <Field label="Server Timestamp" value={beaconTime(source.receivedAt)} />
        <Field label="Device Timestamp" value={beaconTime(source.observedAt)} />
        <Field label="Type BLE" value={card.sensor?.type} />
        <Field label="Beacon ID / Instance ID" value={card.identity.kind === "VERIFIED_SLOT_MAC" ? null : card.identity.instanceId ?? card.identity.value} />
        <Field label="MAC Address" value={display.mac} />
        {!isSensor ? <Field label="Protocol BLE" value={display.protocol} /> : null}
        <Field label="Data From" value={display.dataFrom} />
        <Field label="AVL ID / IO ID" value={display.ioIds.join(", ")} />
        <Field label="BLE Capturing Mode" value={display.capturingMode} />
        <Field label="IMEI Device" value={source.receiverImei} />
      </CardSection>
      <CardSection title="Device Location">

        <Field label="Device Latitude" value={display.deviceLatitude} />

        <Field label="Device Longitude" value={display.deviceLongitude} />

        <Field label="Device Altitude" value={display.deviceAltitude} />

      </CardSection>

      <CardSection title={isSensor ? "EYE Sensor Measurements" : "EyeBeacon Data"}>

        <Field label="RSSI" value={beaconValue(measurement.rssiDbm, " dBm")} />

        <Field label="Estimated Distance (m)" value={display.distance} description={display.distanceReason} />

        <Field label="Battery Voltage" value={beaconValue(measurement.batteryMillivolts, " mV")} description={measurementSource("batteryMillivolts", measurement.batteryMillivolts)} />

        {isSensor ? <>

          {measurement.sensorCapture && measurement.sensorCapture.status !== "DECODED" ? <Field label="Sensor Data Availability"

            value={["PENDING_CONFIGURATION_VERIFICATION","PENDING_FIRMWARE_VERIFICATION"].includes(measurement.sensorCapture.status) ? "Pending — capture profile verification"

              : "Unavailable — sensor capture is not verified or valid"} description={measurement.sensorCapture.reason} /> : null}

          <Field label="Temperature" value={beaconValue(measurement.temperatureCelsius, " °C")} description={measurementSource("temperatureCelsius", measurement.temperatureCelsius)} />

          <Field label="Humidity" value={beaconValue(sensor?.humidityPercent, " %RH")} description={measurementSource("humidityPercent", sensor?.humidityPercent)} />

          <Field label="Movement Status" value={sensor?.movement == null ? null : sensor.movement ? "Moving" : "Not Moving"} description={measurementSource("movement", sensor?.movement)} />

          <Field label="Movement Counter" value={sensor?.movementCounter} description={measurementSource("movementCounter", sensor?.movementCounter)} />

          <Field label="Pitch" value={beaconValue(sensor?.pitchDegrees, "°")} description={measurementSource("pitchDegrees", sensor?.pitchDegrees)} />

          <Field label="Roll" value={beaconValue(sensor?.rollDegrees, "°")} description={measurementSource("rollDegrees", sensor?.rollDegrees)} />

        </> : null}
      </CardSection>

      </div>

    </CardContent>

  </Card>;

}



function CardSection({ title, children }: { title: string; children: React.ReactNode }) {

  return <section><h4 className="mb-2 font-semibold">{title}</h4><dl className="space-y-1">{children}</dl></section>;

}



function Field({ label, value, description }: { label: string; value: string | number | null | undefined; description?: string }) {

  return <div className="grid grid-cols-[minmax(0,1fr)_minmax(0,1.4fr)] gap-2"><dt className="text-muted-foreground">{label}</dt><dd className="min-w-0 break-all">{beaconValue(value)}{description ? <span className="block text-muted-foreground">{description}</span> : null}</dd></div>;

}



/** Every received source block, including observations without a physical MAC. */

export function BleReceivedTable({ deviceId, live, refreshToken }: Omit<Props, "assetType">) {

  const query=useReceivedBle(deviceId,live,refreshToken);

  const rows=query.data?.pages.flatMap(page=>page.beacons).filter(card=>[548,10500].includes(card.source.ioId)) ?? [];

  const uniqueRows=[...new Map(rows.map(card=>[`${card.source.telemetryId}:${card.source.ioId}:${card.source.blockIndex}`,card])).values()];

  return <section className="mt-3 space-y-2 text-xs" aria-label="Parsed BLE received data">

    <h4 className="font-semibold">Parsed BLE data — IO 548 / 10500</h4>

    {query.isPending?<p role="status">Loading parsed BLE data...</p>:null}

    {query.isError?<p role="alert">Parsed BLE data could not be loaded. Check the active session and backend.</p>:null}

    <div className="overflow-x-auto"><table className="w-full text-left">

      <thead><tr>{["Telemetry / timestamp","AVL ID","Type / profile","Beacon ID / Instance","MAC Address","RSSI","Estimated Distance","Temperature","Humidity","Movement / counter","Pitch / roll","Battery","Status"].map(label=><th key={label} className="p-2">{label}</th>)}</tr></thead>

      <tbody>{uniqueRows.map(card=>{

        const m=card.measurement,sensor=m.sensorData,display=eyeBeaconPresentation(card);

        const errors=query.data?.pages.flatMap(page=>page.errors ?? []).filter(error=>error.telemetryId===card.source.telemetryId && error.ioId===card.source.ioId) ?? [];

        return <tr key={`${card.source.telemetryId}:${card.source.ioId}:${card.source.blockIndex}`}>

          <td className="p-2">{card.source.telemetryId}<br />{beaconTime(card.source.observedAt ?? card.source.receivedAt)}</td>

          <td className="p-2">{card.source.ioId}</td>

          <td className="p-2">{card.sensor?.type ?? "Unclassified"}<br />{m.sensorCapture?.profile ?? display.capturingMode}</td>

          <td className="p-2">{card.identity.instanceId ?? card.identity.value}</td>

          <td className="p-2">{beaconValue(display.mac)}</td>

          <td className="p-2">{beaconValue(m.rssiDbm," dBm")}</td>

          <td className="p-2">{display.distance}</td>

          <td className="p-2">{beaconValue(m.temperatureCelsius," °C")}</td>

          <td className="p-2">{beaconValue(sensor?.humidityPercent," %RH")}</td>

          <td className="p-2">{sensor?.movement==null?"—":sensor.movement?"Moving":"Not Moving"} / {beaconValue(sensor?.movementCounter)}</td>

          <td className="p-2">{beaconValue(sensor?.pitchDegrees,"°")} / {beaconValue(sensor?.rollDegrees,"°")}</td>

          <td className="p-2">{beaconValue(m.batteryMillivolts," mV")}</td>

          <td className="p-2">{errors.length?"Partial / diagnostic":m.sensorCapture?.status ?? "Parsed"}<br />{errors.map(error=>error.code).join(", ") || (m.sensorCapture?.status!=="DECODED"?m.sensorCapture?.reason:"")}</td>

        </tr>;

      })}</tbody>

    </table></div>

    {!query.isPending && !query.isError && !uniqueRows.length?<p>No IO 548 / 10500 data received.</p>:null}

    {query.hasNextPage?<Button size="sm" variant="outline" disabled={query.isFetching} onClick={()=>void query.fetchNextPage()}>Load older BLE data</Button>:null}

  </section>;

}

