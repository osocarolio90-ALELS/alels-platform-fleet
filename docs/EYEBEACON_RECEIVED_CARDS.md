> Historical implementation report. Geographic BLE positioning has been removed. Current architecture and verification: [BLE monitoring cleanup](BLE_MONITORING_CLEANUP.md). Positioning interfaces, statuses and test counts below describe a superseded version and are not current behavior.

> Historical implementation report. Geographic BLE positioning has been removed. Current architecture and verification: [BLE monitoring cleanup](BLE_MONITORING_CLEANUP.md). Positioning interfaces, statuses and test counts below describe a superseded version and are not current behavior.

# EyeBeacon cards from received telemetry — 2026-10-29

The local database was inspected first, with explicit user authorization and read-only transactions. Telemetry was current. Of the latest 1,000 packets initially inspected, 460 contained supported BLE IO. `ble_beacons` and `ble_observations` were absent. Therefore, a UI depending only on the optional registry/history tables could not show the received BLE data. No migration, backfill or database modification was performed.

## Implementation

The existing BLE repository/service/controller now expose `GET /api/ble/gateways/{id}/received-beacons?beforeId=0&limit=50`. It reads the selected authorized device's persisted telemetry, with company and device scope in SQL as well as the existing device-access check. This is a read model of existing received data, not a second ingestion pipeline or a write-through fallback. Existing registry/history endpoints remain available and unchanged in purpose.

The backend reuses the exact `EyeBeaconParser` and `EyeBeaconTelemetryNormalizer` compiled by gateway. A small Maven `ble-parser` classifier contains only those classes, their nested records and their existing model/enum dependencies. No parser source was copied, rewritten or moved; no third-party runtime dependency was added. Build order is explicit in CI and the local quality-gate script. For manual builds, run `mvn -f gateway/pom.xml -DskipTests install` before building backend.

All six existing IO IDs are selected and decoded: 10500, 548, 385, 10828, 10829 and 10831. One card is presented per format-aware identity across loaded packet pages. Eddystone Namespace + Instance and iBeacon UUID + Major + Minor retain their full identities. Custom identities are additionally scoped by gateway and IO. Only explicit parser MAC values are shown. There is no synthetic registry or observation ID. Signed RSSI, battery mV, raw temperature, low-battery state, additional parameter HEX and original block HEX are retained. Structured parser diagnostics identify telemetry, IO, error code, offset and RAW HEX without hiding valid packets.

The query defaults to 50 BLE packets per page, bounded to 100 with one lookahead row. The cursor is the last scanned telemetry ID. Older packets can be loaded explicitly; identities absent from the latest packet remain visible when present in loaded pages. The endpoint does not claim to enumerate the entire history in one response. The parser's existing protocol payload limits remain in force. No history write is required for cards, and `ALELS_BLE_HISTORY_ENABLED` remains unchanged.

The existing EyeBeacon component now consumes this endpoint, refreshes while live, merges repeated identities, retains newer measurements across pages and renders one card per identity. Other Telemetry, Maps, Trip & Route, theme and firmware features are unchanged.

## Location status and limits

`BleLocationReadinessEngine` implements the existing location-engine boundary on the read side. It evaluates actual per-beacon observations within 60 seconds of the beacon's latest observation in each packet page, deduplicating the same telemetry row across IOs. It checks finite/nonzero receiver coordinates, satellite count, HDOP, usable RSSI and independent spatial geometry. Results distinguish invalid GPS, insufficient observations, insufficient geometry and calibration required. Assessments are page-local; loading older pages does not combine them into an asserted multi-page position estimate.

Actual received data returned `INSUFFICIENT_GEOMETRY`. Repeated stationary receiver observations cannot establish a beacon coordinate. A calibrated optimization solver and calibrated RSSI model are not configured or implemented by this change. Even with independent geometry, the result remains `CALIBRATION_REQUIRED`. Latitude, longitude, altitude, confidence and uncertainty stay null; receiver GPS is shown only as receiver diagnostics. No location tables, stored positions, fabricated distances or coordinates were introduced. This completes received-data parsing/cards and readiness reporting, not a calibrated positioning solver.

## Actual sample

Production parser test fixture uses actual telemetry 8394 (IO 10500), which decodes eight Eddystone observations with zero errors. Subsequent read-only service verification produced these values from telemetry 8457:

| Namespace | Instance | RSSI dBm | Battery mV |
| --- | --- | ---: | ---: |
| 00000000000000000000 | 7CD9F408DA30 | -55 | 3090 |
| 00000000000000000000 | 7CD9F408DA2B | -60 | 3070 |
| 00000000000000000000 | 7CD9F408DA2F | -55 | 3060 |
| 00000000000000000000 | 7CD9F408DA2C | -50 | 3080 |
| 18B47FACD68055AAC35F | 2A57812B0055 | -61 | 3040 |
| 00000000000000000000 | 7CD9F4134EE1 | -68 | 3110 |
| 00000000000000000000 | 7CD9F408DA32 | -62 | 3140 |
| 00000000000000000000 | 7CD9F408DA22 | -55 | 3110 |

These Eddystone instances are not fabricated MAC addresses. No temperature or UUID is invented where absent. Transport remains UNKNOWN because the stored channel alone is not independent evidence of physical transport.

## Verification

- Backend focused run: **19 passed, zero failed/errors/skipped** (`BleObservationServiceTest` 7, `BleReceivedCardsTest` 6, `BleLocationReadinessEngineTest` 4, `BleReceivedReadOnlyPostgresTest` 2). Tests cover the actual eight-beacon sample, all other supported IO IDs, malformed blocks alongside valid data, identity merging, authorization, cursors, GPS/geometry gates and actual scoped PostgreSQL queries. PostgreSQL enforces `transaction_read_only=on`; the test rolls back and closes its connection. No fixture DDL or writes run on the local database.
- Gateway `mvn -q test`: **61 passed, 5 skipped, zero failures/errors**. The five existing disposable-database regression cases intentionally skip without their dedicated test database. Gateway `mvn -q -DskipTests install` succeeded, including the parser-only artifact. An initial sandboxed install lacked cache write permission and was rerun successfully with process permission.
- Backend `mvn -q -DskipTests package`: exit 0. Full unrelated backend tests were not rerun; no full-suite PASS is claimed.
- Frontend identity/card tests: **6 passed**. `npm run build`: exit 0, including TypeScript compilation; existing Vite large-chunk warning remains.
- Headless Chrome: **four fixtures passed**, desktop 1440x1200 and phone 390x844 in light/dark themes, seeded with the actual eight-card read-only service response. Checks cover stable card DOM identity during a simulated refresh, ordering, unavailable coordinates, unknown transport and no horizontal viewport overflow. These are real component renders, not an authenticated live application session. Earlier attempts exposed Windows Chrome profile handle races and a build/fixture timing conflict; the final run follows completed build, waits for a readable DevTools port and cleans its profile asynchronously.
- Quality-gate PowerShell syntax and smoke-script JavaScript syntax pass; `git diff --check` passes. Hosted CI and the full quality gate were not run.

Ignored verification artifacts: `backend/target/ble-received-readonly.json`, backend Surefire reports, and `web-react/target/eyebeacon-e1/` screenshots/DOM. The UI test simulates a one-second timestamp update solely to verify stable card ordering.

## Changed paths in this task

- `gateway/pom.xml`: attach the existing parser classes as the small classifier.
- `backend/pom.xml`: consume that classifier with transitive dependencies excluded.
- `.github/workflows/alels-quality-gate.yml`, `tools/maintenance/run-quality-gate.ps1`: install the shared artifact before backend builds.
- `backend/src/main/java/com/alels/backend/telemetry/ble/dto/EyeBeaconDtos.java`: received-data DTOs, additional decoded fields, nullable persisted observation ID.
- `backend/src/main/java/com/alels/backend/telemetry/ble/repository/BleObservationRepository.java`: scoped, cursor-paginated received BLE packet query.
- `backend/src/main/java/com/alels/backend/telemetry/ble/service/BleObservationService.java`: shared-parser received cards and per-beacon readiness assessment.
- `backend/src/main/java/com/alels/backend/telemetry/ble/service/BleLocationEngine.java`, `BleLocationReadinessEngine.java`: clarify read-side boundary and implement quality assessment.
- `backend/src/main/java/com/alels/backend/telemetry/ble/controller/BleObservationController.java`: received-card endpoint.
- `backend/src/test/java/com/alels/backend/telemetry/ble/service/BleReceivedCardsTest.java`, `BleLocationReadinessEngineTest.java`, `BleReceivedReadOnlyPostgresTest.java`: focused tests and opt-in read-only database verification.
- `web-react/src/features/telemetry/device/device-workspace/tabs/telemetry/api/eyebeacon-api.ts`, `types/eyebeacon.ts`, `utils/eyebeacon-cards.ts`, `components/eyebeacon-panel.tsx`: received endpoint, cursor, identity merging and card contents.
- `web-react/tests/eyebeacon-cards.test.mjs`, `eyebeacon-ui-smoke.mjs`: received-card regressions and real-response UI fixture support.
- This report and `docs/EYEBEACON_PHASE_E1.md`: current behavior and limits.

No gateway receiver, ACK, publisher, ingestion logic, database schema, firmware or unrelated production UI was changed by this task. Existing uncommitted work was preserved; nothing was staged or committed. Built backend JAR and frontend `dist` are ready. No running application was restarted or deployed; applying these artifacts to the running backend/frontend and authenticated workspace verification remain operational steps.
