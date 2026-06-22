import type { ReactNode } from "react";

import { Card, CardContent } from "@/components/ui/card";
import { cn } from "@/lib/utils";
import { visualFor } from "./monitor-utils";

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
  const visual = visualFor(severity);

  return (
    <Card className={cn("border shadow-sm", visual.border)}>
      <CardContent className="flex items-start justify-between gap-4 p-5">
        <div>
          <p className="text-sm font-extrabold text-card-foreground">{label}</p>
          <p className="mt-1 text-4xl font-black tracking-tight text-card-foreground">
            {value} {unit ? <span className="text-sm font-extrabold text-card-foreground">{unit}</span> : null}
          </p>
          <p className="mt-2 text-xs font-semibold leading-5 text-card-foreground">{note}</p>
        </div>
        <div className={cn("grid h-12 w-12 shrink-0 place-items-center rounded-2xl", visual.bg, visual.text)}>{icon}</div>
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
  const visual = visualFor(severity);

  return (
    <Card className={cn("border shadow-sm", visual.border)}>
      <CardContent className="p-5">
        <div className="mb-4 flex items-center justify-between gap-4">
          <div>
            <p className="text-sm font-extrabold text-card-foreground">{label}</p>
            <p className="mt-1 text-3xl font-black text-card-foreground">{value}</p>
            <p className={cn("mt-1 text-xs font-extrabold", visual.text)}>{visual.label}</p>
          </div>
          <div className={cn("grid h-12 w-12 shrink-0 place-items-center rounded-2xl", visual.bg, visual.text)}>{icon}</div>
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
