> Final architecture and verification: [BLE monitoring cleanup](BLE_MONITORING_CLEANUP.md). Both compact and expanded card types now expose RSSI and Estimated Distance (m). Earlier test counts below are historical.

> Update: The user requested removal of beacon location optimization. BLE card DTOs no longer contain a calculated `location`; the engine interface, readiness implementation and their tests were removed. Receiver GPS remains source metadata. EyeBeacon expanded data now shows RSSI, battery, optional parsed temperature/low-battery, unknown parameter raw hex, and RAW HEX. No calculated coordinates or geometry status are emitted. Historical positioning notes below are superseded by this update.

# EyeSensor Data Receive addendum

## Implemented scope
EyeSensor is immediately after EyeBeacon and before BLE. All previous tabs retain their order. Both tabs reuse EyeBeaconPanel and BeaconCard, with existing per-card expansion, query cache, polling and pagination. No new endpoint, table or migration.

Active company-scoped asset_ble_sensors registrations supply type through the existing sensor DTO. A verified MAC match is required. No classification by IO ID, Eddystone, signal or payload size. Unregistered/unknown observations remain in the existing API/history and are excluded from both typed panels. No new verified payload profile was invented. One latest card per verified MAC; stable React keys use that MAC. Registration updates are reflected on the next existing poll/refetch.

EyeSensor expanded content contains identity, registration type, source timestamps/protocol/receiver, receiver coordinates, RSSI, battery, raw temperature and placeholders for unsupported sensor fields. Compact cards show name, MAC, RSSI and distance availability. Distance is unavailable because there is no calibrated distance model. Humidity, motion, pitch, roll and magnet remain unavailable because the existing decoder does not supply verified values. Temperature is explicitly raw, without unverified Celsius conversion.

## Exact changed frontend files
- D:/alels-platform-work/web-react/src/features/telemetry/device/device-workspace/tabs/telemetry/pages/telemetry-workspace-tab.tsx
- D:/alels-platform-work/web-react/src/features/telemetry/device/device-workspace/tabs/telemetry/components/eyebeacon-panel.tsx
- D:/alels-platform-work/web-react/src/features/telemetry/device/device-workspace/tabs/telemetry/types/eyebeacon.ts
- D:/alels-platform-work/web-react/src/features/telemetry/device/device-workspace/tabs/telemetry/utils/eyebeacon-cards.ts
- D:/alels-platform-work/web-react/tests/eyebeacon-cards.test.mjs

## Backend files
- D:/alels-platform-work/backend/src/main/java/com/alels/backend/telemetry/ble/dto/EyeBeaconDtos.java: additive optional sensor.type, compatibility constructor retained.
- D:/alels-platform-work/backend/src/main/java/com/alels/backend/telemetry/ble/service/BleObservationService.java: active company-scoped type lookup using existing register repository list method and verified identity matching; existing authorization retained.
- D:/alels-platform-work/backend/src/test/java/com/alels/backend/telemetry/ble/service/BleReceivedCardsTest.java: equivalent normalized stored Codec 8E/ALELS JSON payloads classified using registration, unknown identities retained.

## Validation
- Frontend Node unit tests: 18 passed, zero failed.
- Backend focused tests: 18 passed (7 received cards, 7 observation service, 4 location readiness), zero failed/skipped.
- TypeScript check passed. Production frontend build passed with existing large-chunk warning.
- Codec 8E/JSON test exercises stored equivalent IO payloads through the shared decoder, not live TCP framing or device transmission.
- No live browser/end-to-end tab, field visibility or flicker verification performed. Tab insertion verified from navigation code; automated UI interaction coverage remains a limitation.
- No changes this task to BLE Register/Wasted, gateway parser, ingestion, transports, ACK, Kafka, routing, firmware, global styling or other menus. Earlier workspace edits remain intact.

## Runtime
Backend must load the updated DTO/service for sensor.type to be present. This task did not restart the running backend. Older backend responses lacking type cannot populate typed panels. Frontend uses the existing refresh strategy; no global reload added.
