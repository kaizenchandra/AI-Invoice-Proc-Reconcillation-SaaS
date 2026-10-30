# Codex Repository Instructions

## Mission and working scope

Build a complete AI-powered Invoice Processing & Reconciliation SaaS platform for Indian businesses. Deliver a commercially useful, secure, auditable, multi-tenant product with an exceptional dark-theme interface and a fully integrated invoice-to-export workflow.

Apply cross-functional expertise in architecture, fintech product management, business analysis, Java/Spring, Next.js/TypeScript, UI/UX, document AI, PostgreSQL, cloud/DevSecOps, QA, security, and reliability.

These instructions govern implementation throughout the repository. Follow the user's current task scope: a request for documentation, investigation, or a targeted fix does not authorize implementing the entire platform. When asked to build the platform, continue through implementation, integration, testing, and verification; planning and scaffolding alone are not completion.

## Source of truth

- Read [prompt.md](./prompt.md) for the complete product requirements, FR-01 through FR-20, P0/P1/P2 priorities, acceptance scenarios A–O, and required deliverables. The summaries below do not remove any requirement from that document.
- Before UI work, read the applicable screens and shared design rules in [ui-ux-desing-specification.md](./ui-ux-desing-specification.md). Preserve its existing filename when linking to it.
- Use this file for execution rules and engineering guardrails. The product prompt controls feature priority; the UI/UX specification refines presentation and interaction without promoting P1/P2 features into P0.
- Respect higher-priority instructions, the user's current direction, and applicable directory-specific agent instructions. Raise material contradictions instead of silently weakening security, financial integrity, or acceptance criteria.
- Inspect current code and documentation before relying on architectural assumptions. Do not overwrite unrelated user changes.

## Start every implementation task with repository inspection

1. Inspect instructions, specifications, code, manifests/lockfiles, migrations, tests, CI/tasks, and available version-control status. Preserve conventions and unrelated changes.
2. Identify implemented, partial, missing, and externally blocked behavior. Reuse working components.
3. If no application exists, initialize the recommended stack. Verify compatible versions against official documentation, pin dependencies, and document choices. Do not invent existing build commands.
4. Establish each slice's invariants, contracts, migrations, permissions, and acceptance tests before its UI.
5. Maintain a concise implementation plan and traceability aligned with code and test evidence.

## Non-negotiable delivery rules

- Implement complete vertical slices: database, domain behavior, authorization, API, frontend, error handling, audit events, and verification.
- Every visible business action must execute an implemented backend use case and provide clear feedback. Do not ship inert buttons, disconnected screens, or success messages for failed requests.
- Use persisted backend data for operational screens, metrics, searches, and charts. Synthetic fixtures belong in explicitly identified development/test environments.
- Never fabricate AI output, confidence, source evidence, operational metrics, financial figures, or verification results. Test doubles must not masquerade as live integrations.
- Do not leave core P0 behavior behind TODOs, placeholders, or hardcoded success paths.
- Prioritize correctness, usability, auditability, and data integrity. Financial decisions must remain deterministic and authorized.
- Run relevant checks and repair failures caused by the change. State precisely what was tested, what failed, what was not verified, and what needs external credentials or infrastructure.
- If an external dependency is unavailable, implement and test the adapter contract, explicit configuration/error states, and deterministic fixtures; continue independent work. Keep live verification visibly pending.
- Prefer a pragmatic modular monolith. Add infrastructure and abstractions only when a concrete requirement justifies them.

## Product priorities

Convert invoices into validated, reconciled, approval-ready records for Indian SMEs, distributors, manufacturers, AP/shared-service teams, and accounting firms, including multiple GST registrations and client organizations.

The core flow is:

`Intake → Classification → OCR/AI extraction → Normalization → Supplier resolution → GST validation → Duplicate detection → PO matching → Exceptions → Human review → Approval → Accounting export → Audit and analytics`

Optimize for exception-first processing, inspectable AI evidence, Indian finance rules, simple operations, and trustworthy automation.

### P0: mandatory integrated MVP

| Requirement | Required working behavior |
| --- | --- |
| FR-01: Organizations and tenancy | Authentication, onboarding, organization/GST/financial-year profiles, business units, invitations, role/permission matrix, authorized accounting-firm multi-company access, and secure organization switching. |
| FR-02: Intake | PDF/image upload, real email attachment ingestion, multi-file batches, per-file validation/progress, partial success, independent retries, durable storage, and asynchronous processing. ZIP intake is optional and requires archive safety controls if implemented. |
| FR-03: Extraction | A real configured provider processes scanned/searchable multi-page invoices into persisted supplier, buyer, invoice, financial, and line-item fields; preserve runs, errors, raw results, normalization, and provider/model versions. |
| FR-04: Evidence | Show available field evidence, genuine confidence, missing values, and AI/manual provenance separately from validation results. Preserve original and corrected values. |
| FR-05: GST and arithmetic | Deterministic, versioned, explainable GSTIN, tax-context, line/header arithmetic, rounding, and applicable document-type checks; block unresolved mandatory failures. |
| FR-06: Suppliers | Create/edit/search/filter, CSV import, identifier validation, invoice/PO links, duplicate candidates, master-data resolution, and review of ambiguous identities. |
| FR-07: Duplicates | Exact and suspected duplicates with supporting evidence, comparison, authorized false-positive decisions with reasons, and prevention of accidental duplicate export. |
| FR-08: Purchase orders | Create/import POs and lines, supplier references, amendment history as needed, open/closed views, allocations, remaining quantities/value, and linked invoices. |
| FR-09: Reconciliation | Two-way matching with exact decimal differences, versioned tolerances, partial invoicing, previous allocations, line-level findings, authorized resolutions, and concurrency-safe balances. |
| FR-10: Exceptions | Central tenant-safe queue, categories/severity, assignment, evidence, next actions, lifecycle, and resolution history. |
| FR-11: Human review | Document viewer, editable fields/lines, validation/evidence panels, notes, save/revalidate/reconcile/submit actions, unsaved-edit protection, and conflict detection. |
| FR-12: Approvals | Authorized submission, approver assignment, approve/reject with reasons/history, mandatory validation gates, configurable separation of duties, and invalidation after material edits. |
| FR-13: Lifecycle | Separate processing and business states, explicit transition guards, resource versions, retry/idempotency behavior, failure explanations, and transition audits. |
| FR-14: Export | Approved-version CSV export, single/bulk selection, field mapping profile, preview, download/history, recorded failures, safe retries, and explicit audited re-export. |
| FR-15: Dashboard | Persisted operational metrics, queue health, action center, reconciliation summary, activity, filterable drill-downs, and documented metric definitions. |
| FR-16: Search | Tenant-scoped server-side search, filtering, pagination, and sorting by invoice/supplier/GSTIN/PO/date/status/amount/source. |
| FR-17: Notifications | Tenant-safe in-app assignments, approval requests, rejections, failures, and important exceptions with read/unread state and record links; deduplicate events. |
| FR-18: Audit | Immutable-through-normal-operations records of intake, processing, edits, identity resolution, reconciliation, overrides, review, approvals, exports, and administration. |
| FR-19: Settings | Organization/GST/financial year, users, invoice numbering, extraction provider, required fields, tax/matching tolerances, routing thresholds, and export profile. |
| FR-20: AI assistant | P1 only. P0 review and processing must work without a conversational assistant. |

### P1: only after P0 is stable and verified

Follow the P1 catalog in [prompt.md](./prompt.md): evidence-grounded assistance, interactive source highlights, aliases, triage, batch review, saved views, command palette, approval matrices, advanced analytics, export templates, SLAs, customization, and automation. Validate an official TallyPrime or Zoho Books contract before adding direct accounting integration; CSV download is not ERP integration.

### P2: extensibility only

Defer the P2 roadmap in [prompt.md](./prompt.md), including three-way matching, GST filing/reconciliation integrations, payments/banking, multiple ERPs, advanced agents/analytics, mobile apps, billing, and fine-tuning.

Never let P1 or P2 delay completion and verification of the entire P0 flow.

## Technology and architecture

### Recommended stack when initializing

- Frontend: Next.js, React, strict TypeScript, Tailwind CSS, accessible shadcn/ui-style components and Radix primitives, TanStack Query/Table, React Hook Form, Zod, PDF.js, a maintained lightweight icon library, and Playwright.
- Backend: Java 21, a compatible supported Spring Boot version, Spring Security, Spring Data JPA/Hibernate, PostgreSQL, Flyway, Jakarta Bean Validation, Actuator, OpenAPI, JUnit 5, Mockito, Testcontainers, and Micrometer/OpenTelemetry integration.
- Infrastructure: private S3-compatible storage, transactionally durable PostgreSQL-backed jobs/work state, one real document AI/OCR adapter, one real inbound email integration, Docker Compose for local deployment, and an AWS deployment reference. Redis is optional and must be justified.
- Use one coherent frontend design/state strategy and the repository's selected package manager/build tool. Preserve lockfiles and use build wrappers when established.

### Module and layer boundaries

Use modules for identity/tenancy, ingestion, document processing, suppliers, GST/financial validation, duplicates, POs, reconciliation, review/exceptions, approvals, accounting export, notifications, and reporting/audit.

| Layer | Responsibilities |
| --- | --- |
| Domain | Entities, value objects, policies, invariants, and events; independent of persistence frameworks, frontend components, and provider SDKs. |
| Application | Commands/queries, use cases, ports, authorization-aware orchestration, and transaction boundaries. |
| Infrastructure | Persistence, object storage, OCR, inbound email, durable jobs, and export adapters. |
| API | Controllers, typed DTOs, validation, authentication/authorization integration, and consistent error translation. |

Use dependency inversion at actual boundaries; avoid an interface for every class. Controllers must not contain financial policy logic or expose persistence entities as API contracts.

Define `DocumentExtractionPort`, `InvoiceExtractionNormalizer`, `DocumentClassificationPort` when justified, and `AccountingExportPort` for export integration boundaries.

Model entities needed by implemented behavior using the data-model catalog in [prompt.md](./prompt.md). Do not create P1 entities solely because they appear in that catalog.

## Financial and transactional invariants

- Use Java `BigDecimal` and PostgreSQL exact numeric types for money, rates, prices, quantities, and calculations, with explicit scale/rounding rules. Avoid binary floating-point financial calculations and preserve decimal precision across JSON/TypeScript boundaries.
- Scope data, indexes, uniqueness constraints, queries, and relationships to the tenant. Validate referenced supplier, PO, invoice, document, and export ownership on writes as well as reads.
- Use explicit database transaction boundaries and optimistic locking/resource-version preconditions for invoice edits. Reject stale updates; never silently overwrite another reviewer.
- Make ingestion, provider events, jobs, reprocessing, approval/allocation transitions, and export retries idempotent. Protect side effects with database constraints and durable state, not only in-memory checks.
- Allocate PO quantities/value atomically under concurrency. Prevent over-allocation and define how rejection, material edits, supersession, and reversal affect allocations.
- Approve a specific immutable invoice version. Material changes invalidate approval and require fresh validations/reconciliation and approval before export.
- Export only the approved snapshot. Preserve exact invoice-version/export references and totals; prevent accidental duplicate successful export. Require authorization and a business reason for re-export or blocking-finding overrides.
- Do not hold database transactions open during OCR or other long-running external calls. Persist durable work and coordinate object storage/database failures so accepted documents cannot disappear silently.
- Version policies and rule decisions. Settings changes must not silently rewrite historical approvals, exports, or audit records.

### Explicit state models

- Processing: `RECEIVED → QUEUED → EXTRACTING → VALIDATING → MATCHING → PROCESSED`, with explicit retryable/terminal failure and reprocessing behavior.
- Business workflow: `NEEDS_REVIEW → IN_REVIEW → READY_FOR_APPROVAL → APPROVED → EXPORTED`, with defined rejected, duplicate, archived, and superseded outcomes where applicable.
- Exceptions: `OPEN → ASSIGNED → IN_REVIEW → RESOLVED / DISMISSED_WITH_REASON`.

Define and test allowed transitions and permissions. Do not combine extraction, review, approval, and export into one ambiguous status. Audit important transitions transactionally with the business change.

## AI extraction and durable processing

Accept tax/purchase invoices and supported credit/debit notes; route unknown classifications to review. Support scanned/searchable PDFs, multiple pages and lines, and explicit reprocessing.

Extract the full supplier, buyer, invoice, financial, and line-item field catalog in FR-03 of [prompt.md](./prompt.md), including applicable Indian tax breakdowns and e-invoice identifiers.

- Validate provider output against an explicit structured schema. Missing or unsupported fields remain unknown; do not invent values to pass validation.
- Preserve raw output, normalized values, extraction history, provider/model version, durations/errors, and source locations/confidence where supplied. Distinguish provider confidence from deterministic business validity.
- Preserve human corrections and their provenance during reprocessing. Surface differences for explicit reconciliation instead of silently replacing edited fields.
- Treat invoice text, images, attachments, and extracted text as untrusted data. Document content must not issue instructions, invoke privileged tools, bypass permissions, or expand tenant context.
- Keep provider credentials server-side. Document and configure external data exposure, retention, and provider limitations.
- Test fixtures and fake adapters may support deterministic tests, but production extraction must use a real configured provider and show an explicit failure/configuration state when unavailable.

The durable pipeline must validate input, securely store documents, persist intake/work records, classify/extract, normalize, resolve suppliers, validate, detect duplicates, reconcile, derive exceptions/review needs, persist outcomes, and notify relevant users/UI.

Assume at-least-once execution. Implement bounded concurrency, timeouts, bounded retries with backoff/jitter, worker recovery, terminal errors, safe manual retries, correlation IDs, and observable history. Do not claim exactly-once delivery across external systems.

For email, verify provider signatures and tenant routing, preserve sender/subject/received time/message reference, process multiple attachments, and deduplicate repeated events. For batches, retain per-file outcomes, partial success, independent retry, and downloadable error summaries.

## Deterministic validation, duplicates, and matching

### GST and arithmetic

- Validate GSTIN structure/checksum, context-dependent required identifiers, state consistency, intra/inter-state treatment, CGST/SGST versus IGST, HSN/SAC formats, taxable values, rates/amounts, rounding, credit/debit note signs/references, and conditional IRN/e-invoice requirements.
- Support legitimate unregistered suppliers, exempt supplies, and relevant transaction classifications. Use authoritative sources for regulatory assumptions; record applicability and effective dates instead of hardcoding universal tax rules.
- Verify quantity × price, discounts, taxable amounts, per-line/header tax, charges, round-off, and grand totals.
- Every finding must include rule ID/version, severity, field reference, actual/expected values where determinable, explanation, resolution options, and approval/export blocking behavior.
- Unresolved mandatory findings must block approval/export in backend use cases, including direct API calls. Present findings by severity/location and allow navigation to the affected field.

### Supplier identity and duplicates

Resolve suppliers against tenant master data; ambiguous matches require review. Exact duplicate evidence includes SHA-256 and supplier/normalized invoice number with financial-year/document-type context and amount/date checks. Suspected duplicates consider similar names/numbers, totals, nearby dates, PO references, and equivalent documents.

Distinguish exact from suspected matches, show evidence and side-by-side comparison, retain original records, and audit authorized false-positive resolutions. Operational anomaly indicators are review suggestions, never unsubstantiated fraud probabilities.

### Two-way PO matching

Resolve supplier and explicit PO references, compare eligible lines by identifiers/descriptions, calculate exact quantity/price/tax/amount differences, apply versioned tolerances, and check consumed quantities before classifying outcomes.

Support exact/within-tolerance/partial matches, missing POs, supplier/quantity/price/tax mismatches, unmatched lines, over-invoicing, and manual verification. AI suggestions cannot be the sole basis for allocation. Show invoice values, PO values, differences, statuses, resolution controls, source links, totals, and remaining balances.

## Authorization, security, privacy, and audit

- Implement an explicit permission matrix for Platform Administrator, Organization Administrator, Finance Manager, Accountant/Processor, Reviewer, Approver, and Read-Only Auditor.
- Enforce authentication, valid tenant membership, action permissions, and resource ownership on every backend operation. UI visibility is not authorization. Organization switching must update tenant-scoped queries/caches without exposing prior-tenant data.
- Apply tenant isolation to jobs, storage access, signed downloads, search, notifications, exports, analytics, audit queries, and AI context, not only invoice endpoints.
- Use standards-based identity and secure sessions/tokens; apply CSRF protections where applicable, rate limits, input validation, XSS/SQL-injection defenses, and restrictive storage permissions.
- Keep secrets out of source control, logs, responses, and frontend bundles. Supply documented environment variable examples with placeholders only.
- Enforce file type/content, size, page-count, and archive limits; address malformed PDFs, decompression bombs, path traversal, and malware quarantine/scanning. Treat original filenames as untrusted.
- Protect CSV exports from formula injection and ensure downloads remain tenant-authorized.
- Plan encryption in transit/at rest, retention/deletion, backups/restoration, and AI-provider data handling. Document current applicable Indian privacy/financial-record assumptions using authoritative sources; do not claim compliance without evidence.
- Audit uploads, extraction/reprocessing, corrections, supplier/duplicate decisions, reconciliation, exceptions/overrides, assignment, approvals/rejections, exports, settings, and membership/permission changes.
- Audit events capture actor, tenant, timestamp, action, resource, appropriate before/after summary, business justification, and correlation ID. Protect them from ordinary application modification and unnecessary sensitive-data disclosure.

## API and frontend integration

Use versioned REST APIs under `/api/v1` and maintain OpenAPI contracts. Cover organizations, users, invoices/uploads, import batches, processing, validations, duplicates, reconciliation, review tasks, exceptions, approvals, suppliers, POs, exports, dashboard, notifications, audit, and settings.

- Define typed operation-specific requests/responses, pagination/filter/sort behavior, permissions, idempotency, version preconditions, async status, and authorized upload/download flows.
- Use explicit state-transition operations instead of arbitrary client-controlled status updates.
- Return consistent Problem Details/error envelopes with field-level validation mappings and useful failure explanations.
- Generate or validate TypeScript types against OpenAPI and prevent contract drift.
- Use server-side search/pagination/sorting. Derive dashboard aggregates from persisted events/records with documented definitions, time windows, and denominators.
- Scope frontend query keys by organization; handle invalidation, pending jobs, stale data, concurrent edits, expired sessions, and errors explicitly.

## Mandatory dark-theme product design

Build a coherent, premium finance workspace focused on document review and exceptions. Follow the detailed screen specification; do not substitute a generic admin template or decorative dashboard.

### Semantic token baseline

| Token | Starting value |
| --- | --- |
| App background | `#0B101B` |
| Sidebar | `#0E1523` |
| Primary surface | `#141D2C` |
| Elevated surface | `#1A2637` |
| Hover surface | `#243246` |
| Border | `#29374B` |
| Primary text | `#F1F5F9` |
| Secondary text | `#B1BED0` |
| Muted text | `#94A3B8` |
| Primary accent / hover | `#5EEAD4` / `#2DD4BF` |
| AI/secondary accent | `#A78BFA` |
| Success / warning | `#4ADE80` / `#FBBF24` |
| Error / information | `#FB7185` / `#60A5FA` |

Validate actual foreground/background contrast. Use shared semantic tokens rather than hardcoded component colors. Reserve teal for key actions/active states, violet for AI context, and semantic colors for status; always add text or icons conveying status.

Use Geist/Inter or a readable equivalent, clear hierarchy, tabular numerals, appropriate `₹` and Indian number formatting such as `₹12,45,678.90`, a consistent 4/8px spacing scale, restrained elevation/borders/radii, and readable compact financial tables. Avoid tiny secondary text, neon glow, heavy shadows, excessive glass effects, gratuitous gradients, and empty decorative panels.

### Shell and reusable components

- Permission-aware sidebar: Workspace (Overview, Invoice Inbox, Upload Center, Review Queue, Reconciliation, Approvals); Management (Suppliers, POs, Exports); Intelligence (Analytics, Exceptions, Activity/Audit); Administration (Organization, Team/Permissions, Automation Rules, Settings). Preserve P0/P1 scope when exposing destinations.
- Top bar: organization switcher, breadcrumbs/context, global search entry, notifications, help, and profile.
- Provide desktop collapse/active states/tooltips and tablet/mobile drawer navigation with keyboard access.
- Reuse page headers, tables, filters, financial cells, badges, evidence/findings, forms, dialogs/drawers, tabs, toasts, upload zones, progress/skeletons, empty states, document panels, resizable splits, timelines, and confirmations.

### Critical screen behavior

Implement the screen-by-screen requirements in [ui-ux-desing-specification.md](./ui-ux-desing-specification.md) while respecting product priorities. Preserve these core interactions:

- Onboarding keeps optional steps optional; dashboard KPIs open meaningful filtered lists.
- Uploads expose per-file progress/errors/retries and durable processing history.
- Review Studio keeps a resizable/collapsible document viewer, data editor, and validation/evidence panel together, with PDF navigation/zoom/rotation, editable lines, notes, and sticky Save Draft/Revalidate/Reconcile/Submit actions. Protect unsaved edits and detect conflicts.
- Reconciliation aligns invoice/PO/difference/status/resolution columns with remaining balances and re-run actions.
- Approval and export screens show validation/match gates, decision/version history, authorized actions, and clear confirmation/feedback.
- Master data, settings, notifications, and analytics use functional imports/forms/navigation and persisted data.

### Accessibility and complete UI states

Target WCAG 2.2 AA: semantic controls, labels, keyboard operation, visible focus, accessible dialogs/drawers/tables, focus management, readable contrast, non-color status indicators, zoom/text enlargement, and useful form errors. Respect `prefers-reduced-motion`; keep purposeful transitions generally around 120–250 ms.

Handle initial/loading-more, empty, no-results, validation, authorization, network, partial-success, success, retry, stale/conflicting-data, expired-session, and no-permission states. Maintain document context and avoid dead-end actions. Verify responsive layouts on representative desktop, tablet, and mobile sizes; inspect screenshots rather than relying on compilation.

## Implementation sequence

Follow the eight phases in [prompt.md](./prompt.md): foundation/design system; ingestion/processing; invoice intelligence; POs/reconciliation; review/approval; export/dashboard; quality/deployment; then P1 enhancements.

Each phase must deliver working vertical slices. Test throughout implementation; do not postpone security, auditability, or financial integrity to the final phase.

## Verification and acceptance gates

Use the repository's actual configured commands and task definitions. Once initializing the stack, establish documented reproducible commands for frontend lint/type-check/tests/build, backend unit/integration verification, migrations, OpenAPI/type synchronization, Playwright, and local deployment. Do not list a command as successful unless it ran successfully.

- Backend: test domain rules, GST/arithmetic, duplicates, matching, state transitions, RBAC, tenant isolation, database behavior, concurrency, retry/idempotency, and export snapshots.
- Frontend: verify forms/tables, upload progress/errors, review edits, approvals, async updates, keyboard access, dark-theme consistency, and responsive behavior.
- E2E: run real frontend/backend/database workflows using deterministic synthetic India-specific data and scanned fixtures. Keep provider test doubles explicit; separately run a real extraction smoke test when credentials are available. Verify the real inbound email integration when configured.
- Visual QA: capture and inspect critical workflows for overflow/clipping, spacing, hierarchy, financial formatting, contrast/focus, errors, and responsive behavior. Fix observed defects.

| Scenario | Required evidence |
| --- | --- |
| A: Happy path | Upload → real configured extraction → supplier/validation/PO match → approval → CSV → audit. |
| B: GST error | Blocking finding → correction → revalidation → approval. |
| C: Duplicate | Repeated invoice → evidence/comparison → no accidental duplicate export. |
| D: PO mismatch | Over-quantity invoice → line discrepancy → exception → authorized resolution. |
| E: Human review | Uncertain supplier/amount → source evidence → saved correction → revalidation. |
| F: Bulk | 100 documents → independent outcomes → failed-item retry without replaying successes. |
| G: Provider outage | Timeout → bounded retries → persisted failure → recovery without duplicate effects. |
| H: Tenant isolation | Cross-tenant invoice, PDF, supplier, PO, and export requests all denied. |
| I: Authorization | Direct approval API call by an unprivileged user denied. |
| J: Concurrent edits | Two reviewers → stale update rejected without silent data loss. |
| K: Concurrent allocations | Competing invoices cannot over-consume the remaining PO quantity. |
| L: Export recovery | Preparation failure → recorded error → safe retry → no duplicate successful export. |
| M: Usability | Critical workflows pass contrast/focus/keyboard/responsive/error-state checks. |
| N: Provenance | Available extraction evidence shown; original and human-corrected values retained. |
| O: Audit | Final version traces uploader, editors, reviewers, approver, and exporter. |

Performance targets remain unverified targets until measured: 1,000–10,000 invoices/month per target tenant, batches of at least 100, bounded workers, indexed read API p95 below 500 ms at a documented load, and typical small-invoice processing within two minutes where providers permit. Measure representative frontend responsiveness, query efficiency, and provider consumption.

## Traceability, operations, and definition of done

Maintain a requirement matrix using:

`Feature ID → Business requirement → User role → UI screen → Backend use case → API endpoint → Database entity → Authorization rule → Test case → Status`

For each P0 requirement record implementation locations, completion status, test evidence, and known limitations. Use honest statuses such as not started, in progress, implemented but unverified, externally blocked, and verified. A UI or API skeleton is not a completed feature.

Deliver all artifacts listed in [prompt.md](./prompt.md), including the integrated application, migrations, adapters, contracts, tests/India-specific fixtures, Compose environment, architecture diagrams, deployment documentation, README, and operating instructions.

Document configuration/secrets, setup/migrations, worker recovery, provider failures, retry/reprocessing, retention, backup/restore, deployment, health/readiness, and correlation-based troubleshooting. Include a brief implementation review of the six AWS Well-Architected pillars—Operational Excellence, Security, Reliability, Performance Efficiency, Cost Optimization, and Sustainability—with evidence, risks, and trade-offs.

A feature is done only when its business behavior, frontend interaction, backend API, persistence, authorization/validation, error paths, relevant automated tests, accessibility/responsiveness, feedback, and required audit history all work.

The platform is done only when this complete journey is verified in one coherent application:

**Sign in → Select organization → Upload real invoice → Process with configured AI → Review → Validate GST → Detect duplicates → Match PO → Resolve exceptions → Approve → Export CSV → Inspect audit and dashboard history.**

End implementation tasks with a concise account of completed behavior, verification commands/results, remaining limitations or external prerequisites, and the next unfinished P0 work when applicable. Never describe partial delivery as a completed production-ready platform.
