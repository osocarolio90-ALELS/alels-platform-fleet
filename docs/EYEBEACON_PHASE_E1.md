> Historical implementation report. Geographic BLE positioning has been removed. Current architecture and verification: [BLE monitoring cleanup](BLE_MONITORING_CLEANUP.md). Positioning interfaces, statuses and test counts below describe a superseded version and are not current behavior.

> Historical implementation report. Geographic BLE positioning has been removed. Current architecture and verification: [BLE monitoring cleanup](BLE_MONITORING_CLEANUP.md). Positioning interfaces, statuses and test counts below describe a superseded version and are not current behavior.

# EyeBeacon Phase E1 — implementation and verification

Date: 2026-10-28. Branch: fleet-alels-ai. Gate: PARTIAL pending persistent-schema and end-to-end verification.

## Audit and reused behavior

The existing Netty TCP entry point audits frames before publishing. Native Codec 8/8E records go through TelemetryBatchPublisher; ALELS JSON goes through AlelsJsonTelemetryNormalizer. Both existing publishers invoke EyeBeaconTelemetryNormalizer and EyeBeaconParser. The direct publisher already stores parent telemetry and BLE using the same connection/transaction when BLE is present. Kafka ingestion writes BLE inside its existing ingestion transaction. E1 adds no parser, ingestion path, identity generator, registry or observation table.

Important protocol boundary: native Codec 8 uses one-byte AVL IDs and has no variable-length IO group. It cannot encode AVL 10500 or 548. Preserve ordinary Codec 8 support; native EyeBeacon uploads for these IDs require Codec 8E. The official source is https://wiki.teltonika-gps.com/view/Which_data_protocol_to_choose_Codec_8_or_Codec_8_Extended . Existing Codec 8E and ALELS parser tests cover the supported beacon payloads; no fake Codec 8 fixture truncates an ID to make it appear supported.

Actual frontend hierarchy is DeviceWorkspacePage -> TelemetryWorkspaceTab -> DataReceivedPanel. Data Received already has parameter-group buttons and a parameter-card grid. E1 adds EyeBeacon to that same nested navigation and renders a dedicated component only when selected. Existing parameter groups, telemetry cards, route/map panels, colors, CSS, theme and workspace routes are untouched apart from this authorized navigation addition. The UI freeze exception is the user's Phase E1 instruction. Revert the E1 page import/button/branch and remove the added component/API/types/utils to roll back the UI slice; do not reset unrelated working-tree changes.

## Backend API and identity

`GET /api/ble/gateways/{deviceId}/beacons?afterId=0&limit=50`

- Uses the existing authenticated BLE controller, service and repository. Device access is checked with TelemetryDeviceRepository and requires an exact company match, including for broad roles. No cross-device observations are returned as the selected device's latest source.
- Page size is clamped to 1..100; one look-ahead registry row determines hasMore/nextAfterId. The cursor is registry ID ascending. Each card selects the latest VALID observation for the selected company/device/beacon, ordered by source timestamp (or receive timestamp when absent), then receivedAt and observation ID. Old data arriving late cannot replace a newer source event. Across loaded pages, the frontend orders cards by latest observation time and stable IDs.
- Discovery comes from persisted history, without an age filter. Beacons missing from the latest packet stay on the list. Registry identity is unchanged: Eddystone namespace+instance; iBeacon UUID+major+minor; configurable IDs retain their existing gateway/IO scope. No cross-format identity association is invented. Observations without registry identity produce hasUnresolvedObservations, not invented permanent beacon cards.
- Each card exposes identity, latest measurement, source observation/telemetry references, event/receive timestamps, receiver IMEI, protocol, IO and block index, receiver GPS/quality and original block HEX. Display names fall back to the actual protocol/identity because the existing registry has no editable beacon-name column.
- Missing MAC/UUID/major/minor/RSSI/battery/temperature stay null and display as an em dash. Temperature is explicitly labelled raw; the UI does not invent a Celsius conversion. Other configurable parameters and unsupported sensor interpretations remain traceable in block HEX. The current observation schema does not separately store the parser's entire otherParameters/lowBattery map; surfacing those as decoded fields requires an approved persistence decision, not another backend decoder.

Response shape is the typed EyeBeaconDtos.Page: beacons[], hasMore, nextAfterId, hasUnresolvedObservations, serverTime and recentThresholdSeconds. Card has beaconId, displayName, identity, lastSeen, recency, measurement, source and location. Source includes observationId, receiverDeviceId/IMEI, protocol, transport/reason, IO/block index, observedAt/receivedAt, telemetryId/serverTime and receiverGps. Measurement includes rssiDbm, batteryMillivolts, temperatureRaw and rawBlockHex. No credentials, network addresses or raw connection secrets are returned.

## Protocol, physical transport and location

Existing gateway channels GSM/WIFI are selected by protocol handling, not verified physical-link metadata. A TCP peer address does not establish cellular versus Wi-Fi. E1 therefore returns transport UNKNOWN with an explicit reason for these observations, regardless of sourceProtocol. It does not relabel protocol-derived channel values as verified transport. A future verified transport field/persistence contract is a separate dependency.

BleLocationEngine is a future input/output interface only. Its bounded-input owner must authorize company/beacon observations and select a compatible window before invoking a future implementation. LocationResult and LocationStatus centralize the requested NOT_COMPUTED, INSUFFICIENT_GEOMETRY, CALIBRATION_REQUIRED, INVALID_RECEIVER_GPS, INSUFFICIENT_OBSERVATIONS, ESTIMATED_2D, ESTIMATED_3D and COMPUTATION_FAILED states. E1 has no engine or result storage, so every actual card returns NOT_COMPUTED, a reason, and null latitude/longitude/altitude/calculation time/method/window/uncertainty/confidence. It does not diagnose geometry or calibration without running an engine. Receiver GPS is shown separately under diagnostics.

RECENT and STALE describe observation age. Configure `alels.ble.recent-threshold-seconds` (default 300, positive values required; environment equivalent ALELS_BLE_RECENT_THRESHOLD_SECONDS). NEVER POSITIONED is the independent presentation state when no calculation timestamp exists. STALE never means the beacon is physically offline.

## Frontend workflow

The production panel queries the real authenticated API. It uses existing Card/Button components and theme tokens; no new stylesheet or dependency is added. React Query handles 5-second foreground polling when workspace live mode is active, passes cancellation signals into Axios, and reuses the query keyed by device. Refresh/load-more calls do not start another request while one is already active. Cards use registry IDs as keys. Initial loading, empty results, unresolved observations, refresh errors and access errors have explicit states. A transient fetch error retains loaded cards; 401/403/404 suppresses their display. The panel has a responsive single/two-column grid and a bounded scroll area.

## Direct Database development

Use the existing settings for an authorized local database:

```powershell
$env:TELEMETRY_PUBLISHER_MODE='direct'
$env:ALELS_ALLOW_UNSAFE_PUBLISHER_MODE='true'
# Supply ALELS_DB_URL/ALELS_DB_USER/ALELS_DB_PASSWORD through the existing secure local setup.
```

The development opt-in is an existing factory requirement; E1 does not remove it or alter the default Kafka mode. The API/card repository has no Kafka client dependency. Neither Kafka nor ingestion-service needs to run to read existing observations or use direct development mode. No database connection settings were changed during this task.

Direct and Kafka still have the previously documented C2 discrepancies: general telemetry/BLE dedup keys differ, exact source-event identity is not certified, direct downstream IO/normalization/alert calls occur after the parent/BLE transaction, and only the first SQL parser error is persisted by migration 026. E1 does not activate the Canonical Identity v1 design or apply the SQL error candidate. TCP ACK callbacks and raw audit lifecycle are unchanged.

## Files intentionally changed in E1

Existing C2 files extended:

- backend/src/main/java/com/alels/backend/telemetry/ble/controller/BleObservationController.java
- backend/src/main/java/com/alels/backend/telemetry/ble/service/BleObservationService.java
- backend/src/main/java/com/alels/backend/telemetry/ble/repository/BleObservationRepository.java
- backend/src/test/java/com/alels/backend/telemetry/ble/service/BleObservationServiceTest.java
- web-react/src/features/telemetry/device/device-workspace/tabs/telemetry/pages/telemetry-workspace-tab.tsx

Added:

- backend/src/main/java/com/alels/backend/telemetry/ble/dto/EyeBeaconDtos.java
- backend/src/main/java/com/alels/backend/telemetry/ble/service/BleLocationEngine.java
- backend/src/test/java/com/alels/backend/telemetry/ble/repository/BleObservationRepositoryPostgresTest.java
- web-react/src/features/telemetry/device/device-workspace/tabs/telemetry/types/eyebeacon.ts
- web-react/src/features/telemetry/device/device-workspace/tabs/telemetry/api/eyebeacon-api.ts
- web-react/src/features/telemetry/device/device-workspace/tabs/telemetry/utils/eyebeacon-cards.ts
- web-react/src/features/telemetry/device/device-workspace/tabs/telemetry/components/eyebeacon-panel.tsx
- web-react/tests/eyebeacon-cards.test.mjs
- web-react/tests/eyebeacon-ui-smoke.mjs
- docs/EYEBEACON_PHASE_E1.md

All pre-existing parser, canonical-identity design/test, migration 026, SQL candidate, firmware and other untracked work was retained. No staging or commit was performed.

## Executed verification

| Test | Actual result |
| --- | --- |
| gateway: `mvn -q test` | Exit 0; 61 tests, zero failures/errors |
| ingestion-service: `mvn -q test` | Exit 0; 15 tests, zero failures/errors |
| backend: `mvn -q '-Dtest=BleObservationServiceTest,BleObservationRepositoryPostgresTest' test` without disposable URL | Seven service tests passed; four PostgreSQL tests skipped by their explicit environment guard |
| backend: `mvn -q '-Dtest=BleObservationRepositoryPostgresTest' test` with ALELS_E1_TEST_DB_URL pointing to a new disposable localhost:55442 cluster | Exit 0; four PostgreSQL tests passed, zero skipped |
| frontend: `node --experimental-strip-types --test tests/eyebeacon-cards.test.mjs` | Five tests passed: persistent page refresh, dedup/order, null/zero/signed values, no cache mutation or invented position |
| frontend: `npm run build` | Exit 0 on final source; TypeScript compilation and Vite production build passed. Initial replaceAll incompatibility was corrected to the project's supported string API. Vite retains its warning about existing chunks above 500 kB |
| backend: `mvn -q -DskipTests package` | Exit 0 on final source |
| frontend: `node tests/eyebeacon-ui-smoke.mjs` with local headless Chrome | Four fixtures passed: light/dark at exact 1440x1200 and 390x844 viewports. Verified stable card DOM keys on cache refresh, observation ordering, null location, UNKNOWN transport and no horizontal overflow |
| backend: full `mvn -q test` | Incomplete: Java agent attach pipe failure; hung process interrupted. No full-suite PASS claimed and no Mockito configuration changed |

The database tests used PostgreSQL 18.4 with temporary tables matching the query's 026 columns. They execute the production repository SQL through JdbcTemplate, not fake repository results. No migration file was run. Tables were rolled back and the disposable cluster removed. Tests cover two beacon identities, repeated observations, late data, source changes, invalid/error observations, missing RSSI/MAC/sensors/GPS, scoped registry cursors, unauthorized company/device combinations at the service boundary, and persistence across successive reads. They do not prove migration installation, gateway writes or full receiver-to-database behavior.

Chrome testing loads only an isolated component fixture seeded with synthetic React Query data. It does not call a live backend or replace production components with a mock list. Initial sandbox launch failed; the local test was rerun with process permission. A CLI-window screenshot limitation was corrected by explicit DevTools viewport emulation and an assertion of actual innerWidth. Screenshots/DOM are generated under ignored web-react/target/eyebeacon-e1. Desktop light and phone dark screenshots were inspected. This is component/UI evidence, not authenticated application end-to-end evidence.

Full backend attempt logged: `[error][attach] could not open (5) pipe ... to send result of operation load` in backend/target/surefire-reports/2026-10-28T14-07-05_725-jvmRun1.dumpstream. No completed full-suite result was emitted; previously completed reports must not be summed as a new full-suite run. The new service tests use recording repositories and the database tests use actual disposable PostgreSQL.

## Dependencies and gate

The persistent deployment state of 026 remains UNKNOWN. E1 queries require its existing tables; an absent schema causes an API error that the UI reports, not an empty fabricated history. No migration or persistent database was accessed. Before deployment, verify 026 with approved read-only access and make the explicit migration decision requested by the user.

Still unverified/blocked: real gateway-to-Direct-Database writes, Kafka ingestion and replay, physical FMC650/firmware 2.9.2/2.9.3 traffic, real transport verification, full backend environment, decoded additional sensor fields absent from storage, and an authenticated full-workspace browser session against persistent data. The existing C2 identity/error-persistence dependencies remain. Native Codec 8 carrying IO 10500/548 is unsupported by the protocol itself, not a feature to emulate by changing parser semantics.

PARTIAL: the authorized card/API/UI slice is implemented and independently tested; full E1 persistence/deployment verification remains blocked. No E2 positioning solver, calibration, location-result storage or new migration is implemented.

## Wi-Fi regression correction — 2026-10-29

BLE history writes now default to disabled in gateway and ingestion (`ALELS_BLE_HISTORY_ENABLED=false`) until schema 026 is verified. Ordinary telemetry, raw payload and original IO remain stored. History cards do not receive new BLE history while disabled. Explicitly enable the setting in the relevant services only after verification. Real disposable PostgreSQL tests now cover direct publisher writes and ingestion transactions/replay; physical-device, live broker and persistent-deployment verification remain outstanding. See [the regression investigation](ALELS_WIFI_PENDING_REGRESSION.md) for changes, evidence and current build results.

## Received-data cards — 2026-10-29

The EyeBeacon panel now reads and parses persisted received telemetry directly through the existing shared gateway parser. It no longer requires the optional BLE history tables to display received cards. Actual local read-only verification returned eight beacon identities, measurements and `INSUFFICIENT_GEOMETRY` location assessments. Registry/history writes remain gated; no migration was applied. A calibrated positioning solver is still unavailable. See [received-card changes and verification](EYEBEACON_RECEIVED_CARDS.md) for exact scope and results.
