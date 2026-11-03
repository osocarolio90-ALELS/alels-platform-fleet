import type { EyeBeaconCard, EyeBeaconPage } from "../types/eyebeacon";

/** Merge identities across received-packet pages, retaining the newest source measurement. */
export function eyeBeaconCards(pages: readonly EyeBeaconPage[]): EyeBeaconCard[] {
  const byId = new Map<number | string, EyeBeaconCard>();
  for (const page of pages) {
    for (const card of page.beacons) {
      const previous = byId.get(card.beaconId);
      if (!previous || compareLatest(card, previous) < 0) byId.set(card.beaconId, card);
    }
  }
  return [...byId.values()].sort(compareLatest);
}

function compareLatest(left: EyeBeaconCard, right: EyeBeaconCard): number {
  return Date.parse(right.lastSeen) - Date.parse(left.lastSeen)
    || (right.source.observationId ?? right.source.telemetryId ?? 0) - (left.source.observationId ?? left.source.telemetryId ?? 0)
    || (typeof left.beaconId === "number" && typeof right.beaconId === "number"
      ? left.beaconId - right.beaconId : String(left.beaconId).localeCompare(String(right.beaconId)));
}

export function beaconValue(value: string | number | null | undefined, unit = ""): string {
  return value === null || value === undefined || value === "" ? "—" : `${value}${unit}`;
}

export function beaconTime(value: string | null | undefined): string {
  if (!value) return "—";
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? "—" : date.toLocaleString();
}

/** Capture/protocol classification does not require physical MAC or asset registration. */
export function bleAssetCards(pages: readonly EyeBeaconPage[], type: "EyeBeacon" | "EyeSensor"): EyeBeaconCard[] {
  return eyeBeaconCards(pages).filter(card => card.sensor?.type === type);
}
