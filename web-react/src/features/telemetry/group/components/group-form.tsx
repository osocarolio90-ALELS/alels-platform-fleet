import { FormEvent, useMemo, useState } from "react";
import { Save, Trash2, UsersRound, X } from "lucide-react";

import { DataTable, type DataTableColumn } from "@/components/data-table";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { PageHeader } from "@/components/ui/page-header";
import { SearchableSelect } from "@/components/ui/searchable-select";
import { OrganizationTableCard } from "@/features/organization/components/organization-ui";
import type { TelemetryDevice, TelemetryGroup, TelemetryGroupInput } from "../types/telemetry-group";

export function GroupForm({ group, devices, notice, saving, onCancel, onSave }: {
  group?: TelemetryGroup | null;
  devices: TelemetryDevice[];
  notice?: string;
  saving: boolean;
  onCancel: () => void;
  onSave: (value: TelemetryGroupInput) => void;
}) {
  const [name, setName] = useState(group?.groupName || "");
  const [description, setDescription] = useState(group?.description || "");
  const [ids, setIds] = useState<number[]>(group?.deviceIds || []);
  const [deviceNotice, setDeviceNotice] = useState("");
  const selected = ids.map((id) => devices.find((device) => device.id === id)).filter(Boolean) as TelemetryDevice[];
  const lockedCompanyId = selected[0]?.companyId ?? group?.companyId;
  const lockedCompany = selected[0]?.company ?? group?.company;

  const pickerOptions = useMemo(() => devices.filter((device) => !ids.includes(device.id)).map((device) => ({
    value: String(device.id),
    label: device.imei,
    extra: `${device.vehicle} | ${device.company} | ${device.deviceModel}`
  })), [devices, ids]);

  const columns = useMemo<DataTableColumn<TelemetryDevice>[]>(() => [
    { key: "imei", label: "IMEI", value: (row) => row.imei },
    { key: "vehicle", label: "Vehicle", value: (row) => row.vehicle },
    { key: "company", label: "Company", value: (row) => row.company },
    { key: "deviceModel", label: "Device Model", value: (row) => row.deviceModel }
  ], []);

  function addDevice(value: string) {
    const device = devices.find((item) => item.id === Number(value));
    if (!device) return;
    if (lockedCompanyId && device.companyId !== lockedCompanyId) {
      setDeviceNotice(`Device IMEI ${device.imei} belongs to ${device.company}. Devices inside one Group must belong to the same company. Please edit Device Company in Device Register first.`);
      return;
    }
    setIds((current) => [...current, device.id]);
    setDeviceNotice("");
  }

  function submit(event: FormEvent) {
    event.preventDefault();
    if (!name.trim() || ids.length === 0) return;
    onSave({ groupName: name.trim().toUpperCase(), description: description.trim(), deviceIds: ids });
  }

  return <section className="space-y-4 text-foreground">
    <PageHeader title={group ? "Edit Telemetry Group" : "Create New Telemetry Group"} icon={<UsersRound className="h-5 w-5" />} />
    <OrganizationTableCard>
      {notice || deviceNotice ? <div className="mb-4 rounded-lg border border-border bg-muted p-3 text-sm text-foreground">{deviceNotice || notice}</div> : null}
      <form className="space-y-5" onSubmit={submit}>
        <div className="grid gap-4 md:grid-cols-2">
          <label className="text-sm font-medium">Group Name<Input value={name} onChange={(event) => setName(event.target.value.toUpperCase())} required /></label>
          <label className="text-sm font-medium">Company<Input value={lockedCompany || "Locked after first device"} disabled /></label>
          <label className="text-sm font-medium md:col-span-2">Description<Input value={description} onChange={(event) => setDescription(event.target.value)} /></label>
        </div>
        <SearchableSelect label="Device List" value="" onChange={addDevice} options={pickerOptions} placeholder={devices.length ? "Select Device IMEI" : "No accessible active device"} />
        <DataTable data={selected} columns={columns} rowKey={(row) => row.id} emptyMessage="No device selected." actions={(row) =>
          <Button type="button" size="sm" variant="outline" onClick={() => setIds((current) => current.filter((id) => id !== row.id))}><Trash2 className="h-4 w-4" /> Remove</Button>
        } />
        <div className="flex justify-end gap-3">
          <Button type="button" variant="outline" onClick={onCancel}><X className="h-4 w-4" /> Cancel</Button>
          <Button type="submit" disabled={saving || !name.trim() || ids.length === 0}><Save className="h-4 w-4" /> Save</Button>
        </div>
      </form>
    </OrganizationTableCard>
  </section>;
}
