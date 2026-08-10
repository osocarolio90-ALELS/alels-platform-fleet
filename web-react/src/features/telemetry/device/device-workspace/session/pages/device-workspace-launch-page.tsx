import { useEffect } from "react";
import { Navigate, useNavigate, useParams, useSearchParams } from "react-router-dom";

import { useAuthStore } from "@/stores/auth-store";
import { receiveDeviceWorkspaceSession } from "../services/device-workspace-session-handoff";

export function DeviceWorkspaceLaunchPage() {
  const { deviceId, imei } = useParams();
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const token = useAuthStore((state) => state.token);
  const setSession = useAuthStore((state) => state.setSession);
  const validDeviceId = Number.isSafeInteger(Number(deviceId)) && Number(deviceId) > 0;
  const target = validDeviceId && imei
    ? `/telemetry/device/${deviceId}/${encodeURIComponent(imei)}/workspace`
    : null;
  const nonce = searchParams.get("handoff")?.trim() ?? "";

  useEffect(() => {
    if (!target || token) return;
    if (!nonce) {
      navigate("/login", { replace: true });
      return;
    }
    return receiveDeviceWorkspaceSession(
      nonce,
      (session) => {
        setSession(session.token, session.user, session.rememberMe);
        navigate(target, { replace: true });
      },
      () => navigate("/login", { replace: true })
    );
  }, [navigate, nonce, setSession, target, token]);

  if (!target) return <Navigate to="/telemetry/device" replace />;
  if (token) return <Navigate to={target} replace />;

  return (
    <main className="flex min-h-screen items-center justify-center bg-background text-foreground">
      <p className="text-sm text-muted-foreground">Opening device workspace...</p>
    </main>
  );
}
