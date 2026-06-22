# ALELS Phase-1 Foundation Hardening

Tujuan dokumen ini adalah memastikan platform tidak lanjut membuat menu besar sebelum fondasi pipeline data aman untuk skala besar.

## Perubahan utama

### 1. Migration 003 dibuat lebih aman
`003_core_runtime_schema.sql` diperbaiki agar helper partition tidak mencoba membuat partition pada tabel yang belum menjadi partitioned parent. Ini mencegah error seperti:

- `telemetry is not partitioned`
- `relation public.tcp_logs does not exist`

### 2. Migration baru: `004_phase1_foundation_hardening.sql`
Migration ini menambahkan:

- `alels_runtime_settings`
- `partition_maintenance_runs`
- `data_retention_policies`
- `data_retention_runs`
- `device_presence_cache_shadow`
- `device_latest_position`
- helper function partition aman
- retention policy foundation
- command queue retry/lease hardening
- Kafka topic contract target untuk 1 juta device
- operational views:
  - `v_alels_partition_status`
  - `v_alels_pipeline_health`
  - `v_alels_presence_summary`

### 3. Rolling partition 12 bulan
Jalankan manual kapan pun diperlukan:

```sql
SELECT alels_run_partition_maintenance(12);
```

Cek hasil:

```sql
SELECT * FROM v_alels_partition_status ORDER BY parent_table, partition_month;
```

### 4. Retention policy
Retention policy dibuat tetapi default `enabled=false` agar tidak ada data hilang tanpa approval.

Cek candidate partition yang akan dihapus:

```sql
SELECT * FROM alels_apply_retention('telemetry', true);
SELECT * FROM alels_apply_retention('raw_packets', true);
SELECT * FROM alels_apply_retention('tcp_logs', true);
```

Aktifkan hanya setelah cold archive sudah siap:

```sql
UPDATE data_retention_policies
SET enabled = true
WHERE table_name IN ('raw_packets', 'tcp_logs');
```

### 5. Redis foundation
`deploy/docker-compose.yml` sekarang memiliki service Redis.

Redis digunakan untuk target runtime berikut:

- `device:presence:{imei}`
- `device:latest:{imei}`
- TTL default: 420 detik atau 7 menit

Database tetap menyimpan shadow table agar sistem tetap bisa diaudit jika Redis kosong/restart.

### 6. Presence 7 menit
Tabel `device_presence_cache_shadow` dan function `alels_mark_stale_devices_offline()` sudah disiapkan.

Jalankan berkala setiap 1 menit di scheduler/server job:

```sql
SELECT alels_mark_stale_devices_offline();
```

### 7. Command queue hardening
`command_queue` sekarang memiliki field tambahan:

- `max_attempts`
- `attempt_count`
- `lease_owner`
- `lease_until`
- `next_attempt_at`
- `priority`
- `expires_at`

Function requeue lease:

```sql
SELECT alels_requeue_expired_command_leases();
```

### 8. Ingestion update
`TelemetryRepository.insertBatch()` sekarang juga mengisi:

- `device_presence_cache_shadow`
- `device_latest_position`

Selain batch insert ke:

- `raw_packets`
- `telemetry`
- update `devices`

## Cara menjalankan setelah replace project

Dari PowerShell:

```powershell
cd D:\alels-platform-work\database
& "C:\Program Files\PostgreSQL\18\bin\psql.exe" -U alels -d alels_db -f .\migrations\004_phase1_foundation_hardening.sql
```

Cek hasil:

```powershell
cd D:\alels-platform-work\tools\database
.\run-phase1-check.ps1 -Database alels_db -User alels
```

Atau dari psql:

```sql
\dt
SELECT * FROM v_alels_partition_status ORDER BY parent_table, partition_month;
SELECT * FROM data_retention_policies ORDER BY table_name;
SELECT * FROM v_alels_pipeline_health;
```

## Status lanjut menu

Setelah migration 004 sukses tanpa error dan hasil check OK, menu berikut boleh dilanjutkan:

- Company
- User
- Vehicle
- Device Registration
- Device Model
- Dictionary
- Command Center
- Alert Rule
- Reporting UI dasar

Menu realtime besar sebaiknya menunggu Redis cache benar-benar dipakai oleh backend/frontend live socket:

- Live Tracking
- Live Dashboard
- Realtime Fleet
- Notification Engine
- Geofence Engine

