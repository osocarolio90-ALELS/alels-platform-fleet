import type { ReactNode } from "react";

import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { cn } from "@/lib/utils";
import { useLanguageStore } from "@/stores/language-store";
import { localizeMonitorTerm, monitorLabel, visualFor } from "./monitor-utils";

export function MonitorAssessmentCard({
  title,
  icon,
  statusTitle,
  statusDetail,
  impact,
  action,
  severity = "NORMAL"
}: {
  title: string;
  icon: ReactNode;
  statusTitle: string;
  statusDetail: ReactNode;
  impact: ReactNode;
  action: ReactNode;
  severity?: string;
}) {
  const language = useLanguageStore((state) => state.language);
  const visual = visualFor(severity);

  return (
    <Card className={cn("server-monitor-card border shadow-sm", visual.border)}>
      <CardHeader>
        <CardTitle className="flex items-center gap-2 text-lg text-card-foreground">
          {icon}
          {localizeMonitorTerm(language, title)}
        </CardTitle>
      </CardHeader>
      <CardContent className="grid gap-4 xl:grid-cols-3">
        <div className="rounded-xl bg-muted p-4 ring-1 ring-border">
          <p className="text-xs font-extrabold uppercase tracking-wide text-muted-foreground">{monitorLabel(language, "statusReason")}</p>
          <p className="mt-2 text-sm font-extrabold text-card-foreground">{statusTitle}</p>
          <div className="mt-3 text-xs font-semibold leading-5 text-card-foreground">{statusDetail}</div>
        </div>
        <div className="rounded-xl bg-muted p-4 ring-1 ring-border">
          <p className="text-xs font-extrabold uppercase tracking-wide text-muted-foreground">{monitorLabel(language, "operationalImpact")}</p>
          <div className="mt-2 text-sm font-semibold leading-6 text-card-foreground">{impact}</div>
        </div>
        <div className="rounded-xl bg-muted p-4 ring-1 ring-border">
          <p className="text-xs font-extrabold uppercase tracking-wide text-primary">{monitorLabel(language, "recommendedAction")}</p>
          <div className="mt-2 text-sm font-extrabold leading-6 text-card-foreground">{action}</div>
        </div>
      </CardContent>
    </Card>
  );
}
