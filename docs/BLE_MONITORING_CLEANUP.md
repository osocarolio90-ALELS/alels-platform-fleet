> Pembaruan monitoring: [decoding kartu dan jarak RSSI](BLE_CARD_DECODING_DISTANCE_FIX.md) menambahkan estimasi scalar generik terkonfigurasi dan decoded sensor profile opt-in. Penghapusan geographic positioning dalam laporan ini tetap berlaku; keterangan distance belum tersedia di bawah adalah historis.

# Final BLE Monitoring cleanup

Decision: **PARTIAL**. Application positioning removal, compiled artifact checks and automated monitoring regressions passed. Deployment verification against the authenticated running application and dedicated PostgreSQL integration tests remain unavailable in this run. The running backend was not restarted. This document supersedes positioning descriptions in previous E1/received-card/scoped-UI reports.

## A. Dependency audit
Repository D:/alels-platform-work, branch fleet-alels-ai. The audit covered backend services/controllers/DTOs/repositories, jobs/workers/DI, resources and POMs, gateway and ingestion sources/configuration, frontend components/helpers/types/API clients/tests, migrations, and documentation. Searches included the mandatory symbol list plus LocationResult, ReceivedLocation, LocationStatus, readiness-v1, CALIBRATION_REQUIRED, INSUFFICIENT_GEOMETRY, positionStatus, methodVersion, uncertaintyMeters, geographic solver, trilateration and multilateration terminology.

| Reference/category | Classification and disposition |
| --- | --- |
| BleLocationEngine, BleLocationReadinessEngine | Exclusive obsolete BLE positioning; removed in the immediately preceding cleanup and absence reconfirmed. |
| Engine Input/Observation, estimate, hasGeometry, 60-second windows and per-beacon assessment collection | Exclusive obsolete assessment plumbing; removed from receivedCards. |
| LocationStatus, LocationResult, ReceivedLocation and DTO location properties | Exclusive positioning response contract; removed from both received and registry cards. |
| Frontend BeaconLocationStatus, location type, positionValid, method/window/confidence bindings | Obsolete presentation contract; removed. |
| BLE workers, scheduled jobs, controller endpoints, entities, positioning repository writes, DI registrations, config and dedicated dependencies | No remaining active implementation discovered. No placeholder or disabled replacement introduced. |
| ReceiverGps and validReceiverGps | Shared receiver-source metadata validation, not geometry/position estimation. Preserved with existing coordinate/satellite/HDOP checks. |
| Device Location, map/device/vehicle location, browser location.search | Shared/unrelated functionality; retained. Search matches do not justify deletion. |
| BLE history rollout configuration | Controls observation persistence, not positioning. Retained unchanged. |
| Historical reports naming deleted symbols | Kept as historical records, explicitly marked superseded and linked to this document. |
| Negative regression assertions mentioning calculated fields | Active regression protection, retained intentionally. |

## B-C. Exact deleted files and removed contract
These deletions were completed in the immediately preceding user-authorized removal; this addendum audited and verified them:

- D:/alels-platform-work/backend/src/main/java/com/alels/backend/telemetry/ble/service/BleLocationEngine.java
- D:/alels-platform-work/backend/src/main/java/com/alels/backend/telemetry/ble/service/BleLocationReadinessEngine.java
- D:/alels-platform-work/backend/src/test/java/com/alels/backend/telemetry/ble/service/BleLocationReadinessEngineTest.java

Directly updated backend files from that removal: EyeBeaconDtos.java, BleObservationService.java, BleObservationServiceTest.java, BleReceivedCardsTest.java, BleReceivedReadOnlyPostgresTest.java in the existing backend telemetry/ble packages. No location placeholders or compatibility solver remain. Received cards now directly return newest parsed observations, retaining parser diagnostics and raw data. A serialization regression asserts absence of location and preservation of receiver GPS/raw measurements.

## D-E. Protected components and database
The addendum compared SHA-256 snapshots of 201 existing files: gateway production sources, ingestion production sources, firmware, migrations, BLE Sensor Register frontend, and Data Receive navigation. All were unchanged. Register backend implementations were read and tested, not edited. No ingestion/ACK/framing/transport/routing/firmware, normalization, persistence, observation lineage, Wasted, map, global theme or navigation changes were made.

No SQL or database mutation was executed. Historical observations, registry entries and receiver GPS are retained. Migrations 026/027 were neither deleted nor edited. Any historical database positioning fields are intentionally left intact; no new migration or schema change was introduced.

- 026 SHA-256: 396551307624446b88d2bd8977eccefa1a9a6ad2db2019b06d7c52e6810d8206
- 027 SHA-256: 19c1b95e451e701772263da7aa15133920fd935da9a937305117bfab0b9886ac

## F-G. Final card contract
Both EyeBeacon and EyeSensor use the existing shared panel/card, query cache, pagination, live polling and independent expansion behavior. No calculated geographic fields, geometry status, positioning window/method, confidence or uncertainty are rendered. Device Latitude/Longitude/Altitude remain the same source receiver GPS, never re-labelled beacon coordinates.

Both compact and expanded cards show separate RSSI (dBm) and Estimated Distance (m). No approved configured RSSI model was found; the shared presentation provides an em dash plus a concise reason. No distance number or calibration was invented, and no positioning service was replaced with a placeholder. Expanded cards show registered Type BLE and Beacon ID/Instance ID for both asset types. Unknown additional parameters and RAW HEX remain visible for both types. Verified registration type and MAC continue to control classification; IO/protocol/measurement presence do not assign a type.

Measurements without verified decoding remain unavailable. Temperature is explicitly raw; humidity, movement, pitch/roll and magnet are not invented from unknown bytes. No verified Eddystone Ranging Data value or approved calibrated distance model was available to display. Additional Data preservation is tested, but this run does not claim new semantic decoding of unsupported sensor parameters.

## H-J. Verification
| Check | Actual result |
| --- | --- |
| Backend clean package + focused observation/register/controller/identity tests | 21 passed, zero failure/error/skip. |
| Gateway complete mvn test | 66 discovered: 61 passed, 5 skipped; zero failures/errors. Skips are DirectDatabaseWifiRegressionPostgresTest requiring its dedicated disposable database. |
| IO-specific gateway coverage | EyeBeaconParserTest 13 and TeltonikaCodec8EParserTest 3 passed. Includes real ALELS Q=68, IO 10500/548, Additional Data raw preservation, signed RSSI, larger/multi-beacon records, equivalent JSON normalization and Codec 8E extraction. |
| Ingestion complete mvn test | 15 passed, zero failure/error/skip. |
| Frontend Node unit tests | 19 passed, zero failure. Includes classification, deduplication, registered naming, receiver GPS, missing type diagnostics and unavailable RSSI distance. |
| Frontend build/type checking | npm run build passed; includes TypeScript. Existing large-chunk warning remains. |
| Chrome headless shared card fixtures | Eight cases passed: EyeBeacon and EyeSensor, each light/dark desktop/mobile. Tests enforce exact visible field sets, no calculated fields, meter distance with reason, dBm RSSI, source timestamps/raw payload, independent expansion, DOM stability after refresh and responsive layout. These are isolated component fixtures, not authenticated live application tests. |
| Compiled backend | Inspected alels-backend-1.0.0.jar: no deleted engine or geographic location-result/status classes. Both compiled Card DTO classes checked for removed location dependencies. |
| Compiled frontend | Inspected device-workspace-page-CbBVWlCi.js: no obsolete positioning symbols/bindings; receiver GPS, RSSI and distance labels present. |
| Preservation | 201 protected-file hashes unchanged, including migrations and navigation. No SQL executed; source and compiled regressions do not prove deployed database state. |

The Codec 8 general telemetry, TCP/session/hot-path and routing regression tests run in the unchanged gateway suite. There is no new native high-AVL-ID support claim for standard Codec 8. No live device packet replay, real Kafka/Direct Database write or authenticated Register/Wasted transaction was executed in this closure.

## K. Diff summary
This addendum changed only:

- D:/alels-platform-work/web-react/src/features/telemetry/device/device-workspace/tabs/telemetry/components/eyebeacon-panel.tsx — shared distance display on both cards, type/identity fields for both, raw parameter preservation for both.
- D:/alels-platform-work/web-react/src/features/telemetry/device/device-workspace/tabs/telemetry/utils/eyebeacon-presentation.ts — centralized unavailable-distance presentation, without an invented model.
- D:/alels-platform-work/web-react/tests/ble-sensor-ui.test.mjs — distance and no-position regressions; removed stale positioning fixture.
- D:/alels-platform-work/web-react/tests/eyebeacon-ui-smoke.mjs — both asset types, meter distance, exact field contracts and negative positioning assertions.
- D:/alels-platform-work/docs/EYEBEACON_PHASE_E1.md
- D:/alels-platform-work/docs/EYEBEACON_RECEIVED_CARDS.md
- D:/alels-platform-work/docs/BLE_SENSOR_SCOPED_UI_IMPLEMENTATION.md
- D:/alels-platform-work/docs/EYESENSOR_DATA_RECEIVE.md
- D:/alels-platform-work/docs/BLE_MONITORING_CLEANUP.md

The last five files document the final architecture or mark superseded reports. Previous uncommitted/untracked repository work was preserved; a repository-wide HEAD diff would include that earlier work and is not an accurate addendum-only diff. No commit or staging was performed.

## L. Remaining limitations
PARTIAL is due to deployment/integration verification, not known broken dependencies. Reload the updated backend through its existing launch environment before live validation; old processes may still serve old DTOs. Dedicated PostgreSQL regression fixtures were not provisioned. No calibrated RSSI model or verified values for currently unsupported sensor parameters are available. No geographic BLE positioning code is retained for those missing capabilities.
