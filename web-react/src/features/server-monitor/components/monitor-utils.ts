import type { ReactNode } from "react";
import type { Language } from "@/stores/language-store";

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
    dot: "bg-blue-500",
    border: "border-border",
    bg: "bg-card",
    softBg: "bg-muted",
    text: "text-card-foreground",
    label: "Healthy",
    ring: "ring-border"
  },
  WARNING: {
    dot: "bg-amber-500",
    border: "border-border",
    bg: "bg-card",
    softBg: "bg-muted",
    text: "text-card-foreground",
    label: "Needs attention",
    ring: "ring-border"
  },

  ATTENTION: {
    dot: "bg-amber-500",
    border: "border-border",
    bg: "bg-card",
    softBg: "bg-muted",
    text: "text-card-foreground",
    label: "Stable, needs review",
    ring: "ring-border"
  },
  CRITICAL: {
    dot: "bg-red-500",
    border: "border-border",
    bg: "bg-card",
    softBg: "bg-muted",
    text: "text-card-foreground",
    label: "Critical",
    ring: "ring-border"
  },
  EMERGENCY: {
    dot: "bg-red-500",
    border: "border-border",
    bg: "bg-card",
    softBg: "bg-muted",
    text: "text-card-foreground",
    label: "Emergency",
    ring: "ring-border"
  },
  UNKNOWN: {
    dot: "bg-slate-400",
    border: "border-border",
    bg: "bg-card",
    softBg: "bg-muted",
    text: "text-card-foreground",
    label: "Unknown",
    ring: "ring-border"
  },
  OFFLINE: {
    dot: "bg-slate-400",
    border: "border-border",
    bg: "bg-card",
    softBg: "bg-muted",
    text: "text-card-foreground",
    label: "Offline",
    ring: "ring-border"
  }
};

export function visualFor(severity?: ServerMonitorSeverity) {
  return severityVisual[(severity || "UNKNOWN").toUpperCase()] ?? severityVisual.UNKNOWN;
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

const monitorLabels = {
  en: {
    statusReason: "Status Reason",
    operationalImpact: "Operational Impact",
    recommendedAction: "Recommended Action",
    attentionSummary: "Attention Summary",
    trend: "Trend",
    updated: "Updated",
    findingsLoading: "Loading monitor findings...",
    finding: "finding",
    healthy: "Healthy",
    warning: "Needs attention",
    review: "Stable, needs review",
    critical: "Critical",
    emergency: "Emergency",
    unknown: "Unknown",
    offline: "Offline"
  },
  id: {
    statusReason: "Alasan Status",
    operationalImpact: "Dampak Operasional",
    recommendedAction: "Rekomendasi Tindakan",
    attentionSummary: "Ringkasan Perhatian",
    trend: "Tren",
    updated: "Diperbarui",
    findingsLoading: "Memuat temuan monitor...",
    finding: "temuan",
    healthy: "Sehat",
    warning: "Perlu perhatian",
    review: "Stabil, perlu ditinjau",
    critical: "Kritis",
    emergency: "Darurat",
    unknown: "Tidak diketahui",
    offline: "Offline"
  }
} as const;

export type MonitorLabelKey = keyof typeof monitorLabels.en;

export function monitorLabel(language: Language, key: MonitorLabelKey) {
  return monitorLabels[language][key];
}

export function monitorText(language: Language, english: string, indonesian: string) {
  return language === "id" ? indonesian : english;
}

const monitorTermTranslations: Record<string, string> = {
  "Severity": "Keparahan",
  "Problem": "Masalah",
  "Impact": "Dampak",
  "Action": "Tindakan",
  "Status": "Status",
  "Gateway Monitor": "Monitor Gateway",
  "Traffic Monitor": "Monitor Trafik",
  "Database Monitor": "Monitor Database",
  "Storage Monitor": "Monitor Penyimpanan",
  "Security Monitor": "Monitor Keamanan",
  "AI Ops Monitor": "Monitor Operasi AI",
  "Overview Monitor": "Ringkasan Operasi",
  "Gateway Status": "Status Gateway",
  "Traffic Status": "Status Trafik",
  "Database Status": "Status Database",
  "Storage Status": "Status Penyimpanan",
  "Security Status": "Status Keamanan",
  "System Health": "Kesehatan Sistem",
  "Gateway Score": "Skor Gateway",
  "Traffic Score": "Skor Trafik",
  "Database Score": "Skor Database",
  "Storage Score": "Skor Penyimpanan",
  "Security Score": "Skor Keamanan",
  "Score": "Skor",
  "Gateway Assessment": "Penilaian Gateway",
  "Traffic Assessment": "Penilaian Trafik",
  "Database Assessment": "Penilaian Database",
  "Storage Assessment": "Penilaian Penyimpanan",
  "Security Assessment": "Penilaian Keamanan",
  "Executive Assessment": "Penilaian Eksekutif",
  "Channel Distribution": "Distribusi Channel",
  "Protocol Distribution": "Distribusi Protokol",
  "Health Distribution": "Distribusi Kesehatan",
  "Module Scores": "Skor Modul",
  "Operations Snapshot": "Ringkasan Operasi",
  "Gateway Technical Snapshot": "Ringkasan Teknis Gateway",
  "Traffic Technical Snapshot": "Ringkasan Teknis Trafik",
  "Database Technical Snapshot": "Ringkasan Teknis Database",
  "Storage Technical Snapshot": "Ringkasan Teknis Penyimpanan",
  "Security Technical Snapshot": "Ringkasan Teknis Keamanan",
  "Online Devices": "Perangkat Online",
  "Offline Devices": "Perangkat Offline",
  "Packet / Minute": "Paket / Menit",
  "Packet / Second": "Paket / Detik",
  "Invalid Packet": "Paket Tidak Valid",
  "Unknown IO": "IO Tidak Dikenal",
  "DB Connections": "Koneksi Database",
  "Database Size": "Ukuran Database",
  "Slow Queries": "Query Lambat",
  "Blocked Locks": "Lock Terblokir",
  "No Attention": "Tidak Perlu Perhatian",
  "Attention Required": "Perlu Perhatian",
  "Critical Attention": "Perhatian Kritis"
};

export function localizeMonitorTerm(language: Language, value: string) {
  return language === "id" ? monitorTermTranslations[value] ?? value : value;
}

export function severityLabel(language: Language, severity?: ServerMonitorSeverity) {
  const normalized = (severity || "UNKNOWN").toUpperCase();
  if (normalized === "NORMAL") return monitorLabel(language, "healthy");
  if (normalized === "WARNING") return monitorLabel(language, "warning");
  if (normalized === "ATTENTION") return monitorLabel(language, "review");
  if (normalized === "CRITICAL") return monitorLabel(language, "critical");
  if (normalized === "EMERGENCY") return monitorLabel(language, "emergency");
  if (normalized === "OFFLINE") return monitorLabel(language, "offline");
  return monitorLabel(language, "unknown");
}
