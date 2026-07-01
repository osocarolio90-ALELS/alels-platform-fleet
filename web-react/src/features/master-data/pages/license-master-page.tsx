import { FormEvent, ReactNode, useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { BadgeCheck, Pencil, Plus, Save, Trash2, X } from "lucide-react";

import { DataTable, type DataTableBulkAction, type DataTableColumn } from "@/components/data-table";
import { PageHeader } from "@/components/ui/page-header";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { SearchableSelect } from "@/components/ui/searchable-select";
import { OrganizationTableCard, StatusBadge, formatDateTime } from "@/features/organization/components/organization-ui";
import { useAuthStore } from "@/stores/auth-store";
import { hasRole, MASTER_DATA_EDIT_ROLES } from "@/lib/role-access";
import {
  createLicenseMaster,
  deleteLicenseMaster,
  getLicenseMaster,
  updateLicenseMaster,
  type LicenseMasterInput,
  type LicenseMasterRow
} from "@/features/master-data/api/license-master-api";
import { getMasterCountries } from "@/features/master-data/api/master-data-api";

type FormState = { countryCode: string; countryName: string; name: string; active: string };
export function LicenseMasterPage() {
  const queryClient = useQueryClient();
  const user = useAuthStore((state) => state.user);
  const canEdit = hasRole(user?.role, MASTER_DATA_EDIT_ROLES);
  const [formOpen, setFormOpen] = useState(false);
  const [editing, setEditing] = useState<LicenseMasterRow | null>(null);
  const [form, setForm] = useState<FormState>({ countryCode: "ID", countryName: "Indonesia", name: "", active: "ACTIVE" });
  const [notice, setNotice] = useState<string | null>(null);

  const licenseQuery = useQuery({ queryKey: ["license-master"], queryFn: getLicenseMaster });
  const countryQuery = useQuery({ queryKey: ["master-data", "countries"], queryFn: getMasterCountries });
  const countryOptions = useMemo(() => (countryQuery.data || []).map((country) => ({ value: country.countryCode, label: country.countryName, extra: country.currency })), [countryQuery.data]);

  const saveMutation = useMutation({
    mutationFn: () => editing ? updateLicenseMaster(editing.id, toInput(form)) : createLicenseMaster(toInput(form)),
    onSuccess: () => {
      closeForm();
      queryClient.invalidateQueries({ queryKey: ["license-master"] });
    },
    onError: (error: any) => setNotice(error?.response?.data?.message || "License Master gagal disimpan.")
  });
  const deleteMutation = useMutation({ mutationFn: deleteLicenseMaster, onSuccess: () => queryClient.invalidateQueries({ queryKey: ["license-master"] }) });

  const columns = useMemo<DataTableColumn<LicenseMasterRow>[]>(() => [
    { key: "countryName", label: "Country", value: (row) => row.countryName || row.countryCode, render: (row) => <span className="font-bold text-foreground">{row.countryName || row.countryCode}</span> },
    { key: "code", label: "Code", value: (row) => row.code },
    { key: "name", label: "Name", value: (row) => row.name, render: (row) => <span className="font-bold text-foreground">{row.name}</span> },
    { key: "createdAt", label: "Created at", value: (row) => row.createdAt || "", render: (row) => formatDateTime(row.createdAt) },
    { key: "createdBy", label: "Created By", value: (row) => row.createdBy || "-" },
    { key: "status", label: "Status", value: (row) => row.status, render: (row) => <StatusBadge status={row.active ? "ACTIVE" : "INACTIVE"} /> }
  ], []);

  const bulkActions = useMemo<DataTableBulkAction<LicenseMasterRow>[]>(() => canEdit ? [
    { key: "delete", label: "Delete", icon: <Trash2 className="h-4 w-4" />, confirmMessage: (rows) => `Delete ${rows.length} selected license type(s)?`, onClick: async (rows) => { for (const row of rows) await deleteLicenseMaster(row.id); queryClient.invalidateQueries({ queryKey: ["license-master"] }); } }
  ] : [], [canEdit, queryClient]);

  function openCreate() { setEditing(null); setNotice(null); setForm({ countryCode: "", countryName: "", name: "", active: "ACTIVE" }); setFormOpen(true); }
  function openEdit(row: LicenseMasterRow) { setEditing(row); setNotice(null); setForm({ countryCode: row.countryCode || "", countryName: row.countryName || row.countryCode || "", name: row.name, active: row.active ? "ACTIVE" : "INACTIVE" }); setFormOpen(true); }
  function closeForm() { setEditing(null); setFormOpen(false); setNotice(null); }

  function setCountryCode(value: string) {
    const selected = countryQuery.data?.find((country) => country.countryCode === value);
    setForm((current) => ({ ...current, countryCode: value, countryName: selected?.countryName || "" }));
  }

  function submit(event: FormEvent) {
    event.preventDefault();
    if (!canEdit) return;
    if (!form.countryName.trim() || !form.name.trim()) { setNotice("Country dan Name wajib diisi."); return; }
    saveMutation.mutate();
  }

  if (formOpen) {
    return <section className="space-y-5 text-foreground"><PageHeader icon={<BadgeCheck className="h-5 w-5" />} title={editing ? "Edit License Master" : "Create New License Master"} /><OrganizationTableCard>{notice ? <Notice message={notice} /> : null}<form className="grid gap-4 md:grid-cols-3" onSubmit={submit}><SearchableSelect label="Country" required value={form.countryCode} onChange={setCountryCode} options={countryOptions} placeholder="Select Country" /><Field label="Name" required><Input required value={form.name} onChange={(event) => setForm((current) => ({ ...current, name: event.target.value.toUpperCase() }))} placeholder="SIM A" /></Field><Field label="Status"><select className="h-10 rounded-md border border-input bg-background px-3 text-sm text-foreground" value={form.active} onChange={(event) => setForm((current) => ({ ...current, active: event.target.value }))}><option value="ACTIVE">ACTIVE</option><option value="INACTIVE">INACTIVE</option></select></Field><div className="md:col-span-3 flex justify-end gap-3"><Button type="button" variant="outline" onClick={closeForm}><X className="h-4 w-4" /> Cancel</Button><Button type="submit" disabled={saveMutation.isPending}><Save className="h-4 w-4" /> Save</Button></div></form></OrganizationTableCard></section>;
  }

  return <section className="space-y-5 text-foreground"><PageHeader icon={<BadgeCheck className="h-5 w-5" />} title="License Master" actions={canEdit ? <Button type="button" onClick={openCreate}><Plus className="h-4 w-4" /> Created New</Button> : null} /><OrganizationTableCard>{notice ? <Notice message={notice} /> : null}<DataTable data={licenseQuery.data || []} columns={columns} rowKey={(row) => row.id} emptyMessage={licenseQuery.isLoading ? "Loading license master..." : "No license type found."} bulkActions={bulkActions} actions={(row) => canEdit ? <div className="flex items-center gap-2"><Button type="button" size="icon" variant="outline" title="Edit" onClick={() => openEdit(row)}><Pencil className="h-4 w-4" /></Button><Button type="button" size="icon" variant="destructive" title="Delete" onClick={() => deleteMutation.mutate(row.id)}><Trash2 className="h-4 w-4" /></Button></div> : <span className="text-xs text-slate-400">Read only</span>} /></OrganizationTableCard></section>;
}

function toInput(form: FormState): LicenseMasterInput { return { countryCode: form.countryCode || null, countryName: form.countryName.trim(), name: form.name.trim().toUpperCase(), active: form.active === "ACTIVE" }; }

function Field({ label, required, children }: { label: string; required?: boolean; children: ReactNode }) { return <label className="grid gap-2 text-xs font-bold text-foreground"><span>{label}{required ? <span className="text-red-400"> *</span> : null}</span>{children}</label>; }
function Notice({ message }: { message: string }) { return <div className="rounded-lg border border-red-400/30 bg-red-500/10 px-4 py-3 text-sm text-red-100">{message}</div>; }
