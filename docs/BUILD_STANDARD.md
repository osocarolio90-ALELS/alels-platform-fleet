# Build Standard

## Purpose

Ensure ALELS artifacts are repeatable, traceable, reviewable, and produced only through repository-declared build mechanisms.

## Scope

Applies to the Maven builds in `backend/`, `gateway/`, and `ingestion-service/`, the npm/Vite build in `web-react/`, and future approved packaging steps.

## Rules

- Build from a known revision with declared toolchain versions and dependency manifests.
- Use commands already documented or declared by the repository; do not invent release commands.
- Preserve lockfiles and manifests with intentional dependency changes.
- Keep generated outputs such as `target/` and `dist/` untracked.
- A release artifact must be immutable and identifiable by source revision and version.
- Separate compilation, tests, packaging, and publishing in evidence even when one command performs several steps.
- Treat warnings as reviewable signals; document accepted warnings.

## Do

- Start from a clean, reproducible dependency state where practical.
- Record command, tool version, revision, result, warnings, and artifact checksum for releases.
- Run relevant tests and repository validation before promotion.
- Protect artifact repositories and retention rules.

## Do Not

- Do not hand-edit compiled or bundled output.
- Do not publish artifacts built from unknown or dirty source.
- Do not skip tests silently.
- Do not embed production secrets or environment-specific runtime values in artifacts.

## Validation

Run the existing Maven and npm build commands, confirm successful exit codes, inspect warnings, confirm generated output is ignored, and verify release artifact identity/checksum where applicable.

## Future Recommendations

Add build wrappers or pinned CI images, dependency caching with integrity checks, software bills of materials, signing, and reproducibility comparison.
