import { useMemo } from "react";
import { useQuery } from "@tanstack/react-query";
import {
  AlertTriangle,
  CheckCircle2,
  Database,
  Gauge,
  HardDrive,
  KeyRound,
  Layers,
  Lock,
  Network,
  Search,
  Server,
  TimerReset,
  Workflow
} from "lucide-react";

import { Badge } from "@/components/ui/badge";
import { getDatabaseMonitorOverview, type DatabaseMetric, type DatabaseMonitorInsight } from "@/lib/api";
import {
  MonitorAssessmentCard,
  MonitorDistributionBars,
  MonitorDomainGrid,
  MonitorHero,
  MonitorKpiGrid,
  MonitorSnapshot,
  MonitorFindingsTable,
  toneFor
} from "@/features/server-monitor/components/server-monitor-ui";

const metricIcons: Record<string, JSX.Element> = {
  active_connections: <Network className="h-5 w-5" />,
  total_connections: <Server className="h-5 w-5" />,
  connection_usage: <Gauge className="h-5 w-5" />,
  database_size: <HardDrive className="h-5 w-5" />,
  slow_queries: <Search className="h-5 w-5" />,
  blocked_locks: <Lock className="h-5 w-5" />,
  dead_tuples: <Layers className="h-5 w-5" />,
  oldest_vacuum_hours: <TimerReset className="h-5 w-5" />,
  table_count: <Database className="h-5 w-5" />,
  index_size: <KeyRound className="h-5 w-5" />
};

const fallbackOverview = {
  status: "WARNING",
  databaseScore: 70,
  generatedAt: null,
  summary: "Database Monitor API belum tersedia atau backend belum berjalan.",
  recommendation: "Jalankan backend dan pastikan endpoint /api/server-monitor/database/overview tersedia untuk SUPERADMIN.",
  metrics: [
    { key: "database_api", label: "Database API", value: "OFFLINE", unit: "", severity: "WARNING", description: "Frontend belum mendapat data database monitor dari backend." }
  ],
  tableDistribution: [],
  indexDistribution: [],
  insights: [
    {
      id: "database-api-offline",
      severity: "WARNING",
      category: "Database",
      title: "Database Monitor API belum terbaca",
      impact: "Health PostgreSQL belum bisa dianalisa dari UI.",
      action: "Jalankan backend, login sebagai SUPERADMIN, lalu refresh halaman Database Monitor.",
      status: "OPEN"
    }
  ]
};


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

function buildDatabaseAttention(insights: DatabaseMonitorInsight[]) {
  const insight = insights.find((item) => isActionableSeverity(item.severity));
  if (!insight) {
    return {
      status: "NORMAL",
      label: "No Attention",
      title: "Database stable and safe",
      summary: "Tidak ada finding warning/critical pada PostgreSQL connection, query, lock, vacuum, atau index.",
      action: "Lanjutkan monitoring dan aktifkan baseline latency/table growth saat traffic bertambah."
    };
  }
  return {
    status: insight.severity,
    label: "Attention Required",
    title: insight.title,
    summary: insight.impact || "Ada finding database yang perlu ditinjau.",
    action: insight.action,
    footer: `${insight.category || "Database"} · ${insight.status || "OPEN"}`
  };
}


function findMetric(metrics: DatabaseMetric[], key: string) {
  return metrics.find((metric) => metric.key === key);
}

function metricValue(metrics: DatabaseMetric[], key: string, fallback = "0") {
  return findMetric(metrics, key)?.value ?? fallback;
}

function metricUnit(metrics: DatabaseMetric[], key: string, fallback = "") {
  return findMetric(metrics, key)?.unit ?? fallback;
}

function metricDisplay(metrics: DatabaseMetric[], key: string, fallback = "0") {
  const metric = findMetric(metrics, key);
  if (!metric) return fallback;
  const unit = metric.unit ? ` ${metric.unit}` : "";
  return `${metric.value}${unit}`;
}

function metricSeverity(metrics: DatabaseMetric[], key: string, fallback = "NORMAL") {
  return findMetric(metrics, key)?.severity ?? fallback;
}

function buildTopCards(metrics: DatabaseMetric[]) {
  return [
    {
      key: "total_connections",
      label: "DB Connections",
      value: metricValue(metrics, "total_connections"),
      unit: "conn",
      severity: metricSeverity(metrics, "connection_usage"),
      icon: <Network className="h-6 w-6" />,
      note: `${metricValue(metrics, "active_connections")} active, ${metricValue(metrics, "idle_connections")} idle, max ${metricValue(metrics, "max_connections")}. Batas max_connections dipantau.`
    },
    {
      key: "database_size",
      label: "Database Size",
      value: metricValue(metrics, "database_size"),
      unit: metricUnit(metrics, "database_size", "B"),
      severity: metricSeverity(metrics, "database_size"),
      icon: <HardDrive className="h-6 w-6" />,
      note: "Ukuran total database saat ini untuk baseline growth."
    },
    {
      key: "slow_queries",
      label: "Slow Queries",
      value: metricValue(metrics, "slow_queries"),
      unit: "query",
      severity: metricSeverity(metrics, "slow_queries"),
      icon: <Search className="h-6 w-6" />,
      note: "Query aktif lebih dari 2 detik yang berisiko menahan koneksi."
    },
    {
      key: "blocked_locks",
      label: "Blocked Locks",
      value: metricValue(metrics, "blocked_locks"),
      unit: "lock",
      severity: metricSeverity(metrics, "blocked_locks"),
      icon: <Lock className="h-6 w-6" />,
      note: "Lock yang belum granted dan bisa menghambat write/read penting."
    }
  ];
}

function buildDatabaseDomains(metrics: DatabaseMetric[]) {
  return [
    {
      label: "Connection Pool",
      value: `${metricValue(metrics, "connection_usage")}%`,
      severity: metricSeverity(metrics, "connection_usage"),
      icon: <Workflow className="h-5 w-5" />,
      details: [
        `${metricValue(metrics, "active_connections")} active`,
        `${metricValue(metrics, "idle_connections")} idle`,
        `${metricValue(metrics, "total_connections")}/${metricValue(metrics, "max_connections")} total/max connection`,
        "Pool backend/gateway harus dibatasi"
      ]
    },
    {
      label: "Query Performance",
      value: `${metricValue(metrics, "slow_queries")} slow`,
      severity: metricSeverity(metrics, "slow_queries"),
      icon: <Search className="h-5 w-5" />,
      details: ["Threshold awal: >2 detik", "Pagination server-side wajib", "Index perlu dipantau per table besar"]
    },
    {
      label: "Storage Growth",
      value: metricDisplay(metrics, "database_size", "0 B"),
      severity: metricSeverity(metrics, "database_size"),
      icon: <HardDrive className="h-5 w-5" />,
      details: [`Index ${metricDisplay(metrics, "index_size", "0 B")}`, `${metricValue(metrics, "table_count")} table`, "Retention raw/telemetry tahap berikutnya"]
    },
    {
      label: "Vacuum & Locks",
      value: `${metricValue(metrics, "blocked_locks")} lock`,
      severity: metricSeverity(metrics, "blocked_locks"),
      icon: <TimerReset className="h-5 w-5" />,
      details: [`${metricValue(metrics, "dead_tuples")} dead tuple`, `Vacuum age ${metricValue(metrics, "oldest_vacuum_hours")} hour`, "Autovacuum wajib diawasi"]
    }
  ];
}

export function DatabaseMonitorPage() {
  const { data, isLoading, isError } = useQuery({
    queryKey: ["database-monitor-overview"],
    queryFn: getDatabaseMonitorOverview,
    refetchInterval: 15_000,
    staleTime: 10_000
  });

  const overview = data ?? fallbackOverview;
  const topCards = buildTopCards(overview.metrics);
  const databaseDomains = buildDatabaseDomains(overview.metrics);

  const insightColumns = useMemo(
    () => [
      {
        key: "severity",
        label: "Severity",
        value: (row: DatabaseMonitorInsight) => row.severity,
        render: (row: DatabaseMonitorInsight) => <Badge tone={toneFor(row.severity)} className="px-3 py-1 font-extrabold">{row.severity}</Badge>
      },
      { key: "title", label: "Problem", value: (row: DatabaseMonitorInsight) => row.title, render: (row: DatabaseMonitorInsight) => <span className="font-extrabold text-card-foreground">{row.title}</span> },
      { key: "impact", label: "Impact", value: (row: DatabaseMonitorInsight) => row.impact || "-" },
      { key: "action", label: "Action", value: (row: DatabaseMonitorInsight) => row.action },
      { key: "status", label: "Status", value: (row: DatabaseMonitorInsight) => row.status, render: (row: DatabaseMonitorInsight) => <Badge tone="muted">{row.status}</Badge> }
    ],
    []
  );

  return (
    <section className="space-y-5 text-foreground">
      <MonitorHero
        eyebrow="ALELS DATABASE NOC"
        icon={<Database className="h-4 w-4" />}
        title="Database Monitor"
        description={
          <>
            Halaman operasi PostgreSQL untuk memantau connection, size growth, slow query, lock, vacuum, index, dan risiko bottleneck
            sebelum telemetry dan user aktif bertambah besar.
          </>
        }
        statusLabel="Database Status"
        status={healthStatusFromScore(overview.databaseScore, overview.status)}
        scoreLabel="Database Score"
        score={overview.databaseScore}
        updatedAt={overview.generatedAt}
        trendLabel="Baseline learning"
        attention={buildDatabaseAttention(overview.insights)}
      />

      <MonitorKpiGrid cards={topCards} />

      <div className="grid gap-4 xl:grid-cols-[1.15fr_1fr_1fr]">
        <MonitorAssessmentCard
          title="Database Assessment"
          icon={overview.status === "NORMAL" ? <CheckCircle2 className="h-5 w-5 text-emerald-600" /> : <AlertTriangle className="h-5 w-5 text-amber-600" />}
          statusTitle={overview.summary}
          statusDetail="Assessment fokus pada connection pool, slow query, lock, vacuum, dead tuple, table/index growth, dan kesiapan PostgreSQL menerima beban telemetry."
          impact="Database adalah jalur kritis setelah gateway dan traffic. Jika connection, lock, atau slow query naik, API dan ingestion bisa melambat bersamaan."
          action={overview.recommendation}
          severity={overview.status}
        />

        <MonitorDistributionBars
          title="Largest Tables"
          subtitle="Top table public berdasarkan total relation size. Unit otomatis KB/MB/GB agar table kecil tidak tampil 0 MB."
          icon={<Layers className="h-5 w-5" />}
          items={overview.tableDistribution}
          emptyMessage="Belum ada data ukuran table atau permission monitoring belum tersedia."
        />

        <MonitorDistributionBars
          title="Largest Indexes"
          subtitle="Top index public berdasarkan relation size. Unit otomatis KB/MB/GB agar index kecil tetap terbaca."
          icon={<KeyRound className="h-5 w-5" />}
          items={overview.indexDistribution}
          emptyMessage="Belum ada data ukuran index atau index masih kecil."
        />
      </div>

      <MonitorDomainGrid cards={databaseDomains} />

      <MonitorSnapshot
        title="Database Technical Snapshot"
        description="Snapshot kecil untuk verifikasi angka teknis. Detail besar seperti slow query list dapat dibuat pada tahap berikutnya."
        fallbackIcon={<Database className="h-4 w-4" />}
        items={overview.metrics.map((metric) => ({
          key: metric.key,
          label: metric.label,
          value: metric.value,
          unit: metric.unit,
          icon: metricIcons[metric.key]
        }))}
      />

      <MonitorFindingsTable
        title="Database Findings & Actions"
        description="Masalah database yang perlu diprioritaskan sebelum traffic device bertambah besar."
        status={overview.status}
        findings={overview.insights}
        columns={insightColumns}
        rowKey={(row) => row.id}
        searchPlaceholder="Search database finding..."
        emptyMessage="No database findings."
        isLoading={isLoading}
        errorMessage={isError ? "Backend Database Monitor belum terbaca. Fallback UI ditampilkan." : null}
      />
    </section>
  );
}
