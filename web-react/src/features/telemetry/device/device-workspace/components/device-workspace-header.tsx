import type { ReactNode } from "react";

import { StatusIndicator } from "@/components/ui/status-indicator";

type DeviceWorkspaceHeaderProps = {
  imei?: string;
  model?: string | null;
  online: boolean;
  vehiclePlate: string;
  location: string;
  actions: ReactNode;
};

export function DeviceWorkspaceHeader({ imei, model, online, vehiclePlate, location, actions }: DeviceWorkspaceHeaderProps) {
  return (
    <header className="dw-workspace-header">
      <nav className="dw-breadcrumb" aria-label="Breadcrumb">
        <span>Telemetry Device</span><i>/</i><span data-preserve-tone>IMEI Workspace</span>
      </nav>
      <div className="dw-workspace-header-row">
        <div className="dw-workspace-identity">
          <div className="dw-workspace-title-row">
            <h1>{imei ? `IMEI ${imei}` : "IMEI Workspace"}</h1>
            <StatusIndicator status={online ? "ONLINE" : "OFFLINE"} />
          </div>
          <p data-preserve-tone>{model || "Device model unavailable"}</p>
        </div>
        <dl className="dw-workspace-summary">
          <div><dt data-preserve-tone>Vehicle Plate</dt><dd>{vehiclePlate}</dd></div>
          <div><dt data-preserve-tone>Location</dt><dd>{location}</dd></div>
        </dl>
        <div className="dw-header-actions">{actions}</div>
      </div>
    </header>
  );
}
