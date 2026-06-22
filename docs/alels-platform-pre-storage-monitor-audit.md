# ALELS Platform Audit Before Storage Monitor

Scope: repository structure, backend/frontend/gateway/database readiness before continuing `feature/storage-monitor`.

## Keep

- `gateway/` must stay. It already builds and has been proven to receive TCP test data.
- `ingestion-service/` should stay. It is part of the future scalable path: Gateway -> Kafka -> Ingestion -> PostgreSQL.
- `web-react/src/features/server-monitor/components/` is now the reusable framework for all Server Monitor pages.
- `database/migrations/001..004` are the current clean staged database foundation.

## Cleanup Applied / Recommended

- Add `.gitattributes` to prevent noisy LF/CRLF changes between Windows and Linux/CI.
- Fix `monitor-kpi-grid.tsx` so React `key` is not spread into JSX props.
- Remove root patch reports and local helper script from the repository root. They are development artifacts, not runtime source.
- Remove local build artifacts before commit/push: `target/`, `dist/`, logs, `tsconfig.tsbuildinfo`, and `node_modules/`.

## Do Not Delete Yet

- Legacy backend controllers not currently shown in the frontend should not be deleted yet. Some are future modules or still useful for integration testing.
- Gateway direct database and Kafka publisher classes should not be deleted yet. They represent current and future ingestion modes.

## Production-scale Notes

- Backend security is still permissive and password comparison is still plain text. This must be hardened before public deployment.
- Gateway logging is still verbose. Do not remove gateway now, but later replace packet-level `System.out.println` with structured/rate-limited logging.
- `application.properties` and `gateway.properties` still contain local defaults. These should become environment-variable driven before VPS/production.

## Next Feature

Proceed with `Server Monitor > Storage Monitor` after cleanup and build validation.
