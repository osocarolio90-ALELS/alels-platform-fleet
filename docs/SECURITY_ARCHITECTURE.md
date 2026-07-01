# ALELS Security Architecture

## Purpose and scope

Define the target security architecture for users, devices, services, data, infrastructure and operations. This is defense-in-depth guidance only and does not modify current authentication, JWT, RBAC, tenant or runtime behavior.

## Security principles

- Deny by default; grant least privilege for the shortest necessary time.
- Authenticate every human/workload boundary and authorize every protected action.
- Treat tenant/company isolation and device ownership as data-integrity invariants.
- Protect confidentiality, integrity and availability through design, not UI visibility.
- Minimize sensitive data, exposure, trust duration and blast radius.
- Make privileged and security-relevant actions auditable.
- Threat-model material architecture and contract changes.

## Trust boundaries

| Boundary | Primary threats | Required controls |
| --- | --- | --- |
| Internet user → edge/API | credential attacks, injection, abuse, DDoS | TLS, WAF/rate controls, validation, authentication, safe errors |
| Device network → gateway | spoofing, malformed frames, floods, replay | network controls, bounded parsing, device identity, rate/size limits, protocol integrity |
| Gateway → broker | workload spoofing, tampering, data leakage | private network, workload identity, encryption, ACLs, versioned events |
| Broker → ingestion | unauthorized consumption, replay, poisoning | consumer identity/ACLs, validation, deduplication, DLQ governance |
| Services → database | injection, excessive privilege, exfiltration | parameterization, scoped identities, encryption, tenant filters, audit |
| Operators → management | account takeover, unsafe changes | MFA, privileged access, approvals, bastion/private access, session audit |
| CI/CD → production | supply-chain compromise | protected pipeline, short-lived identity, signed immutable artifacts, approvals |

## Network architecture

- Separate public ingress, device ingress, application, event, data, observability and management zones.
- Expose only required ports through controlled ingress; databases and brokers stay private.
- Use L4 controls for device traffic and L7 controls for web/API traffic.
- Restrict east-west communication by workload identity and explicit policy.
- Protect management interfaces with private access, MFA and audited privileged workflows.
- Monitor anomalous traffic, scanning, brute force, egress and data transfer.

## Authentication

- Human authentication uses established secure password hashing and token/session validation; future improvements preserve compatibility through approved migration.
- Privileged users require stronger assurance such as MFA and step-up authentication.
- Workloads use unique, rotatable identities rather than shared static secrets.
- Device identity strategy must match device capability and protocol risk; provisioning, ownership transfer and revocation are controlled.
- Tokens are signed, validated for intended issuer/audience/time where the approved contract supports it, short-lived proportionate to risk, and never logged.

## Authorization and multi-tenancy

- Server-side policy is authoritative at controller/service/repository boundaries.
- RBAC is combined with tenant/company scope and resource ownership.
- Denied cross-tenant access is tested for read, write, export, command and administrative paths.
- Privileged cross-tenant actions are explicit, time-bounded where possible and audited.
- Event consumers, topics and database identities receive only required access.

## Secrets

- Store secrets in an approved manager, not Git, images, logs, frontend bundles or local shared files.
- Separate secrets by environment and workload; rotate, revoke and audit access.
- Prefer short-lived workload credentials and automated rotation.
- Maintain emergency recovery and break-glass procedures with dual control.
- Scan source, artifacts and pipeline output for accidental disclosure.

## Encryption and data protection

- Encrypt external and service traffic in transit according to risk; protect broker/database administrative paths.
- Encrypt sensitive data and backups at rest with controlled keys.
- Classify identity, tenant, device, telemetry, location, credential and audit data.
- Define minimization, retention, archive and deletion policies.
- Separate encryption-key administration from data administration and test key recovery.

## Audit and detection

- Record authentication outcomes, token/session anomalies, RBAC denials, privileged actions, configuration/secrets changes, data exports, device ownership/commands and recovery actions.
- Include safe actor, tenant, target, outcome, source and correlation context.
- Protect audit integrity, restrict access and define retention.
- Alert on brute force, suspicious privilege/tenant access, secret use anomalies, high-risk exports, parser abuse and control-plane changes.

## Secure delivery and operations

- Require review, tests, dependency/secret scanning, immutable artifacts and provenance.
- Patch by risk with inventory and rollback.
- Maintain incident response, forensics, notification and credential-rotation runbooks.
- Test backups and DR without weakening authorization or exposing restored data.

## Validation

Use threat modeling, architecture review, SAST/dependency/secret scanning, authentication/RBAC/tenant tests, parser fuzzing, API testing, configuration review, penetration testing and incident exercises. Findings require owners, severity and deadlines.

## Future recommendations

Prioritize a formal threat model and asset/data inventory, protected secret management, MFA for privileged access, security regression suites, pipeline supply-chain controls, rate limiting, centralized security events and independent penetration testing.
