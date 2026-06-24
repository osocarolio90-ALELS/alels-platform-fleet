import { ReactNode, useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { CarFront, Cpu, IdCard, Recycle, RotateCcw, Trash2 } from "lucide-react";

import { DataTable, type DataTableBulkAction, type DataTableColumn } from "@/components/data-table";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { PageHeader } from "@/components/ui/page-header";
import {
  getAssetWastedItems,
  permanentDeleteAssetWasted,
  restoreAssetWasted,
  type AssetWastedRow,
  type AssetWastedType
} from "@/features/asset-register/api/asset-wasted-api";
import { formatCompanyName, formatDateTime, StatusBadge } from "@/features/organization/components/organization-ui";
import { normalizeRole } from "@/lib/role-access";
import { useAuthStore } from "@/stores/auth-store";

type TabConfig = {
  key: AssetWastedType;
  label: string;
  icon: ReactNode;
  empty: string;
};

const tabs: TabConfig[] = [
  { key: "VEHICLE", label: "Vehicle Wasted", icon: <CarFront className="h-4 w-4" />, empty: "No deleted vehicle found." },
  { key: "DEVICE", label: "Device Wasted", icon: <Cpu className="h-4 w-4" />, empty: "No deleted device found." },
  { key: "DRIVER", label: "Driver Wasted", icon: <IdCard className="h-4 w-4" />, empty: "No deleted driver found." }
];

export function AssetWastedPage() {
  const queryClient = useQueryClient();
  const user = useAuthStore((state) => state.user);
  const role = normalizeRole(user?.role);
  const canPermanentDelete = role === "SUPERADMIN" || role === "OWNER";
  const [activeTab, setActiveTab] = useState<AssetWastedType>("VEHICLE");

  const { data = [], isLoading, isError } = useQuery({
    queryKey: ["asset-register", "wasted", activeTab],
    queryFn: () => getAssetWastedItems(activeTab),
    refetchInterval: 30_000
  });

  const restoreMutation = useMutation({
    mutationFn: ({ type, id }: { type: AssetWastedType; id: number }) => restoreAssetWasted(type, id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["asset-register", "wasted"] });
      queryClient.invalidateQueries({ queryKey: ["asset-register", "vehicles"] });
    }
  });

  const permanentDeleteMutation = useMutation({
    mutationFn: ({ type, id }: { type: AssetWastedType; id: number }) => permanentDeleteAssetWasted(type, id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["asset-register", "wasted"] })
  });

  const bulkActions = useMemo<DataTableBulkAction<AssetWastedRow>[]>(() => [
    { key: "restore", label: "Restore", icon: <RotateCcw className="h-4 w-4" />, variant: "outline", confirmMessage: (rows) => `Restore ${rows.length} ${activeTab.toLowerCase()} item(s)?`, onClick: (rows) => rows.forEach((row) => restoreMutation.mutate({ type: activeTab, id: row.id })) },
    { key: "permanent-delete", label: "Permanent Delete", icon: <Trash2 className="h-4 w-4" />, variant: "outline", hidden: () => !canPermanentDelete, confirmMessage: (rows) => `Permanent delete ${rows.length} ${activeTab.toLowerCase()} item(s)? This cannot be undone.`, onClick: (rows) => rows.forEach((row) => permanentDeleteMutation.mutate({ type: activeTab, id: row.id })) }
  ], [activeTab, canPermanentDelete, permanentDeleteMutation, restoreMutation]);

  const columns = useMemo<DataTableColumn<AssetWastedRow>[]>(() => [
    { key: "name", label: "Name", value: (row) => row.name, render: (row) => <span className="font-extrabold text-white">{row.name || "-"}</span> },
    { key: "code", label: activeTab === "VEHICLE" ? "Plate / Code" : activeTab === "DEVICE" ? "IMEI" : "Driver Code", value: (row) => row.code || "-" },
    { key: "companyName", label: "Company", value: (row) => formatCompanyName(row.companyName), render: (row) => <span>{formatCompanyName(row.companyName)}</span> },
    { key: "extra", label: activeTab === "VEHICLE" ? "Brand / Model" : activeTab === "DEVICE" ? "Device Model" : "Phone", value: (row) => row.extra || "-" },
    { key: "status", label: "Status", value: (row) => row.status || "-", render: (row) => <StatusBadge status={row.status || "DELETED"} /> },
    { key: "deletedAt", label: "Deleted at", value: (row) => formatDateTime(row.deletedAt), render: (row) => <span>{formatDateTime(row.deletedAt)}</span> },
    { key: "remainingDays", label: "Remaining", value: (row) => remainingLabel(row), render: (row) => <span className="font-semibold">{remainingLabel(row)}</span> },
    { key: "deletePermanentAt", label: "Auto purge date", value: (row) => formatDateTime(row.deletePermanentAt), render: (row) => <span>{formatDateTime(row.deletePermanentAt)}</span> },
    { key: "deletedByEmail", label: "Deleted by", value: (row) => row.deletedByEmail || "-" },
    { key: "deletedReason", label: "Reason", value: (row) => row.deletedReason || "-" }
  ], [activeTab]);

  const currentTab = tabs.find((tab) => tab.key === activeTab) ?? tabs[0];

  return (
    <section className="space-y-5 text-foreground">
      <PageHeader icon={<Recycle className="h-4 w-4" />} title="Wasted Asset Register" />

      <Card className="border-white/10 bg-white/[0.03] text-foreground">
        <CardContent>
          <div className="mb-4 flex flex-wrap gap-2 border-b border-white/10 pb-4">
            {tabs.map((tab) => (
              <TabButton key={tab.key} active={activeTab === tab.key} onClick={() => setActiveTab(tab.key)}>
                <span className="flex items-center gap-2">{tab.icon}{tab.label}</span>
              </TabButton>
            ))}
          </div>

          {isError ? <div className="mb-4 rounded-lg border border-red-500/30 bg-red-500/10 p-3 text-sm text-red-200">Wasted Asset API belum tersedia atau backend belum berjalan.</div> : null}

          <DataTable
            data={data}
            columns={columns}
            rowKey={(row) => `${row.itemType}-${row.id}`}
            emptyMessage={isLoading ? "Loading wasted asset..." : currentTab.empty}
            bulkActions={bulkActions}
            actions={(row) => (
              <div className="flex items-center gap-2">
                <Button type="button" size="sm" variant="outline" title="Restore" onClick={() => restoreMutation.mutate({ type: activeTab, id: row.id })}>
                  <RotateCcw className="h-4 w-4" /> Restore
                </Button>
                {canPermanentDelete ? (
                  <Button type="button" size="sm" variant="outline" title="Permanent delete" onClick={() => confirm(`Permanent delete this ${activeTab.toLowerCase()}? This cannot be undone.`) && permanentDeleteMutation.mutate({ type: activeTab, id: row.id })}>
                    <Trash2 className="h-4 w-4" /> Permanent Delete
                  </Button>
                ) : null}
              </div>
            )}
          />
        </CardContent>
      </Card>
    </section>
  );
}

function TabButton({ active, children, onClick }: { active: boolean; children: ReactNode; onClick: () => void }) {
  return <Button type="button" variant={active ? "default" : "outline"} onClick={onClick}>{children}</Button>;
}



function remainingLabel(row: AssetWastedRow) {
  if (typeof row.remainingDays === "number") {
    return row.remainingDays <= 0 ? "Ready to purge" : `${row.remainingDays} day${row.remainingDays === 1 ? "" : "s"}`;
  }
  if (!row.deletePermanentAt) return "30 days";
  const purgeAt = new Date(row.deletePermanentAt).getTime();
  if (Number.isNaN(purgeAt)) return "-";
  const diff = Math.ceil((purgeAt - Date.now()) / 86_400_000);
  return diff <= 0 ? "Ready to purge" : `${diff} day${diff === 1 ? "" : "s"}`;
}
