# Delivery ledger

The starting repository contained only the three product specifications and repository instructions. No application, migrations, tests, lockfiles, or Git metadata existed. The system Git launcher is unavailable because Apple command line tools are missing; no commits are claimed.

## Plan and invariants

1. Foundation: Java 21 / Spring Boot, PostgreSQL migrations, secure sessions and tenant membership, exact-decimal domain models.
2. Intake: private object storage, admission checks, persisted batches and jobs, bounded retry, real Azure Document Intelligence adapter and signed Mailgun inbound adapter.
3. Intelligence: versioned GST/arithmetic findings, supplier resolution, duplicates and two-way PO comparison.
4. Workflow: review versions, notes/assignment, mandatory gates, approvals and atomic allocations, immutable CSV snapshots.
5. Interface: responsive dark workspace, operational lists/dashboard, document review, master data, settings and notification flows.
6. Verification: domain and real PostgreSQL integration tests, browser tests, rendered screenshots, deployment/runbooks and requirement traceability.

Core invariants: organization membership checked on every operation; composite tenant foreign keys; money represented as decimal strings across JSON; stale versions rejected; provider calls outside transactions; leased jobs fenced by token; original extraction and human versions retained; approval locks PO balances and reserves business identity; exported records remain immutable; audit events are insert-only through normal operations.

## Architecture decisions

- Modular monolith, package boundaries for domain/application/infrastructure/API/security. JDBC repositories use explicit SQL/locks instead of JPA for transparent allocation and tenant predicates. Spring transaction boundaries remain in application services.
- PostgreSQL jobs with SKIP LOCKED are sufficient for P0; SQS/outbox transport is deferred until deployment scale justifies it.
- Azure Document Intelligence prebuilt-invoice is the live extraction provider. Missing Indian tax details remain unknown and require review; generic tax is never treated as verified CGST/SGST/IGST.
- Mailgun signed inbound routes supply real attachments and metadata; credentials and DNS configuration are external prerequisites.
- Session authentication with BCrypt and CSRF for local onboarding. Optional OIDC is the enterprise deployment path. No browser-held long-lived bearer tokens.
- Full application remains local/repository based. Sites hosting cannot execute the requested Java/PostgreSQL stack.

Verification status and remaining limitations are maintained in TRACEABILITY.md and VERIFICATION.md. No unexecuted test is considered passing.
