import { ReactNode, useMemo, useState } from "react";
import { CarFront, IdCard, Recycle } from "lucide-react";

import { DataTable, type DataTableColumn } from "@/components/data-table";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";

type AssetWastedTab = "vehicle" | "driver";

type VehicleWastedRow = {
  id: number;
  vehicleNumber: string;
  companyName: string;
  deletedAt: string;
  deletedReason?: string;
};

type DriverWastedRow = {
  id: number;
  driverName: string;
  companyName: string;
  deletedAt: string;
  deletedReason?: string;
};

const vehicleRows: VehicleWastedRow[] = [];
const driverRows: DriverWastedRow[] = [];

export function AssetWastedPage() {
  const [activeTab, setActiveTab] = useState<AssetWastedTab>("vehicle");

  const vehicleColumns = useMemo<DataTableColumn<VehicleWastedRow>[]>(() => [
    { key: "vehicleNumber", label: "Vehicle", value: (row) => row.vehicleNumber, render: (row) => <span className="font-extrabold text-white">{row.vehicleNumber}</span> },
    { key: "companyName", label: "Company", value: (row) => row.companyName || "-" },
    { key: "deletedAt", label: "Deleted at", value: (row) => row.deletedAt || "-" },
    { key: "deletedReason", label: "Reason", value: (row) => row.deletedReason || "-" }
  ], []);

  const driverColumns = useMemo<DataTableColumn<DriverWastedRow>[]>(() => [
    { key: "driverName", label: "Driver", value: (row) => row.driverName, render: (row) => <span className="font-extrabold text-white">{row.driverName}</span> },
    { key: "companyName", label: "Company", value: (row) => row.companyName || "-" },
    { key: "deletedAt", label: "Deleted at", value: (row) => row.deletedAt || "-" },
    { key: "deletedReason", label: "Reason", value: (row) => row.deletedReason || "-" }
  ], []);

  return (
    <section className="space-y-5 text-foreground">
      <AssetRegisterPageHeader icon={<Recycle className="h-5 w-5" />} title="Wasted Asset Register" />

      <Card className="border-white/10 bg-white/[0.03] p-3">
        <div className="flex flex-wrap gap-2">
          <Button
            type="button"
            variant={activeTab === "vehicle" ? "default" : "outline"}
            onClick={() => setActiveTab("vehicle")}
            className="gap-2"
          >
            <CarFront className="h-4 w-4" /> Vehicle Wasted
          </Button>
          <Button
            type="button"
            variant={activeTab === "driver" ? "default" : "outline"}
            onClick={() => setActiveTab("driver")}
            className="gap-2"
          >
            <IdCard className="h-4 w-4" /> Driver Wasted
          </Button>
        </div>
      </Card>

      <Card className="border-white/10 bg-white/[0.03] p-4">
        {activeTab === "vehicle" ? (
          <DataTable
            data={vehicleRows}
            columns={vehicleColumns}
            rowKey={(row) => `vehicle-${row.id}`}
            emptyMessage="No deleted vehicle found."
          />
        ) : (
          <DataTable
            data={driverRows}
            columns={driverColumns}
            rowKey={(row) => `driver-${row.id}`}
            emptyMessage="No deleted driver found."
          />
        )}
      </Card>
    </section>
  );
}

function AssetRegisterPageHeader({ title, icon, description }: { title: string; icon?: ReactNode; description?: string }) {
  return (
    <div className="flex flex-col gap-3 rounded-2xl border border-white/10 bg-white/5 p-5 text-foreground shadow-sm md:flex-row md:items-center md:justify-between">
      <div className="flex items-start gap-3">
        <div className="flex h-11 w-11 items-center justify-center rounded-xl border border-sky-400/30 bg-sky-400/10 text-sky-300">
          {icon ?? <Recycle className="h-5 w-5" />}
        </div>
        <div>
          <h1 className="mt-1 text-2xl font-extrabold text-white">{title}</h1>
          {description ? <p className="mt-1 text-sm text-slate-300">{description}</p> : null}
        </div>
      </div>
    </div>
  );
}
