# BLE Sensor asset schema proposal — approval required

Date: 2026-10-30 (Asia/Jakarta). Status: **PROPOSAL ONLY**. No migration file has been created or executed. Migration 026 must remain unchanged.

## Why a new schema is necessary

Read-only inspection of the actual local `alels_db` found no BLE/sensor asset table. The only `asset_*` table was `asset_drivers`; `ble_beacons` and `ble_observations` were also absent. Existing Driver, Vehicle and Device tables represent different assets and must not be repurposed. Their `metadata` fields must not become an alternative BLE registry.

The existing 026 design describes automatically observed BLE identities/history. It does not provide the requested manually maintained name, asset type, company movement, creation actor and Wasted lifecycle. Do not turn incoming telemetry into asset registrations or edit 026 to add this feature.

## Minimum proposed table

New logical table: `asset_ble_sensors` (final migration number to be selected after approval and review of the then-current migration sequence).

| Column | Proposed contract |
| --- | --- |
| id | bigint generated primary key |
| company_id | required company FK, retention-safe delete behavior |
| sensor_name | required trimmed name, maximum 180 characters |
| mac_address | PostgreSQL `macaddr` (48-bit), canonical uppercase colon format at API boundary |
| ble_type | required constrained `EyeSensor` or `EyeBeacon`; independent of advertisement protocol |
| created_at / created_by | server timestamp and authenticated creator FK |
| updated_at / updated_by | authenticated update and move audit |
| deleted_at / deleted_by / deleted_reason | soft-delete and Wasted provenance |

Required indexes: unique `(company_id, mac_address)` **where `deleted_at IS NULL`**, active company/list ordering and company/deleted-time Wasted access. A database unique index is required for concurrent duplicate prevention, not a client-side list check. Deleted rows retain their original IDs and MACs. Do not add automatic purge until retention has been explicitly reviewed.

No observation/history table is proposed here. Existing telemetry and future deployment of 026 remain separate concerns.

## Required transactional operations after approval

1. Create/edit: validate normalized 48-bit MAC, name and BLE type; authorize target company with the existing asset scope; map unique-index violations to controlled HTTP 409. No automatic registration from telemetry.
2. Ordinary Delete: update deletion provenance only; preserve identity and all BLE/telemetry history. List only active rows on Register.
3. Restore: retain the asset ID; authorize its company, lock/recheck its deleted state and check active-MAC uniqueness. If a replacement asset now has the same company/MAC, return 409 rather than merging or overwriting it.
4. Move: follow existing Asset Move source/target authorization, permitted move roles and audit logging. Update company ownership; never reinterpret Move as Delete. Lock affected rows, check target duplicates and dependencies. If movement would break tenant-scoped historical associations, return 409 pending a reviewed historical ownership solution. Do not rewrite historical observations to the destination company.
5. Permanent Delete: only Superadmin and Owner, within existing company/asset scope, after confirmation in the UI. Lock and require an already deleted row. Do not cascade-delete observations or telemetry. Resolve any history/retention references before deletion and use restrictive FKs; map FK conflicts to controlled 409. If history requires keeping the asset identity, retain the soft-deleted row as the minimum safe solution.
6. Name lookup: use the selected authorized device's company plus verified normalized MAC, excluding deleted registrations. Resolve names on each card refresh so edits and soft deletes are reflected without altering raw observations. No global MAC-only name cache.

## Role contract

| Role | Restore, within scope | Permanent Delete, within scope |
| --- | --- | --- |
| Superadmin | Yes | Yes |
| Owner | Yes | Yes |
| Admin | Yes | No |
| Manager | Yes | No |
| Techuser | Yes | No |
| Clientuser | Yes | No |

The role matrix grants no additional company visibility. Existing asset company hierarchy/access checks must govern both source and target. Existing Move permissions remain Superadmin/Admin/Owner/Manager; the restore matrix does not grant Move permission.

## Approval boundary and later verification

Approval is needed to create/review/apply the new migration and complete persistence-backed Register/Wasted endpoints. They are intentionally not implemented against a guessed table. Current frontend fields and column structure are available, but Save/Move/Edit/Delete/Restore/Permanent Delete cannot run. Current authenticated availability endpoint reports this gate without pretending a mutation succeeded.

After approval, use disposable PostgreSQL integration tests for unique-MAC races, company isolation, move semantics, soft delete/restore races, replacement-MAC restore conflict, all role combinations and retained-history FK conflicts. Test actual authorized name lookup/update/deletion against stored assets. Mocks and the current read-only telemetry tests do not establish that future asset lifecycle.
