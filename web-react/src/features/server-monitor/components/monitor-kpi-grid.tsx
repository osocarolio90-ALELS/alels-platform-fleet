import type { ReactNode } from "react";

import { Card, CardContent } from "@/components/ui/card";
import { StatusDot } from "@/components/ui/status-indicator";
import { cn } from "@/lib/utils";
import { useLanguageStore } from "@/stores/language-store";
import { localizeMonitorTerm, severityLabel, visualFor } from "./monitor-utils";

export type MonitorKpiCardConfig = {
  key?: string;
  label: string;
  value: ReactNode;
  unit?: string;
  severity?: string;
  icon: ReactNode;
  note: ReactNode;
};

export type MonitorDomainCardConfig = {
  id?: string;
  label: string;
  value: ReactNode;
  severity?: string;
  icon: ReactNode;
  details: ReactNode[];
};

export function MonitorKpiGrid({ cards }: { cards: MonitorKpiCardConfig[] }) {
  return (
    <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
      {cards.map(({ key, ...card }) => (
        <MonitorKpiCard key={key || card.label} {...card} />
      ))}
    </div>
  );
}

export function MonitorDomainGrid({ cards }: { cards: MonitorDomainCardConfig[] }) {
  return (
    <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
      {cards.map(({ id, ...card }) => (
        <MonitorDomainCard key={id || card.label} {...card} />
      ))}
    </div>
  );
}

export function MonitorKpiCard({
  label,
  value,
  unit,
  severity = "NORMAL",
  icon,
  note
}: MonitorKpiCardConfig) {
  const language = useLanguageStore((state) => state.language);
  const visual = visualFor(severity);

  return (
    <Card className={cn("server-monitor-card border shadow-sm", visual.border)}>
      <CardContent className="flex items-start justify-between gap-4 p-5">
        <div>
          <p className="text-sm font-extrabold text-card-foreground">{localizeMonitorTerm(language, label)}</p>
          <p className="mt-1 text-4xl font-black tracking-tight text-card-foreground">
            {value} {unit ? <span className="text-sm font-extrabold text-card-foreground">{unit}</span> : null}
          </p>
          <p className="mt-2 text-xs font-semibold leading-5 text-card-foreground">{note}</p>
        </div>
        <div className="server-monitor-icon grid h-12 w-12 shrink-0 place-items-center rounded-xl bg-primary/10">{icon}</div>
        <StatusDot status={severity} className="server-monitor-status-dot" />
      </CardContent>
    </Card>
  );
}

export function MonitorDomainCard({
  label,
  value,
  severity = "NORMAL",
  icon,
  details
}: MonitorDomainCardConfig) {
  const language = useLanguageStore((state) => state.language);
  const visual = visualFor(severity);

  return (
    <Card className={cn("server-monitor-card border shadow-sm", visual.border)}>
      <CardContent className="p-5">
        <div className="mb-4 flex items-center justify-between gap-4">
          <div>
            <p className="text-sm font-extrabold text-card-foreground">{localizeMonitorTerm(language, label)}</p>
            <p className="mt-1 text-3xl font-black text-card-foreground">{value}</p>
            <p className="mt-1 text-xs font-extrabold text-muted-foreground">{severityLabel(language, severity)}</p>
          </div>
          <div className="flex shrink-0 items-center gap-2">
            <StatusDot status={severity} className="server-monitor-status-dot" />
            <div className="server-monitor-icon grid h-12 w-12 place-items-center rounded-xl bg-primary/10">{icon}</div>
          </div>
        </div>
        <div className="space-y-1 rounded-xl bg-muted p-3 ring-1 ring-border">
          {details.map((detail, index) => (
            <p key={index} className="text-xs font-semibold leading-5 text-card-foreground">
              {detail}
            </p>
          ))}
        </div>
      </CardContent>
    </Card>
  );
}
