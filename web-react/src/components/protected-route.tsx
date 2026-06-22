import { Navigate, Outlet, useLocation } from "react-router-dom";

import { useAuthStore } from "@/stores/auth-store";
import { AlelsRole, getDefaultRouteForRole, hasRole } from "@/lib/role-access";

type ProtectedRouteProps = {
  allowedRoles?: AlelsRole[];
};

export function ProtectedRoute({ allowedRoles }: ProtectedRouteProps) {
  const location = useLocation();
  const token = useAuthStore((state) => state.token);
  const user = useAuthStore((state) => state.user);

  if (!token) {
    return <Navigate to="/login" replace />;
  }

  if (allowedRoles && !hasRole(user?.role, allowedRoles)) {
    return <Navigate to={getDefaultRouteForRole(user?.role)} replace state={{ from: location }} />;
  }

  return <Outlet />;
}
