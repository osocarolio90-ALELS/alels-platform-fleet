> Historical implementation report. Geographic BLE positioning has been removed. Current architecture and verification: [BLE monitoring cleanup](BLE_MONITORING_CLEANUP.md). Positioning interfaces, statuses and test counts below describe a superseded version and are not current behavior.

# Scoped BLE Sensor UI implementation

Date: 2026-10-30, Asia/Jakarta. Repository: `D:/alels-platform-work`, branch `fleet-alels-ai`. Final gate: **PARTIAL** — safe UI and display bindings verified; asset persistence requires approval of a new schema. No migration was created or run, no firmware/ingestion/ACK/location-algorithm change, no staging or commit.

## A. Audit and file-level impact map

The existing reusable UI is `DriverRegisterPage`, `VehicleRegisterPage`, `AssetWastedPage`, `DataTable`, `OrganizationTableCard`, `PageHeader`, existing inputs/buttons and Asset Move patterns. Asset movement is an authorized ownership change with source/target scope and dependency checks, not a Wasted operation. Existing Wasted backend supports Vehicle/Device/Driver only. BLE is not passed into its legacy type dispatch.

Read-only inspection of the actual PostgreSQL schema found no BLE Sensor asset persistence; `asset_drivers` was the only `asset_*` table. None of the existing tables may safely store these assets without changing their meaning. The affected persistence portion stops at a concrete [schema proposal](D:/alels-platform-work/docs/BLE_SENSOR_ASSET_SCHEMA_PROPOSAL.md). Independent UI work proceeds.

| Area | Reuse and allowed impact |
| --- | --- |
| Asset Register | New BLE Sensor page, one lazy route, one asset menu entry and its two translation labels. Existing Driver/Vehicle pages untouched. |
| Wasted | Add a BLE Sensor selection and isolated gated panel. Existing type state, requests and actions retain their prior behavior when selected. |
| EyeBeacon | Change internal card sections and view bindings; preserve parent panel, query key, refresh, load/error handling, pagination, list identity and responsive grid. |
| Backend | Add read-only feature availability, pure asset MAC validation/profile presentation and additive card sensor information. No asset writes or new tables. |

## B. Exact changed/new paths

“Existing” includes files already untracked before this task; they were modified in place, not replaced with duplicate implementations.

| Status | Actual path | Change |
| --- | --- | --- |
| New | [BleSensorIdentity.java](D:/alels-platform-work/backend/src/main/java/com/alels/backend/assetregister/service/BleSensorIdentity.java) | MAC validation and narrowly verified display mapping. |
| New | [BleSensorRegisterService.java](D:/alels-platform-work/backend/src/main/java/com/alels/backend/assetregister/service/BleSensorRegisterService.java) | Authenticated schema gate and role capability metadata. |
| New | [BleSensorRegisterController.java](D:/alels-platform-work/backend/src/main/java/com/alels/backend/assetregister/controller/BleSensorRegisterController.java) | GET availability only; no pretend CRUD endpoints. |
| Existing | [EyeBeaconDtos.java](D:/alels-platform-work/backend/src/main/java/com/alels/backend/telemetry/ble/dto/EyeBeaconDtos.java) | Additive sensor presentation data; original constructors supported. |
| Existing | [BleObservationService.java](D:/alels-platform-work/backend/src/main/java/com/alels/backend/telemetry/ble/service/BleObservationService.java) | Attach verified presentation MAC and explicit unavailable-registration status to card responses. |
| New | [BleSensorIdentityTest.java](D:/alels-platform-work/backend/src/test/java/com/alels/backend/assetregister/service/BleSensorIdentityTest.java) | Format and exact-profile positive/negative tests. |
| New | [BleSensorRegisterServiceTest.java](D:/alels-platform-work/backend/src/test/java/com/alels/backend/assetregister/service/BleSensorRegisterServiceTest.java) | Six-role gate matrix and fail-closed company/role checks. |
| New | [BleSensorRegisterControllerTest.java](D:/alels-platform-work/backend/src/test/java/com/alels/backend/assetregister/controller/BleSensorRegisterControllerTest.java) | Actual MVC availability response, missing-context rejection and no mutation handlers. |
| Existing | [BleReceivedCardsTest.java](D:/alels-platform-work/backend/src/test/java/com/alels/backend/telemetry/ble/service/BleReceivedCardsTest.java) | Verify display MAC mapping while raw parser MAC remains null. |
| New | [ble-sensor-register-api.ts](D:/alels-platform-work/web-react/src/features/asset-register/api/ble-sensor-register-api.ts) | Authenticated gate request only. |
| New | [ble-sensor.ts](D:/alels-platform-work/web-react/src/features/asset-register/utils/ble-sensor.ts) | Frontend MAC normalization, name/type validation. |
| New | [ble-sensor-schema-gate.tsx](D:/alels-platform-work/web-react/src/features/asset-register/components/ble-sensor-schema-gate.tsx) | Company/user/role-keyed capability query and honest availability notice. |
| New | [ble-sensor-wasted-panel.tsx](D:/alels-platform-work/web-react/src/features/asset-register/components/ble-sensor-wasted-panel.tsx) | Gated BLE Wasted columns and disabled action structure. |
| New | [ble-sensor-register-page.tsx](D:/alels-platform-work/web-react/src/features/asset-register/pages/ble-sensor-register-page.tsx) | Requested form, list columns and disabled asset action structure. |
| Existing | [wasted-page.tsx](D:/alels-platform-work/web-react/src/features/asset-register/pages/wasted-page.tsx) | BLE Sensor tab; no BLE requests routed as Vehicle/Driver/Device. |
| Existing | [router.tsx](D:/alels-platform-work/web-react/src/app/router.tsx) | One BLE Sensor Register route under existing authenticated Asset Register. |
| Existing | [app-layout.tsx](D:/alels-platform-work/web-react/src/layouts/app-layout.tsx) | One BLE Sensor Register asset-menu link; no workspace navigation change. |
| Existing | [i18n.ts](D:/alels-platform-work/web-react/src/lib/i18n.ts) | Only new menu label in English and Indonesian. |
| Existing | [eyebeacon-panel.tsx](D:/alels-platform-work/web-react/src/features/telemetry/device/device-workspace/tabs/telemetry/components/eyebeacon-panel.tsx) | Simplified internal card fields. Parent lifecycle/refresh/error handling unchanged. |
| Existing | [eyebeacon.ts](D:/alels-platform-work/web-react/src/features/telemetry/device/device-workspace/tabs/telemetry/types/eyebeacon.ts) | Optional additive sensor presentation contract. |
| New | [eyebeacon-presentation.ts](D:/alels-platform-work/web-react/src/features/telemetry/device/device-workspace/tabs/telemetry/utils/eyebeacon-presentation.ts) | Pure display mapping; no Instance-to-MAC heuristic in frontend, no positioning calculation. |
| New | [ble-sensor-ui.test.mjs](D:/alels-platform-work/web-react/tests/ble-sensor-ui.test.mjs) | Nine form/display/protocol/GPS/evidence cases. |
| Existing | [eyebeacon-ui-smoke.mjs](D:/alels-platform-work/web-react/tests/eyebeacon-ui-smoke.mjs) | Revised card field contract; additional isolated Register/Wasted mode using the same browser harness. |
| New | [BLE_SENSOR_ASSET_SCHEMA_PROPOSAL.md](D:/alels-platform-work/docs/BLE_SENSOR_ASSET_SCHEMA_PROPOSAL.md) | Concrete minimum schema, lifecycle, permission and retention proposal; no migration file. |
| New | [BLE_SENSOR_SCOPED_UI_IMPLEMENTATION.md](D:/alels-platform-work/docs/BLE_SENSOR_SCOPED_UI_IMPLEMENTATION.md) | This audit/implementation/validation record. |

## C–E. Register, Wasted and backend authorization

Register route: `/asset-register/ble-sensor-register`. Form fields: Name BLE Sensor, MAC Address, Type BLE. Type choices are EyeSensor/EyeBeacon. Compact, colon-separated and hyphen-separated 48-bit MACs normalize to uppercase colon form; malformed or mixed separators are rejected. Name and type validation is implemented. List columns are Name, MAC Address, Type BLE, Created At, Created By and Action. Move/Edit/Delete structure is present but cannot operate without real persistence. No assets are synthesized from incoming telemetry, stored in browser storage or inserted into unrelated tables.

Wasted adds BLE Sensor within the existing module. Its unavailable state is distinct from an asserted empty database list. Original Wasted tabs are preserved. Restore/Permanent Delete structure remains disabled. No operation is silently converted to Vehicle, and no destructive request is sent.

`GET /api/asset-register/ble-sensors/availability` requires an authorized company context and one of the six supported roles. It reports the schema gate, restore eligibility for all six roles, and permanent-delete eligibility only for Superadmin/Owner. No company asset rows are exposed. These flags are capability metadata, **not completed persistence authorization**. CRUD, scoped Move, soft delete, restore, permanent-delete conflict handling and their permission enforcement await schema approval; mutation endpoints are intentionally absent. Their success is not claimed based on unit/MVC tests.

## F. EyeBeacon before/after

Before: Identity, Latest BLE measurement, Beacon location, Receiver/data source, and expandable internal observation diagnostics.

After, within the same cards/grid:

1. **BLE Sensor Information:** Name BLE Sensor, normalized verified MAC, advertisement protocol, source protocol, actual server timestamp, separate device timestamp, receiver IMEI.
2. **Device Location:** Latitude, longitude, altitude from that same BLE-bearing telemetry record. Invalid/missing source GPS displays unavailable values; no nearby general-telemetry replacement.
3. **EyeBeacon Data:** RSSI, decoded battery mV, explicit position availability, calculated latitude/longitude/altitude where an evidenced result exists.

Removed from the card: scope, Namespace/Instance rows, method/window details, internal IDs, block indexes, raw HEX, HDOP/satellites, transport narrative and internal diagnostic details. Their DTO/API values and persistence are retained. Existing authorized observation/history APIs remain the diagnostic paths when their schema is deployed. The parent panel's existing parser-diagnostics handling outside the card was not changed.

Current registration names are unavailable because no registration schema exists. Cards use a safe unregistered label plus verified MAC or full protocol identity. Frontend binding accepts a name only with active registration status and a verified MAC. Actual database-backed matching, immediate reflected name edits and excluding soft-deleted registrations in SQL remain blocked, not simulated.

## G. Verified Instance → MAC behavior

The user explicitly confirmed **only Instance `7CD9F408DA22`**, not every `7CD9F4...` instance. Mapping is narrowed further to the known ALELS receiver IMEI `863719066421737`, Eddystone advertisement protocol and namespace `00000000000000000000`. Its display MAC is `7C:D9:F4:08:DA:22`. Different instances, namespaces, receiver devices or advertisement protocols are not mapped. Explicit valid parser MACs take precedence.

Actual read-only response verified that this card has `sensor.verifiedMac=7C:D9:F4:08:DA:22`, while `identity.mac` remains null. Thus standard Eddystone semantics and IO decoding are unchanged. The backend presentation layer holds the mapping; the frontend never globally converts Instance IDs to MACs. No other beacon has been marked verified.

## H. GPS, protocols and calculated position

The card reads only `source.receiverGps` for Device Location. It does not decode raw D/E/F again: the existing ALELS normalizer supplies D/10,000,000 longitude, E/10,000,000 latitude and F altitude; existing normalized Codec fields are used as supplied. No unit is invented for source altitude. Source/server timestamps use `observedAt`/`receivedAt` separately, with an em dash for missing device time.

Advertisement EDDYSTONE displays Eddystone; IBEACON displays iBeacon; EyeSensor/EyeBeacon labels are used only if the parsed protocol actually declares them. Registered asset type is never substituted for advertisement protocol. ALELS_JSON, TELTONIKA_CODEC8 and TELTONIKA_CODEC8E display ALELS JSON, Codec 8 and Codec 8E.

Calculated coordinates require an ESTIMATED_2D/3D result, finite coordinates, method, calculation timestamp and valid source window evidence. A 2D result has unavailable altitude; a 3D result may display finite altitude. Receiver GPS can remain visible even when positioning is unavailable. No coordinate copies, new solver, inferred distance/direction or algorithm changes occur.

## I. Database dependency

The required new asset table and unique active company/MAC constraint are proposed in the linked document. Duplicate-registration races, Wasted lifecycle, name lookup and retention/FK conflicts cannot be verified without that schema. Migration 026 was not modified; its SHA-256 remains `396551307624446B88D2BD8977ECCEFA1A9A6AD2DB2019B06D7C52E6810D8206` at verification. No new migration file was created. No local persistent writes were made.

## J. Actual tests/builds

| Command / scope | Actual result |
| --- | --- |
| backend: `mvn -q '-Dtest=BleSensorIdentityTest,BleSensorRegisterServiceTest,BleSensorRegisterControllerTest,BleObservationServiceTest,BleReceivedCardsTest,BleLocationReadinessEngineTest,BleReceivedReadOnlyPostgresTest' test` | **25 passed**, zero failures/errors/skips. |
| backend: `mvn -q -DskipTests package` | Exit 0; packaged JAR built. |
| gateway: `mvn -q test` | 66 discovered: **61 passed, 5 skipped**, zero failures/errors. Five existing disposable-database tests skip without their dedicated database configuration. |
| frontend: `node --experimental-strip-types --test tests/eyebeacon-cards.test.mjs tests/ble-sensor-ui.test.mjs` | **15 passed**. MAC/form/name-state bindings, source protocols, same-record GPS, nulls/zero values, valid 2D/3D display, rejected incomplete positioning evidence, identity/page merging. |
| frontend: `npm run build` | Exit 0, TypeScript and Vite build successful. Existing large-chunk warning remains; no global build/theme settings changed. |
| frontend: `npm run typecheck` | Exit 0 on final source. |
| frontend: `node --check tests/eyebeacon-ui-smoke.mjs` | Exit 0. |
| frontend: `node tests/eyebeacon-ui-smoke.mjs` with actual read-only card response fixture | **4 passed**, desktop/phone × light/dark; exact field contract, timestamps, RSSI/battery, verified MAC, unavailable calculated coordinates, stable identity on refresh and responsive layout. |
| same smoke command with `ALELS_BLE_UI_SCOPE=assets` | **4 passed**, desktop/phone × light/dark; real components with test-only cached capability/empty-list fixtures, form MAC normalization, exact type choices, disabled Save, BLE Wasted tab, returning to original Vehicle Wasted and zero mutations. |
| source diff and whitespace | `git diff --check` passes; existing Driver/Vehicle pages/services and existing Move/Wasted backend services unchanged from HEAD. |

The read-only PostgreSQL tests exercise real scoped telemetry repository SQL and presentation using explicit opt-in credentials previously authorized by the user; PostgreSQL enforces `transaction_read_only=on`. They do not create fixtures on that database, write assets or prove BLE asset lifecycle integration. The other asset service/MVC tests use no persistence and make no database-success claim. Full unrelated backend suite, hosted CI, authenticated running-workspace browser validation and physical-device ACK/retry were not rerun. No process was restarted.

UI screenshots/DOM are ignored artifacts under `web-react/target/eyebeacon-e1/` and `web-react/target/ble-sensor-assets/`; desktop light and mobile dark images were inspected. All temporary browser profiles were cleaned up.

## K–L. Blocked functionality and gate

**PARTIAL.** Verified: scoped UI, card simplification, MAC format validation, exactly approved profile mapping, same-record GPS and timestamp display, additive API metadata, feature gate authorization and focused regressions. Pending schema approval: persistent Register list/create/edit, duplicate-active enforcement, real Move, soft delete/restore, permanent-delete retention conflicts, active registered-name matching and full mutation role/company authorization tests. No production data was fabricated to conceal these gaps.

Next required decision: approve or amend the concrete asset schema proposal before a migration is authored/applied and the persistence portion proceeds. Existing untracked files, firmware and prior telemetry work were preserved. No Location Optimization Engine changes began.
