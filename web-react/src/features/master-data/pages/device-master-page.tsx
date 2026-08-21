import { FormEvent, ReactNode, useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Database, Pencil, Plus, Save, Trash2, X } from "lucide-react";

import { DataTable, type DataTableBulkAction, type DataTableColumn } from "@/components/data-table";
import { PageHeader } from "@/components/ui/page-header";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { SearchableSelect } from "@/components/ui/searchable-select";
import { OrganizationTableCard, formatDateTime } from "@/features/organization/components/organization-ui";
import { useAuthStore } from "@/stores/auth-store";
import { hasRole, MASTER_DATA_EDIT_ROLES } from "@/lib/role-access";
import {
  createDeviceModel,
  deleteDeviceModel,
  getDeviceBrands,
  getDeviceModels,
  updateDeviceModel,
  type DeviceMasterInput,
  type DeviceModelRow
} from "@/features/master-data/api/device-master-api";

export function DeviceMasterPage() {
  const queryClient = useQueryClient();
  const user = useAuthStore((state) => state.user);
  const canEdit = hasRole(user?.role, MASTER_DATA_EDIT_ROLES);
  const [formOpen, setFormOpen] = useState(false);
  const [editing, setEditing] = useState<DeviceModelRow | null>(null);
  const [form, setForm] = useState<FormState>(emptyForm());
  const [notice, setNotice] = useState<string | null>(null);

  const { data: rows = [], isLoading } = useQuery({
    queryKey: ["device-master", "models"],
    queryFn: () => getDeviceModels()
  });
  const { data: brands = [] } = useQuery({ queryKey: ["device-master", "brands"], queryFn: getDeviceBrands });

  const saveMutation = useMutation({
    mutationFn: () => editing ? updateDeviceModel(editing.id, toInput(form)) : createDeviceModel(toInput(form)),
    onSuccess: () => {
      closeForm();
      queryClient.invalidateQueries({ queryKey: ["device-master"] });
    },
    onError: (error: any) => setNotice(error?.response?.data?.message || "Device Master gagal disimpan.")
  });

  const deleteMutation = useMutation({
    mutationFn: (row: DeviceModelRow) => deleteDeviceModel(row.id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["device-master"] })
  });

  const columns = useMemo<DataTableColumn<DeviceModelRow>[]>(() => [
    { key: "code", label: "Code", value: (row) => row.modelCode || "-", render: (row) => <span className="font-bold text-foreground">{row.modelCode || "-"}</span> },
    { key: "brandName", label: "Device Brand", value: (row) => row.brandName || "-", render: (row) => <span className="font-bold text-foreground">{row.brandName || "-"}</span> },
    { key: "modelName", label: "Device Model", value: (row) => row.modelName || "-", render: (row) => <span className="font-bold text-foreground">{row.modelName || "-"}</span> },
    { key: "createdAt", label: "Created at", value: (row) => row.createdAt || "", render: (row) => formatDateTime(row.createdAt) },
    { key: "createdBy", label: "Created By", value: (row) => row.createdBy || "-" }
  ], []);

  const bulkActions = useMemo<DataTableBulkAction<DeviceModelRow>[]>(() => canEdit ? [
    {
      key: "delete",
      label: "Delete",
      icon: <Trash2 className="h-4 w-4" />,
      confirmMessage: (selected) => `Delete ${selected.length} selected device master item(s)?`,
      onClick: async (selected) => {
        for (const row of selected) await deleteDeviceModel(row.id);
        queryClient.invalidateQueries({ queryKey: ["device-master"] });
      }
    }
  ] : [], [canEdit, queryClient]);

  function openCreate() {
    setEditing(null);
    setForm(emptyForm());
    setNotice(null);
    setFormOpen(true);
  }

  function startEdit(row: DeviceModelRow) {
    setEditing(row);
    setNotice(null);
    setForm({
      modelCode: row.modelCode || "",
      brandId: row.brandId ? String(row.brandId) : "",
      modelName: row.modelName || "",
      protocolCode: row.protocolCode || "",
      parserCode: row.parserCode || "",
      dictionaryCode: row.dictionaryCode || "",
      avlDefinitions: formatAvlDefinitions(row.avlDefinitions || []),
      active: true
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
    if (!form.brandId) { setNotice("Device Brand wajib dipilih."); return; }
    try { parseAvlDefinitions(form.avlDefinitions); } catch (error) { setNotice(error instanceof Error ? error.message : "AVL Dictionary tidak valid."); return; }
    saveMutation.mutate();
  }

  if (formOpen) {
    return (
      <section className="space-y-5 text-foreground">
        <PageHeader
          title={editing ? "Edit Device Master" : "Create New Device Master"}
          icon={<Database className="h-5 w-5" />}
        />
        <OrganizationTableCard>
          {notice ? <Notice message={notice} /> : null}
          <form className="grid gap-3 md:grid-cols-3" onSubmit={submit}>
            <Field label="Code">
              <Input value={form.modelCode} onChange={(event) => setForm((current) => ({ ...current, modelCode: event.target.value.toUpperCase() }))} placeholder="AUTO / MANUAL" />
            </Field>
            <SearchableSelect label="Device Brand" required value={form.brandId} onChange={(value) => setForm((current) => ({ ...current, brandId: value }))} options={brands.map((brand) => ({ value: String(brand.id), label: brand.brandName, extra: brand.brandCode }))} />
            <Field label="Device Model">
              <Input value={form.modelName} onChange={(event) => setForm((current) => ({ ...current, modelName: event.target.value }))} required />
            </Field>
            <Field label="Protocol Code"><Input value={form.protocolCode} onChange={(event) => setForm((current) => ({ ...current, protocolCode: event.target.value.toUpperCase() }))} required /></Field>
            <Field label="Parser Code"><Input value={form.parserCode} onChange={(event) => setForm((current) => ({ ...current, parserCode: event.target.value.toUpperCase() }))} placeholder="TELTONIKA_AUTO / ALELS_JSON" required /></Field>
            <Field label="Dictionary Code"><Input value={form.dictionaryCode} onChange={(event) => setForm((current) => ({ ...current, dictionaryCode: event.target.value.toUpperCase() }))} required /></Field>
            <div className="md:col-span-3"><Field label="AVL Dictionary (satu baris: ID | Nama | Unit | Tipe | Multiplier | Kategori)"><textarea className="min-h-32 w-full rounded-md border border-input bg-background px-3 py-2 text-sm text-foreground" value={form.avlDefinitions} onChange={(event) => setForm((current) => ({ ...current, avlDefinitions: event.target.value }))} placeholder="239 | Ignition | - | BOOLEAN | 1 | vehicle" required /></Field></div>
            <div className="md:col-span-3 flex justify-end gap-2">
              <Button type="button" variant="outline" onClick={closeForm}><X className="h-4 w-4" /> Cancel</Button>
              <Button type="submit" disabled={saveMutation.isPending}><Save className="h-4 w-4" /> Save</Button>
            </div>
          </form>
        </OrganizationTableCard>
      </section>
    );
  }

  return (
    <section className="space-y-5 text-foreground">
      <PageHeader
        title="Device Master"
        icon={<Database className="h-5 w-5" />}
        actions={canEdit ? <Button type="button" onClick={openCreate}><Plus className="h-4 w-4" /> Created New</Button> : null}
      />
      <OrganizationTableCard>
        <DataTable
          data={rows}
          columns={columns}
          rowKey={(row) => row.id}
          emptyMessage={isLoading ? "Loading device master..." : "No device master found."}
          bulkActions={bulkActions}
          actions={(row) => canEdit ? (
            <div className="flex items-center gap-2">
              <Button type="button" size="icon" variant="outline" onClick={() => startEdit(row)} title="Edit"><Pencil className="h-4 w-4" /></Button>
              <Button type="button" size="icon" variant="destructive" onClick={() => deleteMutation.mutate(row)} title="Delete"><Trash2 className="h-4 w-4" /></Button>
            </div>
          ) : <span className="text-xs text-slate-400">Read only</span>}
        />
      </OrganizationTableCard>
    </section>
  );
}

type FormState = { modelCode: string; brandId: string; modelName: string; protocolCode: string; parserCode: string; dictionaryCode: string; avlDefinitions: string; active: boolean };

function emptyForm(): FormState {
  return { modelCode: "", brandId: "", modelName: "", protocolCode: "", parserCode: "", dictionaryCode: "", avlDefinitions: "", active: true };
}

function toInput(form: FormState): DeviceMasterInput {
  return {
    brandId: form.brandId ? Number(form.brandId) : null,
    modelCode: form.modelCode || null,
    modelName: form.modelName || null,
    protocolCode: form.protocolCode || null,
    parserCode: form.parserCode || null,
    dictionaryCode: form.dictionaryCode || null,
    avlDefinitions: parseAvlDefinitions(form.avlDefinitions),
    active: true
  };
}

function parseAvlDefinitions(value: string) {
  const rows = value.split(/\r?\n/).map((line) => line.trim()).filter(Boolean).map((line, index) => {
    const [avlId, name, unit = "", valueType = "NUMBER", multiplierText = "1", category = ""] = line.split("|").map((part) => part.trim());
    const multiplier = Number(multiplierText);
    if (!avlId || !name || !Number.isFinite(multiplier) || multiplier === 0) throw new Error(`AVL Dictionary baris ${index + 1} tidak valid.`);
    return { avlId, name, unit: unit || null, valueType: valueType || "NUMBER", multiplier, category: category || null };
  });
  if (!rows.length) throw new Error("Minimal satu AVL ID terverifikasi wajib diisi.");
  if (new Set(rows.map((row) => row.avlId)).size !== rows.length) throw new Error("AVL ID tidak boleh duplikat.");
  return rows;
}

function formatAvlDefinitions(rows: NonNullable<DeviceModelRow["avlDefinitions"]>) {
  return rows.map((row) => [row.avlId, row.name, row.unit || "", row.valueType || "NUMBER", row.multiplier ?? 1, row.category || ""].join(" | ")).join("\n");
}

function Field({ label, children }: { label: string; children: ReactNode }) {
  return <label className="grid gap-2 text-xs font-bold text-foreground"><span>{label}</span>{children}</label>;
}

function Notice({ message }: { message: string }) {
  return <div className="mb-4 rounded-lg border border-red-500/30 bg-red-500/10 p-3 text-sm text-red-700 dark:text-red-200">{message}</div>;
}
