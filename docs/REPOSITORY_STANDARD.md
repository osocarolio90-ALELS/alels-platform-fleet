# Repository Standard

## Canonical top-level layout

The current locations are authoritative and must not be renamed or moved casually.

| Path | Responsibility |
| --- | --- |
| `backend/` | Backend application |
| `gateway/` | Device gateway |
| `ingestion-service/` | Telemetry ingestion |
| `web-react/` | Frontend application |
| `database/` | Schema, migrations, seeds, and recovery assets |
| `deploy/` | Deployment assets |
| `docs/` | Engineering and system documentation |
| `tools/` | Maintenance, replay, dictionary, and database tools |

## Repository rules

- Source, tests, configuration templates, lockfiles, and documentation belong in version control.
- Build output, dependency caches, IDE state, logs, secrets, local environment files, and runtime data do not.
- Do not rename existing packages, classes, folders, or files without an approved compatibility plan.
- Use repository-relative paths in documentation and scripts.
- Tooling must state whether it reads or writes. Validation tools are read-only.
- New files must have a clear owner and fit an existing responsibility; new top-level folders require architecture approval.
- `.env.example` contains placeholders only. Real credentials and local `.env` files are never committed.

## Generated artifacts

The following are generated/local and must remain ignored and untracked: `target/`, `dist/`, `node_modules/`, `.vite/`, `.cache/`, coverage output, compiled Java artifacts, logs, and runtime uploads. Their local presence is a warning at most; tracking them is a failure.

## Change hygiene

Every change must avoid unrelated formatting, list files intentionally changed, pass `git diff --check`, and include validation and impact notes.

## Future Refactor Recommendation

Consider a dedicated, approved repository cleanup for currently tracked runtime upload artifacts and any local helper artifacts after ownership and retention requirements are confirmed.
