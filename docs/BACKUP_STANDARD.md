# Backup Standard

## Purpose

Ensure ALELS can recover required data, configuration, and operational state after deletion, corruption, failed deployment, or infrastructure loss.

## Scope

Applies to databases, approved runtime data, configuration records, secrets through their secure system, deployment metadata, artifact provenance, and recovery documentation. Source control is not a database backup.

## Rules

- Define owner, data set, frequency, retention, location, encryption, access, recovery point objective (RPO), and recovery time objective (RTO).
- Use application-consistent or database-supported backup methods.
- Encrypt backups in transit and at rest; separate backup credentials and storage from production.
- Keep at least one protected copy outside the primary failure domain.
- Monitor job completion, size, age, integrity, and retention.
- Treat backups as sensitive production data and audit access.
- A backup is not trusted until a restore test proves it usable.

## Do

- Document included and excluded assets.
- Capture required version and dependency metadata.
- Rotate and revoke backup credentials safely.
- Test representative full and point-in-time recovery on a schedule.

## Do Not

- Do not place production backups in Git, source workspaces, or public storage.
- Do not rely only on snapshots in the same failure domain.
- Do not report success based solely on file creation.
- Do not overwrite the last known-good recovery point during an incident.

## Validation

Verify schedule completion, checksum/integrity, encryption, retention, access controls, off-domain copy, restore test result, recovered data checks, and measured RPO/RTO.

## Future Recommendations

Establish approved RPO/RTO tiers, immutable backup storage, automated restore drills, backup inventory dashboards, and expiry alerts.
