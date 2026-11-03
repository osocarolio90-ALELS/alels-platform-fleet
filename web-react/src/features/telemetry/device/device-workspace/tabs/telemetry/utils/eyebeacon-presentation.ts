import { normalizeBleMac } from "./ble-mac";
import type { EyeBeaconCard } from "../types/eyebeacon";

export function eyeBeaconPresentation(card: EyeBeaconCard) {
  const instanceId=card.identity.instanceId ?? card.measurement?.advertisementData?.instanceId
    ?? (card.identity.protocol === "CUSTOM" ? card.identity.value : null);
  const mac = (card.sensor?.type === "EyeBeacon" ? normalizeBleMac(instanceId) : null)
    ?? normalizeBleMac(card.sensor?.verifiedMac) ?? normalizeBleMac(card.identity.mac);
  const name = card.measurement.sensorData?.deviceName?.trim() || card.sensor?.name?.trim()
    || `${card.sensor?.type ?? "BLE"} ${card.identity.instanceId ?? card.identity.value ?? "Unknown identity"}`;
  const gps = card.source.receiverGps;
  const coordinatesValid = gps.valid && validCoordinates(gps.latitude, gps.longitude);
  const estimate = card.measurement?.distance;
  const meters = estimate?.meters;
  const validDistance = meters != null && Number.isFinite(meters) && meters > 0
    && ["GENERIC_UNCALIBRATED", "CALIBRATED"].includes(estimate?.model ?? "");
  const distance = validDistance ? `~${meters < 0.01 ? meters.toPrecision(2) : meters < 1000 ? meters.toFixed(2) : meters.toPrecision(4)} m` : "—";
  const sourceCodec = ({ TELTONIKA_CODEC8: "Codec 8", TELTONIKA_CODEC8E: "Codec 8 Extended", CODEC8: "Codec 8", CODEC8E: "Codec 8 Extended" } as Record<string,string>)[card.source.protocol] ?? "Not Provided / Unknown";
  const ioIds = card.source.ioIds ?? (Number.isInteger(card.source.ioId) ? [card.source.ioId] : []);
  const capturingMode = card.source.ioId === 11317 ? "EYE Sensor List"
    : card.measurement?.sensorData?.profile === "EYE_DEDICATED_IO" ? "Dedicated EYE Sensor IO"
    : [385,10500].includes(card.source.ioId) ? "Simple Beacon"
    : card.source.ioId === 548 ? "Advanced Beacon"
    : "Unknown";
  return {
    ioIds, sourceCodec, capturingMode,
    namespace: card.identity.namespace ?? card.measurement?.advertisementData?.namespace,
    instanceId: card.identity.instanceId ?? card.measurement?.advertisementData?.instanceId,
    uuid: card.identity.uuid ?? card.measurement?.advertisementData?.uuid,
    major: card.identity.major ?? card.measurement?.advertisementData?.major,
    minor: card.identity.minor ?? card.measurement?.advertisementData?.minor,
    distance, distanceReason: validDistance
      ? undefined
      : estimate?.reason === "INVALID_OR_MISSING_RSSI" ? "RSSI tidak tersedia atau tidak valid."
      : "Estimasi jarak belum tersedia dari backend.",
    name, mac, identifier: mac ?? `${card.identity.protocol} ${card.identity.value}`,
    protocol: card.measurement?.advertisementData?.protocol === "EDDYSTONE_UID" ? "Eddystone UID"
      : card.measurement?.advertisementData?.protocol === "IBEACON" ? "iBeacon"
      : card.source.ioId === 11317 || card.measurement?.sensorData?.profile === "EYE_DEDICATED_IO" ? "Not Provided / Unknown"
      : ({ EDDYSTONE: "Eddystone", IBEACON: "iBeacon", CUSTOM: "Custom" } as Record<string, string>)[card.identity.protocol] ?? "Not Provided / Unknown",
    dataFrom: card.source.protocol === "ALELS_JSON" ? "ALELS JSON" : sourceCodec !== "Not Provided / Unknown" ? "Native Teltonika Codec" : "Unknown",
    deviceLatitude: coordinatesValid ? gps.latitude : null,
    deviceLongitude: coordinatesValid ? gps.longitude : null,
    deviceAltitude: coordinatesValid && finite(gps.altitude) ? gps.altitude : null,
  };
}

function finite(value: number | null): value is number { return value !== null && Number.isFinite(value); }
function validCoordinates(latitude: number | null, longitude: number | null): boolean {
  return finite(latitude) && finite(longitude) && Math.abs(latitude) <= 90 && Math.abs(longitude) <= 180
    && !(latitude === 0 && longitude === 0);
}
