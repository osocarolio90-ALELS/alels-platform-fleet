import { api } from "@/lib/api";

const BASE = "/api/assignment";

export type AssignmentLookupOption = {
  id: number;
  label: string;
  code?: string | null;
  extra?: string | null;
  companyId?: number | null;
  companyName?: string | null;
};

export type AssetAssignmentRow = {
  id: number;
  companyId: number;
  companyName: string;
  vehicleId: number;
  vehicleName: string;
  plateNumber?: string | null;
  deviceId: number;
  deviceLabel: string;
  deviceImei: string;
  driverAssignmentId?: number | null;
  driverId?: number | null;
  driverCode?: string | null;
  driverName?: string | null;
  licenseNumber?: string | null;
  rfidIbutton?: string | null;
  assignmentStatus: string;
  assignedAt?: string | null;
  assignedBy?: string | null;
};

export type AssetAssignmentInput = {
  companyId?: number | null;
  vehicleId?: number | null;
  deviceId?: number | null;
  driverId?: number | null;
  notes?: string | null;
};

export type VehicleDeviceAssignmentRow = AssetAssignmentRow;
export type VehicleDeviceAssignmentInput = Omit<AssetAssignmentInput, "driverId">;
export type DriverManualAssignmentRow = AssetAssignmentRow;
export type DriverManualAssignmentInput = AssetAssignmentInput;

export async function getAssetAssignments() {
  const response = await api.get<AssetAssignmentRow[]>(`${BASE}/assets`);
  return response.data;
}

export async function createAssetAssignment(input: AssetAssignmentInput) {
  const response = await api.post<{ success: boolean; id: number }>(`${BASE}/assets`, input);
  return response.data;
}

export async function updateAssetAssignment(id: number, input: AssetAssignmentInput) {
  const response = await api.put<{ success: boolean }>(`${BASE}/assets/${id}`, input);
  return response.data;
}

export async function unpairAssignmentVehicle(id: number) {
  const response = await api.post<{ success: boolean }>(`${BASE}/assets/${id}/unpair-vehicle`);
  return response.data;
}

export async function unpairAssignmentDevice(id: number) {
  const response = await api.post<{ success: boolean }>(`${BASE}/assets/${id}/unpair-device`);
  return response.data;
}

export async function unpairAssignmentDriver(id: number) {
  const response = await api.post<{ success: boolean }>(`${BASE}/assets/${id}/unpair-driver`);
  return response.data;
}

export async function getVehicleDeviceAssignments() {
  const response = await api.get<VehicleDeviceAssignmentRow[]>(`${BASE}/vehicle-device`);
  return response.data;
}

export async function createVehicleDeviceAssignment(input: VehicleDeviceAssignmentInput) {
  const response = await api.post<{ success: boolean; id: number }>(`${BASE}/vehicle-device`, input);
  return response.data;
}

export async function updateVehicleDeviceAssignment(id: number, input: VehicleDeviceAssignmentInput) {
  const response = await api.put<{ success: boolean }>(`${BASE}/vehicle-device/${id}`, input);
  return response.data;
}

export async function removeVehicleDeviceAssignment(id: number) {
  const response = await api.post<{ success: boolean }>(`${BASE}/vehicle-device/${id}/remove`);
  return response.data;
}

export async function getDriverManualAssignments() {
  const response = await api.get<DriverManualAssignmentRow[]>(`${BASE}/driver-manual`);
  return response.data;
}

export async function createDriverManualAssignment(input: DriverManualAssignmentInput) {
  const response = await api.post<{ success: boolean; id: number }>(`${BASE}/driver-manual`, input);
  return response.data;
}

export async function updateDriverManualAssignment(id: number, input: DriverManualAssignmentInput) {
  const response = await api.put<{ success: boolean }>(`${BASE}/driver-manual/${id}`, input);
  return response.data;
}

export async function removeDriverManualAssignment(id: number) {
  const response = await api.post<{ success: boolean }>(`${BASE}/driver-manual/${id}/remove`);
  return response.data;
}

export async function getAssignmentCompanyOptions() {
  const response = await api.get<AssignmentLookupOption[]>(`${BASE}/options/companies`);
  return response.data;
}

export async function getAssignmentVehicleOptions() {
  const response = await api.get<AssignmentLookupOption[]>(`${BASE}/options/vehicles`);
  return response.data;
}

export async function getAssignmentDeviceOptions() {
  const response = await api.get<AssignmentLookupOption[]>(`${BASE}/options/devices`);
  return response.data;
}

export async function getAssignmentDriverOptions() {
  const response = await api.get<AssignmentLookupOption[]>(`${BASE}/options/drivers`);
  return response.data;
}
