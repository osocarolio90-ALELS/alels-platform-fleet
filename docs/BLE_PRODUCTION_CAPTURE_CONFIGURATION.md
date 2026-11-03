# Current BLE capture configuration — no register dependency

Supersedes the previous registry-gated contract after the user's2November2026confirmation. See [implementation and actual database results](BLE_DIRECT_PARSING_AND_STORAGE.md).

`alels.ble.capture.profiles` is CSV receiver-IMEI/captured-ID=PROFILE. Exact verified profiles are external deployment metadata in backend/config/ble-capture.properties, imported through existing Spring application.properties. Environment overrides remain available. A wildcard receiver/* is valid only when every applicable capture rule has the same verified layout. A9byte payload alone never selects a profile.

Supported profiles: EYE_SENSOR_SHORT_V1 (exact9bytes without Flags), EYE_SENSOR_FLAGS_V1, EYE_BEACON_FLAGS_V1, EYE_BEACON_BATTERY_V1 and FULL_AD. Existing avl-profiles support documented11317endianness; slot-identities attest dedicated108xxslots. Reserved receiver/548 selects ADVANCED_PARAMETERIZED or ADVANCED_LEGACY_COUNT if needed; receiver/385=SIMPLE_BEACON_V1 needs documented model evidence and cannot override known ManualCAN385models.

No registration or physicalMAC is required to decode/classify/display a supported observation. PhysicalMAC is optional; keep it separate from received BeaconID/Namespace/Instance/UUID. Verified tenant-owned identity associations can live in026ble_beacons.verified_mac, or the existing scoped external identity-associations property. External mac-identities only attests actual ID Source=MAC; it is never inferred from a six-byte length. Ambiguity preserves RAW and diagnostics.

Source telemetry is read, never rewritten by the BLE projection. All parsed fields, original RAW and provenance are persisted in026ble_observations with explicit parser/decode status. Missing/unverified measurements are not fabricated. Historical asset_ble_sensors data is retained but is no longer used by monitoring or exposed through the removed register module.


##3November2026update

Receiver profiles can be an explicit list separated by`|`, e.g.EYE_BEACON_BATTERY_V1|EYE_SENSOR_SHORT_V1. Selection must have exactly one layout accepted by the existing decoder; unknown/ambiguous formats remain diagnostic. The approved receiver uses`receiver/*`for its attestedEYEcapture rules, so newIDs need no allow-list. This does not assert a physicalMAC or apply to unconfiguredreceivers. A configuration stamp refreshes existing026snapshots once after a metadata change. Migration027and its obsolete register table/data were explicitly deleted. See[latest audit](BLE_20261103_AUDIT_AND_FIX.md).
