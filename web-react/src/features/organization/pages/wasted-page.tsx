import { ReactNode, useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { RotateCcw, Trash2 } from "lucide-react";

import { DataTable, type DataTableBulkAction, type DataTableColumn } from "@/components/data-table";
import { Button } from "@/components/ui/button";
import { companyAction, getOrganizationWastedItems, userAction, type OrganizationWastedItem } from "@/features/organization/api/organization-api";
import { formatCompanyName, formatDateTime, OrganizationPageHeader, OrganizationTableCard, StatusBadge } from "@/features/organization/components/organization-ui";
import { normalizeRole } from "@/lib/role-access";
import { useAuthStore } from "@/stores/auth-store";

type WasteTab = "COMPANY" | "USER";

export function WastedPage() {
  const queryClient = useQueryClient();
  const user = useAuthStore((state) => state.user);
  const role = normalizeRole(user?.role);
  const canPermanentDelete = role === "SUPERADMIN" || role === "OWNER";
  const [activeTab, setActiveTab] = useState<WasteTab>("COMPANY");
  const { data = [], isLoading, isError } = useQuery({ queryKey: ["organization", "wasted"], queryFn: getOrganizationWastedItems, refetchInterval: 30_000 });

  const actionMutation = useMutation({
    mutationFn: ({ item, action }: { item: OrganizationWastedItem; action: "restore" | "permanent-delete" }) => item.itemType === "COMPANY" ? companyAction(item.id, action) : userAction(item.id, action),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["organization"] })
  });

  const bulkActions = useMemo<DataTableBulkAction<OrganizationWastedItem>[]>(() => [
    { key: "restore", label: "Restore", icon: <RotateCcw className="h-4 w-4" />, variant: "outline", confirmMessage: (rows) => `Restore ${rows.length} wasted item(s)?`, onClick: (rows) => rows.forEach((row) => actionMutation.mutate({ item: row, action: "restore" })) },
    { key: "permanent-delete", label: "Permanent Delete", icon: <Trash2 className="h-4 w-4" />, variant: "outline", hidden: () => !canPermanentDelete, confirmMessage: (rows) => `Permanent delete ${rows.length} item(s)? This cannot be undone.`, onClick: (rows) => rows.forEach((row) => actionMutation.mutate({ item: row, action: "permanent-delete" })) }
  ], [actionMutation, canPermanentDelete]);

  const companyItems = useMemo(() => data.filter((item) => item.itemType === "COMPANY"), [data]);
  const userItems = useMemo(() => data.filter((item) => item.itemType === "USER"), [data]);
  const tableData = activeTab === "COMPANY" ? companyItems : userItems;

  const companyColumns = useMemo<DataTableColumn<OrganizationWastedItem>[]>(() => [
    { key: "name", label: "Company", value: (row) => row.name, render: (row) => <span className="font-extrabold text-white">{formatCompanyName(row.name)}</span> },
    { key: "companyName", label: "Parent / Line", value: (row) => formatCompanyName(row.companyName), render: (row) => <span>{formatCompanyName(row.companyName)}</span> },
    { key: "roleOrType", label: "Type", value: (row) => row.roleOrType || "-", render: (row) => <StatusBadge status={row.roleOrType} /> },
    { key: "deletedAt", label: "Deleted at", value: (row) => formatDateTime(row.deletedAt), render: (row) => <span>{formatDateTime(row.deletedAt)}</span> },
    { key: "remainingDays", label: "Remaining", value: (row) => remainingLabel(row), render: (row) => <span className="font-semibold">{remainingLabel(row)}</span> },
    { key: "deletePermanentAt", label: "Auto purge date", value: (row) => formatDateTime(row.deletePermanentAt), render: (row) => <span>{formatDateTime(row.deletePermanentAt)}</span> },
    { key: "deletedReason", label: "Reason", value: (row) => row.deletedReason || "-" }
  ], []);

  const userColumns = useMemo<DataTableColumn<OrganizationWastedItem>[]>(() => [
    { key: "name", label: "User", value: (row) => row.name, render: (row) => <span className="font-extrabold text-white">{row.name}</span> },
    { key: "companyName", label: "Company", value: (row) => formatCompanyName(row.companyName), render: (row) => <span>{formatCompanyName(row.companyName)}</span> },
    { key: "roleOrType", label: "Role", value: (row) => row.roleOrType || "-", render: (row) => <StatusBadge status={row.roleOrType} /> },
    { key: "deletedAt", label: "Deleted at", value: (row) => formatDateTime(row.deletedAt), render: (row) => <span>{formatDateTime(row.deletedAt)}</span> },
    { key: "remainingDays", label: "Remaining", value: (row) => remainingLabel(row), render: (row) => <span className="font-semibold">{remainingLabel(row)}</span> },
    { key: "deletePermanentAt", label: "Auto purge date", value: (row) => formatDateTime(row.deletePermanentAt), render: (row) => <span>{formatDateTime(row.deletePermanentAt)}</span> },
    { key: "deletedReason", label: "Reason", value: (row) => row.deletedReason || "-" }
  ], []);

  return (
    <section className="space-y-5 text-foreground">
      <OrganizationPageHeader icon={<Trash2 className="h-5 w-5" />} title="Wasted Organization" />
      <OrganizationTableCard>
        <div className="mb-4 flex flex-wrap gap-2 border-b border-white/10 pb-4">
          <TabButton active={activeTab === "COMPANY"} onClick={() => setActiveTab("COMPANY")}>Company Wasted ({companyItems.length})</TabButton>
          <TabButton active={activeTab === "USER"} onClick={() => setActiveTab("USER")}>User Wasted ({userItems.length})</TabButton>
        </div>
        {isError ? <div className="mb-4 rounded-lg border border-red-500/30 bg-red-500/10 p-3 text-sm text-red-200">Wasted API belum tersedia atau backend belum berjalan.</div> : null}
        <DataTable
          data={tableData}
          columns={activeTab === "COMPANY" ? companyColumns : userColumns}
          rowKey={(row) => `${row.itemType}-${row.id}`}
          emptyMessage={isLoading ? "Loading wasted data..." : activeTab === "COMPANY" ? "No company wasted item found." : "No user wasted item found."}
          bulkActions={bulkActions}
          actions={(row) => <div className="flex items-center gap-2"><Button type="button" size="sm" variant="outline" title="Restore" onClick={() => actionMutation.mutate({ item: row, action: "restore" })}><RotateCcw className="h-4 w-4" /> Restore</Button>{canPermanentDelete ? <Button type="button" size="sm" variant="outline" title="Permanent delete" onClick={() => confirm("Permanent delete this item? This cannot be undone.") && actionMutation.mutate({ item: row, action: "permanent-delete" })}><Trash2 className="h-4 w-4" /> Permanent Delete</Button> : null}</div>}
        />
      </OrganizationTableCard>
    </section>
  );
}

function TabButton({ active, children, onClick }: { active: boolean; children: ReactNode; onClick: () => void }) {
  return <Button type="button" variant={active ? "default" : "outline"} onClick={onClick}>{children}</Button>;
}



function remainingLabel(row: OrganizationWastedItem) {
  if (typeof row.remainingDays === "number") {
    return row.remainingDays <= 0 ? "Ready to purge" : `${row.remainingDays} day${row.remainingDays === 1 ? "" : "s"}`;
  }
  if (!row.deletePermanentAt) return "30 days";
  const purgeAt = new Date(row.deletePermanentAt).getTime();
  if (Number.isNaN(purgeAt)) return "-";
  const diff = Math.ceil((purgeAt - Date.now()) / 86_400_000);
  return diff <= 0 ? "Ready to purge" : `${diff} day${diff === 1 ? "" : "s"}`;
}
