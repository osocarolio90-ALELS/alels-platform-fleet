import type { ReactNode } from "react";

import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { useLanguageStore } from "@/stores/language-store";
import { localizeMonitorTerm } from "./monitor-utils";

export type MonitorSnapshotItem = {
  key: string;
  label: string;
  value: ReactNode;
  unit?: ReactNode;
  icon?: ReactNode;
};

export function MonitorSnapshot({
  title,
  description,
  items,
  fallbackIcon
}: {
  title: string;
  description?: ReactNode;
  items: MonitorSnapshotItem[];
  fallbackIcon?: ReactNode;
}) {
  const language = useLanguageStore((state) => state.language);
  return (
    <Card className="server-monitor-card border shadow-sm">
      <CardHeader>
        <CardTitle className="text-lg text-card-foreground">{localizeMonitorTerm(language, title)}</CardTitle>
        {description ? <p className="text-sm font-semibold text-muted-foreground">{description}</p> : null}
      </CardHeader>
      <CardContent className="grid gap-3 sm:grid-cols-2 xl:grid-cols-5">
        {items.map((item) => (
          <div key={item.key} className="flex items-center justify-between gap-3 rounded-xl bg-muted px-3 py-3 ring-1 ring-border">
            <div className="flex min-w-0 items-center gap-2">
              <span className="server-monitor-icon grid h-8 w-8 shrink-0 place-items-center rounded-lg bg-primary/10">
                {item.icon ?? fallbackIcon}
              </span>
              <span className="truncate text-xs font-extrabold text-card-foreground">{localizeMonitorTerm(language, item.label)}</span>
            </div>
            <span className="shrink-0 text-sm font-black text-card-foreground">
              {item.value} {item.unit ? <span className="text-xs font-bold text-card-foreground">{item.unit}</span> : null}
            </span>
          </div>
        ))}
      </CardContent>
    </Card>
  );
}
