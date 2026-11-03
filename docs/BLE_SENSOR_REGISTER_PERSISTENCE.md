# BLE Sensor Register: automatic MAC input and persistence

Date: 2026-10-30, Asia/Jakarta.

The user explicitly requested a dedicated migration and working Save. The earlier schema gate is replaced by real database availability and persistent operations.

## Changes

- `database/migrations/027_ble_sensor_register.sql` creates `asset_ble_sensors`, with company ownership, name, native 48-bit PostgreSQL `macaddr`, BLE type, creator/update timestamps and actors, soft-delete fields and an active-company/MAC unique index. Migration 026 is untouched. This table stores manually registered BLE assets; existing incoming telemetry and observed beacon history stay in their existing storage.
- `backend/src/main/java/com/alels/backend/assetregister/repository/BleSensorRegisterRepository.java` implements scoped lists, row locking, create/update, soft delete/restore, ownership moves, permanent deletion, active name lookup and existing organization audit logging. No data or names are fabricated.
- Existing `BleSensorRegisterService.java` and `BleSensorRegisterController.java` now expose real Save/list/edit/delete/restore/move/permanent-delete operations. Existing Driver asset company scope and Asset Move source/target authorization/audit are reused without changing their implementations. Writes are transactional. Duplicate active MAC and FK conflicts return controlled 409. Moves and permanent deletes are blocked where matching retained BLE observations are found. No cascade-delete of telemetry/history is introduced.
- Existing `BleObservationService.java` resolves active names by the selected authorized device's company and verified MAC, batched once per page. Rename is reflected on refresh; soft-deleted registrations stop supplying active names. The earlier user-approved Instance-to-MAC mapping remains restricted to `7CD9F408DA22`; this change does not extend that verification to other instances.
- Existing frontend `utils/ble-sensor.ts`, `api/ble-sensor-register-api.ts`, `pages/ble-sensor-register-page.tsx` and `components/ble-sensor-wasted-panel.tsx` implement automatic partial MAC formatting while typing, real API calls, Save/loading/error states, refreshed lists, Edit/Move/soft delete, Restore and confirmation for permanent delete. Entering `7cd9f408da2c` produces `7C:D9:F4:08:DA:2C`; the user need not type colons. No theme, parser, ACK or ingestion changes.

## Verification and deployment

The actual migration was tested in disposable PostgreSQL on port 55444 before applying it to local `alels_db`. Twelve PostgreSQL integration cases exercise real Spring transactions and production SQL: Save/creator fields, rename/name lookup, company-scoped uniqueness, rollback, soft delete/restore, restore conflict, six-role restrictions, Move semantics, tenant isolation, FK/history protection, migration rerun and real MVC Save/list/conflict responses. A card integration case reads actual test telemetry through the shared parser and verifies registered name changes and exclusion after deletion. These fixtures never write to the application database.

- Focused backend run: **35 passed, 0 failures/errors/skips**, including the 12 new PostgreSQL cases and existing BLE/service/controller tests. Initial test compilation exposed a loop-variable lambda capture; it was corrected before the successful final run.
- `mvn -q -DskipTests package`: exit 0; backend JAR built.
- Frontend unit tests: **16 passed**, including partial automatic MAC formatting, existing card merging and field bindings.
- `npm run build`: exit 0, including TypeScript compilation; existing large-chunk warning remains.
- Headless Chrome `ALELS_BLE_UI_SCOPE=assets-ready`: **four passed**, desktop/mobile in light/dark. Real components use an isolated test-only API adapter to verify enabled Save, normalized POST data, list refresh, soft delete and restore. This is component evidence; PostgreSQL/MVC integration is verified separately, not inferred from that adapter.
- `git diff --check` passed. No full unrelated backend or gateway suite rerun is claimed for this task.

Only migration 027 was applied to the local application database, after successful disposable tests. The table and its primary key, active unique index and two list indexes were verified. Registered row count immediately after application: **0**. No sample registrations were inserted. The general migration runner was not used, so migration 026 and all other migrations were not executed. The disposable test cluster was stopped and removed.

The running backend started before this build and has **not been restarted**. Reload it from its existing terminal/configuration, then refresh the BLE Register page. Do not change gateway configuration. Browser verification via Computer Use was stopped by the tool because it could not confidently determine the current Windows browser URL; no further browser interaction was attempted. An authenticated live Save is therefore not claimed.

Additional tests changed: `BleSensorRegisterServiceTest.java`, `BleSensorRegisterControllerTest.java`, new `BleSensorRegisterPostgresTest.java`, frontend `ble-sensor-ui.test.mjs` and shared `eyebeacon-ui-smoke.mjs`. Existing firmware and unrelated untracked work were preserved. No staging or commit.

Historical documents `BLE_SENSOR_ASSET_SCHEMA_PROPOSAL.md` and `BLE_SENSOR_SCOPED_UI_IMPLEMENTATION.md` describe the prior PARTIAL gate. This record supersedes their pending-migration/persistence status: migration 027 is now explicitly requested, authored, tested and applied locally; runtime backend reload remains outstanding.
