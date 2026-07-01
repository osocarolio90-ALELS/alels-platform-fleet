# Versioning Standard

## Purpose

Provide traceable, compatible version identifiers for ALELS source, artifacts, APIs, schemas, events, protocols, configuration contracts, and releases.

## Scope

Applies to repository releases and separately versioned external or operational contracts. It does not retroactively rename current packages or endpoints.

## Rules

- Use Semantic Versioning (`MAJOR.MINOR.PATCH`) for release intent once formal release versioning is adopted.
- `MAJOR` indicates an approved breaking contract change; `MINOR` adds backward-compatible capability; `PATCH` fixes behavior compatibly.
- A version identifies an immutable source revision and artifact set.
- Tags and release records include date, revision, artifacts, migrations, compatibility, validation, and rollback.
- API/event/protocol/schema versions are independent contracts and change only through their compatibility standards.
- Pre-release identifiers must not be presented as production-stable.
- Never reuse or mutate a published version.

## Do

- Document affected consumers and supported compatibility window.
- Use migration/deprecation periods for breaking changes.
- Tie deployed versions to observable health and incident records.
- Keep version comparison machine-readable.

## Do Not

- Do not infer compatibility from a filename or build timestamp alone.
- Do not make breaking changes under a patch or minor release.
- Do not version every implementation detail independently without ownership.
- Do not alter existing version fields merely for documentation compliance.

## Validation

Verify unique version/tag, source revision, artifact checksums, release notes, migration list, compatibility classification, dependency matrix, deployment evidence, and rollback target.

## Future Recommendations

Adopt signed tags, automated changelogs, artifact provenance, support-window policy, and coordinated contract-version inventory.
