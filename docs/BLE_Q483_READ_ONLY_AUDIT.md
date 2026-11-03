# Audit read-only Q483 — PARTIAL

Receiver 863719066421737, telemetry9408, raw_packets13062. Device time 2026-11-02 19:50:45+07; server time 19:50:46.154346+07. Receiver GPS latitude -6.2111233, longitude107.0100383, altitude23. Tidak ada perubahan production source, konfigurasi, database, firmware, registry atau UI.

## Byte-level validation

RAW548: `010E0001D301067CD9F408DA2C02016E160001C401062A57812B005502090C6E4808A401FFFE67160001D201067CD9F4134EE102090C644F12B700FFFF6F`.

Total62bytes. Offset zero-based: byte0=01; byte1=0E (14-byte content at2–15); byte16=16 (22-byte content at17–38); byte39=16 (22-byte content at40–61). Exactly1+(1+14)+(1+22)+(1+22)=62, no trailing bytes. Every record has TLVs00/length01/RSSI,01/length06/ID,02/length01-or09/Additional Data. RSSI D3=-45,C4=-60,D2=-46.

[Official FMC650 Beacon List](https://wiki.teltonika-gps.com/view/FMC650_Beacon_List), section firmware03.01.00.Rev.03, explicitly defines first01 as Constant followed by length-prefixed TLV records. Earlier firmware uses count followed by RSSI/ID-length, a different structure. Therefore the alleged count mismatch is caused by applying the legacy header meaning to parameterized bytes. No byte rewriting or count normalization is needed. Actual firmware/configurator proof remains unavailable; this demonstrates structural compatibility, not physical capture attestation.

Existing compiled parser: ADVANCED_PARAMETERIZED yields3blocks/noerrors; ADVANCED_LEGACY_COUNT yields0blocks plus INVALID_LEGACY_ID_LENGTH at byte1, preserving RAW. No partial legacy records were recovered. Do not call the whole packet production verified: capture profile/identity and runtime verification are separate pending checks.

## Read-only provenance

Database transaction BEGIN READ ONLY confirms raw_packets13062 raw_json Q=483 and raw_json548 exactly equals telemetry9408 io_data548. RAW parse_status RECEIVED is storage metadata, not proof of validated BLE measurements. No evidence that ALELS JSON storage changed payload. Original FMC650 wire capture is not available, so byte-for-byte tracker-to-ESP32 capture cannot be confirmed. No replay or ingestion modification was performed.

## Conditional measurements

[Teltonika BTSMP1](https://wiki.teltonika-gps.com/view/BTSMP1) defines the sensor field encoding. Explicit EYE_SENSOR_SHORT_V1 evaluation on the existing decoder:

| ID | RSSI | Additional | °C | RH% | Movement/counter | Pitch/roll | mV |
|---|---:|---|---:|---:|---|---|---:|
| 7CD9F408DA2C | -45 | 6E | — | — | — | — | pending |
| 2A57812B0055 | -60 | 0C6E4808A401FFFE67 |31.82|72|false/2212|+1/-2|3030|
| 7CD9F4134EE1 | -46 | 0C644F12B700FFFF6F |31.72|79|false/4791|0/-1|3110|

Nine-byte captures have no received Flags. They require verified field selection/offset/size before exposing readings as validated live measurements. 6E would give3100mV only under an explicitly verified battery-byte capture profile; not enabled by payload length. No hardcoded IDs/receiver were added. Temporary checker used actual production parser then was removed.

## Registry and card/API

Previously read active registry contains SENSOR2 physicalMAC7C:D9:F4:14:7A:FC, SENSOR1 MAC7C:D9:F4:13:4E:E1, BEACON2 MAC7C:D9:F4:08:DA:2C. User verified associations for SENSOR2 and BEACON2; SENSOR1 physical association still unverified. CapturedID/Instance and physicalMAC remain separate. Local registry lacks captured identity columns and optional ble_beacons table was absent; verified metadata must be supplied through existing approved external configuration or established association model, never inferred from hex length. Dynamic resolver scopes matches to tenant and active registry. Registration alone does not validate capture profile.

Actual unauthenticated GET /api/ble/gateways/4/received-beacons returnsHTTP403. Authenticated response and actual card rendering NOT VERIFIED; no new JWT or credentials generated. RSSI/distance, actualIO548, receiver GPS and timestamps retain existing paths. RAW persistence is proven; decoded-history persistence is not established. No location engine enabled.

## Result

PASS: byte boundaries, actual raw-to-telemetry equality, expected conditional arithmetic, strict rejection under legacy interpretation. PARTIAL: firmware/capture evidence, configured verified associations, authenticated API/card. Packet is structurally consistent with the documented parameterized format, but is not claimed fully validated end-to-end.
