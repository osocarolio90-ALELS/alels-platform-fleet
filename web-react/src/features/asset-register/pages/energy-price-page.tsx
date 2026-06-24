import { FormEvent, ReactNode, useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Fuel, Pencil, Save, X } from "lucide-react";

import { DataTable, type DataTableColumn } from "@/components/data-table";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { getEnergyPriceCountries, getEnergyPrices, updateEnergyPrice, type EnergyPriceRow } from "@/features/asset-register/api/energy-price-api";
import { OrganizationPageHeader, OrganizationTableCard, StatusBadge, formatDateTime } from "@/features/organization/components/organization-ui";
import { useAuthStore } from "@/stores/auth-store";
import { hasRole, ENERGY_PRICE_EDIT_ROLES } from "@/lib/role-access";

export function EnergyPricePage() {
  const queryClient = useQueryClient();
  const user = useAuthStore((state) => state.user);
  const canEdit = hasRole(user?.role, ENERGY_PRICE_EDIT_ROLES);
  const [notice, setNotice] = useState<string | null>(null);
  const [selectedCountryCode, setSelectedCountryCode] = useState("ID");
  const [editing, setEditing] = useState<EnergyPriceRow | null>(null);
  const [form, setForm] = useState<EnergyPriceForm>(emptyForm());

  const { data: countries = [] } = useQuery({
    queryKey: ["asset-register", "energy-price-countries"],
    queryFn: getEnergyPriceCountries
  });

  const { data = [], isLoading, isError } = useQuery({
    queryKey: ["asset-register", "energy-prices", selectedCountryCode],
    queryFn: () => getEnergyPrices(selectedCountryCode),
    refetchInterval: 60_000
  });

  const selectedCountry = countries.find((country) => country.countryCode === form.countryCode);

  const updateMutation = useMutation({
    mutationFn: ({ id, input }: { id: number; input: EnergyPriceForm }) => updateEnergyPrice(id, normalizeInput(input)),
    onSuccess: () => {
      setNotice("Harga energy berhasil diperbarui.");
      setEditing(null);
      setForm(emptyForm());
      queryClient.invalidateQueries({ queryKey: ["asset-register", "energy-prices"] });
    },
    onError: (error) => setNotice(error instanceof Error ? error.message : "Update harga energy gagal.")
  });

  const columns = useMemo<DataTableColumn<EnergyPriceRow>[]>(() => [
    { key: "energyName", label: "Energy", value: (row) => row.energyName, render: (row) => <div><p className="font-extrabold text-white">{row.energyName}</p><p className="text-xs text-slate-400">{row.energyCode}</p></div> },
    { key: "energyGroup", label: "Group", value: (row) => row.energyGroup, render: (row) => <StatusBadge status={row.energyGroup} /> },
    { key: "unit", label: "Unit", value: (row) => row.unit, render: (row) => <span className="font-bold text-slate-100">{row.unit}</span> },
    { key: "priceEnergy", label: "Price Energy", value: (row) => money(row.priceEnergy, row.currency), render: (row) => <span className="font-extrabold text-sky-200">{money(row.priceEnergy, row.currency)}</span> },
    { key: "referencePriceCountryIdr", label: "Reference Price Country", value: (row) => money(row.referencePriceCountryIdr, row.currency), render: (row) => <span>{money(row.referencePriceCountryIdr, row.currency)}</span> },
    { key: "referencePriceGlobalUsd", label: "Reference Price Global (USD)", value: (row) => usd(row.referencePriceGlobalUsd), render: (row) => <span>{usd(row.referencePriceGlobalUsd)}</span> },
    { key: "country", label: "Country", value: (row) => `${row.country || "-"} ${row.countryCode || ""}`, render: (row) => <div><p>{row.country || "-"}</p><p className="text-xs text-slate-400">{row.countryCode || "-"} · {row.currency || "-"}</p></div> },
    { key: "source", label: "Source", value: (row) => `${row.priceSource || "-"} ${row.referenceSource || ""}`, render: (row) => <div><p>{row.priceSource || "-"}</p><p className="line-clamp-2 text-xs text-slate-400">{row.referenceSource || "-"}</p></div> },
    { key: "updatedAt", label: "Updated at", value: (row) => formatDateTime(row.updatedAt), render: (row) => <span>{formatDateTime(row.updatedAt)}</span> }
  ], []);

  function startEdit(row: EnergyPriceRow) {
    setNotice(null);
    setEditing(row);
    setForm({
      countryCode: row.countryCode || "ID",
      priceEnergy: toInput(row.priceEnergy)
    });
  }

  function changeCountry(countryCode: string) {
    setForm((current) => ({ ...current, countryCode }));
  }

  function submit(event: FormEvent) {
    event.preventDefault();
    if (!editing) return;
    updateMutation.mutate({ id: editing.id, input: form });
  }

  return (
    <section className="space-y-3 text-foreground">
      <OrganizationPageHeader icon={<Fuel className="h-5 w-5" />} title="Harga Energy" />

      {notice ? <Notice message={notice} /> : null}

      <div className="flex flex-wrap items-center gap-3 rounded-lg border border-white/10 bg-[#111317] px-3 py-2">
        <label className="grid gap-1 text-xs font-bold text-white">
          <span>Country</span>
          <select
            className="h-9 min-w-[220px] rounded-md border border-white/10 bg-[#070b12] px-3 text-sm text-white"
            value={selectedCountryCode}
            onChange={(event) => {
              setSelectedCountryCode(event.target.value);
              setEditing(null);
              setForm(emptyForm());
            }}
          >
            {countries.length === 0 ? <option value="ID">Indonesia (IDR)</option> : null}
            {countries.map((country) => (
              <option key={country.countryCode} value={country.countryCode}>
                {country.countryName} ({country.currency})
              </option>
            ))}
          </select>
        </label>
        <div className="pt-4 text-xs leading-5 text-slate-400">
          <span className="block"></span> 
          <span className="block">Harga referensi hanya rekomendasi, harga yang akan digunakan adalah Price Energy. Jika harga referensi tidak sesuai, tekan Edit untuk merubah Price Energy.</span>
        </div>
      </div>

      {editing ? (
        <OrganizationTableCard title={`Edit Harga Energy - ${editing.energyName}`}>
          <form className="grid gap-4 md:grid-cols-2" onSubmit={submit}>
            <ReadOnly label="Energy" value={editing.energyName} />
            <ReadOnly label="Unit" value={editing.unit} />
            <Field label="Country">
              <select className="h-10 rounded-md border border-white/10 bg-[#070b12] px-3 text-sm text-white" value={form.countryCode} onChange={(event) => changeCountry(event.target.value)}>
                {countries.map((country) => <option key={country.countryCode} value={country.countryCode}>{country.countryName} ({country.currency})</option>)}
              </select>
            </Field>
            <ReadOnly label="Currency" value={selectedCountry?.currency || editing.currency} />
            <Field label="Price Energy">
              <Input type="number" min="0" step="0.0001" value={form.priceEnergy} onChange={(event) => setForm((current) => ({ ...current, priceEnergy: event.target.value }))} />
            </Field>
            <div className="rounded-lg border border-sky-500/20 bg-sky-500/5 p-4 text-xs text-slate-300">
              Jika country diubah dan Price Energy dikosongkan, harga operasional akan mengikuti Reference Price Country terbaru untuk negara tersebut.
            </div>
            <div className="md:col-span-2 flex justify-end gap-3 pt-2">
              <Button type="button" variant="outline" onClick={() => { setEditing(null); setForm(emptyForm()); }}><X className="h-4 w-4" /> Cancel</Button>
              <Button type="submit" disabled={updateMutation.isPending}><Save className="h-4 w-4" /> Save</Button>
            </div>
          </form>
        </OrganizationTableCard>
      ) : null}

      <OrganizationTableCard>
        {isError ? <Notice message="Energy Price API belum tersedia, migration belum berjalan, atau backend belum aktif." /> : null}
        <DataTable
          data={data}
          columns={columns}
          rowKey={(row) => row.id}
          emptyMessage={isLoading ? "Loading energy prices..." : "No energy price found."}
          actions={(row) => canEdit ? <Button type="button" size="sm" variant="outline" onClick={() => startEdit(row)}><Pencil className="h-4 w-4" /> Edit</Button> : <span className="text-xs text-slate-400">Read only</span>}
        />
      </OrganizationTableCard>
    </section>
  );
}

type EnergyPriceForm = {
  countryCode: string;
  priceEnergy: string;
};

function emptyForm(): EnergyPriceForm {
  return { countryCode: "ID", priceEnergy: "" };
}

function normalizeInput(input: EnergyPriceForm) {
  return {
    countryCode: input.countryCode || null,
    priceEnergy: parseNullableNumber(input.priceEnergy)
  };
}

function parseNullableNumber(value: string) {
  if (value.trim() === "") return null;
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : null;
}

function toInput(value?: number | null) {
  return value == null ? "" : String(value);
}

function money(value?: number | null, currency = "IDR") {
  if (value == null) return "-";
  return `${currency || "IDR"} ${Number(value).toLocaleString("id-ID", { maximumFractionDigits: 4 })}`;
}

function usd(value?: number | null) {
  if (value == null) return "-";
  return `USD ${Number(value).toLocaleString("en-US", { maximumFractionDigits: 4 })}`;
}

function Field({ label, children }: { label: string; children: ReactNode }) {
  return <label className="grid gap-2 text-xs font-bold text-white"><span>{label}</span>{children}</label>;
}

function ReadOnly({ label, value }: { label: string; value?: string | null }) {
  return <Field label={label}><Input value={value || "-"} disabled /></Field>;
}

function Notice({ message }: { message: string }) {
  return <div className="rounded-lg border border-amber-500/30 bg-amber-500/10 p-3 text-sm text-amber-100">{message}</div>;
}
