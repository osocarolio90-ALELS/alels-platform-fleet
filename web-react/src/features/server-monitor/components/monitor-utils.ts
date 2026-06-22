import type { ReactNode } from "react";

export type ServerMonitorSeverity = "NORMAL" | "WARNING" | "CRITICAL" | "EMERGENCY" | string;

export type MonitorMetricItem = {
  key: string;
  label: string;
  value: ReactNode;
  unit?: ReactNode;
  icon?: ReactNode;
};

export const severityTone: Record<string, "success" | "warning" | "danger" | "muted" | "default"> = {
  NORMAL: "success",
  WARNING: "warning",
  ATTENTION: "warning",
  CRITICAL: "danger",
  EMERGENCY: "danger"
};

export const severityVisual: Record<
  string,
  { dot: string; border: string; bg: string; softBg: string; text: string; label: string; ring: string }
> = {
  NORMAL: {
    dot: "bg-emerald-500",
    border: "border-emerald-300 dark:border-emerald-700/70",
    bg: "bg-emerald-50 dark:bg-emerald-950/40",
    softBg: "bg-emerald-50/80 dark:bg-emerald-950/30",
    text: "text-emerald-800 dark:text-emerald-200",
    label: "Healthy",
    ring: "ring-emerald-100 dark:ring-emerald-900/50"
  },
  WARNING: {
    dot: "bg-amber-500",
    border: "border-amber-300 dark:border-amber-700/70",
    bg: "bg-amber-50 dark:bg-amber-950/40",
    softBg: "bg-amber-50/80 dark:bg-amber-950/30",
    text: "text-amber-800 dark:text-amber-200",
    label: "Needs attention",
    ring: "ring-amber-100 dark:ring-amber-900/50"
  },

  ATTENTION: {
    dot: "bg-amber-500",
    border: "border-amber-300 dark:border-amber-700/70",
    bg: "bg-amber-50 dark:bg-amber-950/40",
    softBg: "bg-amber-50/80 dark:bg-amber-950/30",
    text: "text-amber-800 dark:text-amber-200",
    label: "Stable, needs review",
    ring: "ring-amber-100 dark:ring-amber-900/50"
  },
  CRITICAL: {
    dot: "bg-red-500",
    border: "border-red-300 dark:border-red-700/70",
    bg: "bg-red-50 dark:bg-red-950/40",
    softBg: "bg-red-50/80 dark:bg-red-950/30",
    text: "text-red-800 dark:text-red-200",
    label: "Critical",
    ring: "ring-red-100 dark:ring-red-900/50"
  },
  EMERGENCY: {
    dot: "bg-red-700",
    border: "border-red-400 dark:border-red-600",
    bg: "bg-red-50 dark:bg-red-950/50",
    softBg: "bg-red-50/80 dark:bg-red-950/40",
    text: "text-red-900 dark:text-red-100",
    label: "Emergency",
    ring: "ring-red-100 dark:ring-red-900/60"
  }
};

export function visualFor(severity?: ServerMonitorSeverity) {
  return severityVisual[severity || "NORMAL"] ?? severityVisual.NORMAL;
}

export function toneFor(severity?: ServerMonitorSeverity) {
  return severityTone[severity || "NORMAL"] ?? "default";
}

export function formatMonitorBytes(value?: number | null) {
  const bytes = Number(value ?? 0);
  if (!Number.isFinite(bytes) || bytes <= 0) return "0 B";
  if (bytes < 1024) return `${Math.round(bytes)} B`;
  const kb = bytes / 1024;
  if (kb < 1024) return `${trimMonitorNumber(kb)} KB`;
  const mb = kb / 1024;
  if (mb < 1024) return `${trimMonitorNumber(mb)} MB`;
  const gb = mb / 1024;
  if (gb < 1024) return `${trimMonitorNumber(gb)} GB`;
  return `${trimMonitorNumber(gb / 1024)} TB`;
}

function trimMonitorNumber(value: number) {
  if (value >= 100) return value.toFixed(0);
  if (value >= 10) return value.toFixed(1).replace(/\.0$/, "");
  return value.toFixed(2).replace(/0$/, "").replace(/\.0$/, "");
}

export function formatMonitorTimestamp(value?: string | null) {
  return value ? new Date(value).toLocaleString() : "Waiting for live backend data";
}
