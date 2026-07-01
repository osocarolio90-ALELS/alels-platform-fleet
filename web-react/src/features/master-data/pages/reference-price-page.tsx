import { FormEvent, ReactNode, useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { DatabaseZap, Pencil, Plus, RefreshCw, Save, X } from "lucide-react";

import { DataTable, type DataTableBulkAction, type DataTableColumn } from "@/components/data-table";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { SearchableSelect } from "@/components/ui/searchable-select";
import { createEnergyReferenceCountry, getEnergyReferenceCountries, getEnergyReferences, updateEnergyReference, updateEnergyReferenceProvider, type EnergyReferenceCountryInput, type EnergyReferenceCountryRow, type EnergyReferenceRow } from "@/features/master-data/api/reference-price-api";
import { OrganizationTableCard, StatusBadge, formatDateTime } from "@/features/organization/components/organization-ui";
import { PageHeader } from "@/components/ui/page-header";
import { useAuthStore } from "@/stores/auth-store";
import { hasRole, ENERGY_REFERENCE_EDIT_ROLES } from "@/lib/role-access";

export function ReferencePricePage() {
  const queryClient = useQueryClient();
  const user = useAuthStore((state) => state.user);
  const isSuperAdmin = hasRole(user?.role, ENERGY_REFERENCE_EDIT_ROLES);
  const [notice, setNotice] = useState<string | null>(null);
  const [countryCode, setCountryCode] = useState("ID");
  const [editing, setEditing] = useState<EnergyReferenceRow | null>(null);
  const [form, setForm] = useState<EnergyReferenceForm>(emptyForm());
  const [creatingCountry, setCreatingCountry] = useState(false);
  const [countryForm, setCountryForm] = useState<EnergyCountryForm>(emptyCountryForm());

  const { data: countries = [] } = useQuery({
    queryKey: ["master-data", "energy-reference-countries"],
    queryFn: getEnergyReferenceCountries
  });

  const { data = [], isLoading, isError } = useQuery({
    queryKey: ["master-data", "energy-reference-prices", countryCode],
    queryFn: () => getEnergyReferences(countryCode),
    enabled: Boolean(countryCode),
    refetchInterval: 60_000
  });

  const providerMutation = useMutation({
    mutationFn: () => updateEnergyReferenceProvider(countryCode),
    onSuccess: (result) => {
      setNotice(result.message || `Harga referensi berhasil disinkronkan (${result.updatedRows} row).`);
      invalidateAll();
    },
    onError: (error) => setNotice(error instanceof Error ? error.message : "Update provider gagal.")
  });

  const updateMutation = useMutation({
    mutationFn: ({ id, input }: { id: number; input: EnergyReferenceForm }) => updateEnergyReference(id, normalizeInput(input)),
    onSuccess: () => {
      setNotice("Harga referensi berhasil diperbarui.");
      setEditing(null);
      setForm(emptyForm());
      invalidateAll();
    },
    onError: (error) => setNotice(error instanceof Error ? error.message : "Edit harga referensi gagal.")
  });

  const countryMutation = useMutation({
    mutationFn: (input: EnergyCountryForm) => createEnergyReferenceCountry(normalizeCountryInput(input)),
    onSuccess: () => {
      setNotice("Country reference berhasil disimpan.");
      setCreatingCountry(false);
      setCountryForm(emptyCountryForm());
      invalidateAll();
    },
    onError: (error) => setNotice(error instanceof Error ? error.message : "Create country gagal.")
  });


  const bulkProviderMutation = useMutation({
    mutationFn: async (rows: EnergyReferenceRow[]) => {
      await Promise.all(rows.map((row) => updateEnergyReference(row.id, {
        referencePriceCountry: row.providerReferencePriceCountry ?? row.referencePriceCountry ?? null,
        referencePriceGlobalUsd: row.providerReferencePriceGlobalUsd ?? row.referencePriceGlobalUsd ?? null,
        providerReferencePriceCountry: row.providerReferencePriceCountry ?? null,
        providerReferencePriceGlobalUsd: row.providerReferencePriceGlobalUsd ?? null,
        sourceName: row.sourceName || "ALELS_REFERENCE_PROVIDER",
        sourceDetail: row.sourceDetail || null,
        sourceUrl: row.sourceUrl || null,
        providerStatus: row.providerStatus || "MANUAL_OVERRIDE"
      })));
    },
    onSuccess: () => {
      setNotice("Harga referensi terpilih berhasil diperbarui dari provider/reference.");
      invalidateAll();
    },
    onError: (error) => setNotice(error instanceof Error ? error.message : "Bulk update harga referensi gagal.")
  });

  function invalidateAll() {
    queryClient.invalidateQueries({ queryKey: ["master-data", "energy-reference-prices"] });
    queryClient.invalidateQueries({ queryKey: ["master-data", "energy-reference-countries"] });
    queryClient.invalidateQueries({ queryKey: ["asset-register", "energy-prices"] });
  }


  const bulkActions = useMemo<DataTableBulkAction<EnergyReferenceRow>[]>(() => {
    if (!isSuperAdmin) return [];
    return [
      {
        key: "update-selected-from-provider",
        label: "Update Selected from Provider",
        icon: <RefreshCw className="h-4 w-4" />,
        confirmMessage: (rows) => `Update ${rows.length} selected harga master row(s) from provider/reference?`,
        disabled: () => bulkProviderMutation.isPending,
        onClick: (rows) => bulkProviderMutation.mutateAsync(rows)
      }
    ];
  }, [bulkProviderMutation, isSuperAdmin]);

  const columns = useMemo<DataTableColumn<EnergyReferenceRow>[]>(() => [
    { key: "energyName", label: "Energy", value: (row) => row.energyName, render: (row) => <div><p className="font-extrabold text-foreground">{row.energyName}</p><p className="text-xs text-muted-foreground">{row.energyCode}</p></div> },
    { key: "energyGroup", label: "Group", value: (row) => row.energyGroup, render: (row) => <StatusBadge status={row.energyGroup} /> },
    { key: "unit", label: "Unit", value: (row) => row.unit, render: (row) => <span className="font-bold text-foreground">{row.unit}</span> },
    { key: "countryName", label: "Country", value: (row) => `${row.countryName} ${row.countryCode}`, render: (row) => <div><p className="font-bold text-foreground">{row.countryName}</p><p className="text-xs text-muted-foreground">{row.countryCode} · {row.currency}</p></div> },
    { key: "referencePriceCountry", label: "Reference Country", value: (row) => money(row.referencePriceCountry, row.currency), render: (row) => <span className="font-extrabold text-foreground">{money(row.referencePriceCountry, row.currency)}</span> },
    { key: "referencePriceGlobalUsd", label: "Reference Global USD", value: (row) => usd(row.referencePriceGlobalUsd), render: (row) => <span>{usd(row.referencePriceGlobalUsd)}</span> },
    { key: "providerStatus", label: "Provider Status", value: (row) => row.providerStatus || "-", render: (row) => <StatusBadge status={row.providerStatus || "-"} /> },
    { key: "sourceName", label: "Source", value: (row) => `${row.sourceName} ${row.sourceUrl || ""}`, render: (row) => <div><p className="font-extrabold text-foreground">{row.sourceName || "-"}</p><p className="line-clamp-2 text-xs text-muted-foreground">{row.sourceUrl || row.sourceDetail || "-"}</p></div> },
    { key: "lastSyncAt", label: "Last Sync", value: (row) => formatDateTime(row.lastSyncAt), render: (row) => <span>{formatDateTime(row.lastSyncAt)}</span> },
    { key: "updatedAt", label: "Updated at", value: (row) => formatDateTime(row.updatedAt), render: (row) => <span>{formatDateTime(row.updatedAt)}</span> }
  ], []);

  function startEdit(row: EnergyReferenceRow) {
    setNotice(null);
    setEditing(row);
    setForm({
      referencePriceCountry: toInput(row.referencePriceCountry),
      referencePriceGlobalUsd: toInput(row.referencePriceGlobalUsd),
      providerReferencePriceCountry: toInput(row.providerReferencePriceCountry),
      providerReferencePriceGlobalUsd: toInput(row.providerReferencePriceGlobalUsd),
      sourceName: row.sourceName || "ALELS_REFERENCE_PROVIDER",
      sourceDetail: row.sourceDetail || "",
      sourceUrl: row.sourceUrl || "",
      providerStatus: row.providerStatus || "MANUAL_OVERRIDE"
    });
  }

  function submit(event: FormEvent) {
    event.preventDefault();
    if (!editing) return;
    updateMutation.mutate({ id: editing.id, input: form });
  }

  function submitCountry(event: FormEvent) {
    event.preventDefault();
    countryMutation.mutate(countryForm);
  }

  if (creatingCountry && isSuperAdmin) {
    return (
      <section className="space-y-5 text-foreground">
        <PageHeader icon={<DatabaseZap className="h-5 w-5" />} title="Create New Harga Master Country" />
        {notice ? <Notice message={notice} /> : null}
        <OrganizationTableCard>
          <form className="grid gap-4 md:grid-cols-3" onSubmit={submitCountry}>
            <Field label="Country Code"><Input value={countryForm.countryCode} onChange={(event) => setCountryForm((current) => ({ ...current, countryCode: event.target.value.toUpperCase() }))} placeholder="JP" maxLength={10} /></Field>
            <Field label="Country Name"><Input value={countryForm.countryName} onChange={(event) => setCountryForm((current) => ({ ...current, countryName: event.target.value }))} placeholder="Japan" /></Field>
            <Field label="Currency"><Input value={countryForm.currency} onChange={(event) => setCountryForm((current) => ({ ...current, currency: event.target.value.toUpperCase() }))} placeholder="JPY" maxLength={10} /></Field>
            <Field label="USD to Local Rate"><Input type="number" min="0" step="0.000001" value={countryForm.usdToLocalRate} onChange={(event) => setCountryForm((current) => ({ ...current, usdToLocalRate: event.target.value }))} /></Field>
            <Field label="Source Name"><Input value={countryForm.sourceName} onChange={(event) => setCountryForm((current) => ({ ...current, sourceName: event.target.value }))} placeholder="Official Provider" /></Field>
            <Field label="Source URL"><Input value={countryForm.sourceUrl} onChange={(event) => setCountryForm((current) => ({ ...current, sourceUrl: event.target.value }))} placeholder="https://..." /></Field>
            <div className="md:col-span-3 flex justify-end gap-3">
              <Button type="button" variant="outline" onClick={() => { setCreatingCountry(false); setCountryForm(emptyCountryForm()); }}><X className="h-4 w-4" /> Cancel</Button>
              <Button type="submit" disabled={countryMutation.isPending}><Save className="h-4 w-4" /> Save Country</Button>
            </div>
          </form>
        </OrganizationTableCard>
      </section>
    );
  }

  return (
    <section className="space-y-5 text-foreground">
      <PageHeader icon={<DatabaseZap className="h-5 w-5" />} title="Harga Master" actions={isSuperAdmin ? <Button type="button" onClick={() => setCreatingCountry(true)}><Plus className="h-4 w-4" /> Created New Country</Button> : null} />

      {notice ? <Notice message={notice} /> : null}

      <OrganizationTableCard>
        <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
          <div className="w-full max-w-sm">
            <SearchableSelect label="Country" value={countryCode} onChange={setCountryCode} options={countries.map((country) => ({ value: country.countryCode, label: country.countryName, extra: country.currency }))} />
          </div>
          <div className="flex items-center gap-2">
            <Button type="button" onClick={() => providerMutation.mutate()} disabled={providerMutation.isPending}>
              <RefreshCw className="h-4 w-4" /> Update Provider
            </Button>
          </div>
        </div>

        {isError ? <Notice message="Harga Referensi API belum tersedia atau migration 010 belum dijalankan." /> : null}
        <DataTable
          data={data}
          columns={columns}
          rowKey={(row) => row.id}
          emptyMessage={isLoading ? "Loading harga referensi..." : "No reference price found."}
          selectable={isSuperAdmin}
          bulkActions={bulkActions}
          actions={(row) => isSuperAdmin ? <Button type="button" size="sm" variant="outline" onClick={() => startEdit(row)}><Pencil className="h-4 w-4" /> Edit</Button> : <span className="text-xs text-slate-400">Read only</span>}
        />
      </OrganizationTableCard>

      {editing && isSuperAdmin ? (
        <OrganizationTableCard title={`Edit Harga Referensi - ${editing.energyName}`}>
          <form className="grid gap-4 md:grid-cols-2" onSubmit={submit}>
            <ReadOnly label="Energy" value={editing.energyName} />
            <ReadOnly label="Country" value={`${editing.countryName} (${editing.currency})`} />
            <Field label={`Reference Country (${editing.currency})`}><Input type="number" min="0" step="0.0001" value={form.referencePriceCountry} onChange={(event) => setForm((current) => ({ ...current, referencePriceCountry: event.target.value }))} /></Field>
            <Field label="Reference Global (USD)"><Input type="number" min="0" step="0.0001" value={form.referencePriceGlobalUsd} onChange={(event) => setForm((current) => ({ ...current, referencePriceGlobalUsd: event.target.value }))} /></Field>
            <Field label="Provider Reference Country"><Input type="number" min="0" step="0.0001" value={form.providerReferencePriceCountry} onChange={(event) => setForm((current) => ({ ...current, providerReferencePriceCountry: event.target.value }))} /></Field>
            <Field label="Provider Reference Global USD"><Input type="number" min="0" step="0.0001" value={form.providerReferencePriceGlobalUsd} onChange={(event) => setForm((current) => ({ ...current, providerReferencePriceGlobalUsd: event.target.value }))} /></Field>
            <Field label="Source"><Input value={form.sourceName} onChange={(event) => setForm((current) => ({ ...current, sourceName: event.target.value }))} maxLength={120} /></Field>
            <Field label="Provider Status"><Input value={form.providerStatus} onChange={(event) => setForm((current) => ({ ...current, providerStatus: event.target.value }))} maxLength={40} /></Field>
            <Field label="Source URL"><Input value={form.sourceUrl} onChange={(event) => setForm((current) => ({ ...current, sourceUrl: event.target.value }))} /></Field>
            <Field label="Source Detail"><Input value={form.sourceDetail} onChange={(event) => setForm((current) => ({ ...current, sourceDetail: event.target.value }))} /></Field>
            <div className="md:col-span-2 flex justify-end gap-3 pt-2">
              <Button type="button" variant="outline" onClick={() => { setEditing(null); setForm(emptyForm()); }}><X className="h-4 w-4" /> Cancel</Button>
              <Button type="submit" disabled={updateMutation.isPending}><Save className="h-4 w-4" /> Save</Button>
            </div>
          </form>
        </OrganizationTableCard>
      ) : null}
    </section>
  );
}

type EnergyReferenceForm = {
  referencePriceCountry: string;
  referencePriceGlobalUsd: string;
  providerReferencePriceCountry: string;
  providerReferencePriceGlobalUsd: string;
  sourceName: string;
  sourceDetail: string;
  sourceUrl: string;
  providerStatus: string;
};

type EnergyCountryForm = {
  countryCode: string;
  countryName: string;
  currency: string;
  usdToLocalRate: string;
  sourceName: string;
  sourceUrl: string;
};

function emptyForm(): EnergyReferenceForm {
  return { referencePriceCountry: "", referencePriceGlobalUsd: "", providerReferencePriceCountry: "", providerReferencePriceGlobalUsd: "", sourceName: "ALELS_REFERENCE_PROVIDER", sourceDetail: "", sourceUrl: "", providerStatus: "MANUAL_OVERRIDE" };
}

function emptyCountryForm(): EnergyCountryForm {
  return { countryCode: "", countryName: "", currency: "", usdToLocalRate: "", sourceName: "MANUAL_PROVIDER_LINK", sourceUrl: "" };
}

function normalizeInput(input: EnergyReferenceForm) {
  return {
    referencePriceCountry: toNumber(input.referencePriceCountry),
    referencePriceGlobalUsd: toNumber(input.referencePriceGlobalUsd),
    providerReferencePriceCountry: toNumber(input.providerReferencePriceCountry),
    providerReferencePriceGlobalUsd: toNumber(input.providerReferencePriceGlobalUsd),
    sourceName: input.sourceName.trim() || null,
    sourceDetail: input.sourceDetail.trim() || null,
    sourceUrl: input.sourceUrl.trim() || null,
    providerStatus: input.providerStatus.trim() || null
  };
}

function normalizeCountryInput(input: EnergyCountryForm): EnergyReferenceCountryInput {
  return {
    countryCode: input.countryCode.trim().toUpperCase(),
    countryName: input.countryName.trim(),
    currency: input.currency.trim().toUpperCase(),
    usdToLocalRate: toNumber(input.usdToLocalRate),
    sourceName: input.sourceName.trim() || "MANUAL_PROVIDER_LINK",
    sourceUrl: input.sourceUrl.trim() || null
  };
}

function toInput(value?: number | null) {
  return value === null || value === undefined ? "" : String(value);
}

function toNumber(value: string) {
  const trimmed = value.trim();
  if (trimmed === "") return null;
  const parsed = Number(trimmed);
  return Number.isFinite(parsed) ? parsed : null;
}

function money(value?: number | null, currency?: string | null) {
  if (value === null || value === undefined) return "-";
  const code = currency || "IDR";
  return `${code} ${Number(value).toLocaleString("id-ID", { maximumFractionDigits: 4 })}`;
}

function usd(value?: number | null) {
  if (value === null || value === undefined) return "-";
  return `USD ${Number(value).toLocaleString("en-US", { maximumFractionDigits: 4 })}`;
}

function Field({ label, children }: { label: string; children: ReactNode }) {
  return <label className="grid gap-2 text-xs font-bold text-foreground"><span>{label}</span>{children}</label>;
}

function ReadOnly({ label, value }: { label: string; value?: string | null }) {
  return <Field label={label}><Input value={value || "-"} disabled /></Field>;
}

function Notice({ message }: { message: string }) {
  return <div className="rounded-lg border border-amber-500/30 bg-amber-500/10 p-3 text-sm text-amber-100">{message}</div>;
}


export const EnergyReferencePage = ReferencePricePage;
