# Architecture Decision Record Standard

## Purpose

Define how ALELS records significant architecture decisions so future engineers understand context, trade-offs, consequences, and replacement criteria.

## Scope

Applies to decisions affecting system boundaries, data ownership, protocols, APIs, security, tenancy, telemetry, persistence, deployment, resilience, or long-term operational cost. This file defines the ADR format; it does not change architecture.

## Rules

- Create one immutable numbered ADR per significant decision in a future approved ADR location.
- Use status: Proposed, Accepted, Superseded, or Deprecated.
- Include title, date, owners, context, constraints, decision drivers, considered options, decision, consequences, risks, security/data/operational impact, validation, and references.
- Record dissent and rejected alternatives fairly.
- An accepted ADR is amended by a new ADR that supersedes it; preserve historical text.
- Link implementation work and affected standards without embedding secrets.

## Do

- Write the ADR before irreversible implementation where practical.
- Use measurable drivers and explicit assumptions.
- Define conditions that would trigger reconsideration.
- Review with owners of affected contracts and operations.

## Do Not

- Do not use an ADR to authorize work outside an approved scope.
- Do not rewrite history after outcomes are known.
- Do not record routine local coding choices or confidential credentials.
- Do not treat an ADR as a substitute for implementation/testing plans.

## Validation

Confirm unique identifier, complete context/options, decision authority, affected-owner review, consequences, compatibility, security, operations, validation plan, and supersession links.

## Future Recommendations

Create `docs/adr/` with an index and template after ADR numbering, ownership, and approval workflow are formally adopted.
