import { useMemo } from "react";
import { useQuery } from "@tanstack/react-query";
import {
  Activity,
  AlertTriangle,
  BrainCircuit,
  Database,
  Gauge,
  HardDrive,
  Network,
  ServerCog,
  ShieldCheck,
  Wifi
} from "lucide-react";

import { Badge } from "@/components/ui/badge";
import { StatusIndicator } from "@/components/ui/status-indicator";
import {
  getOverviewMonitorOverview,
  type OverviewMetric,
  type OverviewMonitorInsight,
  type OverviewMonitorModule
} from "@/lib/api";
import {
  MonitorAssessmentCard,
  MonitorDistributionBars,
  MonitorFindingsTable,
  MonitorKpiGrid,
  MonitorSnapshot,
  toneFor
} from "@/features/server-monitor/components/server-monitor-ui";

const moduleIcons: Record<string, JSX.Element> = {
  ai_ops: <BrainCircuit className="h-4 w-4" />,
  gateway: <Wifi className="h-4 w-4" />,
  traffic: <Activity className="h-4 w-4" />,
  database: <Database className="h-4 w-4" />,
  storage: <HardDrive className="h-4 w-4" />,
  security: <ShieldCheck className="h-4 w-4" />
};

const fallbackOverview = {
  status: "WARNING",
  overviewScore: 70,
  generatedAt: null,
  summary: "Overview Monitor API belum tersedia.",
  recommendation: "Jalankan backend dan pastikan endpoint Overview Monitor tersedia untuk SUPERADMIN.",
  metrics: [
    { key: "overview_api", label: "Overview API", value: "OFFLINE", unit: "", severity: "WARNING", description: "Frontend belum mendapat data Overview Monitor dari backend." }
  ],
  modules: [],
  healthDistribution: [],
  priorityDistribution: [],
  insights: [
    {
      id: "overview-api-offline",
      severity: "WARNING",
      category: "Overview",
      title: "Overview Monitor API belum terbaca",
      impact: "Status AI Ops, Gateway, Traffic, Database, Storage, dan Security belum bisa diagregasi.",
      action: "Jalankan backend, login sebagai SUPERADMIN, lalu refresh halaman Overview Monitor.",
      status: "OPEN"
    }
  ]
};

const topMetricKeys = ["overview_score", "normal_modules", "attention_modules", "critical_modules"];
const platformMetricKeys = ["total_devices", "online_devices", "active_users", "security_foundation", "open_alerts", "warning_findings"];

function findMetric(metrics: OverviewMetric[], key: string) {
  return metrics.find((metric) => metric.key === key);
}

function metricValue(metrics: OverviewMetric[], key: string, fallback = "0") {
  return findMetric(metrics, key)?.value ?? fallback;
}

function metricNumber(metrics: OverviewMetric[], key: string) {
  return Number(metricValue(metrics, key, "0")) || 0;
}

function formatUpdatedAt(value?: string | null) {
  return value ? new Date(value).toLocaleString() : "Waiting for live backend data";
}

function buildTopCards(metrics: OverviewMetric[]) {
  return topMetricKeys.map((key) => {
    const metric = findMetric(metrics, key);
    return {
      key,
      label: metric?.label ?? key,
      value: metric?.value ?? "0",
      unit: metric?.unit ?? "",
      severity: metric?.severity ?? "NORMAL",
      icon:
        key === "overview_score" ? <Gauge className="h-6 w-6" /> :
        key === "critical_modules" ? <AlertTriangle className="h-6 w-6" /> :
        key === "normal_modules" ? <ShieldCheck className="h-6 w-6" /> :
        <ServerCog className="h-6 w-6" />,
      note: metric?.description ?? "Overview metric."
    };
  });
}

function moduleSnapshotItems(modules: OverviewMonitorModule[]) {
  return modules.map((module) => ({
    key: module.key,
    label: module.label,
    value: String(module.score),
    unit: `% ${module.status === "WARNING" ? "ATTENTION" : module.status}`,
    icon: moduleIcons[module.key] ?? <ServerCog className="h-4 w-4" />
  }));
}

function metricSnapshotItems(metrics: OverviewMetric[]) {
  return platformMetricKeys.map((key) => {
    const metric = findMetric(metrics, key);
    return {
      key,
      label: metric?.label ?? key,
      value: metric?.value ?? "0",
      unit: metric?.unit,
      icon:
        key === "security_foundation" ? <ShieldCheck className="h-4 w-4" /> :
        key === "online_devices" ? <Wifi className="h-4 w-4" /> :
        key === "warning_findings" ? <AlertTriangle className="h-4 w-4" /> :
        <Activity className="h-4 w-4" />
    };
  });
}

function healthTone(status: string) {
  const normalized = status?.toUpperCase?.() ?? "NORMAL";
  if (normalized === "CRITICAL" || normalized === "EMERGENCY") {
    return {
      box: "border-red-400 bg-red-50 text-red-950 dark:border-red-700 dark:bg-red-950/40 dark:text-red-100",
      dot: "bg-red-500",
      badge: "bg-red-100 text-red-800 dark:bg-red-900/70 dark:text-red-100",
      label: "Critical issue"
    };
  }
  if (normalized === "WARNING") {
    return {
      box: "border-amber-300 bg-amber-50 text-amber-950 dark:border-amber-700 dark:bg-amber-950/40 dark:text-amber-100",
      dot: "bg-amber-500",
      badge: "bg-amber-100 text-amber-800 dark:bg-amber-900/70 dark:text-amber-100",
      label: "Needs technical check"
    };
  }
  return {
    box: "border-emerald-300 bg-emerald-50 text-emerald-950 dark:border-emerald-700 dark:bg-emerald-950/40 dark:text-emerald-100",
    dot: "bg-emerald-500",
    badge: "bg-emerald-100 text-emerald-800 dark:bg-emerald-900/70 dark:text-emerald-100",
    label: "System stable"
  };
}

function attentionTone(hasAttention: boolean, hasCritical: boolean) {
  if (hasCritical) {
    return {
      box: "border-red-400 bg-red-50 text-red-950 dark:border-red-700 dark:bg-red-950/40 dark:text-red-100",
      dot: "bg-red-500",
      badge: "bg-red-100 text-red-800 dark:bg-red-900/70 dark:text-red-100",
      title: "Critical Attention"
    };
  }
  if (hasAttention) {
    return {
      box: "border-amber-300 bg-amber-50 text-amber-950 dark:border-amber-700 dark:bg-amber-950/40 dark:text-amber-100",
      dot: "bg-amber-500",
      badge: "bg-amber-100 text-amber-800 dark:bg-amber-900/70 dark:text-amber-100",
      title: "Attention Required"
    };
  }
  return {
    box: "border-emerald-300 bg-emerald-50 text-emerald-950 dark:border-emerald-700 dark:bg-emerald-950/40 dark:text-emerald-100",
    dot: "bg-emerald-500",
    badge: "bg-emerald-100 text-emerald-800 dark:bg-emerald-900/70 dark:text-emerald-100",
    title: "No Attention"
  };
}

type OverviewHeroProps = {
  status: string;
  score: number;
  updatedAt?: string | null;
  securityFoundation: string;
  attentionModules: number;
  warningFindings: number;
  criticalModules: number;
  firstInsight?: OverviewMonitorInsight;
};

function OverviewHero({ status, score, updatedAt, securityFoundation, attentionModules, warningFindings, criticalModules, firstInsight }: OverviewHeroProps) {
  const health = healthTone(status);
  const hasCritical = criticalModules > 0;
  const hasAttention = hasCritical || attentionModules > 0 || warningFindings > 0;
  const attention = attentionTone(hasAttention, hasCritical);

  return (
    <div className="rounded-2xl border border-border bg-card p-5 shadow-sm lg:p-6">
      <div className="grid gap-5 xl:grid-cols-[minmax(0,1fr)_260px_300px]">
        <div className="flex min-w-0 flex-col justify-center">
          <div className="mb-3 inline-flex w-fit items-center gap-2 rounded-full bg-sky-50 px-3 py-1 text-xs font-extrabold uppercase tracking-wide text-sky-700 dark:bg-sky-950/40 dark:text-sky-200">
            <ServerCog className="h-4 w-4" />
            ALELS OVERVIEW NOC
          </div>
          <h1 className="text-3xl font-black tracking-tight text-foreground">Overview Monitor</h1>
          <p className="mt-3 max-w-4xl text-sm font-medium leading-6 text-muted-foreground">
            Halaman Executive NOC untuk menggabungkan status AI Ops, Gateway, Traffic, Database, Storage, dan Security sebelum ALELS masuk skala 1 juta device aktif.
          </p>
        </div>

        <div className={`relative rounded-2xl border p-4 shadow-sm ${health.box}`}>
          <span className={`absolute right-4 top-4 h-3 w-3 rounded-full ${health.dot}`} />
          <div className="text-xs font-black uppercase tracking-wide">Overview Status</div>
          <div className="mt-3 flex items-center justify-between gap-3">
            <div>
              <div className={`inline-flex rounded-md px-2 py-1 text-xs font-black ${health.badge}`}>{status}</div>
              <div className="mt-3 text-sm font-black">{health.label}</div>
              <div className="text-xs font-bold opacity-80">Trend: {securityFoundation === "READY" ? "Foundation ready" : "Baseline mode"}</div>
            </div>
            <div className="text-right">
              <div className="text-xs font-black uppercase tracking-wide opacity-80">Overview Score</div>
              <div className="text-5xl font-black leading-none tracking-tight">{score}%</div>
            </div>
          </div>
          <div className="mt-3 text-xs font-bold opacity-80">Updated: {formatUpdatedAt(updatedAt)}</div>
        </div>

        <div className={`relative rounded-2xl border p-4 shadow-sm ${attention.box}`}>
          <span className={`absolute right-4 top-4 h-3 w-3 rounded-full ${attention.dot}`} />
          <div className="text-xs font-black uppercase tracking-wide">Attention Details</div>
          <div className={`mt-3 inline-flex rounded-md px-2 py-1 text-xs font-black ${attention.badge}`}>{attention.title}</div>
          {hasAttention ? (
            <div className="mt-3 space-y-2 text-sm font-bold leading-5">
              <div>{firstInsight ? `${firstInsight.category}: ${firstInsight.title}` : `${attentionModules} module perlu review.`}</div>
              <div className="text-xs font-semibold opacity-80">{firstInsight?.action ?? "Review module attention dan selesaikan sebelum traffic device/user meningkat."}</div>
            </div>
          ) : (
            <div className="mt-3 space-y-2 text-sm font-bold leading-5">
              <div>System stable, secure, and safe.</div>
              <div className="text-xs font-semibold opacity-80">Tidak ada attention atau critical action lintas domain.</div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

export function OverviewMonitorPage() {
  const { data, isLoading, isError } = useQuery({
    queryKey: ["overview-monitor"],
    queryFn: getOverviewMonitorOverview,
    refetchInterval: 15_000,
    staleTime: 10_000
  });

  const overview = data ?? fallbackOverview;
  const topCards = buildTopCards(overview.metrics);
  const warningFindings = metricValue(overview.metrics, "warning_findings", String(overview.insights.length));
  const attentionModules = metricNumber(overview.metrics, "attention_modules");
  const criticalModules = metricNumber(overview.metrics, "critical_modules");
  const firstActionableInsight = overview.insights.find((insight) => ["ATTENTION", "WARNING", "CRITICAL", "EMERGENCY"].includes(insight.severity?.toUpperCase?.() ?? ""));
  const insightColumns = useMemo(
    () => [
      {
        key: "severity",
        label: "Severity",
        value: (row: OverviewMonitorInsight) => row.severity,
        render: (row: OverviewMonitorInsight) => <Badge tone={toneFor(row.severity)} className="px-3 py-1 font-extrabold">{row.severity}</Badge>
      },
      { key: "category", label: "Domain", value: (row: OverviewMonitorInsight) => row.category },
      { key: "title", label: "Problem", value: (row: OverviewMonitorInsight) => row.title, render: (row: OverviewMonitorInsight) => <span className="font-extrabold text-foreground">{row.title}</span> },
      { key: "impact", label: "Impact", value: (row: OverviewMonitorInsight) => row.impact || "-" },
      { key: "action", label: "Action", value: (row: OverviewMonitorInsight) => row.action },
      { key: "status", label: "Status", value: (row: OverviewMonitorInsight) => row.status, render: (row: OverviewMonitorInsight) => <StatusIndicator status={row.status} /> }
    ],
    []
  );

  return (
    <section className="space-y-5 text-foreground">
      <OverviewHero
        status={overview.status}
        score={overview.overviewScore}
        updatedAt={overview.generatedAt}
        securityFoundation={metricValue(overview.metrics, "security_foundation", "BASELINE")}
        attentionModules={attentionModules}
        warningFindings={Number(warningFindings) || 0}
        criticalModules={criticalModules}
        firstInsight={firstActionableInsight}
      />

      <MonitorKpiGrid cards={topCards} />

      <div className="grid gap-5 xl:grid-cols-[1.1fr_0.9fr_1fr]">
        <MonitorAssessmentCard
          title="Executive Assessment"
          icon={overview.status === "NORMAL" ? <ShieldCheck className="h-5 w-5 text-emerald-500" /> : <AlertTriangle className="h-5 w-5 text-amber-500" />}
          statusTitle={overview.summary}
          statusDetail="Fokus NOC: gateway ingestion, traffic parser, PostgreSQL, storage capacity, security event, dan AI Ops action."
          impact={criticalModules > 0 ? "Ada domain critical yang harus ditangani segera." : attentionModules > 0 || Number(warningFindings) > 0 ? "Server stabil, namun ada attention operasional yang perlu diarahkan ke tim teknikal." : "Seluruh domain utama berada dalam threshold awal dan siap menjadi NOC entry point."}
          action={overview.recommendation}
          severity={criticalModules > 0 ? "CRITICAL" : attentionModules > 0 || Number(warningFindings) > 0 ? "ATTENTION" : overview.status}
        />
        <MonitorDistributionBars
          title="Health Distribution"
          subtitle="Jumlah modul normal, attention, dan critical."
          icon={<Gauge className="h-5 w-5" />}
          items={overview.healthDistribution}
          emptyMessage="Belum ada health distribution."
        />
        <MonitorDistributionBars
          title="Module Scores"
          subtitle="Skor domain utama untuk prioritas operasi NOC."
          icon={<Network className="h-5 w-5" />}
          items={overview.priorityDistribution}
          emptyMessage="Belum ada module score."
        />
      </div>

      <MonitorSnapshot
        title="Operations Snapshot"
        description="Gabungan domain score dan metrik platform prioritas agar halaman tetap padat dan tidak perlu 3 kali scroll."
        fallbackIcon={<ServerCog className="h-4 w-4" />}
        items={[...moduleSnapshotItems(overview.modules), ...metricSnapshotItems(overview.metrics)]}
      />

      <MonitorFindingsTable
        title="Overview Actions"
        description={`Hanya menampilkan warning/critical/attention lintas domain. Warning finding aktif: ${warningFindings}.`}
        status={criticalModules > 0 ? "CRITICAL" : attentionModules > 0 || Number(warningFindings) > 0 ? "ATTENTION" : overview.status}
        findings={overview.insights}
        columns={insightColumns}
        rowKey={(row) => row.id}
        searchPlaceholder="Search overview action..."
        emptyMessage="Tidak ada warning atau critical action lintas domain."
        isLoading={isLoading}
        errorMessage={isError ? "Overview Monitor API belum tersedia atau gagal mengambil data." : null}
      />
    </section>
  );
}
