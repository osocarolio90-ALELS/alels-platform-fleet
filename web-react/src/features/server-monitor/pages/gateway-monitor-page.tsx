import { useMemo } from "react";
import { useQuery } from "@tanstack/react-query";
import {
  Activity,
  AlertTriangle,
  CheckCircle2,
  Clock3,
  Network,
  RadioTower,
  Route,
  ServerCog,
  ShieldAlert,
  Wifi,
  WifiOff
} from "lucide-react";

import { Badge } from "@/components/ui/badge";
import { StatusIndicator } from "@/components/ui/status-indicator";
import { getGatewayMonitorOverview, type GatewayMonitorInsight, type GatewayMetric } from "@/lib/api";
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
  total_devices: <Network className="h-5 w-5" />,
  online_devices: <Wifi className="h-5 w-5" />,
  offline_devices: <WifiOff className="h-5 w-5" />,
  gsm_connected: <RadioTower className="h-5 w-5" />,
  wifi_connected: <Wifi className="h-5 w-5" />,
  telemetry_minute: <Activity className="h-5 w-5" />,
  invalid_packets_hour: <ShieldAlert className="h-5 w-5" />,
  stale_devices: <Clock3 className="h-5 w-5" />
};

const fallbackOverview = {
  status: "WARNING",
  gatewayScore: 70,
  generatedAt: null,
  summary: "Gateway Monitor API belum tersedia atau backend belum berjalan.",
  recommendation: "Jalankan backend dan pastikan endpoint /api/server-monitor/gateway/overview tersedia untuk SUPERADMIN.",
  metrics: [
    { key: "gateway_api", label: "Gateway API", value: "OFFLINE", unit: "", severity: "WARNING", description: "Frontend belum mendapat data gateway dari backend." }
  ],
  protocolDistribution: [],
  channelDistribution: [],
  insights: [
    {
      id: "gateway-api-offline",
      severity: "WARNING",
      category: "Gateway",
      title: "Gateway Monitor API belum terbaca",
      impact: "Data koneksi device belum bisa dianalisa dari UI.",
      action: "Jalankan backend, login sebagai SUPERADMIN, lalu refresh halaman Gateway Monitor.",
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

function buildGatewayAttention(insights: GatewayMonitorInsight[]) {
  const insight = insights.find((item) => isActionableSeverity(item.severity));
  if (!insight) {
    return {
      status: "NORMAL",
      label: "No Attention",
      title: "Gateway stable and safe",
      summary: "Tidak ada finding warning/critical pada koneksi gateway, route, atau packet gateway.",
      action: "Lanjutkan monitoring dan validasi baseline setelah device aktif bertambah."
    };
  }
  return {
    status: insight.severity,
    label: "Attention Required",
    title: insight.title,
    summary: insight.impact || "Ada finding gateway yang perlu ditinjau.",
    action: insight.action,
    footer: `${insight.category || "Gateway"} · ${insight.status || "OPEN"}`
  };
}


function findMetric(metrics: GatewayMetric[], key: string) {
  return metrics.find((metric) => metric.key === key);
}

function metricValue(metrics: GatewayMetric[], key: string, fallback = "0") {
  return findMetric(metrics, key)?.value ?? fallback;
}

function metricSeverity(metrics: GatewayMetric[], key: string, fallback = "NORMAL") {
  return findMetric(metrics, key)?.severity ?? fallback;
}

function buildTopCards(metrics: GatewayMetric[]) {
  return [
    {
      key: "online_devices",
      label: "Online Devices",
      value: metricValue(metrics, "online_devices"),
      unit: "device",
      severity: metricSeverity(metrics, "online_devices"),
      icon: <Wifi className="h-6 w-6" />,
      note: "Presence ONLINE atau online=true."
    },
    {
      key: "offline_devices",
      label: "Offline Devices",
      value: metricValue(metrics, "offline_devices"),
      unit: "device",
      severity: metricSeverity(metrics, "offline_devices"),
      icon: <WifiOff className="h-6 w-6" />,
      note: "Device melewati threshold offline 7 menit."
    },
    {
      key: "telemetry_minute",
      label: "Packet / Minute",
      value: metricValue(metrics, "telemetry_minute"),
      unit: "pkt/min",
      severity: metricSeverity(metrics, "telemetry_minute"),
      icon: <RadioTower className="h-6 w-6" />,
      note: "Telemetry yang diterima backend 1 menit terakhir."
    },
    {
      key: "invalid_packets_hour",
      label: "Invalid Packet",
      value: metricValue(metrics, "invalid_packets_hour"),
      unit: "pkt/hour",
      severity: metricSeverity(metrics, "invalid_packets_hour"),
      icon: <ShieldAlert className="h-6 w-6" />,
      note: "Packet parse_status bukan VALID."
    }
  ];
}

function buildGatewayIndicators(metrics: GatewayMetric[]) {
  return [
    {
      label: "Device Inventory",
      value: metricValue(metrics, "total_devices"),
      severity: metricSeverity(metrics, "total_devices"),
      icon: metricIcons.total_devices,
      details: ["Total device terdaftar", `Online ${metricValue(metrics, "online_devices")}`, `Offline ${metricValue(metrics, "offline_devices")}`]
    },
    {
      label: "Channel Sessions",
      value: `${metricValue(metrics, "gsm_connected")} / ${metricValue(metrics, "wifi_connected")}`,
      severity: "NORMAL",
      icon: <RadioTower className="h-5 w-5" />,
      details: [`GSM ${metricValue(metrics, "gsm_connected")} session`, `WiFi ${metricValue(metrics, "wifi_connected")} session`, "Route command mengikuti active channel"]
    },
    {
      label: "Traffic Quality",
      value: `${metricValue(metrics, "telemetry_minute")}`,
      severity: metricSeverity(metrics, "telemetry_minute"),
      icon: metricIcons.telemetry_minute,
      details: [`${metricValue(metrics, "telemetry_minute")} pkt/min`, `${metricValue(metrics, "invalid_packets_hour")} invalid/hour`, "Duplicate IMEI baseline: learning"]
    },
    {
      label: "Presence Risk",
      value: metricValue(metrics, "stale_devices"),
      severity: metricSeverity(metrics, "stale_devices"),
      icon: metricIcons.stale_devices,
      details: [`${metricValue(metrics, "stale_devices")} stale device`, "Offline threshold 420 detik", "Siap untuk presence monitor 7 menit"]
    }
  ];
}

export function GatewayMonitorPage() {
  const { data, isLoading, isError } = useQuery({
    queryKey: ["gateway-monitor-overview"],
    queryFn: getGatewayMonitorOverview,
    refetchInterval: 15_000,
    staleTime: 10_000
  });

  const overview = data ?? fallbackOverview;
  const topCards = buildTopCards(overview.metrics);
  const gatewayIndicators = buildGatewayIndicators(overview.metrics);

  const insightColumns = useMemo(
    () => [
      {
        key: "severity",
        label: "Severity",
        value: (row: GatewayMonitorInsight) => row.severity,
        render: (row: GatewayMonitorInsight) => <Badge tone={toneFor(row.severity)} className="px-3 py-1 font-extrabold">{row.severity}</Badge>
      },
      { key: "title", label: "Problem", value: (row: GatewayMonitorInsight) => row.title, render: (row: GatewayMonitorInsight) => <span className="font-extrabold text-card-foreground">{row.title}</span> },
      { key: "impact", label: "Impact", value: (row: GatewayMonitorInsight) => row.impact || "-" },
      { key: "action", label: "Action", value: (row: GatewayMonitorInsight) => row.action },
      { key: "status", label: "Status", value: (row: GatewayMonitorInsight) => row.status, render: (row: GatewayMonitorInsight) => <StatusIndicator status={row.status} /> }
    ],
    []
  );

  return (
    <section className="server-operations-ui space-y-5 text-foreground">
      <MonitorHero
        eyebrow="ALELS GATEWAY NOC"
        icon={<ServerCog className="h-4 w-4" />}
        title="Gateway Monitor"
        description={
          <>
            Halaman operasi gateway untuk memantau koneksi device, channel GSM/WiFi, traffic telemetry, invalid packet,
            presence timeout 7 menit, dan risiko bottleneck sebelum sistem masuk skala besar.
          </>
        }
        statusLabel="Gateway Status"
        status={healthStatusFromScore(overview.gatewayScore, overview.status)}
        scoreLabel="Gateway Score"
        score={overview.gatewayScore}
        updatedAt={overview.generatedAt}
        trendLabel="Baseline learning"
        attention={buildGatewayAttention(overview.insights)}
      />

      <MonitorKpiGrid cards={topCards} />

      <div className="grid gap-4 xl:grid-cols-[1.15fr_1fr_1fr]">
        <MonitorAssessmentCard
          title="Gateway Assessment"
          icon={overview.status === "NORMAL" ? <CheckCircle2 className="h-5 w-5 text-emerald-600" /> : <AlertTriangle className="h-5 w-5 text-amber-600" />}
          statusTitle={overview.summary}
          statusDetail="Assessment fokus pada connection stability, packet quality, dan command route readiness."
          impact="Belum ada anomali gateway melewati threshold tahap awal. Baseline akan semakin akurat saat traffic device nyata bertambah."
          action={overview.recommendation}
          severity={overview.status}
        />

        <MonitorDistributionBars
          title="Channel Distribution"
          subtitle="Aktif berdasarkan active channel device/session."
          icon={<Route className="h-5 w-5" />}
          items={overview.channelDistribution}
          emptyMessage="Belum ada channel GSM/WiFi aktif."
        />

        <MonitorDistributionBars
          title="Protocol Distribution"
          subtitle="Codec8, Codec8E, Codec12, dan ALELS JSON."
          icon={<Network className="h-5 w-5" />}
          items={overview.protocolDistribution}
          emptyMessage="Belum ada protocol aktif."
        />
      </div>

      <MonitorDomainGrid cards={gatewayIndicators} />

      <MonitorSnapshot
        title="Gateway Technical Snapshot"
        description="Snapshot kecil untuk verifikasi angka teknis tanpa mengulang KPI utama di atas."
        fallbackIcon={<Network className="h-4 w-4" />}
        items={overview.metrics.map((metric) => ({
          key: metric.key,
          label: metric.label,
          value: metric.value,
          unit: metric.unit,
          icon: metricIcons[metric.key]
        }))}
      />

      <MonitorFindingsTable
        title="Gateway Findings & Actions"
        description="Masalah gateway yang perlu diprioritaskan sebelum traffic device bertambah besar."
        status={overview.status}
        findings={overview.insights}
        columns={insightColumns}
        rowKey={(row) => row.id}
        searchPlaceholder="Search gateway finding..."
        emptyMessage="No gateway findings."
        isLoading={isLoading}
        errorMessage={isError ? "Backend Gateway Monitor belum terbaca. Fallback UI ditampilkan." : null}
      />
    </section>
  );
}
