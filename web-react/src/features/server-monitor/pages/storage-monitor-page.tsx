import { useMemo } from "react";
import { useQuery } from "@tanstack/react-query";
import {
  AlertTriangle,
  Archive,
  CheckCircle2,
  Clock3,
  Database,
  Gauge,
  HardDrive,
  History,
  Layers,
  PackageOpen,
  Server,
  ShieldAlert,
  TimerReset
} from "lucide-react";

import { Badge } from "@/components/ui/badge";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { getStorageMonitorOverview, type StorageDistribution, type StorageMetric, type StorageMonitorInsight } from "@/lib/api";
import {
  MonitorAssessmentCard,
  MonitorHero,
  MonitorKpiGrid,
  MonitorSnapshot,
  MonitorFindingsTable,
  toneFor
} from "@/features/server-monitor/components/server-monitor-ui";

const metricIcons: Record<string, JSX.Element> = {
  total_disk: <Server className="h-5 w-5" />,
  used_disk: <HardDrive className="h-5 w-5" />,
  free_disk: <PackageOpen className="h-5 w-5" />,
  disk_usage: <Gauge className="h-5 w-5" />,
  database_storage: <Database className="h-5 w-5" />,
  telemetry_storage: <Layers className="h-5 w-5" />,
  raw_packet_storage: <Archive className="h-5 w-5" />,
  index_storage: <ShieldAlert className="h-5 w-5" />,
  log_storage: <History className="h-5 w-5" />,
  backup_storage: <Archive className="h-5 w-5" />,
  daily_growth: <Clock3 className="h-5 w-5" />,
  weekly_growth: <TimerReset className="h-5 w-5" />,
  monthly_growth: <TimerReset className="h-5 w-5" />,
  predicted_full: <Gauge className="h-5 w-5" />,
  raw_retention: <Archive className="h-5 w-5" />,
  telemetry_retention: <Layers className="h-5 w-5" />
};

const fallbackOverview = {
  status: "WARNING",
  storageScore: 70,
  generatedAt: null,
  summary: "Storage Monitor API belum tersedia atau backend belum berjalan.",
  recommendation: "Jalankan backend dan pastikan endpoint /api/server-monitor/storage/overview tersedia untuk SUPERADMIN.",
  metrics: [
    { key: "storage_api", label: "Storage API", value: "OFFLINE", unit: "", severity: "WARNING", description: "Frontend belum mendapat data storage monitor dari backend." }
  ],
  dataDistribution: [],
  retentionDistribution: [],
  insights: [
    {
      id: "storage-api-offline",
      severity: "WARNING",
      category: "Storage",
      title: "Storage Monitor API belum terbaca",
      impact: "Kapasitas disk, database footprint, retention, dan growth prediction belum bisa dianalisa dari UI.",
      action: "Jalankan backend, login sebagai SUPERADMIN, lalu refresh halaman Storage Monitor.",
      status: "OPEN"
    }
  ]
};

const snapshotKeys = [
  "telemetry_storage",
  "raw_packet_storage",
  "index_storage",
  "log_storage",
  "backup_storage",
  "daily_growth",
  "weekly_growth",
  "monthly_growth",
  "raw_retention",
  "telemetry_retention"
];


function isActionableSeverity(severity?: string | null) {
  const value = (severity || "NORMAL").toUpperCase();
  return value === "ATTENTION" || value === "WARNING" || value === "CRITICAL" || value === "EMERGENCY";
}

function healthStatusFromScore(score: number, fallbackStatus: string) {
  const status = (fallbackStatus || "NORMAL").toUpperCase();
  if (status === "CRITICAL" || status === "EMERGENCY") return status;
  if (score >= 90) return "NORMAL";
  return status;
}

function buildStorageAttention(insights: StorageMonitorInsight[]) {
  const insight = insights.find((item) => isActionableSeverity(item.severity));
  if (!insight) {
    return {
      status: "NORMAL",
      label: "No Attention",
      title: "Storage stable and safe",
      summary: "Tidak ada finding warning/critical pada disk capacity, retention, growth baseline, atau storage footprint.",
      action: "Lanjutkan monitoring storage dan validasi growth baseline setelah snapshot harian tersedia."
    };
  }
  return {
    status: insight.severity,
    label: "Attention Required",
    title: insight.title,
    summary: insight.impact || "Ada finding storage yang perlu ditinjau.",
    action: insight.action,
    footer: `${insight.category || "Storage"} · ${insight.status || "OPEN"}`
  };
}


function findMetric(metrics: StorageMetric[], key: string) {
  return metrics.find((metric) => metric.key === key);
}

function metricValue(metrics: StorageMetric[], key: string, fallback = "0") {
  return findMetric(metrics, key)?.value ?? fallback;
}

function metricUnit(metrics: StorageMetric[], key: string, fallback = "") {
  return findMetric(metrics, key)?.unit ?? fallback;
}

function metricDisplay(metrics: StorageMetric[], key: string, fallback = "0") {
  const metric = findMetric(metrics, key);
  if (!metric) return fallback;
  const unit = metric.unit ? ` ${metric.unit}` : "";
  return `${metric.value}${unit}`;
}

function metricSeverity(metrics: StorageMetric[], key: string, fallback = "NORMAL") {
  return findMetric(metrics, key)?.severity ?? fallback;
}

function buildTopCards(metrics: StorageMetric[]) {
  return [
    {
      key: "disk_usage",
      label: "Disk Usage",
      value: metricValue(metrics, "disk_usage"),
      unit: "%",
      severity: metricSeverity(metrics, "disk_usage"),
      icon: <Gauge className="h-6 w-6" />,
      note: `${metricDisplay(metrics, "used_disk", "0 B")} used dari ${metricDisplay(metrics, "total_disk", "0 B")} total host backend.`
    },
    {
      key: "free_disk",
      label: "Free Disk",
      value: metricValue(metrics, "free_disk"),
      unit: metricUnit(metrics, "free_disk", "B"),
      severity: metricSeverity(metrics, "free_disk"),
      icon: <PackageOpen className="h-6 w-6" />,
      note: "Free space untuk PostgreSQL, log, backup, dan runtime service."
    },
    {
      key: "database_storage",
      label: "Database Storage",
      value: metricValue(metrics, "database_storage"),
      unit: metricUnit(metrics, "database_storage", "B"),
      severity: metricSeverity(metrics, "database_storage"),
      icon: <Database className="h-6 w-6" />,
      note: "Total footprint PostgreSQL saat ini sebagai baseline growth."
    },
    {
      key: "predicted_full",
      label: "Predicted Full",
      value: metricValue(metrics, "predicted_full", "Waiting"),
      unit: metricUnit(metrics, "predicted_full", ""),
      severity: metricSeverity(metrics, "predicted_full"),
      icon: <TimerReset className="h-6 w-6" />,
      note: "Aktif setelah minimal 7 hari snapshot storage tersedia."
    }
  ];
}

function CompactDistributionPanel({
  title,
  subtitle,
  icon,
  items,
  limit = 5,
  emptyMessage
}: {
  title: string;
  subtitle: string;
  icon: JSX.Element;
  items: StorageDistribution[];
  limit?: number;
  emptyMessage: string;
}) {
  const visibleItems = items.slice(0, limit);
  const max = Math.max(...visibleItems.map((item) => item.count), 1);

  return (
    <Card className="border shadow-sm">
      <CardHeader className="pb-2">
        <CardTitle className="flex items-center gap-2 text-base text-card-foreground">
          {icon}
          {title}
        </CardTitle>
        <p className="text-xs font-semibold leading-5 text-muted-foreground">{subtitle}</p>
      </CardHeader>
      <CardContent className="space-y-2">
        {visibleItems.length ? (
          visibleItems.map((item) => {
            const percent = Math.max(4, Math.round((item.count / max) * 100));
            return (
              <div key={item.name} className="rounded-lg border border-border bg-muted px-3 py-2">
                <div className="mb-1 flex items-center justify-between gap-3">
                  <span className="truncate text-xs font-extrabold text-card-foreground">{item.name}</span>
                  <span className="shrink-0 text-xs font-black text-foreground">{item.displayValue}</span>
                </div>
                <div className="h-1.5 overflow-hidden rounded-full bg-background">
                  <div className="h-full rounded-full bg-emerald-500" style={{ width: `${percent}%` }} />
                </div>
              </div>
            );
          })
        ) : (
          <div className="rounded-lg border border-border bg-muted p-3 text-xs font-semibold text-card-foreground">
            {emptyMessage}
          </div>
        )}
      </CardContent>
    </Card>
  );
}

function compactStatusLabel(status?: string) {
  if (status === "WARNING") return "Needs attention";
  if (status === "CRITICAL" || status === "EMERGENCY") return "Critical";
  return "Healthy";
}

function compactStatusClass(status?: string) {
  if (status === "WARNING") return "text-amber-500";
  if (status === "CRITICAL" || status === "EMERGENCY") return "text-red-500";
  return "text-emerald-500";
}

function CompactStorageSummary({ metrics }: { metrics: StorageMetric[] }) {
  const items = [
    {
      label: "Capacity",
      value: `${metricValue(metrics, "disk_usage")}%`,
      status: metricSeverity(metrics, "disk_usage"),
      detail: `${metricDisplay(metrics, "used_disk", "0 B")} used / ${metricDisplay(metrics, "free_disk", "0 B")} free`,
      icon: <HardDrive className="h-4 w-4" />
    },
    {
      label: "Growth",
      value: metricDisplay(metrics, "daily_growth", "Waiting Baseline"),
      status: metricSeverity(metrics, "daily_growth"),
      detail: `Weekly ${metricDisplay(metrics, "weekly_growth", "Waiting Baseline")} · Monthly ${metricDisplay(metrics, "monthly_growth", "Waiting Baseline")}`,
      icon: <Clock3 className="h-4 w-4" />
    },
    {
      label: "Retention",
      value: "Policy",
      status: metricSeverity(metrics, "raw_retention", "WARNING"),
      detail: `Raw ${metricDisplay(metrics, "raw_retention", "0 policy")} · Telemetry ${metricDisplay(metrics, "telemetry_retention", "0 policy")}`,
      icon: <Archive className="h-4 w-4" />
    },
    {
      label: "Footprint",
      value: metricDisplay(metrics, "database_storage", "0 B"),
      status: metricSeverity(metrics, "database_storage"),
      detail: `Telemetry ${metricDisplay(metrics, "telemetry_storage", "0 B")} · Index ${metricDisplay(metrics, "index_storage", "0 B")}`,
      icon: <Layers className="h-4 w-4" />
    }
  ];

  return (
    <Card className="border shadow-sm">
      <CardHeader className="pb-2">
        <CardTitle className="text-base text-card-foreground">Storage Operational Summary</CardTitle>
        <p className="text-xs font-semibold text-muted-foreground">Ringkasan capacity, growth, retention, dan footprint tanpa mengulang semua metrik teknis.</p>
      </CardHeader>
      <CardContent className="grid gap-2 md:grid-cols-2 xl:grid-cols-4">
        {items.map((item) => (
          <div key={item.label} className="rounded-xl border border-border bg-muted px-3 py-3">
            <div className="flex items-start justify-between gap-3">
              <div>
                <p className="text-xs font-extrabold text-card-foreground">{item.label}</p>
                <p className="mt-1 text-2xl font-black text-card-foreground">{item.value}</p>
                <p className={`mt-1 text-xs font-bold ${compactStatusClass(item.status)}`}>{compactStatusLabel(item.status)}</p>
              </div>
              <span className="grid h-9 w-9 shrink-0 place-items-center rounded-xl bg-card text-emerald-500 ring-1 ring-border">
                {item.icon}
              </span>
            </div>
            <p className="mt-3 truncate text-xs font-semibold text-card-foreground">{item.detail}</p>
          </div>
        ))}
      </CardContent>
    </Card>
  );
}

export function StorageMonitorPage() {
  const { data, isLoading, isError } = useQuery({
    queryKey: ["storage-monitor-overview"],
    queryFn: getStorageMonitorOverview,
    refetchInterval: 15_000,
    staleTime: 10_000
  });

  const overview = data ?? fallbackOverview;
  const topCards = buildTopCards(overview.metrics);
  const snapshotMetrics = overview.metrics.filter((metric) => snapshotKeys.includes(metric.key));

  const insightColumns = useMemo(
    () => [
      {
        key: "severity",
        label: "Severity",
        value: (row: StorageMonitorInsight) => row.severity,
        render: (row: StorageMonitorInsight) => <Badge tone={toneFor(row.severity)} className="px-3 py-1 font-extrabold">{row.severity}</Badge>
      },
      { key: "title", label: "Problem", value: (row: StorageMonitorInsight) => row.title, render: (row: StorageMonitorInsight) => <span className="font-extrabold text-card-foreground">{row.title}</span> },
      { key: "impact", label: "Impact", value: (row: StorageMonitorInsight) => row.impact || "-" },
      { key: "action", label: "Action", value: (row: StorageMonitorInsight) => row.action },
      { key: "status", label: "Status", value: (row: StorageMonitorInsight) => row.status, render: (row: StorageMonitorInsight) => <Badge tone="muted">{row.status}</Badge> }
    ],
    []
  );

  return (
    <section className="space-y-4 text-foreground">
      <MonitorHero
        eyebrow="ALELS STORAGE NOC"
        icon={<HardDrive className="h-4 w-4" />}
        title="Storage Monitor"
        description={
          <>
            Halaman operasi storage untuk memantau disk capacity, PostgreSQL footprint, raw packet growth, telemetry retention,
            backup/log usage, dan prediksi risiko disk penuh sebelum ALELS masuk skala 1 juta device.
          </>
        }
        statusLabel="Storage Status"
        status={healthStatusFromScore(overview.storageScore, overview.status)}
        scoreLabel="Storage Score"
        score={overview.storageScore}
        updatedAt={overview.generatedAt}
        trendLabel="Baseline learning"
        attention={buildStorageAttention(overview.insights)}
      />

      <MonitorKpiGrid cards={topCards} />

      <div className="grid gap-4 xl:grid-cols-[1.05fr_1fr_1fr]">
        <MonitorAssessmentCard
          title="Storage Assessment"
          icon={overview.status === "NORMAL" ? <CheckCircle2 className="h-5 w-5 text-emerald-600" /> : <AlertTriangle className="h-5 w-5 text-amber-600" />}
          statusTitle={overview.summary}
          statusDetail="Assessment fokus pada disk capacity, PostgreSQL footprint, raw packet/telemetry growth, backup/log size, dan retention readiness."
          impact="Jika storage penuh, PostgreSQL insert, gateway log, backup, dan telemetry processing bisa berhenti bersamaan."
          action={overview.recommendation}
          severity={overview.status}
        />

        <CompactDistributionPanel
          title="Storage Distribution"
          subtitle="Top footprint utama. Detail lengkap tetap tersedia di snapshot teknis."
          icon={<Layers className="h-5 w-5" />}
          items={overview.dataDistribution}
          limit={5}
          emptyMessage="Belum ada data storage distribution atau permission monitoring belum tersedia."
        />

        <CompactDistributionPanel
          title="Retention Distribution"
          subtitle="Area data yang perlu retention/archive policy sebelum traffic device besar."
          icon={<Archive className="h-5 w-5" />}
          items={overview.retentionDistribution}
          limit={4}
          emptyMessage="Belum ada data retention distribution."
        />
      </div>

      <CompactStorageSummary metrics={overview.metrics} />

      <MonitorSnapshot
        title="Storage Technical Snapshot"
        description="Metrik teknis prioritas tanpa mengulang KPI utama, agar halaman tetap efektif seperti submenu monitor lain."
        fallbackIcon={<HardDrive className="h-4 w-4" />}
        items={snapshotMetrics.map((metric) => ({
          key: metric.key,
          label: metric.label,
          value: metric.value,
          unit: metric.unit,
          icon: metricIcons[metric.key]
        }))}
      />

      <MonitorFindingsTable
        title="Storage Findings & Actions"
        description="Masalah storage yang perlu diprioritaskan sebelum telemetry dan raw packet bertumbuh besar."
        status={overview.status}
        findings={overview.insights}
        columns={insightColumns}
        rowKey={(row) => row.id}
        searchPlaceholder="Search storage finding..."
        emptyMessage="No storage findings."
        isLoading={isLoading}
        errorMessage={isError ? "Backend Storage Monitor belum terbaca. Fallback UI ditampilkan." : null}
      />
    </section>
  );
}
