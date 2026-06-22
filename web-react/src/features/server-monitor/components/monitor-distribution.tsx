import type { ReactNode } from "react";

import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { formatMonitorBytes } from "./monitor-utils";

export type MonitorDistributionItem = { name: string; count: number; displayValue?: string | null };

export function MonitorDistributionBars({
  title,
  subtitle,
  icon,
  items,
  emptyMessage
}: {
  title: string;
  subtitle?: string;
  icon: ReactNode;
  items: MonitorDistributionItem[];
  emptyMessage: string;
}) {
  const max = Math.max(...items.map((item) => item.count), 1);

  return (
    <Card className="border shadow-sm">
      <CardHeader>
        <CardTitle className="flex items-center gap-2 text-lg text-card-foreground">
          {icon}
          {title}
        </CardTitle>
        {subtitle ? <p className="text-sm font-semibold text-muted-foreground">{subtitle}</p> : null}
      </CardHeader>
      <CardContent className="space-y-3">
        {items.length ? (
          items.map((item) => {
            const percent = Math.max(4, Math.round((item.count / max) * 100));
            return (
              <div key={item.name} className="rounded-xl border border-border bg-muted p-3">
                <div className="mb-2 flex items-center justify-between gap-4">
                  <span className="text-sm font-extrabold text-foreground">{item.name}</span>
                  <span className="text-sm font-black text-card-foreground">{item.displayValue || formatMonitorBytes(item.count)}</span>
                </div>
                <div className="h-2 overflow-hidden rounded-full bg-background">
                  <div className="h-full rounded-full bg-emerald-500" style={{ width: `${percent}%` }} />
                </div>
              </div>
            );
          })
        ) : (
          <div className="rounded-xl border border-border bg-muted p-4 text-sm font-semibold text-card-foreground">
            {emptyMessage}
          </div>
        )}
      </CardContent>
    </Card>
  );
}
