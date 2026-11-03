# ALELS Wi-Fi pending regression investigation — 2026-10-29

Audited the working tree against HEAD `bcf44af`, focusing on ALELS JSON reception, publisher completion, database writes and the firmware ACK wait. Existing unrelated uncommitted work is preserved. No service restart, persistent database access, migration execution or firmware modification was performed.

## Findings and corrections

The uncommitted BLE persistence path made a successful telemetry write depend unconditionally on `alels_store_ble_observations`. On a database without migration 026, the real direct publisher fails with PostgreSQL SQLState 42883 and rolls back the telemetry transaction. The existing receiver correctly withholds `01\n` when publishing fails; the firmware waits three seconds and retains the pending delivery. This failure was reproduced before the correction using the actual six-observation Q=68 sample and production publisher/repository code. The failing report is retained in ignored `gateway/target/wifi-regression-before-fix.txt`. This proves a code regression, but does not establish the schema or running binary on the user's live deployment. Existing local restart logs predate this incident.

BLE history persistence now requires explicit `ALELS_BLE_HISTORY_ENABLED=true` (property `ble.history.enabled=true`) in both gateway and ingestion. The default is false until migration 026 is verified. Ordinary telemetry, original IO and raw payload remain stored; normalized BLE parsing remains active. BLE history and its cards will not receive new history rows while this flag is false. This is an explicit rollout setting, not an exception handler that acknowledges failed required writes. When enabled, missing schema still fails and rolls back the transaction.

The connection-aware telemetry refactor also held a write connection while database-backed status, company and driver resolvers borrowed additional connections. A saturated or one-connection pool could wait for its five-second acquisition timeout, exceeding the firmware ACK wait. Enrichment now runs before borrowing the write connection, as in the previous committed flow; the shared insertion SQL and durable status lookup remain in place. A real one-connection PostgreSQL test verifies successful non-BLE delivery with a four-second assertion after warmup; this is not a physical-device latency guarantee.

TCP framing, ACK content and success condition, firmware retry behavior, Kafka publishing and offset identity rules are unchanged. No backend/UI changes were made for this repair.

## Files changed for this repair

| Path | Purpose |
| --- | --- |
| `.env.example` | Document the safe default and schema prerequisite. |
| `gateway/src/main/resources/gateway.properties` | Default BLE history persistence to disabled. |
| `gateway/src/main/java/com/alels/gateway/publisher/TelemetryPublisherConfig.java` | Read the explicit history setting. |
| `gateway/src/main/java/com/alels/gateway/publisher/DirectDatabaseTelemetryPublisher.java` | Gate history writes, prepare enrichment before the transaction, log publish failures and configuration. |
| `gateway/src/main/java/com/alels/gateway/repository/TelemetryRepository.java` | Resolve pooled enrichment before acquiring the insert connection, sharing the existing insert implementation. |
| `ingestion-service/src/main/resources/application.properties` | Match the gateway rollout default. |
| `ingestion-service/src/main/java/com/alels/ingestion/config/IngestionConfig.java` | Read the same history setting. |
| `ingestion-service/src/main/java/com/alels/ingestion/repository/TelemetryRepository.java` | Gate history writes without changing ordinary batch/offset transactions. |
| `gateway/src/test/java/com/alels/gateway/publisher/DirectDatabaseWifiRegressionPostgresTest.java` | Five real publisher/database regression cases. |
| `ingestion-service/src/test/java/com/alels/ingestion/repository/WifiHistoryRolloutPostgresTest.java` | Three real ingestion transaction/replay cases. |
| `database/tests/fixtures/wifi-telemetry-regression.sql` | Shared disposable core-schema fixture. |
| `database/tests/fixtures/wifi-ble-history-regression.sql` | Disposable BLE table fixture. |
| `docs/EYEBEACON_PHASE_E1.md` | Clarify rollout and remaining deployment verification. |
| `docs/ALELS_WIFI_PENDING_REGRESSION.md` | Investigation and validation record. |

## Actual verification

Both modules ran `mvn -q package`, sequentially, with exit 0: gateway **66 tests, 0 failures, 0 errors, 0 skipped**; ingestion-service **18 tests, 0 failures, 0 errors, 0 skipped**. This includes five new direct-publisher and three new ingestion cases on PostgreSQL 18, not mocks. Java compilation and packaged JAR generation succeeded. There are no TypeScript changes in this repair; frontend/backend suites were not rerun.

The disposable database used `jdbc:postgresql://localhost:55443/postgres`, explicit matching `ALELS_WIFI_REGRESSION_TEST_DB_URL`, and a one-connection pool in each module. Tests are guarded against other URLs. Core tables were synthetic fixtures; enabled-history tests execute the unchanged function definition extracted from 026 against matching disposable BLE tables. No migration script was deployed. The test cluster was stopped after the build.

Direct cases cover Q=68 with six normalized observations and preserved source IO/raw, enabled-history insertion of all six observations, rollback on absent function, successful non-BLE publishing with a single connection, and failed parent insertion never returning publish success. Ingestion cases cover disabled-history persistence and same-offset replay, enabled-history missing-function rollback including offsets followed by successful retry, and enabled-history observation insertion without duplicate rows on exact-offset replay. They do not exercise a live Kafka broker.

Built artifacts: `gateway/target/alels-gateway-1.0.0.jar` and `ingestion-service/target/alels-ingestion-service-1.0.0.jar`. Deploy/restart using the existing secure service configuration; if Kafka ingestion is used, update both services. Keep history disabled until 026 has been verified on the target database, then explicitly opt in. No running service or physical ALELS Wi-Fi device has been verified in this session; final live confirmation must include receipt, successful persistence and returned `01` ACK.
