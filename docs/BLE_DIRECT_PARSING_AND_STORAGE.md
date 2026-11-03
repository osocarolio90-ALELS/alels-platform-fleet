# Direct BLE parsing, storage026 and card/table display

Tanggal:2 November2026. Latest user instruction supersedes the registry requirement: no BLE Register, no new migration file, decode real incoming BLE and persist it in026. Source/build/database checks PASS; authenticated live browser/deployment verification remains PARTIAL.

## Processing flow

TCP/ACK, Codec framing, ALELS JSON transport, Kafka/DirectDB ingestion and source telemetry remain unchanged. Existing backend BleObservationService now reuses one parseReceivedPackets implementation for the authenticated received-beacons API and a bounded scheduled BLE projection. It reads at most25 pending telemetry packets per run, prioritizes recent data, and stores source blocks through the existing alels_store_ble_observations function. Source rows and RAW are not rewritten. The scheduler runs on the next loaded backend build, every5seconds by default. Failed packets are logged and remain eligible for retry; other packet transactions can complete.

The queue materializes the canonical source key once per candidate and uses indexed anti-joins. It detects both a previously decoded telemetry row and a retransmitted canonical source block. A transaction-level advisory lock serializes concurrent workers per telemetry. Existing source-block unique constraint makes replay idempotent. Existing observations retain their source key when enriched. SQL source-key bytes were checked against the existing gateway SHA256 contract, including NUL separators. General telemetry identity/dedup is unchanged.

## Migration026 and actual persistence

Before this change, public.ble_beacons and public.ble_observations did not exist locally. Migration026 was extended in place with decoded_payload JSONB, decoder_revision, decoded_at and an index, plus the audited BLE source-key helper. No028 or other new migration file was created. Transactional dry-run succeeded and rolled back; installation then committed successfully with a3second lock timeout. Existing027 and stored asset_ble_sensors data were preserved, not deleted or reapplied.

ble_observations is a historical name for real received measurements, not simulated data. decoded_payload stores the complete decoded card snapshot: measurement values, field errors/profile status, captured identity, verified physical MAC association, protocol, exact IO, timestamps, telemetry provenance and receiver GPS. raw_block_hex remains the original block. parser_status distinguishes VALID, PARTIAL and ERROR; malformed historical data is not normalized to valid sensor measurements.

Backfill completed using a temporary worker invoking the production service/repository, with a pool limited to2connections. The worker was stopped and its pool closed. Snapshot counts:

| Metric | Count |
|---|---:|
| Total source-block/diagnostic rows |4284|
| Rows with decoded snapshot |3941|
| VALID |3899|
| PARTIAL |42|
| ERROR |343|
| Distinct source telemetry IDs |1232|
| IO548 rows, including diagnostics |1055|
| IO10500 rows, including diagnostics |3229|
| Duplicate canonical source blocks |0|

Replay after the completed backfill kept count4284→4284. Live telemetry continues arriving; new packets after that verification require the new backend scheduler to be running. Historical retransmitted source records are represented once by canonical source block, not duplicated for every retry.

## Confirmed capture profiles

The user explicitly confirmed field selection/offset/size, EyeBeacon battery-byte profile and SENSOR1 physical mapping in the latest reply. The actual deployment metadata is in backend/config/ble-capture.properties; parser source contains no receiver/fixture branches. Spring existing external configuration import was tested from the backend working directory. Profiles remain exact receiver/captured-ID selections, not global9byte detection.

- EYE_SENSOR_SHORT_V1:9bytes without received Flags; signed BE temperature/100, humidity, movement MSB plus15-bit counter, signed pitch/roll, battery2000+raw×10.
- EYE_BEACON_BATTERY_V1:1byte with verified battery field capture.
- Existing flags/full-AD/list/dedicated profiles remain supported with their documented structure/model restrictions.

[Teltonika BTSMP1](https://wiki.teltonika-gps.com/view/BTSMP1), [FMC650 Beacon List](https://wiki.teltonika-gps.com/view/FMC650_Beacon_List). Parameterized548 first01 is a format constant, not a count. Legacy format remains separate.385FMC650 remains ManualCAN; model dictionaries are not mixed. No parser/framing changes were required in this task.

## Q569 real stored rows

Telemetry9494, raw packet13308, receiver863719066421737. Source timestamp20:12:16WIB, original JSONB13:12:16; server20:12:16.838773WIB. GPS latitude-6.2111233,longitude107.0100383,altitude30m.

| Captured ID | Physical MAC association | Type/profile | RSSI | °C | RH | Movement/counter | Pitch/roll | mV |
|---|---|---|---:|---:|---:|---|---|---:|
|7CD9F408DA2C|7C:D9:F4:08:DA:2C|EyeBeacon/battery|-44|—|—|—|—|3090|
|7CD9F4134EE1|7C:D9:F4:13:4E:E1|EyeSensor/short|-49|31.41|79%|false/4791|0°/-1°|3110|
|2A57812B0055|7C:D9:F4:14:7A:FC|EyeSensor/short|-59|31.42|72%|false/2212|1°/-2°|3030|

These values were read back from026, not substituted from fixtures. The raw MAC field remains null because548did not transmit an explicit physicalMAC; the confirmed association is stored separately in ble_beacons.verified_mac and decoded_payload.sensor.verifiedMac. Three tenant/gateway-scoped relationships were applied from the user's confirmation, not by treating arbitrary6byte IDs as MACs. Additional sensor fields absent from the bytes remain absent.

## Register removal and classification

Removed backend register controller/service/repository, registration UI/API/schema gate and Wasted BLE panel, navigation/route/translations. Manifest:BLE_REGISTER_REMOVAL_MANIFEST.csv (9sourcefiles, including moved MAC-formatting utilities). Existing register database rows, historical027 and unrelated Wasted vehicle/device/driver workflows are retained. Pure MAC formatting moved into monitoring; no duplicate formatter was introduced.

Monitoring no longer requires ACTIVE registration, sensor names from an asset table or physicalMAC to show a card. Verified capture profiles determine EyeSensor/EyeBeacon for current548records; parsed beacon formats and documented sensor structures determine other supported classifications. Unknown layouts remain unclassified in the parsed table rather than assigning a guessed physical asset. The DTO sensor metadata retains its existing envelope for API compatibility; it no longer invokes registration CRUD or gates display. PhysicalMAC remains optional and separate from captured identity.

## Card and Data Receive behavior

EyeBeacon/EyeSensor cards use parsed type without the former registration/MAC gate. Name defaults to decoded type plus received ID, or an actually transmitted device name. RSSI, Generic/Uncalibrated distance, exact548/10500, source protocol/codec, timestamps and receiver GPS stay available. No geographic engine/calculated BLE location.

ALL Data and BLE display a parsed BLE table, one row per telemetry/IO/block, with type/profile, capturedID, optionalMAC, RSSI,distance,temperature,humidity,movement/counter,pitch/roll,battery and diagnostics/status. Generic548/10500HEX parameter cards are filtered from that display; RAW stays in database and diagnostic detail. Pagination retains all received source blocks; card views keep the latest per identity. Multiple records in the same packet are not collapsed during persistence.

## Exact production files changed in this task

- backend/src/main/java/com/alels/backend/telemetry/ble/service/BleObservationService.java — remove registry dependency, shared parser/read projection, profile/protocol classification and scheduler.
- backend/src/main/java/com/alels/backend/telemetry/ble/repository/BleObservationRepository.java — independent verified identity lookup, bounded backlog query, transactional026projection and snapshots.
- backend/src/main/java/com/alels/backend/telemetry/ble/service/BleMacAddress.java — move existing explicitMAC utility out of deleted register module.
- backend/src/main/resources/application.properties — remove obsolete register-specific profile property and import verified external capture configuration.
- backend/config/ble-capture.properties — user-confirmed per-capture deployment profiles.
- database/migrations/026_ble_observation_history.sql — extend/install the requested existing026storage, no new migration file.
- web-react/src/app/router.tsx — remove register route/lazy import.
- web-react/src/layouts/app-layout.tsx — remove register navigation entry.
- web-react/src/lib/i18n.ts — remove register navigation translations.
- web-react/src/features/asset-register/pages/wasted-page.tsx — remove only BLE Wasted branch.
- web-react/src/features/telemetry/device/device-workspace/tabs/telemetry/utils/ble-mac.ts — move existing normalizer into monitoring.
- web-react/src/features/telemetry/device/device-workspace/tabs/telemetry/utils/eyebeacon-cards.ts — remove register/MAC display gate.
- web-react/src/features/telemetry/device/device-workspace/tabs/telemetry/utils/eyebeacon-presentation.ts — derive display name from parsed metadata, no registry dependency.
- web-react/src/features/telemetry/device/device-workspace/tabs/telemetry/components/eyebeacon-panel.tsx — shared received query, direct parsed cards and source-block table.
- web-react/src/features/telemetry/device/device-workspace/tabs/telemetry/pages/telemetry-workspace-tab.tsx — bind parsed table and replace generic548/10500cards only.

## Actual validation

- Backend final mvn package:46tests,0failures/errors/skips,BUILD SUCCESS20:44:21WIB.
- Frontend npm run build:TypeScript and Vite PASS; existing large-chunk warning only.
- Real database migration dry-run and installation PASS.
- Real backfill, Q569field readback and replay idempotency PASS; no source-block duplicates.
- SQL source-key parity with existing gateway byte contract PASS.
- Actual PostgreSQL snapshot → actual React BeaconCard and BleReceivedTable SSR PASS:oneBeacon/twoSensors without register or physicalMAC, three table rows even with repeated pagination, expectedvalues/units/zero/false/source548.
- Spring external capture-profile configuration import PASS.
- Packaged JAR contains current BLE service/repository/formatter and zero obsolete register classes. All frontend sources validate as UTF8. git diff --check PASS (existing CRLF warnings only).
- Temporary worker/checker sources, compiled classes, bundle and verification JSON are removed after validation; logs remain. Dedicated BLE test sources previously removed remain removed per user's cleanup requirement, so permanent BLE regression coverage is reduced.

## Runtime boundary and required reload

Actual unauthenticated received-beacons endpoint still returns403. Browser connector reported Chrome unavailable; no authenticated live card screenshot was obtained. Existing backend startup environment is held outside this shell; no new launcher/JWT/credential/environment or service restart was invented. The running8080process is not claimed to have reloaded the newly built classes. Start/restart only the backend through the user's existing mechanism from the repository/backend working directory to load profiles and enable ongoing scheduled storage; gateway restart is not required. Refresh Vite/browser afterward. Actual authenticated API/card and continued projection after reload remain the final runtime checks.

Final status: database/backfill/source/build/SSR PASS; deployment/authenticated visual verification PARTIAL. No general telemetry, TCP/ACK, JSONformat, firmware, Kafka/DirectDB routing or source identity changes were made. Global styling and unrelated UI behaviors remain unchanged; the register navigation/removal and parsed table were explicitly authorized by the latest user instruction.

Temporary verification deletion manifest: [BLE_DIRECT_PARSING_VERIFICATION_CLEANUP.csv](BLE_DIRECT_PARSING_VERIFICATION_CLEANUP.csv). Register module deletion manifest: [BLE_REGISTER_REMOVAL_MANIFEST.csv](BLE_REGISTER_REMOVAL_MANIFEST.csv).
