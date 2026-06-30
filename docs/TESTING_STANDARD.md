# Testing Standard

## Principles

Tests provide evidence proportional to change risk. They must be deterministic, isolated, readable, and safe to repeat. A passing test does not waive contract, security, tenant, or telemetry review.

## Minimum expectations

- Documentation/tools: syntax or execution validation plus repository checks.
- Backend: focused unit tests and integration tests for persistence or HTTP boundaries.
- Frontend: type/build checks and focused component or flow tests when behavior changes.
- Gateway: protocol fixtures, malformed packets, acknowledgement, normalization, and publish/persist outcomes.
- Database: clean application, representative existing-data upgrade, constraint/index verification, and recovery evidence.
- Security/RBAC: allowed and denied cases across tenant/company boundaries.

## Test data

Use synthetic data. Never commit production credentials, tokens, packet captures containing sensitive identifiers, or personal information.

## Reporting

Change descriptions list exact commands, results, environment constraints, skipped checks, and residual risk. Flaky tests are defects, not acceptable evidence.

## Future Refactor Recommendation

Establish shared test-fixture conventions, coverage baselines, and CI quality gates after current module test capabilities are inventoried.
