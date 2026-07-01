# Dependency Management Standard

## Purpose

Define how ALELS selects, pins, reviews, updates, inventories and retires third-party dependencies while controlling security, licensing, compatibility and supply-chain risk.

## Scope

Applies to Maven, npm, container images, CI actions, operating-system packages, runtime services, transitive dependencies and future tooling.

## Rules

- Every dependency has a justified purpose, owner and supported source.
- Use manifests and lockfiles or equivalent integrity controls; never edit dependency output manually.
- Pin production and pipeline inputs to an approved level of immutability.
- Review license, maintenance health, vulnerability exposure, transitive graph, artifact provenance and operational cost.
- Prefer fewer, actively maintained dependencies with narrow capability.
- Updates are isolated, reviewed, tested and rollback-ready; do not bundle unrelated feature work.
- Critical vulnerability response follows severity, exploitability and exposure, not calendar cadence alone.
- Remove unused dependencies only through approved, tested work.
- Never download runtime code from untrusted sources or execute install scripts with excessive privilege.

## Certification Criteria

- Maven manifests, npm lockfile, container images, CI actions and OS dependencies are inventoried.
- Reproducible dependency restoration succeeds.
- Vulnerability and license scans pass approved policy with no unowned exception.
- Direct and critical transitive dependencies have owners and update status.
- Artifacts verify checksum/signature/provenance where available.
- SBOM is generated for release artifacts and retained.
- End-of-life or unmaintained dependencies have remediation plans.
- Dependency changes pass builds, tests, security, compatibility and performance checks.

Manifests and a frontend lockfile exist, but automated inventory/scanning/SBOM evidence is absent; dependency certification is **PARTIAL**.

## Validation

Resolve from clean caches where practical, compare lock/manifests, inspect dependency trees, run vulnerability/license/provenance scans, verify artifacts, execute applicable tests and retain the SBOM and approvals.

## Future Implementation

Introduce automated update proposals, policy-controlled vulnerability/license scanning, SBOM generation, signed artifact verification, approved registries, dependency ownership and end-of-life dashboards.
