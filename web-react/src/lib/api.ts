import axios from "axios";
import { useAuthStore } from "@/stores/auth-store";

const TOKEN_STORAGE_KEY = "alels_token";
const AUTH_STORAGE_KEY = "alels-web-react-auth";

function readTokenFromStoredSession(raw: string | null): string | null {
  if (!raw) {
    return null;
  }

  try {
    const parsed = JSON.parse(raw) as { token?: string | null; state?: { token?: string | null } };
    return parsed.token || parsed.state?.token || null;
  } catch {
    return null;
  }
}

function readTokenFromBrowserStorage(): string | null {
  if (typeof window === "undefined") {
    return null;
  }

  return (
    localStorage.getItem(TOKEN_STORAGE_KEY) ||
    localStorage.getItem("token") ||
    localStorage.getItem("accessToken") ||
    localStorage.getItem("authToken") ||
    readTokenFromStoredSession(localStorage.getItem(AUTH_STORAGE_KEY)) ||
    sessionStorage.getItem(TOKEN_STORAGE_KEY) ||
    sessionStorage.getItem("token") ||
    sessionStorage.getItem("accessToken") ||
    sessionStorage.getItem("authToken") ||
    readTokenFromStoredSession(sessionStorage.getItem(AUTH_STORAGE_KEY))
  );
}

function getAuthToken(): string | null {
  return useAuthStore.getState().token || readTokenFromBrowserStorage();
}

function persistAuthToken(token: string) {
  if (typeof window === "undefined" || !token) {
    return;
  }

  localStorage.setItem(TOKEN_STORAGE_KEY, token);
}

function clearPersistedAuthToken() {
  if (typeof window === "undefined") {
    return;
  }

  [TOKEN_STORAGE_KEY, "token", "accessToken", "authToken"].forEach((key) => {
    localStorage.removeItem(key);
    sessionStorage.removeItem(key);
  });
}

export const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || "",
  timeout: 15_000
});

api.interceptors.request.use((config) => {
  const token = getAuthToken();

  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }

  return config;
});

api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (axios.isAxiosError(error) && (error.response?.status === 401 || error.response?.status === 423)) {
      const message = (error.response?.data as { message?: string } | undefined)?.message;
      clearPersistedAuthToken();
      useAuthStore.getState().logout();
      if (typeof window !== "undefined" && message) {
        window.alert(message);
      }
    }
    return Promise.reject(error);
  }
);

export type LoginResponse = {
  success: boolean;
  message: string;
  token: string;
  user: User;
};

export type User = {
  id: number;
  companyId?: number | null;
  companyName?: string | null;
  parentCompanyName?: string | null;
  username: string;
  fullName: string;
  email: string;
  role: string;
  status: string;
  passwordHash?: string | null;
  profilePhotoUrl?: string | null;
  deletedAt?: string | null;
  deletePermanentAt?: string | null;
  deletedReason?: string | null;
};

export type UserCreateInput = {
  companyId?: number | null;
  username: string;
  fullName?: string;
  email: string;
  role: string;
  status?: string;
  passwordHash: string;
};

export type UserUpdateInput = {
  id: number;
  companyId?: number | null;
  username: string;
  fullName?: string;
  email: string;
  role: string;
  status?: string;
  passwordHash?: string;
};

export type UserProfileUpdateInput = {
  fullName: string;
  email: string;
  passwordHash?: string;
};

export type DeviceModelOption = {
  id: number;
  brandCode?: string | null;
  brandName?: string | null;
  modelCode?: string | null;
  modelName: string;
  protocolCode?: string | null;
  dictionaryCode?: string | null;
};

export type DeviceEndpoint = {
  host?: string | null;
  port?: string | null;
  source?: string | null;
};

export type VehicleRegister = {
  id: number;
  companyId?: number | null;
  companyName?: string | null;
  parentCompanyName?: string | null;
  vehicleName?: string | null;
  vehicleNumber: string;
  vehicleType: string;
  fuelType: string;
  fuelPriceAmount: number;
  fuelPriceCurrency: string;
  imei: string;
  deviceModel: string;
  deviceModelId?: number | null;
  simNumber?: string | null;
  domainHost?: string | null;
  domainPort?: number | null;
  alelsWifiEnabled?: boolean | null;
  protocolSummary?: string | null;
  status?: string | null;
  receiveStatus?: string | null;
  deletedAt?: string | null;
  deletePermanentAt?: string | null;
  deletedReason?: string | null;
  createdAt?: string | null;
  updatedAt?: string | null;
};

export type VehicleRegisterInput = {
  companyId?: number | null;
  vehicleNumber: string;
  vehicleType: string;
  fuelType: string;
  fuelPriceAmount: number;
  fuelPriceCurrency: string;
  imei: string;
  deviceModel: string;
  deviceModelId?: number | null;
  simNumber?: string | null;
  domainHost?: string | null;
  domainPort?: number | null;
  alelsWifiEnabled?: boolean | null;
};

export type VehicleRegisterUpdateInput = Omit<VehicleRegisterInput, "imei" | "domainHost" | "domainPort"> & {
  id: number;
};

export type Company = {
  id: number;
  parentCompanyId?: number | null;
  parentCompanyName?: string | null;
  companyName: string;
  companyCode?: string | null;
  companyType?: string | null;
  type?: string | null;
  plan?: string | null;
  country?: string | null;
  status?: string | null;
  monthPacket?: number | null;
  deviceLimit?: number | null;
  maxUsers?: number | null;
  storageSize?: number | null;
  storageQuotaMb?: number | null;
  storageUnit?: string | null;
  subscriptionStatus?: string | null;
  isInternal?: boolean | null;
  startedAt?: string | null;
  startAt?: string | null;
  expiredAt?: string | null;
  firstUserId?: number | null;
  firstUserEmail?: string | null;
  firstUserFullName?: string | null;
  firstUserRole?: string | null;
  deletedAt?: string | null;
  deletePermanentAt?: string | null;
  deletedReason?: string | null;
};

export type CompanyRegisterInput = {
  companyName: string;
  parentCompanyId?: number | null;
  companyCode?: string;
  companyType?: string;
  type?: string;
  plan?: string;
  monthPacket?: number;
  deviceLimit?: number;
  maxUsers?: number;
  storageSize?: number;
  storageUnit?: string;
  status?: string;
  subscriptionStatus?: string;
  startedAt?: string | null;
  startAt?: string | null;
  expiredAt?: string | null;
  country?: string;
  adminFullName: string;
  adminEmail: string;
  adminRole?: string;
  temporaryPassword?: string;
};

export type CompanyUpdateInput = {
  id: number;
  companyName: string;
  companyCode?: string | null;
  companyType?: string | null;
  type?: string | null;
  plan?: string | null;
  monthPacket?: number | null;
  deviceLimit?: number | null;
  maxUsers?: number | null;
  storageSize?: number | null;
  storageUnit?: string | null;
  country?: string | null;
  status?: string | null;
  subscriptionStatus?: string | null;
  isInternal?: boolean | null;
  startedAt?: string | null;
  startAt?: string | null;
  expiredAt?: string | null;
  firstUserFullName?: string | null;
  firstUserEmail?: string | null;
  firstUserRole?: string | null;
};

export type CompanyNameCheckResponse = {
  exists: boolean;
  normalizedName?: string;
};

export type CompanyRegisterResponse = {
  success: boolean;
  message: string;
  companyId?: number | null;
  companyName?: string | null;
  userId?: number | null;
  adminEmail?: string | null;
  adminRole?: string | null;
  generatedPassword?: string | null;
};

export type CompanyStatus = {
  companyId: number;
  companyName: string;
  parentCompanyName?: string | null;
  companyCode?: string | null;
  companyType?: string | null;
  type?: string | null;
  plan?: string | null;
  country?: string | null;
  companyStatus?: string | null;
  subscriptionStatus?: string | null;
  remainingDays?: number | null;
  monthPacket?: number | null;
  deviceLimit?: number | null;
  registeredDevices?: number | null;
  availableDevices?: number | null;
  onlineDevices?: number | null;
  offlineDevices?: number | null;
  maxUsers?: number | null;
  registeredUsers?: number | null;
  availableUsers?: number | null;
  activeUsers?: number | null;
  inactiveUsers?: number | null;
  storageSize?: number | null;
  storageUnit?: string | null;
  storageUsed?: number | null;
  storageAvailable?: number | null;
  activatedAt?: string | null;
  startedAt?: string | null;
  startAt?: string | null;
  expiredAt?: string | null;
};

export type UserStatus = {
  userId: number;
  companyId?: number | null;
  companyName?: string | null;
  parentCompanyName?: string | null;
  username: string;
  fullName?: string | null;
  email: string;
  role: string;
  status: string;
  lastLoginAt?: string | null;
  createdAt?: string | null;
};

export type OrganizationWastedItem = {
  itemType: "COMPANY" | "USER" | string;
  id: number;
  name: string;
  parentCompanyName?: string | null;
  companyName?: string | null;
  roleOrType?: string | null;
  status?: string | null;
  deletedAt?: string | null;
  deletePermanentAt?: string | null;
  deletedReason?: string | null;
};


export type AiOpsMetric = {
  key: string;
  label: string;
  value: string;
  unit?: string | null;
  severity: "NORMAL" | "WARNING" | "CRITICAL" | "EMERGENCY" | string;
  description?: string | null;
};

export type AiOpsFinding = {
  id: string;
  severity: "NORMAL" | "WARNING" | "CRITICAL" | "EMERGENCY" | string;
  category: string;
  title: string;
  problem: string;
  impact?: string | null;
  recommendedAction: string;
  status: string;
};

export type AiOpsOverview = {
  systemStatus: "NORMAL" | "WARNING" | "CRITICAL" | "EMERGENCY" | string;
  mainIssue: string;
  impact?: string | null;
  recommendedAction: string;
  generatedAt?: string | null;
  metrics: AiOpsMetric[];
  findings: AiOpsFinding[];
};

export type GatewayMetric = {
  key: string;
  label: string;
  value: string;
  unit?: string | null;
  severity: "NORMAL" | "WARNING" | "CRITICAL" | "EMERGENCY" | string;
  description?: string | null;
};

export type GatewayDistribution = {
  name: string;
  count: number;
  percent: number;
};

export type GatewayMonitorInsight = {
  id: string;
  severity: "NORMAL" | "WARNING" | "CRITICAL" | "EMERGENCY" | string;
  category: string;
  title: string;
  impact?: string | null;
  action: string;
  status: string;
};

export type GatewayMonitorOverview = {
  status: "NORMAL" | "WARNING" | "CRITICAL" | "EMERGENCY" | string;
  gatewayScore: number;
  generatedAt?: string | null;
  summary: string;
  recommendation: string;
  metrics: GatewayMetric[];
  protocolDistribution: GatewayDistribution[];
  channelDistribution: GatewayDistribution[];
  insights: GatewayMonitorInsight[];
};


export type TrafficMetric = {
  key: string;
  label: string;
  value: string;
  unit?: string | null;
  severity: "NORMAL" | "WARNING" | "CRITICAL" | "EMERGENCY" | string;
  description?: string | null;
};

export type TrafficDistribution = {
  name: string;
  count: number;
  percent: number;
};

export type TrafficMonitorInsight = {
  id: string;
  severity: "NORMAL" | "WARNING" | "CRITICAL" | "EMERGENCY" | string;
  category: string;
  title: string;
  impact?: string | null;
  action: string;
  status: string;
};

export type TrafficMonitorOverview = {
  status: "NORMAL" | "WARNING" | "CRITICAL" | "EMERGENCY" | string;
  trafficScore: number;
  generatedAt?: string | null;
  summary: string;
  recommendation: string;
  metrics: TrafficMetric[];
  protocolDistribution: TrafficDistribution[];
  channelDistribution: TrafficDistribution[];
  insights: TrafficMonitorInsight[];
};



export type DatabaseMetric = {
  key: string;
  label: string;
  value: string;
  unit?: string | null;
  severity: "NORMAL" | "WARNING" | "CRITICAL" | "EMERGENCY" | string;
  description?: string | null;
};

export type DatabaseDistribution = {
  name: string;
  count: number;
  percent: number;
  displayValue?: string | null;
};

export type DatabaseMonitorInsight = {
  id: string;
  severity: "NORMAL" | "WARNING" | "CRITICAL" | "EMERGENCY" | string;
  category: string;
  title: string;
  impact?: string | null;
  action: string;
  status: string;
};

export type DatabaseMonitorOverview = {
  status: "NORMAL" | "WARNING" | "CRITICAL" | "EMERGENCY" | string;
  databaseScore: number;
  generatedAt?: string | null;
  summary: string;
  recommendation: string;
  metrics: DatabaseMetric[];
  tableDistribution: DatabaseDistribution[];
  indexDistribution: DatabaseDistribution[];
  insights: DatabaseMonitorInsight[];
};


export type StorageMetric = {
  key: string;
  label: string;
  value: string;
  unit?: string | null;
  severity: "NORMAL" | "WARNING" | "CRITICAL" | "EMERGENCY" | string;
  description?: string | null;
};

export type StorageDistribution = {
  name: string;
  count: number;
  percent: number;
  displayValue?: string | null;
};

export type StorageMonitorInsight = {
  id: string;
  severity: "NORMAL" | "WARNING" | "CRITICAL" | "EMERGENCY" | string;
  category: string;
  title: string;
  impact?: string | null;
  action: string;
  status: string;
};

export type StorageMonitorOverview = {
  status: "NORMAL" | "WARNING" | "CRITICAL" | "EMERGENCY" | string;
  storageScore: number;
  generatedAt?: string | null;
  summary: string;
  recommendation: string;
  metrics: StorageMetric[];
  dataDistribution: StorageDistribution[];
  retentionDistribution: StorageDistribution[];
  insights: StorageMonitorInsight[];
};



export type SecurityMetric = {
  key: string;
  label: string;
  value: string;
  unit?: string | null;
  severity: "NORMAL" | "WARNING" | "CRITICAL" | "EMERGENCY" | string;
  description?: string | null;
};

export type SecurityDistribution = {
  name: string;
  count: number;
  percent: number;
  displayValue?: string | null;
};

export type SecurityMonitorInsight = {
  id: string;
  severity: "NORMAL" | "WARNING" | "CRITICAL" | "EMERGENCY" | string;
  category: string;
  title: string;
  impact?: string | null;
  action: string;
  status: string;
};

export type SecurityMonitorOverview = {
  status: "NORMAL" | "WARNING" | "CRITICAL" | "EMERGENCY" | string;
  securityScore: number;
  generatedAt?: string | null;
  summary: string;
  recommendation: string;
  metrics: SecurityMetric[];
  signalDistribution: SecurityDistribution[];
  userRiskDistribution: SecurityDistribution[];
  insights: SecurityMonitorInsight[];
};


export type OverviewMetric = {
  key: string;
  label: string;
  value: string;
  unit?: string | null;
  severity: "NORMAL" | "WARNING" | "CRITICAL" | "EMERGENCY" | string;
  description?: string | null;
};

export type OverviewDistribution = {
  name: string;
  count: number;
  percent: number;
  displayValue?: string | null;
};

export type OverviewMonitorModule = {
  key: string;
  label: string;
  status: "NORMAL" | "WARNING" | "CRITICAL" | "EMERGENCY" | string;
  score: number;
  summary?: string | null;
  recommendation?: string | null;
};

export type OverviewMonitorInsight = {
  id: string;
  severity: "NORMAL" | "WARNING" | "CRITICAL" | "EMERGENCY" | string;
  category: string;
  title: string;
  impact?: string | null;
  action: string;
  status: string;
};

export type OverviewMonitorOverview = {
  status: "NORMAL" | "WARNING" | "CRITICAL" | "EMERGENCY" | string;
  overviewScore: number;
  generatedAt?: string | null;
  summary: string;
  recommendation: string;
  metrics: OverviewMetric[];
  modules: OverviewMonitorModule[];
  healthDistribution: OverviewDistribution[];
  priorityDistribution: OverviewDistribution[];
  insights: OverviewMonitorInsight[];
};

export type RealtimeAlert = {
  type: string;
  severity: string;
  title: string;
  message: string;
  imei?: string;
  time?: string;
};

export type OwnerDashboard = {
  totalCompanies?: number;
  activeCompanies?: number;
  inactiveCompanies?: number;
  wastedCompanies?: number;
  totalUsers?: number;
  deviceActive?: number;
  deviceInactive?: number;
  unknownIo?: number;
  totalVehicles?: number;
  vehicleMoving?: number;
  vehicleIdle?: number;
  vehicleStop?: number;
  mapPoints?: Array<{
    imei: string;
    deviceModel?: string;
    status?: string;
    latitude: number;
    longitude: number;
    speed?: number;
    lastSeen?: string;
  }>;
  recentAlerts?: RealtimeAlert[];
  userLogs?: Array<{
    email?: string;
    fullName?: string;
    companyName?: string;
    action?: string;
    time?: string;
  }>;
  quickActions?: Array<{
    label: string;
    target: string;
    icon: string;
  }>;
};

export async function login(email: string, password: string) {
  const response = await api.post<LoginResponse>("/api/auth/login", { email, password });

  if (response.data?.token) {
    persistAuthToken(response.data.token);
  }

  return response.data;
}

export async function getOwnerDashboard() {
  const response = await api.get<OwnerDashboard>("/api/owner-dashboard");
  return response.data;
}

export async function getCompanies() {
  const response = await api.get<Company[]>("/api/organization/companies");
  return response.data;
}

export async function checkCompanyName(companyName: string) {
  const response = await api.get<CompanyNameCheckResponse>("/api/organization/company/check-name", { params: { name: companyName } });
  return response.data;
}

export async function createCompany(input: CompanyRegisterInput) {
  const response = await api.post<CompanyRegisterResponse>("/api/organization/company-registration", input);
  return { ...response.data, success: true, message: "Company berhasil dibuat." };
}

export async function updateCompany(company: CompanyUpdateInput) {
  const response = await api.put<Company>(`/api/organization/companies/${company.id}`, company);
  return response.data;
}

export async function deleteCompany(companyId: number) {
  await api.post(`/api/organization/companies/${companyId}/delete`);
}

export async function getWastedCompanies() {
  const response = await api.get<Company[]>("/api/organization/wasted");
  return response.data as unknown as Company[];
}

export async function restoreCompany(companyId: number) {
  const response = await api.post<Company>(`/api/organization/companies/${companyId}/restore`);
  return response.data;
}

export async function permanentlyDeleteCompany(companyId: number) {
  await api.post(`/api/organization/companies/${companyId}/permanent-delete`);
}

export async function getCompanyStatuses() {
  const response = await api.get<CompanyStatus[]>("/api/company-status");
  return response.data;
}

export async function activateCompanyStatus(companyId: number) {
  const response = await api.post<CompanyStatus>(`/api/company-status/${companyId}/activate`);
  return response.data;
}

export async function suspendCompanyStatus(companyId: number) {
  const response = await api.post<CompanyStatus>(`/api/company-status/${companyId}/suspend`);
  return response.data;
}

export async function getUsers() {
  const response = await api.get<User[]>("/api/users");
  return response.data;
}

export async function createUser(input: UserCreateInput) {
  const response = await api.post<User>("/api/users", input);
  return response.data;
}

export async function updateUser(input: UserUpdateInput) {
  const response = await api.put<User>(`/api/users/${input.id}`, input);
  return response.data;
}

export async function updateUserProfile(input: UserProfileUpdateInput) {
  const response = await api.put<User>("/api/users/profile", input);
  return response.data;
}

export async function deleteUser(userId: number) {
  await api.delete(`/api/users/${userId}`);
}

export async function getWastedUsers() {
  const response = await api.get<User[]>("/api/users/wasted");
  return response.data;
}

export async function restoreUser(userId: number) {
  const response = await api.post<User>(`/api/users/${userId}/restore`);
  return response.data;
}

export async function permanentlyDeleteUser(userId: number) {
  await api.delete(`/api/users/${userId}/permanent`);
}



export async function getUserStatuses() {
  const response = await api.get<UserStatus[]>("/api/user-status");
  return response.data;
}

export async function activateUserStatus(userId: number) {
  const response = await api.post<UserStatus>(`/api/user-status/${userId}/activate`);
  return response.data;
}

export async function suspendUserStatus(userId: number) {
  const response = await api.post<UserStatus>(`/api/user-status/${userId}/suspend`);
  return response.data;
}

export async function getOrganizationWastedItems() {
  const response = await api.get<OrganizationWastedItem[]>("/api/organization/wasted");
  return response.data;
}

export async function getVehicleRegisters() {
  const response = await api.get<VehicleRegister[]>("/api/vehicle-register");
  return response.data;
}

export async function getWastedVehicleRegisters() {
  const response = await api.get<VehicleRegister[]>("/api/vehicle-register/wasted");
  return response.data;
}

export async function getVehicleDeviceModels() {
  const response = await api.get<DeviceModelOption[]>("/api/vehicle-register/device-models");
  return response.data;
}

export async function getDeviceEndpoint() {
  const response = await api.get<DeviceEndpoint>("/api/system/device-endpoint");
  return response.data;
}

export async function createVehicleRegister(input: VehicleRegisterInput) {
  const response = await api.post<VehicleRegister>("/api/vehicle-register", input);
  return response.data;
}

export async function updateVehicleRegister(input: VehicleRegisterUpdateInput) {
  const response = await api.put<VehicleRegister>(`/api/vehicle-register/${input.id}`, input);
  return response.data;
}

export async function deleteVehicleRegister(vehicleId: number) {
  await api.delete(`/api/vehicle-register/${vehicleId}`);
}

export async function restoreVehicleRegister(vehicleId: number) {
  const response = await api.post<VehicleRegister>(`/api/vehicle-register/${vehicleId}/restore`);
  return response.data;
}

export async function suspendVehicleRegister(vehicleId: number) {
  const response = await api.post<VehicleRegister>(`/api/vehicle-register/${vehicleId}/suspend`);
  return response.data;
}

export async function activateVehicleRegister(vehicleId: number) {
  const response = await api.post<VehicleRegister>(`/api/vehicle-register/${vehicleId}/activate`);
  return response.data;
}

export async function permanentlyDeleteVehicleRegister(vehicleId: number) {
  await api.delete(`/api/vehicle-register/${vehicleId}/permanent`);
}



export async function getOverviewMonitorOverview() {
  const response = await api.get<OverviewMonitorOverview>("/api/server-monitor/overview");
  return response.data;
}

export async function getAiOpsOverview() {
  const response = await api.get<AiOpsOverview>("/api/server-monitor/ai-ops/overview");
  return response.data;
}


export async function getGatewayMonitorOverview() {
  const response = await api.get<GatewayMonitorOverview>("/api/server-monitor/gateway/overview");
  return response.data;
}

export async function getTrafficMonitorOverview() {
  const response = await api.get<TrafficMonitorOverview>("/api/server-monitor/traffic/overview");
  return response.data;
}


export async function getDatabaseMonitorOverview() {
  const response = await api.get<DatabaseMonitorOverview>("/api/server-monitor/database/overview");
  return response.data;
}


export async function getStorageMonitorOverview() {
  const response = await api.get<StorageMonitorOverview>("/api/server-monitor/storage/overview");
  return response.data;
}


export async function getSecurityMonitorOverview() {
  const response = await api.get<SecurityMonitorOverview>("/api/server-monitor/security/overview");
  return response.data;
}
