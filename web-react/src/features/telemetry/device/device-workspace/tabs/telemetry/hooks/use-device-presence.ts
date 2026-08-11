import { useEffect, useState } from "react";

import type { WorkspaceTelemetry } from "../types/device-workspace-telemetry";

const OFFLINE_AFTER_MS=30*60*1000;
const CLOCK_INTERVAL_MS=5_000;

export function useDevicePresence(data?:WorkspaceTelemetry){
 const [now,setNow]=useState(()=>Date.now());
 useEffect(()=>{const interval=window.setInterval(()=>setNow(Date.now()),CLOCK_INTERVAL_MS);return()=>window.clearInterval(interval);},[]);
 if(!data)return false;
 const timestamps=[data.device.lastSeen,data.packet?.receivedAt,data.position.serverTime]
  .map(value=>value?Date.parse(value):Number.NaN)
  .filter(Number.isFinite);
 if(!timestamps.length)return false;
 return now-Math.max(...timestamps)<=OFFLINE_AFTER_MS;
}
