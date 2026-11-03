# BLE production ownership / dependency audit — 2 November 2026

Audit performed before implementation and cleanup. No deployment/database mutation authorized or performed.

| Owner | Production path | Dependency / protection |
| --- | --- | --- |
| Shared wire-independent BLE parser | gateway/src/main/java/com/alels/gateway/parser/EyeBeaconParser.java | Reused via Maven ble-parser classifier; no second decoder |
| General BLE normalization | gateway/src/main/java/com/alels/gateway/normalizer/EyeBeaconTelemetryNormalizer.java | Protected; do not change source identity/persistence |
| Transport/framing/ACK/publish | gateway server/netty/parser Codec classes; ingestion-service | Protected; mixed regression tests retained |
| Durable source | telemetry.io_data and existing telemetry timestamps/GPS | Read only; no rewrites |
| Optional identity/history | database/migrations/026_ble_observation_history.sql: ble_beacons.verified_mac / ble_observations | Existing association model; migration and historical rows protected; both tables absent locally |
| Registry | asset_ble_sensors; backend/assetregister repository/controller/service | Active tenant-owned name, MAC and type authoritative; no schema/CRUD/Wasted changes |
| Identity display utility | backend/.../assetregister/service/BleSensorIdentity.java | Parsing MAC remains; sample-only display fallback must be removed without touching registry operations |
| BLE read model | backend/.../telemetry/ble/repository/BleObservationRepository.java and service/BleObservationService.java | Candidate IO read path, registration matching, metadata; remove sample IDs/receiver restrictions; ambiguous matches unresolved |
| Existing configuration | backend/src/main/resources/application.properties | Spring external property mechanism; existing short-profile-identities, avl-profiles, slot-identities. No enabled sample defaults |
| BLE API | backend/.../telemetry/ble/controller/BleObservationController.java; dto/EyeBeaconDtos.java | Read-only cards and exact IO/provenance |
| Distance | backend/.../telemetry/ble/service/BleRssiDistanceEstimator.java | Preserve centralized Generic/Uncalibrated scalar estimate |
| Cards/filter | web-react/.../telemetry/components/eyebeacon-panel.tsx; utils/eyebeacon-cards.ts and eyebeacon-presentation.ts | Existing polling/layout/type routing; only data-field support if required |
| Production package registration | gateway/pom.xml ble-parser classifier; backend/pom.xml classifier dependency | Runtime dependency, NOT test-only; retain |

## Cleanup ownership

BLE-exclusive candidates: gateway EyeBeaconParserTest; backend telemetry/ble tests; backend assetregister BLE-only tests; frontend four BLE test/fixture files; database/tests/026_ble* and wifi-ble-history-regression.sql fixture. Audit dependencies before deleting each. Mixed TeltonikaCodec8EParserTest, DirectDatabaseWifiRegressionPostgresTest, WifiHistoryRolloutPostgresTest, shared canonical identity tests and general replay/capacity tools are not BLE-only and remain. A mixed fixture may be required by those tests and must not be removed before its dependency is resolved. Existing migrations are production, not fixtures.

Generated fixtures under module target and BLE monitoring screenshot directories are temporary outputs. Preserve this audit, protocol documentation and test summaries, but remove BLE-only generated data/source after verification. No production component may depend on these outputs.

## Evidence / limits

Local registry has only MAC/name/type/tenant/lifecycle columns; it has no Namespace/Instance/UUID association fields. Optional verified identity/history tables are absent. No firmware or capture configuration columns were found on devices. Removing sample exceptions cannot manufacture the missing verified associations. Use existing optional verified identity records and explicit external capture configuration; otherwise return unresolved/pending diagnostics. No schema change or activation of unverified nine-byte profiles is allowed.

Official sources being audited: Teltonika BTSMP1, BTSID1, FMC650 Beacon List, Advanced Beacon Capturing, GH5200 AVL/Bluetooth tables. FMC650385 is Manual CAN, while other documented families use385 Simple Beacon. 11317/dedicated108xx applicability and endian remain family/profile specific. No model inference from a plausible numeric value.
