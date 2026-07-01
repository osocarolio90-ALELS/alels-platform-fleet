import { FormEvent, ReactNode, useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Database, Pencil, Plus, Save, Trash2, X } from "lucide-react";

import { DataTable, type DataTableBulkAction, type DataTableColumn } from "@/components/data-table";
import { PageHeader } from "@/components/ui/page-header";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { SearchableSelect } from "@/components/ui/searchable-select";
import { OrganizationTableCard, StatusBadge, formatDateTime } from "@/features/organization/components/organization-ui";
import { useAuthStore } from "@/stores/auth-store";
import { hasRole, MASTER_DATA_EDIT_ROLES } from "@/lib/role-access";
import {
  createMasterData,
  createVehicleModel,
  deleteMasterData,
  deleteVehicleModel,
  getMasterData,
  getVehicleModels,
  updateMasterData,
  updateVehicleModel,
  type MasterDataInput,
  type MasterDataRow,
  type VehicleModelInput,
  type VehicleModelRow
} from "@/features/master-data/api/master-data-api";

type TabKey = "vehicle-types" | "vehicle-brands" | "vehicle-models" | "ownership-types" | "capacity-units";

const tabs: { key: TabKey; label: string }[] = [
  { key: "vehicle-types", label: "Vehicle Type" },
  { key: "vehicle-brands", label: "Vehicle Brand" },
  { key: "vehicle-models", label: "Vehicle Model" },
  { key: "ownership-types", label: "Ownership Type" },
  { key: "capacity-units", label: "Capacity Unit" }
];

export function VehicleMasterPage() {
  const queryClient = useQueryClient();
  const user = useAuthStore((state) => state.user);
  const canEdit = hasRole(user?.role, MASTER_DATA_EDIT_ROLES);
  const [activeTab, setActiveTab] = useState<TabKey>("vehicle-types");
  const [editing, setEditing] = useState<MasterDataRow | VehicleModelRow | null>(null);
  const [formOpen, setFormOpen] = useState(false);
  const [form, setForm] = useState<MasterForm>(emptyForm());
  const [notice, setNotice] = useState<string | null>(null);
  const isModelTab = activeTab === "vehicle-models";

  const { data: rows = [], isLoading } = useQuery({
    queryKey: ["master-data", activeTab],
    queryFn: () => isModelTab ? getVehicleModels() : getMasterData(activeTab)
  });
  const { data: brands = [] } = useQuery({ queryKey: ["master-data", "vehicle-brands"], queryFn: () => getMasterData("vehicle-brands") });

  const saveMutation = useMutation({
    mutationFn: () => {
      if (isModelTab) {
        const input: VehicleModelInput = normalizeModelInput(form);
        return editing ? updateVehicleModel(editing.id, input) : createVehicleModel(input);
      }
      const input: MasterDataInput = normalizeInput(form);
      return editing ? updateMasterData(activeTab, editing.id, input) : createMasterData(activeTab, input);
    },
    onSuccess: () => {
      closeForm();
      queryClient.invalidateQueries({ queryKey: ["master-data"] });
    },
    onError: (error: any) => setNotice(error?.response?.data?.message || "Master Data gagal disimpan.")
  });

  const deleteMutation = useMutation({
    mutationFn: (row: MasterDataRow | VehicleModelRow) => isModelTab ? deleteVehicleModel(row.id) : deleteMasterData(activeTab, row.id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["master-data"] })
  });


  const bulkActions = useMemo<DataTableBulkAction<MasterDataRow | VehicleModelRow>[]>(() => {
    if (!canEdit) return [];
    return [
      {
        key: "delete-selected",
        label: "Delete Selected",
        icon: <Trash2 className="h-4 w-4" />,
        variant: "destructive",
        confirmMessage: (selectedRows) => `Delete ${selectedRows.length} selected ${activeTabLabel(activeTab)} item(s)?`,
        disabled: () => deleteMutation.isPending,
        onClick: async (selectedRows) => {
          await Promise.all(selectedRows.map((row) => isModelTab ? deleteVehicleModel(row.id) : deleteMasterData(activeTab, row.id)));
          await queryClient.invalidateQueries({ queryKey: ["master-data"] });
        }
      }
    ];
  }, [activeTab, canEdit, deleteMutation.isPending, isModelTab, queryClient]);

  const columns = useMemo<DataTableColumn<MasterDataRow | VehicleModelRow>[]>(() => {
    const base: DataTableColumn<MasterDataRow | VehicleModelRow>[] = [
      { key: "code", label: "Code", value: (row) => row.code, render: (row) => <span className="font-bold text-foreground">{row.code}</span> },
      { key: "name", label: "Name", value: (row) => row.name, render: (row) => <span className="font-bold text-foreground">{row.name}</span> },
      { key: "description", label: "Description", value: (row) => row.description || "-", render: (row) => <span className="text-muted-foreground">{row.description || "-"}</span> },
      { key: "status", label: "Status", value: (row) => row.status, render: (row) => <StatusBadge status={row.status} /> },
      { key: "sortOrder", label: "Sort", value: (row) => row.sortOrder },
      { key: "updatedAt", label: "Updated at", value: (row) => formatDateTime(row.updatedAt), render: (row) => <span>{formatDateTime(row.updatedAt)}</span> }
    ];
    if (isModelTab) {
      base.splice(1, 0, { key: "brandName", label: "Brand", value: (row) => (row as VehicleModelRow).brandName || "-", render: (row) => <span>{(row as VehicleModelRow).brandName || "-"}</span> });
    }
    return base;
  }, [isModelTab]);

  function startCreate() {
    setEditing(null);
    setForm(emptyForm());
    setNotice(null);
    setFormOpen(true);
  }

  function startEdit(row: MasterDataRow | VehicleModelRow) {
    setEditing(row);
    setNotice(null);
    setForm({
      code: row.code || "",
      name: row.name || "",
      description: row.description || "",
      status: row.status || "ACTIVE",
      sortOrder: String(row.sortOrder || 1000),
      brandId: String((row as VehicleModelRow).brandId || "")
    });
    setFormOpen(true);
  }

  function closeForm() {
    setEditing(null);
    setForm(emptyForm());
    setFormOpen(false);
  }

  function submit(event: FormEvent) {
    event.preventDefault();
    if (!canEdit) return;
    if (isModelTab && !form.brandId) { setNotice("Vehicle Brand wajib dipilih."); return; }
    saveMutation.mutate();
  }


  if (formOpen) {
    return (
      <section className="space-y-5 text-foreground">
        <PageHeader title={`${editing ? "Edit" : "Create New"} ${activeTabLabel(activeTab)}`} icon={<Database className="h-5 w-5" />} />
        <OrganizationTableCard>
          {notice ? <Notice message={notice} /> : null}
          <form className="grid gap-4 md:grid-cols-3" onSubmit={submit}>
            {isModelTab ? <SearchableSelect label="Brand" required value={form.brandId} onChange={(value) => setForm((current) => ({ ...current, brandId: value }))} options={brands.map((brand) => ({ value: String(brand.id), label: brand.name, extra: brand.code }))} placeholder="Select Brand" /> : null}
            <Field label="Code"><Input value={form.code} onChange={(event) => setForm((current) => ({ ...current, code: event.target.value.toUpperCase() }))} placeholder="AUTO_IF_EMPTY" /></Field>
            <Field label="Name"><Input value={form.name} onChange={(event) => setForm((current) => ({ ...current, name: event.target.value }))} required /></Field>
            <Field label="Status"><select className="h-10 rounded-md border border-input bg-background px-3 text-sm text-foreground" value={form.status} onChange={(event) => setForm((current) => ({ ...current, status: event.target.value }))}><option value="ACTIVE">ACTIVE</option><option value="INACTIVE">INACTIVE</option></select></Field>
            <Field label="Sort"><Input type="number" value={form.sortOrder} onChange={(event) => setForm((current) => ({ ...current, sortOrder: event.target.value }))} /></Field>
            <div className="md:col-span-3"><Field label="Description"><Input value={form.description} onChange={(event) => setForm((current) => ({ ...current, description: event.target.value }))} /></Field></div>
            <div className="md:col-span-3 flex justify-end gap-3">
              <Button type="button" variant="outline" onClick={closeForm}><X className="h-4 w-4" /> Cancel</Button>
              <Button type="submit" disabled={saveMutation.isPending}><Save className="h-4 w-4" /> {editing ? "Save" : "Create"}</Button>
            </div>
          </form>
        </OrganizationTableCard>
      </section>
    );
  }

  return (
    <section className="space-y-5 text-foreground">
      <PageHeader title="Vehicle Master" icon={<Database className="h-5 w-5" />} actions={canEdit ? <Button type="button" onClick={startCreate}><Plus className="h-4 w-4" /> Created New</Button> : null} />
      <OrganizationTableCard>
        <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
          <div className="flex flex-wrap gap-2">
            {tabs.map((tab) => (
              <Button key={tab.key} type="button" size="sm" variant={activeTab === tab.key ? "default" : "outline"} onClick={() => { setActiveTab(tab.key); setEditing(null); setForm(emptyForm()); }}>
                {tab.label}
              </Button>
            ))}
          </div>
          {!canEdit ? <span className="text-xs font-bold text-slate-400">ADMIN view only</span> : null}
        </div>
        <DataTable
          data={rows}
          columns={columns}
          rowKey={(row) => row.id}
          emptyMessage={isLoading ? "Loading master data..." : "No master data found."}
          selectable={canEdit}
          bulkActions={bulkActions}
          actions={(row) => canEdit ? (
            <div className="flex items-center gap-2">
              <Button type="button" size="sm" variant="outline" onClick={() => startEdit(row)}><Pencil className="h-4 w-4" /> Edit</Button>
              <Button type="button" size="sm" variant="destructive" onClick={() => deleteMutation.mutate(row)}><Trash2 className="h-4 w-4" /> Delete</Button>
            </div>
          ) : <span className="text-xs text-slate-400">Read only</span>}
        />
      </OrganizationTableCard>
    </section>
  );
}

type MasterForm = { code: string; name: string; description: string; status: string; sortOrder: string; brandId: string };
function emptyForm(): MasterForm { return { code: "", name: "", description: "", status: "ACTIVE", sortOrder: "1000", brandId: "" }; }
function normalizeInput(input: MasterForm): MasterDataInput { return { code: input.code || null, name: input.name, description: input.description || null, status: input.status, sortOrder: toNumber(input.sortOrder) }; }
function normalizeModelInput(input: MasterForm): VehicleModelInput { return { ...normalizeInput(input), brandId: toNumber(input.brandId) }; }
function toNumber(value: string) { if (!value.trim()) return null; const parsed = Number(value); return Number.isFinite(parsed) ? parsed : null; }
function activeTabLabel(tab: TabKey) { return tabs.find((item) => item.key === tab)?.label || "Vehicle Master"; }
function Field({ label, children }: { label: string; children: ReactNode }) { return <label className="grid gap-2 text-xs font-bold text-foreground"><span>{label}</span>{children}</label>; }
function Notice({ message }: { message: string }) { return <div className="mb-4 rounded-lg border border-red-500/30 bg-red-500/10 p-3 text-sm text-red-700 dark:text-red-200">{message}</div>; }
