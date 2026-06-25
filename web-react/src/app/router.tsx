import { Suspense, lazy } from "react";
import { createBrowserRouter, Navigate } from "react-router-dom";

import { ProtectedRoute } from "@/components/protected-route";
import { AppLayout } from "@/layouts/app-layout";
import { ALL_ROLES, MASTER_DATA_ROLES, ORGANIZATION_ROLES, SERVER_MONITOR_ROLES } from "@/lib/role-access";

const LoginPage = lazy(() =>
  import("@/features/auth/pages/login-page").then((module) => ({ default: module.LoginPage }))
);

const OverviewMonitorPage = lazy(() =>
  import("@/features/server-monitor/pages/overview-monitor-page").then((module) => ({ default: module.OverviewMonitorPage }))
);
const AiOpsMonitorPage = lazy(() =>
  import("@/features/server-monitor/pages/ai-ops-monitor-page").then((module) => ({ default: module.AiOpsMonitorPage }))
);
const GatewayMonitorPage = lazy(() =>
  import("@/features/server-monitor/pages/gateway-monitor-page").then((module) => ({ default: module.GatewayMonitorPage }))
);
const TrafficMonitorPage = lazy(() =>
  import("@/features/server-monitor/pages/traffic-monitor-page").then((module) => ({ default: module.TrafficMonitorPage }))
);
const DatabaseMonitorPage = lazy(() =>
  import("@/features/server-monitor/pages/database-monitor-page").then((module) => ({ default: module.DatabaseMonitorPage }))
);
const StorageMonitorPage = lazy(() =>
  import("@/features/server-monitor/pages/storage-monitor-page").then((module) => ({ default: module.StorageMonitorPage }))
);
const SecurityMonitorPage = lazy(() =>
  import("@/features/server-monitor/pages/security-monitor-page").then((module) => ({ default: module.SecurityMonitorPage }))
);
const CompanyListPage = lazy(() =>
  import("@/features/organization/pages/company-list-page").then((module) => ({ default: module.CompanyListPage }))
);
const UserListPage = lazy(() =>
  import("@/features/organization/pages/user-list-page").then((module) => ({ default: module.UserListPage }))
);
const WastedPage = lazy(() =>
  import("@/features/organization/pages/wasted-page").then((module) => ({ default: module.WastedPage }))
);
const UserProfilePage = lazy(() =>
  import("@/features/user/pages/user-profile-page").then((module) => ({ default: module.UserProfilePage }))
);
const VehicleRegisterPage = lazy(() =>
  import("@/features/asset-register/pages/vehicle-register-page").then((module) => ({ default: module.VehicleRegisterPage }))
);
const DriverRegisterPage = lazy(() =>
  import("@/features/asset-register/pages/driver-register-page").then((module) => ({ default: module.DriverRegisterPage }))
);
const DeviceRegisterPage = lazy(() =>
  import("@/features/asset-register/pages/device-register-page").then((module) => ({ default: module.DeviceRegisterPage }))
);
const EnergyPricePage = lazy(() =>
  import("@/features/asset-register/pages/energy-price-page").then((module) => ({ default: module.EnergyPricePage }))
);
const ReferencePricePage = lazy(() =>
  import("@/features/master-data/pages/reference-price-page").then((module) => ({ default: module.ReferencePricePage }))
);
const VehicleMasterPage = lazy(() =>
  import("@/features/master-data/pages/vehicle-master-page").then((module) => ({ default: module.VehicleMasterPage }))
);
const DeviceMasterPage = lazy(() =>
  import("@/features/master-data/pages/device-master-page").then((module) => ({ default: module.DeviceMasterPage }))
);
const LicenseMasterPage = lazy(() =>
  import("@/features/master-data/pages/license-master-page").then((module) => ({ default: module.LicenseMasterPage }))
);
const MasterWastedPage = lazy(() =>
  import("@/features/master-data/pages/master-wasted-page").then((module) => ({ default: module.MasterWastedPage }))
);
const AssetWastedPage = lazy(() =>
  import("@/features/asset-register/pages/wasted-page").then((module) => ({ default: module.AssetWastedPage }))
);
const AssignmentPage = lazy(() =>
  import("@/features/assignment/pages/assignment-page").then((module) => ({ default: module.AssignmentPage }))
);


function PageLoader() {
  return (
    <div className="flex min-h-[240px] items-center justify-center rounded-2xl border border-white/10 bg-white/5 text-sm text-slate-300">
      Loading ALELS Platform...
    </div>
  );
}

function withSuspense(element: JSX.Element) {
  return <Suspense fallback={<PageLoader />}>{element}</Suspense>;
}

export const router = createBrowserRouter([
  { path: "/login", element: withSuspense(<LoginPage />) },
  {
    element: <ProtectedRoute allowedRoles={ALL_ROLES} />,
    children: [
      {
        element: <AppLayout />,
        children: [
          { path: "/", element: <Navigate to="/organization/company-list" replace /> },
          { path: "/dashboard", element: <Navigate to="/organization/company-list" replace /> },
          { path: "/user/profile", element: withSuspense(<UserProfilePage />) },
          { path: "/asset-register/vehicle-register", element: withSuspense(<VehicleRegisterPage />) },
          { path: "/asset-register/device-register", element: withSuspense(<DeviceRegisterPage />) },
          { path: "/asset-register/driver-register", element: withSuspense(<DriverRegisterPage />) },
          { path: "/asset-register/energy-price", element: withSuspense(<EnergyPricePage />) },
          { path: "/asset-register/wasted", element: withSuspense(<AssetWastedPage />) },
          {
            element: <ProtectedRoute allowedRoles={MASTER_DATA_ROLES} />,
            children: [
              { path: "/master-data/vehicle-master", element: withSuspense(<VehicleMasterPage />) },
              { path: "/master-data/device-master", element: withSuspense(<DeviceMasterPage />) },
              { path: "/master-data/license-master", element: withSuspense(<LicenseMasterPage />) },
              { path: "/master-data/wasted", element: withSuspense(<MasterWastedPage />) },
              { path: "/master-data/reference-price", element: withSuspense(<ReferencePricePage />) },
              { path: "/master-data", element: <Navigate to="/master-data/vehicle-master" replace /> }
            ]
          },
          { path: "/asset-register", element: <Navigate to="/asset-register/vehicle-register" replace /> },
          { path: "/assignment", element: withSuspense(<AssignmentPage />) },

          {
            element: <ProtectedRoute allowedRoles={ORGANIZATION_ROLES} />,
            children: [
              { path: "/organization/company-list", element: withSuspense(<CompanyListPage />) },
              { path: "/organization/user-list", element: withSuspense(<UserListPage />) },
              { path: "/organization/wasted", element: withSuspense(<WastedPage />) },
              { path: "/organization", element: <Navigate to="/organization/company-list" replace /> }
            ]
          },
          {
            element: <ProtectedRoute allowedRoles={SERVER_MONITOR_ROLES} />,
            children: [
              { path: "/server-monitor/overview", element: withSuspense(<OverviewMonitorPage />) },
              { path: "/server-monitor/ai-ops", element: withSuspense(<AiOpsMonitorPage />) },
              { path: "/server-monitor/gateway", element: withSuspense(<GatewayMonitorPage />) },
              { path: "/server-monitor/traffic", element: withSuspense(<TrafficMonitorPage />) },
              { path: "/server-monitor/database", element: withSuspense(<DatabaseMonitorPage />) },
              { path: "/server-monitor/storage", element: withSuspense(<StorageMonitorPage />) },
              { path: "/server-monitor/security", element: withSuspense(<SecurityMonitorPage />) },
              { path: "/server-monitor", element: <Navigate to="/server-monitor/overview" replace /> }
            ]
          }
        ]
      }
    ]
  },
  { path: "*", element: <Navigate to="/organization/company-list" replace /> }
]);
