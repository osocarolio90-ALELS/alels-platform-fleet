# Database Structure Standard

## Ownership

- `database/schema.sql` documents or establishes the baseline according to the existing project workflow.
- `database/migrations/` contains ordered forward migrations.
- `database/seed.sql` contains seed behavior.
- `database/recovery/` contains explicitly operated recovery procedures.

These roles must not be mixed without an approved database plan.

## Rules

- Production schema change occurs only through a reviewed migration.
- Existing schema, migration, seed, and recovery files are behavior-bearing assets.
- Use explicit names, data types, constraints, and indexes consistent with current conventions.
- Preserve tenant/company isolation and referential integrity.
- Destructive or irreversible operations require backup, impact, rollout, and recovery evidence.
- Application and gateway queries must remain compatible throughout rollout.
- Credentials and production data never enter the repository.

## Review evidence

Database changes require affected objects, expected volume/locking, compatibility window, verification query, rollback or forward-recovery plan, and owner.

## Future Refactor Recommendation

Schema ownership boundaries and drift detection may be strengthened in a separately approved database-governance phase.
