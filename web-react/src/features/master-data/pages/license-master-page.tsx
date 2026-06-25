import { FormEvent, ReactNode, useMemo, useRef, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { BadgeCheck, ChevronDown, Pencil, Plus, Save, Search, Trash2, X } from "lucide-react";

import { DataTable, type DataTableBulkAction, type DataTableColumn } from "@/components/data-table";
import { PageHeader } from "@/components/ui/page-header";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
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

type FormState = { countryCode: string; countryName: string; name: string; active: string };
type CountryOption = { code: string; name: string };

export function LicenseMasterPage() {
  const queryClient = useQueryClient();
  const user = useAuthStore((state) => state.user);
  const canEdit = hasRole(user?.role, MASTER_DATA_EDIT_ROLES);
  const [formOpen, setFormOpen] = useState(false);
  const [editing, setEditing] = useState<LicenseMasterRow | null>(null);
  const [form, setForm] = useState<FormState>({ countryCode: "ID", countryName: "Indonesia", name: "", active: "ACTIVE" });
  const [notice, setNotice] = useState<string | null>(null);

  const licenseQuery = useQuery({ queryKey: ["license-master"], queryFn: getLicenseMaster });

  const countryOptions = useMemo<CountryOption[]>(() => {
    const map = new Map<string, CountryOption>();
    for (const row of licenseQuery.data || []) {
      const name = (row.countryName || row.countryCode || "").trim();
      const code = (row.countryCode || "").trim();
      if (!name) continue;
      map.set(name.toUpperCase(), { code, name });
    }
    return Array.from(map.values()).sort((a, b) => a.name.localeCompare(b.name));
  }, [licenseQuery.data]);

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
    { key: "countryName", label: "Country", value: (row) => row.countryName || row.countryCode, render: (row) => <span className="font-bold text-white">{row.countryName || row.countryCode}</span> },
    { key: "code", label: "Code", value: (row) => row.code },
    { key: "name", label: "Name", value: (row) => row.name, render: (row) => <span className="font-bold text-white">{row.name}</span> },
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

  function setCountryName(value: string) {
    const selected = countryOptions.find((country) => country.name.toUpperCase() === value.trim().toUpperCase());
    setForm((current) => ({ ...current, countryName: value, countryCode: selected?.code || "" }));
  }

  function submit(event: FormEvent) {
    event.preventDefault();
    if (!canEdit) return;
    if (!form.countryName.trim() || !form.name.trim()) { setNotice("Country dan Name wajib diisi."); return; }
    saveMutation.mutate();
  }

  if (formOpen) {
    return <section className="space-y-5 text-foreground"><PageHeader icon={<BadgeCheck className="h-5 w-5" />} title={editing ? "Edit License Master" : "Create New License Master"} /><OrganizationTableCard>{notice ? <Notice message={notice} /> : null}<form className="grid gap-4 md:grid-cols-3" onSubmit={submit}><CountryInput label="Country" required value={form.countryName} onChange={setCountryName} options={countryOptions} placeholder="Indonesia" /><Field label="Name" required><Input required value={form.name} onChange={(event) => setForm((current) => ({ ...current, name: event.target.value.toUpperCase() }))} placeholder="SIM A" /></Field><Field label="Status"><select className="h-10 rounded-md border border-white/10 bg-[#070b12] px-3 text-sm text-white" value={form.active} onChange={(event) => setForm((current) => ({ ...current, active: event.target.value }))}><option value="ACTIVE">ACTIVE</option><option value="INACTIVE">INACTIVE</option></select></Field><div className="md:col-span-3 flex justify-end gap-3"><Button type="button" variant="outline" onClick={closeForm}><X className="h-4 w-4" /> Cancel</Button><Button type="submit" disabled={saveMutation.isPending}><Save className="h-4 w-4" /> Save</Button></div></form></OrganizationTableCard></section>;
  }

  return <section className="space-y-5 text-foreground"><PageHeader icon={<BadgeCheck className="h-5 w-5" />} title="License Master" actions={canEdit ? <Button type="button" onClick={openCreate}><Plus className="h-4 w-4" /> Created New</Button> : null} /><OrganizationTableCard>{notice ? <Notice message={notice} /> : null}<DataTable data={licenseQuery.data || []} columns={columns} rowKey={(row) => row.id} emptyMessage={licenseQuery.isLoading ? "Loading license master..." : "No license type found."} bulkActions={bulkActions} actions={(row) => canEdit ? <div className="flex items-center gap-2"><Button type="button" size="icon" variant="outline" title="Edit" onClick={() => openEdit(row)}><Pencil className="h-4 w-4" /></Button><Button type="button" size="icon" variant="destructive" title="Delete" onClick={() => deleteMutation.mutate(row.id)}><Trash2 className="h-4 w-4" /></Button></div> : <span className="text-xs text-slate-400">Read only</span>} /></OrganizationTableCard></section>;
}

function toInput(form: FormState): LicenseMasterInput { return { countryCode: form.countryCode || null, countryName: form.countryName.trim(), name: form.name.trim().toUpperCase(), active: form.active === "ACTIVE" }; }

function CountryInput({ label, required, value, onChange, options, placeholder }: { label: string; required?: boolean; value: string; onChange: (value: string) => void; options: CountryOption[]; placeholder?: string }) {
  const [open, setOpen] = useState(false);
  const [query, setQuery] = useState("");
  const rootRef = useRef<HTMLLabelElement>(null);
  const term = query.trim().toLowerCase();
  const filtered = useMemo(() => {
    if (!term) return options;
    return options.filter((option) => `${option.name} ${option.code}`.toLowerCase().includes(term));
  }, [options, term]);

  function select(option: CountryOption) {
    onChange(option.name);
    setQuery("");
    setOpen(false);
  }

  function useTypedCountry() {
    const next = query.trim();
    if (!next) return;
    onChange(titleCase(next));
    setQuery("");
    setOpen(false);
  }

  return (
    <label ref={rootRef} onBlur={(event) => { if (!rootRef.current?.contains(event.relatedTarget as Node | null)) setOpen(false); }} className="relative grid gap-2 text-xs font-bold text-white">
      <span>{label}{required ? <span className="text-red-400"> *</span> : null}</span>
      <button type="button" onClick={() => setOpen((current) => !current)} className="flex h-10 w-full items-center justify-between rounded-md border border-white/10 bg-[#070b12] px-3 text-left text-sm text-white">
        <span className={value ? "truncate" : "truncate text-slate-500"}>{value || placeholder || "Select option"}</span>
        <ChevronDown className="h-4 w-4 text-slate-400" />
      </button>
      {required ? <input className="sr-only" tabIndex={-1} required value={value} onChange={() => undefined} /> : null}
      {open ? <div className="absolute left-0 right-0 top-[4.25rem] z-50 rounded-xl border border-sky-400/40 bg-[#080d16] p-2 shadow-2xl shadow-black/60">
        <div className="relative mb-2">
          <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" />
          <Input autoFocus value={query} onChange={(event) => setQuery(event.target.value)} onKeyDown={(event) => { if (event.key === "Enter") { event.preventDefault(); useTypedCountry(); } }} placeholder="Search..." className="pl-9 pr-9" />
          {query ? <Button type="button" variant="ghost" size="icon" className="absolute right-1 top-1/2 h-7 w-7 -translate-y-1/2" onClick={() => setQuery("")}><X className="h-3.5 w-3.5" /></Button> : null}
        </div>
        <div className="max-h-64 overflow-y-auto rounded-lg border border-white/5 bg-[#0b1018]">
          <button type="button" className="block w-full rounded-md border border-sky-400/30 bg-sky-500/10 px-3 py-2 text-left text-sm font-bold text-sky-100 hover:bg-sky-500/20" onMouseDown={(event) => event.preventDefault()} onClick={() => { setQuery(""); onChange(""); }}>
            + New Country
            <span className="block text-xs font-medium text-sky-200/80">Type country name in search field, then press Enter or choose manual country.</span>
          </button>
          {filtered.map((option) => <button key={`${option.code}-${option.name}`} type="button" className="mt-1 block w-full px-3 py-2 text-left text-sm text-white hover:bg-sky-500/15" onMouseDown={(event) => event.preventDefault()} onClick={() => select(option)}><span className="block truncate font-bold">{option.name}</span>{option.code ? <span className="block truncate text-xs text-slate-400">{option.code}</span> : null}</button>)}
          {term ? <button type="button" className="block w-full border-t border-white/5 px-3 py-2 text-left text-sm text-sky-200 hover:bg-sky-500/15" onMouseDown={(event) => event.preventDefault()} onClick={useTypedCountry}>Use manual country: <span className="font-bold">{titleCase(query)}</span></button> : null}
          {!filtered.length && !term ? <div className="px-3 py-3 text-sm text-slate-400">No country found.</div> : null}
        </div>
      </div> : null}
    </label>
  );
}

function titleCase(value: string) { return value.trim().toLowerCase().replace(/\s+/g, " ").replace(/\b\w/g, (letter) => letter.toUpperCase()); }
function Field({ label, required, children }: { label: string; required?: boolean; children: ReactNode }) { return <label className="grid gap-2 text-xs font-bold text-white"><span>{label}{required ? <span className="text-red-400"> *</span> : null}</span>{children}</label>; }
function Notice({ message }: { message: string }) { return <div className="rounded-lg border border-red-400/30 bg-red-500/10 px-4 py-3 text-sm text-red-100">{message}</div>; }
