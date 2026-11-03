export type EyeBeaconCard = {
  beaconId: number | string;
  displayName: string;
  sensor?: { name: string | null; verifiedMac: string | null; registrationStatus: string; type?: "EyeBeacon" | "EyeSensor" | null } | null;
  identity: {
    protocol: string; kind: string; value: string | null; namespace: string | null; instanceId: string | null;
    uuid: string | null; major: number | null; minor: number | null; mac: string | null;
  };
  lastSeen: string;
  recency: "RECENT" | "STALE";
  measurement: { rssiDbm: number | null; batteryMillivolts: number | null; temperatureRaw: number | null; rawBlockHex: string | null;
    lowBattery?: boolean | null; otherParameters?: Record<string, string>;
    temperatureCelsius?: number | null;
    sensorCapture?: { profile: string | null; status: "DECODED" | "PENDING_CONFIGURATION_VERIFICATION" | "INVALID_PROFILE_DATA" | "PENDING_FIRMWARE_VERIFICATION"; reason: string } | null;
    distance?: { meters: number | null; model: string | null; referenceRssiAt1m: number | null; pathLossExponent: number | null; reason: string | null } | null;
    advertisementData?: { protocol: string; namespace: string | null; instanceId: string | null; rangingDataAt0mDbm: number | null; uuid?: string | null; major?: number | null; minor?: number | null } | null;
    sensorData?: { profile: string; temperatureCelsius: number | null; humidityPercent: number | null;
      movement: boolean | null; movementCounter: number | null; pitchDegrees: number | null;
      rollDegrees: number | null; batteryMillivolts: number | null; magnet: boolean | null; deviceName?: string | null; lowBattery?: boolean | null;
      magnetCounter?: number | null; fieldErrors?: Record<string,string>; sourceIoIds?: Record<string,number> } | null };
  source: {
    observationId: number | null; receiverDeviceId: number; receiverImei: string; protocol: string;
    transport: "CELLULAR" | "WIFI" | "UNKNOWN"; transportReason: string; ioId: number; ioIds?: number[]; blockIndex: number;
    observedAt: string | null; receivedAt: string; telemetryId: number; telemetryServerTime: string;
    receiverGps: { latitude: number | null; longitude: number | null; altitude: number | null;
      hdop: number | null; satellites: number | null; valid: boolean };
  };

};

export type EyeBeaconPage = {
  beacons: EyeBeaconCard[]; hasMore: boolean; nextAfterId?: number | null; hasUnresolvedObservations?: boolean;
  serverTime: string; recentThresholdSeconds: number;
  nextBeforeId?: number | null;
  errors?: { telemetryId: number; ioId: number; code: string; byteOffset: number; rawHex: string }[];
};
