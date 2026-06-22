import { create } from "zustand";
import type { RealtimeAlert } from "@/lib/api";

const ALERT_STORAGE_KEY = "alels-web-react-read-alerts";

type AlertState = {
  readAlertKeys: string[];
  markAlertRead: (key: string) => void;
  markAlertsRead: (keys: string[]) => void;
};

function readAlertKeys() {
  try {
    const stored = localStorage.getItem(ALERT_STORAGE_KEY);
    const parsed = stored ? JSON.parse(stored) : [];
    return Array.isArray(parsed) ? parsed.filter((item) => typeof item === "string") : [];
  } catch {
    return [];
  }
}

function writeAlertKeys(keys: string[]) {
  try {
    localStorage.setItem(ALERT_STORAGE_KEY, JSON.stringify(keys.slice(-300)));
  } catch {
    // Browser storage can be unavailable in strict privacy modes.
  }
}

export function getAlertKey(alert: RealtimeAlert, index = 0) {
  const parts = [
    alert.time ?? "no-time",
    alert.type ?? "no-type",
    alert.severity ?? "no-severity",
    alert.imei ?? "no-imei",
    alert.title ?? "no-title",
    alert.message ?? "no-message"
  ];

  if (parts.every((part) => part.startsWith("no-"))) {
    parts.push(String(index));
  }

  return parts.join("|");
}

export const useAlertStore = create<AlertState>()((set, get) => ({
  readAlertKeys: readAlertKeys(),
  markAlertRead: (key) => {
    const keys = new Set(get().readAlertKeys);
    keys.add(key);
    const nextKeys = Array.from(keys);
    writeAlertKeys(nextKeys);
    set({ readAlertKeys: nextKeys });
  },
  markAlertsRead: (keys) => {
    const nextKeys = Array.from(new Set([...get().readAlertKeys, ...keys]));
    writeAlertKeys(nextKeys);
    set({ readAlertKeys: nextKeys });
  }
}));
