import type { User } from "@/lib/api";

const CHANNEL_PREFIX = "alels-device-workspace-handoff";
export const DEVICE_WORKSPACE_HANDOFF_TIMEOUT_MS = 5_000;

type WorkspaceSession = {
  token: string;
  user: User;
  rememberMe: boolean;
};

type HandoffMessage =
  | { type: "ready"; nonce: string }
  | ({ type: "session"; nonce: string } & WorkspaceSession)
  | { type: "accepted"; nonce: string };

export function openDeviceWorkspace(
  deviceId: number,
  imei: string,
  session: WorkspaceSession
): boolean {
  const nonce = createNonce();
  const channel = new BroadcastChannel(channelName(nonce));
  let closed = false;
  const close = () => {
    if (closed) return;
    closed = true;
    channel.close();
  };
  const timeout = window.setTimeout(close, DEVICE_WORKSPACE_HANDOFF_TIMEOUT_MS);

  channel.onmessage = (event: MessageEvent<HandoffMessage>) => {
    const message = event.data;
    if (!message || message.nonce !== nonce) return;
    if (message.type === "ready") {
      channel.postMessage({ type: "session", nonce, ...session } satisfies HandoffMessage);
    }
    if (message.type === "accepted") {
      window.clearTimeout(timeout);
      close();
    }
  };

  const launchPath = `/telemetry/device/workspace-launch/${deviceId}/${encodeURIComponent(imei)}`;
  const popup = window.open("about:blank", "_blank");
  if (!popup) {
    window.clearTimeout(timeout);
    close();
    return false;
  }
  popup.opener = null;
  popup.location.replace(`${launchPath}?handoff=${encodeURIComponent(nonce)}`);
  return true;
}

export function receiveDeviceWorkspaceSession(
  nonce: string,
  onSession: (session: WorkspaceSession) => void,
  onTimeout: () => void
): () => void {
  const channel = new BroadcastChannel(channelName(nonce));
  let completed = false;
  const timeout = window.setTimeout(() => {
    if (completed) return;
    completed = true;
    channel.close();
    onTimeout();
  }, DEVICE_WORKSPACE_HANDOFF_TIMEOUT_MS);

  channel.onmessage = (event: MessageEvent<HandoffMessage>) => {
    const message = event.data;
    if (completed || !message || message.type !== "session" || message.nonce !== nonce) return;
    completed = true;
    window.clearTimeout(timeout);
    onSession({ token: message.token, user: message.user, rememberMe: message.rememberMe });
    channel.postMessage({ type: "accepted", nonce } satisfies HandoffMessage);
    window.setTimeout(() => channel.close(), 0);
  };
  channel.postMessage({ type: "ready", nonce } satisfies HandoffMessage);

  return () => {
    if (completed) return;
    completed = true;
    window.clearTimeout(timeout);
    channel.close();
  };
}

function channelName(nonce: string) {
  return `${CHANNEL_PREFIX}:${nonce}`;
}

function createNonce() {
  if (typeof crypto.randomUUID === "function") return crypto.randomUUID();
  const bytes = new Uint8Array(24);
  crypto.getRandomValues(bytes);
  return Array.from(bytes, (value) => value.toString(16).padStart(2, "0")).join("");
}
