import type { ReactNode } from "react";

import { Badge } from "@/components/ui/badge";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { DataTable, type DataTableColumn } from "@/components/data-table";
import { useLanguageStore } from "@/stores/language-store";
import { localizeMonitorTerm, monitorLabel, toneFor } from "./monitor-utils";

export function MonitorFindingsTable<T>({
  title,
  description,
  status,
  findings,
  columns,
  rowKey,
  searchPlaceholder,
  emptyMessage,
  isLoading,
  errorMessage
}: {
  title: string;
  description: ReactNode;
  status: string;
  findings: T[];
  columns: DataTableColumn<T>[];
  rowKey: (row: T) => string | number;
  searchPlaceholder?: string;
  emptyMessage?: string;
  isLoading?: boolean;
  errorMessage?: ReactNode;
}) {
  const language = useLanguageStore((state) => state.language);
  return (
    <Card className="server-monitor-card border shadow-sm">
      <CardHeader className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <CardTitle className="text-lg text-card-foreground">{localizeMonitorTerm(language, title)}</CardTitle>
          <p className="mt-1 text-sm font-semibold text-muted-foreground">{description}</p>
        </div>
        <Badge tone={toneFor(status)} className="w-fit px-3 py-1 font-extrabold">
          {findings.length} {monitorLabel(language, "finding")}
        </Badge>
      </CardHeader>
      <CardContent>
        {isLoading ? <div className="rounded-lg border border-border bg-muted p-4 text-sm font-semibold text-muted-foreground">{monitorLabel(language, "findingsLoading")}</div> : null}
        {errorMessage ? <div className="mb-3 rounded-lg border border-border bg-muted p-3 text-sm font-bold text-card-foreground">{errorMessage}</div> : null}
        <DataTable
          data={findings}
          columns={columns.map((column) => ({ ...column, label: localizeMonitorTerm(language, column.label) }))}
          rowKey={rowKey}
          searchPlaceholder={searchPlaceholder}
          emptyMessage={emptyMessage}
        />
      </CardContent>
    </Card>
  );
}
