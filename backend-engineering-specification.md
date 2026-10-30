# AI Invoice Processing & Reconciliation SaaS

Detailed Architecture Design & Backend Engineering Specification — Version 1.0

Architecture decision: Build a domain-oriented modular monolith using Java 21, Spring Boot, PostgreSQL, and Hexagonal Architecture, with separately scalable API and background-worker deployments. Use Amazon S3 for documents, Amazon SQS for durable processing, and an interchangeable AI extraction adapter.

This is the preferred starting architecture for Indian distributors, manufacturers, and accounting firms processing roughly 1,000–10,000 invoices per tenant each month. It keeps financial transactions and business invariants within clear boundaries, while allowing document processing to scale independently.

Application style

Modular monolith

DDD + Hexagonal Architecture

Runtime

Java 21 / Spring Boot

REST API + async workers

System of record

PostgreSQL

ACID transactions + tenant isolation

Integration model

Ports and adapters

OCR, email, storage, export

The engineering design follows the six pillars of the AWS Well-Architected Framework and treats financial correctness, concurrency safety, auditability, and security as higher priorities than microservice decomposition.&#x20;

[image](https://www.google.com/s2/favicons?domain=https://wa.aws.amazon.com\&sz=32)

AWS Well-Architected Framework

+1



## 1. Reference system architecture

The API and worker should initially share one codebase and one PostgreSQL schema, but run as separately deployable processes with different startup profiles and scaling configurations.

Key design constraint: SQS is a delivery mechanism, not the authoritative workflow state. PostgreSQL owns invoice status, job checkpoints, approval records, reconciliation allocations, and export history. SQS consumers must tolerate duplicates and out-of-order deliveries. AWS documents these limitations explicitly.&#x20;

[image](https://www.google.com/s2/favicons?domain=https://aws.amazon.com\&sz=32)

AWS

+2



## 2. Detailed backend engineering specification

The following specification is intended to guide domain modeling, database design, API implementation, engineering reviews, and delivery planning. It retains the MVP/P1/P2 boundaries from Master Prompt V2.0 and introduces concrete decisions for the failure modes most likely to affect financial data integrity.

# AI Invoice Processing & Reconciliation SaaS

## Detailed Backend Architecture and Engineering Specification

**Version:** 1.0\
**Target market:** India\
**Architecture:** Modular Monolith + Hexagonal Architecture + DDD\
**Primary stack:** Java 21, Spring Boot, PostgreSQL, AWS\
**Scope:** P0 MVP with P1/P2 extension boundaries

---

# 1. Architecture Objectives

The architecture must support a secure multi-tenant SaaS platform that processes invoice documents from receipt through accounting export.

## Business objectives

- Reduce manual invoice data entry.
- Provide dependable AI-assisted extraction.
- Detect incorrect and duplicate invoices.
- Validate GST and financial arithmetic.
- Reconcile invoices against purchase orders.
- Route discrepancies to human reviewers.
- Enforce controlled approvals.
- Export accurate, approved records.
- Maintain complete operational traceability.
- Support multiple client organizations for accounting firms.

## Engineering objectives

- Financial correctness and transaction integrity
- Secure tenant isolation
- Recoverable asynchronous processing
- Explainable business rules
- Versioned and auditable decisions
- Minimal coupling to AI providers
- API contracts compatible with the defined frontend
- Cost-effective scaling
- Operational observability
- Testable module boundaries
- Maintainability over premature distribution

## Working assumptions

- Primary business flow is accounts payable.
- Default currency is INR, while currency is modeled explicitly.
- P0 supports two-way PO matching.
- P0 accounting export is CSV.
- One actual OCR provider is integrated initially.
- One actual inbound email integration is required.
- GST filing, payment execution, and three-way matching are outside P0.
- Human approval remains the default for export eligibility.
- Organizations can process 1,000–10,000 invoices monthly.
- Compliance rules must be evaluated against applicable law and effective dates.

---

# 2. Architecture Decision Records

| ID      | Decision                             | Rationale                                                                                |
| ------- | ------------------------------------ | ---------------------------------------------------------------------------------------- |
| ADR-001 | Modular monolith                     | Keeps distributed transaction and operational complexity low                             |
| ADR-002 | Hexagonal module boundaries          | Isolates domain logic from infrastructure                                                |
| ADR-003 | Java 21                              | Mature Java platform with modern language and concurrency features                       |
| ADR-004 | Spring Boot                          | Application framework, security, data access, observability                              |
| ADR-005 | PostgreSQL                           | ACID guarantees and relational financial data integrity                                  |
| ADR-006 | Shared database, tenant-scoped model | Simpler MVP operations; explicit isolation controls required                             |
| ADR-007 | S3-compatible object storage         | Separates binary documents from relational records                                       |
| ADR-008 | SQS and durable outbox               | Reliable asynchronous processing and retry                                               |
| ADR-009 | Provider-agnostic OCR adapter        | Avoids coupling the business domain to a document AI vendor                              |
| ADR-010 | Deterministic financial rules        | Reproducible validation, matching, and approval decisions                                |
| ADR-011 | Versioned invoice records            | Protects approvals and exports against subsequent edits                                  |
| ADR-012 | Immutable export snapshots           | Supports reliable accounting auditability                                                |
| ADR-013 | REST + OpenAPI                       | Predictable integration with the Next.js frontend                                        |
| ADR-014 | Polling initially                    | Simpler reliable processing-status UI; SSE optional later                                |
| ADR-015 | No Kafka in initial MVP              | SQS is sufficient for independent async work; avoid unnecessary messaging infrastructure |
| ADR-016 | No Redis initially                   | Introduce only after measuring a concrete need                                           |

## Technology version policy

Verify current versions through official documentation at implementation time.

Spring Boot 4.x may be selected when the required Spring Security, JPA, OpenAPI, testing, cloud, and Modulith libraries are confirmed compatible. Otherwise use an appropriately supported Spring Boot 3.5.x baseline and document the decision.

Pin versions through dependency management. Do not mix incompatible major versions merely to use the latest release.

---

# 3. System Architecture

## 3.1 Architectural layers

**Presentation/API layer**

- REST controllers
- API authentication
- Request validation
- Authorization policies
- DTO mapping
- Error responses

**Application layer**

- Use-case orchestration
- Commands and queries
- Transaction boundaries
- Workflow coordination
- Port interfaces

**Domain layer**

- Aggregates
- Entities and value objects
- Financial invariants
- Matching policies
- Validation rules
- Approval rules
- Business events

**Infrastructure layer**

- PostgreSQL persistence adapters
- S3 storage adapters
- Textract/document AI adapter
- SES/email adapter
- SQS processing adapter
- CSV export adapter
- Notification adapters

Dependency flow must point inward.

The domain must not depend on Spring annotations, JPA entities, HTTP request objects, or AWS SDK types.

## 3.2 Physical deployments

Run the same backend codebase in two roles:

**API application**

- Receives browser/API requests
- Handles tenant access
- Exposes commands and queries
- Manages upload sessions
- Returns processing states
- Generates authorized download access

**Processing worker**

- Consumes durable tasks
- Calls OCR providers
- Normalizes extraction
- Runs validation and matching
- Generates export files
- Handles retries and failures

The application and worker can scale independently without introducing inter-service REST calls.

## 3.3 Component responsibilities

| Component               | Responsibility                                      |
| ----------------------- | --------------------------------------------------- |
| Next.js frontend        | User experience and typed API consumption           |
| Authentication provider | User identity and secure authentication             |
| Spring Boot API         | Business commands, queries, and authorization       |
| PostgreSQL              | Source of truth for business and processing state   |
| S3                      | Raw documents, clean documents, export files        |
| SQS                     | Delivery of background work                         |
| Worker                  | Durable asynchronous execution                      |
| OCR adapter             | External document extraction                        |
| Validation engine       | GST and financial rule evaluation                   |
| Reconciliation engine   | Invoice/PO comparison and allocation decisions      |
| Export adapter          | CSV generation and optional future ERP integrations |
| Observability stack     | Logs, metrics, alerts, and traces                   |

---

# 4. Bounded Contexts and Domain Modules

## 4.1 Module catalog

| Module                | Ownership                                           | Key application use cases                          |
| --------------------- | --------------------------------------------------- | -------------------------------------------------- |
| Identity and Tenancy  | Tenant, membership, roles, legal entities           | Create organization, invite user, authorize access |
| Ingestion             | Intake session, ingestion batch, document admission | Initiate upload, finalize upload, process email    |
| Document Processing   | Processing jobs, extraction runs                    | Classify, extract, normalize, reprocess            |
| Invoice Management    | Invoice, versions, invoice lines                    | Create draft, correct fields, read invoice         |
| Supplier Management   | Supplier master, aliases                            | Resolve supplier, import suppliers                 |
| Tax Validation        | Rules and validation findings                       | Validate invoice and GST                           |
| Duplicate Detection   | Duplicate candidates and decisions                  | Evaluate duplicates and resolve findings           |
| Purchase Orders       | PO, lines, amendments                               | Create/import PO, track remaining quantities       |
| Reconciliation        | Matching results and PO allocations                 | Match, recalculate, allocate, release              |
| Review and Exceptions | Tasks, findings, reviewer decisions                 | Assign, claim, edit, resolve                       |
| Approvals             | Approval policy and decisions                       | Submit, approve, reject, revoke                    |
| Accounting Export     | Export batches and records                          | Generate CSV, track and download                   |
| Notifications         | User notifications                                  | Create, list, mark read                            |
| Reporting and Audit   | Audit events, read models                           | Dashboard, search, audit history                   |

## 4.2 Module dependency rules

- Identity/Tenancy exposes authorization and membership services.
- Invoice Management owns the canonical invoice business model.
- Document Processing produces extraction results but does not approve invoices.
- Validation evaluates invoice snapshots.
- Reconciliation consumes invoice and PO information through stable module interfaces.
- Approval owns final approval decisions and coordinates allocation commitment.
- Export consumes immutable approved snapshots.
- Reporting consumes read-only business data.
- No module may directly modify another module's owned tables as a shortcut.

Use application services for cross-module orchestration when a single database transaction must preserve invariants.

Use domain events for asynchronous consequences that do not require immediate consistency.

## 4.3 Suggested repository structure

```
invoice-saas/
  apps/
    web/
    backend/
      src/main/java/com/example/invoice/
        InvoiceApplication.java
        shared/
          security/
          errors/
          money/
          tenancy/
          observability/
        identity/
        ingestion/
        document/
        invoice/
        supplier/
        tax/
        duplicate/
        purchaseorder/
        reconciliation/
        review/
        approval/
        export/
        notification/
        reporting/
      src/main/resources/
        db/migration/
        application.yml
      src/test/
  infrastructure/
    docker/
    terraform/
  docs/
    architecture/
    adr/
    api/
    runbooks/
  test-fixtures/
    invoices/
    purchase-orders/
```

Within a module:

```
invoice/
  api/
    InvoiceController.java
    dto/
  application/
    command/
    query/
    port/in/
    port/out/
    service/
  domain/
    model/
    policy/
    event/
    exception/
  infrastructure/
    persistence/
    mapper/
```

Keep internal module packages inaccessible to unrelated modules.

Use ArchUnit or a compatible Spring Modulith verifier to enforce architecture boundaries automatically.

---

# 5. Core Domain Model

## 5.1 Primary aggregates

### Tenant

Represents an isolated SaaS customer account.

Attributes:

- Tenant ID
- Organization name
- Status
- Default currency
- Timezone
- Settings/policy references
- Creation timestamp

### LegalEntity

Represents a business identity/GST registration associated with a tenant.

Attributes:

- Legal entity ID
- Tenant ID
- Legal/trade name
- GSTIN if applicable
- Registered state
- Address
- Status

This avoids assuming one tenant always has exactly one GST registration.

### Invoice

Represents the logical invoice within the platform.

Attributes:

- Invoice ID
- Tenant ID
- Current version
- Supplier reference
- Legal entity reference
- Document type
- Processing status
- Review status
- Approval status
- Export status
- Creation/update timestamps
- Optimistic lock version

Separate business state from technical processing state.

### InvoiceVersion

Represents a versioned financial interpretation of an invoice.

Attributes:

- Invoice version ID
- Invoice ID
- Version number
- Supplier/buyer fields
- Invoice number and dates
- PO reference
- Currency
- Financial totals
- Tax breakdown
- Validation snapshot references
- Created by
- Created timestamp

Versioned invoice data protects approvals and exports from subsequent corrections.

### InvoiceLine

Attributes:

- Invoice version ID
- Line identifier
- Description
- HSN/SAC
- Quantity
- Unit
- Unit price
- Discount
- Taxable value
- Tax rates and amounts
- Line total
- Source evidence reference

### InvoiceDocument

Represents an uploaded source artifact.

Attributes:

- Document ID
- Tenant ID
- Invoice ID when associated
- Object storage key/version
- Filename and MIME type
- SHA-256 checksum
- File size and page count
- Intake channel
- Admission/security status
- Creation timestamp

Never use an uploaded filename as a storage path.

### ExtractionRun

Attributes:

- Run ID
- Document ID
- Provider
- Provider job ID
- Model/provider version
- Status
- Raw result object reference
- Normalized result version
- Start/end timestamps
- Error classification

### PurchaseOrder

Attributes:

- PO ID
- Tenant ID
- Supplier ID
- Legal entity ID
- PO number
- Currency
- Status
- Version
- Creation/update timestamps

### PurchaseOrderLine

Attributes:

- Line ID
- PO ID
- Description/product reference
- Ordered quantity
- Unit price
- Tax metadata
- Ordered amount
- Amendment version

### POAllocation

Represents invoice quantities and amounts committed against a PO line.

Attributes:

- Allocation ID
- Tenant ID
- PO line ID
- Invoice version ID
- Quantity
- Amount
- Status
- Created timestamp
- Released timestamp when applicable

### ReviewTask

Attributes:

- Review task ID
- Tenant ID
- Invoice ID/version
- Assignment
- Status
- Priority
- Review reason
- Created/completed timestamps

### ApprovalDecision

Attributes:

- Decision ID
- Invoice version ID
- Approver
- Decision
- Reason
- Policy version
- Timestamp

### ExportBatch / ExportRecord

Track:

- Export request
- Export profile and version
- Invoice version references
- Export artifact
- Checksum
- Attempt status
- Completion timestamp
- Initiator
- Error details

## 5.2 Value objects

Introduce meaningful value objects:

- `Money`
- `Quantity`
- `Gstin`
- `InvoiceNumber`
- `TaxRate`
- `TaxBreakdown`
- `FinancialYear`
- `InvoiceDate`
- `SupplierIdentity`
- `MatchTolerance`
- `DocumentHash`
- `TenantId`

Avoid introducing a wrapper class for every primitive unless it enforces useful semantics.

## 5.3 Monetary rules

Use `BigDecimal`.

Suggested database types:

- Money: `NUMERIC(20,4)`
- Quantity: `NUMERIC(20,6)`
- Unit price: `NUMERIC(20,6)` where needed
- Tax rate: `NUMERIC(9,6)`

Define:

- Currency
- Internal precision
- Tax rounding mode
- Per-line versus invoice-level rounding
- Tolerance policies
- Export precision

Do not use Java `double` for money.

Do not assume that rounding rules are identical for every document type or supplier context.

---

# 6. Database Design

## 6.1 Major tables

**Identity**

- `tenants`
- `legal_entities`
- `users`
- `tenant_memberships`
- `roles`
- `membership_permissions`

**Ingestion**

- `ingestion_batches`
- `upload_sessions`
- `invoice_documents`
- `email_ingestion_events`

**Invoices**

- `invoices`
- `invoice_versions`
- `invoice_lines`
- `invoice_field_evidence`
- `extraction_runs`

**Validation**

- `validation_rules`
- `validation_runs`
- `validation_findings`
- `duplicate_candidates`
- `duplicate_resolutions`

**Suppliers and POs**

- `suppliers`
- `supplier_aliases`
- `purchase_orders`
- `purchase_order_lines`
- `po_allocations`

**Workflow**

- `reconciliation_runs`
- `reconciliation_findings`
- `review_tasks`
- `exception_cases`
- `approval_decisions`

**Exports and operations**

- `export_batches`
- `export_records`
- `processing_jobs`
- `outbox_events`
- `notifications`
- `audit_events`

## 6.2 Tenant isolation

All tenant-owned business tables must carry `tenant_id`.

Tenant context must derive from authenticated membership, not from an arbitrary client-supplied header.

Recommended protections:

- Tenant-aware application queries
- Tenant-scoped repository methods
- Composite foreign keys where practical
- PostgreSQL Row-Level Security
- Separate non-owner application database role
- Explicit tenant context on background jobs
- Cross-tenant API and database tests

If using PostgreSQL RLS:

- Use transaction-local tenant context.
- Ensure it is established for every relevant transaction.
- Deny access when tenant context is absent.
- Test pooled connections for tenant leakage.
- Do not use an application role that bypasses RLS.
- Account for privileged maintenance and migration roles separately.

## 6.3 Critical constraints and indexes

Recommended constraints:

- Unique tenant membership per user and tenant
- Unique upload request idempotency key within tenant/source scope
- Unique provider email event reference within intake scope
- Unique invoice version number within logical invoice
- Unique active review-task identity where workflow requires it
- Unique export idempotency key
- Unique outbox event ID
- Composite tenant/resource foreign keys
- Guarded approved-invoice business identity

Recommended indexes:

- `(tenant_id, created_at DESC)` for invoices
- `(tenant_id, processing_status, created_at)`
- `(tenant_id, review_status, created_at)`
- `(tenant_id, supplier_id, invoice_date)`
- `(tenant_id, normalized_invoice_number)`
- `(tenant_id, gstin_normalized)` for suppliers
- `(tenant_id, po_number)` for purchase orders
- `(tenant_id, assignee_id, status)` for review tasks
- `(tenant_id, status, created_at)` for export batches

Create indexes based on measured query plans, not by indexing every column.

## 6.4 Data lifecycle

**Source documents:** Stored in object storage with restricted access.

**Extraction outputs:** Store normalized relational values plus versioned raw extraction references.

**Invoice corrections:** Preserve version and audit history.

**Approved snapshots:** Immutable for export purposes.

**Exports:** Preserve file checksum and exact invoice-version references.

**Retention:** Configurable based on legal, financial, contractual, and privacy requirements.

Do not perform physical deletion where applicable statutory retention or audit obligations prohibit it. Use documented retention/erasure policies.

---

# 7. Invoice State Machines

A single invoice status is not sufficient.

Use separate but coordinated state dimensions.

## 7.1 Technical processing status

```
RECEIVED
   |
QUARANTINED
   |
QUEUED
   |
EXTRACTING
   |
NORMALIZING
   |
VALIDATING
   |
MATCHING
   |
PROCESSED
```

Terminal or recoverable outcomes include:

- `PROCESSING_FAILED`
- `REPROCESS_REQUESTED`
- `UNSUPPORTED_DOCUMENT`
- `SECURITY_REJECTED`

Transitions must be explicit.

## 7.2 Review lifecycle

```
NOT_REQUIRED / REQUIRED
        |
      OPEN
        |
     CLAIMED
        |
    IN_REVIEW
        |
    COMPLETED
```

Also support:

- `REASSIGNED`
- `CANCELLED`
- `REOPENED`

## 7.3 Business approval lifecycle

```
DRAFT
  |
READY_FOR_APPROVAL
  |
PENDING_APPROVAL
  |----------------|
APPROVED         REJECTED
  |
EXPORT_ELIGIBLE
```

Approval status and export status must remain distinct.

## 7.4 Export lifecycle

```
NOT_EXPORTED
     |
EXPORT_PENDING
     |
GENERATING
     |
EXPORTED
```

Failure paths:

- `EXPORT_FAILED`
- `RETRY_PENDING`
- `REEXPORTED`

## 7.5 State invariants

- A rejected document cannot be exported.
- An unapproved invoice version cannot be exported.
- Blocking validations prevent submission unless an explicitly authorized resolution exists.
- A material correction after approval requires a new approval decision.
- Approved financial versions are immutable.
- Exported snapshots must not be modified in place.
- Reprocessing cannot silently overwrite reviewed financial data.
- State changes must be authorized, auditable, and concurrency-safe.

For corrections after export, use a controlled supersession, reversal, or adjustment workflow rather than rewriting the exported accounting record.

---

# 8. Document Ingestion Architecture

## 8.1 Direct upload sequence

1. Frontend requests an upload session.
2. Backend authenticates user and tenant.
3. Backend validates upload metadata and quotas.
4. Backend creates an upload session with a unique ID.
5. Backend returns a short-lived upload authorization.
6. Frontend uploads to a private quarantine object location.
7. Frontend calls upload completion.
8. Backend verifies object existence, size, checksum, and intake authorization.
9. Security validation/scanning runs.
10. Clean document is promoted to admitted storage.
11. Document metadata and processing work are persisted atomically.
12. Background processing begins.
13. Frontend observes document status.

Do not trust client-reported completion without server-side object verification.

## 8.2 Email ingestion

Recommended AWS implementation:

- SES inbound receiving
- Dedicated intake domain
- Unique tenant-specific recipient aliases
- Raw MIME storage in S3
- SQS-triggered parsing worker
- Attachment extraction and validation
- Standardized document ingestion use case

Tenant identity must come from validated recipient routing configuration.

Do not route an email to a tenant merely because the sender claims a certain company.

Deduplicate by provider event/message identity and attachment-specific identifiers.

## 8.3 Bulk processing

- Each file receives a distinct document ID.
- The batch references all document IDs.
- Failure of one document must not invalidate other documents.
- Support per-document retry.
- Persist batch progress based on actual completed states.
- Enforce archive expansion limits and ZIP traversal protections.

## 8.4 Intake security

Required:

- Allowlisted MIME types/extensions
- Magic-byte/content verification
- File size and page limits
- Filename normalization
- Quarantine
- Malware scanning
- Archive decompression limits
- Private storage
- Authorized downloads
- Rate limits
- Tenant quotas
- Idempotent finalization

---

# 9. Durable Asynchronous Processing

## 9.1 Recommended orchestration

Use:

- PostgreSQL job state
- Transactional outbox
- SQS work queue
- Spring Boot workers
- Dead-letter queue

The database is authoritative.

## 9.2 Outbox flow

1. A business transaction updates the database.
2. The same transaction inserts an outbox event.
3. A separate dispatcher reads pending outbox events.
4. Dispatcher publishes messages to SQS.
5. Successful sends are acknowledged in outbox state.
6. Consumers process idempotently.

A dispatcher crash after sending but before marking an event as published can produce duplicates. Consumers must tolerate this.

Use row locking or `SKIP LOCKED` for parallel outbox dispatchers.

## 9.3 Processing stages

- Document admission
- Classification
- Extraction
- Normalization
- Supplier resolution
- Validation
- Duplicate evaluation
- Reconciliation
- Review routing

Persist checkpoints or stable stage results.

Every stage must identify:

- Input version
- Output version
- Idempotency scope
- Status
- Retry count
- Error category
- Provider reference
- Execution timestamps

## 9.4 Message envelope

Recommended fields:

```
eventId
eventType
schemaVersion
tenantId
aggregateId
aggregateVersion
jobId
workflowStage
correlationId
occurredAt
```

Use an explicit message schema version.

Never put raw invoice PDF bytes or unnecessary financial data into queue messages.

## 9.5 Worker execution

Worker algorithm:

1. Receive message.
2. Validate schema.
3. Load authoritative job.
4. Establish authorized tenant context.
5. Check job status and input version.
6. Acquire processing lease or atomic transition.
7. Execute permitted stage.
8. Persist result.
9. Schedule next stage.
10. Acknowledge message.

Do not acknowledge before durable completion.

## 9.6 Retry policy

Classify errors.

**Transient**

- OCR timeout
- Rate limiting
- Temporary network failure
- Temporary object-store failure

Apply bounded retries with exponential backoff and jitter.

**Permanent**

- Unsupported document
- Malformed PDF
- Invalid source object
- Irrecoverable provider validation error

Record a terminal failure without pointless retries.

**Business errors**

- Incorrect GST
- Missing invoice number
- PO mismatch
- Duplicate suspicion

These are not infrastructure failures. Route them to review.

## 9.7 Poison messages

After bounded attempts:

- Persist failure state.
- Send to DLQ where appropriate.
- Expose failure in operational UI.
- Alert on abnormal DLQ depth.
- Provide controlled replay after remediation.

---

# 10. AI Extraction Architecture

## 10.1 Provider abstraction

Define the following contracts where appropriate:

- `DocumentExtractionPort`
- `DocumentClassificationPort`
- `ExtractionNormalizer`
- `ExtractionResultRepository`
- `ExtractionEvidenceRepository`

Recommended initial AWS implementation: Amazon Textract's invoice/expense analysis capabilities.

The adapter must account for different synchronous/asynchronous provider operations and limits.

## 10.2 Canonical extraction output

```
ExtractionResult
  extractionRunId
  documentId
  provider
  providerModelVersion
  documentType
  headerFields
  supplierFields
  buyerFields
  taxBreakdown
  lineItems[]
  fieldEvidence[]
  missingFields[]
  extractionWarnings[]
```

Each extracted field may contain:

- Canonical field name
- Raw value
- Normalized value
- Source page
- Bounding box
- Provider confidence if available
- Extraction run reference

Store field provenance independently from current edited business values.

## 10.3 Normalization

Normalize:

- Invoice dates
- Monetary values
- Supplier/buyer GSTIN
- Invoice-number whitespace/case
- Currency symbols
- Quantity and units
- Tax percentage representation
- Decimal separators
- Line-item relationships

Normalization must not silently invent values.

## 10.4 Indian invoice handling

Document AI results may not reliably identify:

- GSTIN
- HSN/SAC
- CGST versus SGST versus IGST
- Place of supply
- IRN
- Document-specific invoice references

Build a deterministic post-processing layer and additional bounded extraction strategies where justified.

Do not interpret an OCR provider's generic `TAX` field as a verified Indian GST breakdown.

## 10.5 Human correction

Store:

- Original value
- Corrected value
- Correction actor
- Timestamp
- Invoice version
- Associated validation finding
- Optional reason

An extraction rerun must produce a separate result that can be compared or selectively applied.

## 10.6 AI security

- Treat document content as untrusted.
- Disable autonomous business actions from extracted text.
- Minimize data shared with external models.
- Restrict tenant context.
- Protect secrets and internal prompts.
- Validate all AI-structured outputs.
- Never rely on AI alone for tax compliance or approval.

---

# 11. GST and Financial Validation Engine

## 11.1 Design

Implement business rules independently of OCR.

Recommended interfaces:

- `InvoiceValidationService`
- `ValidationRule`
- `ValidationPolicyRepository`
- `TaxCalculationService`

A rule evaluates a canonical invoice version and contextual policy, producing findings.

## 11.2 Rule categories

**Identity**

- GSTIN structure/checksum
- Buyer and seller registration context
- Supplier master-data consistency

**Document**

- Required invoice fields
- Invoice date
- Due date
- Document type
- Credit/debit note references

**Arithmetic**

- Line-item totals
- Discount calculations
- Taxable amount
- Tax totals
- Grand total
- Rounding

**Tax**

- Applicable GST components
- State and place-of-supply consistency
- HSN/SAC format
- Tax rate and amount consistency
- Conditional IRN/e-invoice checks

**Business**

- Supplier status
- Invoice age limits
- Expected currency
- Optional organization-specific restrictions

## 11.3 Finding model

```
ValidationFinding
  findingId
  invoiceVersionId
  validationRunId
  ruleId
  ruleVersion
  severity
  fieldPath
  actualValue
  expectedValue
  explanation
  resolutionStatus
  overrideReason
```

Severity:

- INFO
- WARNING
- BLOCKING

## 11.4 Rule governance

- Every rule has an identifier and version.
- Applicability considers document and transaction context.
- Regulatory rules use effective dates.
- Tenant policies may extend baseline rules.
- Policy changes must not silently alter historic approvals.
- Historical evaluation runs remain available.
- Rule overrides require permission and an auditable reason.

The rules engine need not use a heavyweight external DSL in P0. Prefer Java rule classes with explicit registration/configuration.

## 11.5 GST compliance boundary

Validate available invoice information against configured rules.

Do not claim:

- Government verification of GST registration without an authorized verification source.
- Legal validity of an IRN merely because it was extracted from a PDF.
- Full tax compliance solely from arithmetic checks.

Live GST portal verification and return reconciliation remain separate future integrations.

---

# 12. Supplier Resolution and Duplicate Detection

## 12.1 Supplier resolution

Matching order:

1. Exact tenant-scoped supplier reference
2. Exact GSTIN
3. Trusted supplier master-data identifier
4. Normalized name/address
5. Similarity-based candidate recommendation

Ambiguous results create a review finding.

Never automatically merge suppliers based only on fuzzy names.

## 12.2 Duplicate classes

**Binary duplicate**

Same content checksum within permitted tenant scope.

**Business identity duplicate**

Same resolved supplier, invoice reference, financial year, and appropriate document context.

**Suspected duplicate**

Similar invoice identity with related amounts/dates or document fingerprints.

## 12.3 Decision model

Results:

- NOT_DUPLICATE
- EXACT_DUPLICATE
- BUSINESS_DUPLICATE
- SUSPECTED_DUPLICATE
- MANUALLY_CLEARED

Store candidate explanations and linked invoice IDs.

## 12.4 Concurrency and approval

Perform authoritative business-identity uniqueness checks during approval eligibility and finalization.

Use a database-backed uniqueness reservation or approved-invoice identity ledger.

Controlled exceptional duplicates require a separately authorized, audited path.

Do not make every same-number invoice a hard duplicate without accounting for business context.

---

# 13. Purchase Order Reconciliation Engine

## 13.1 P0 matching type

**Two-way matching:** Invoice versus Purchase Order.

Goods receipts/three-way matching are P2.

## 13.2 Matching algorithm

1. Resolve supplier.
2. Find referenced PO.
3. Validate PO currency/legal entity.
4. Match invoice lines to PO lines.
5. Compare quantity.
6. Compare unit price.
7. Compare tax treatment.
8. Calculate line and header variance.
9. Apply configured tolerances.
10. Evaluate remaining quantities/amounts.
11. Create reconciliation findings.
12. Produce matching result.

## 13.3 Matching rules

Examples:

- Supplier GSTIN or trusted supplier ID agrees.
- Referenced PO exists.
- PO is eligible for invoicing.
- Invoice quantity is within remaining allowable quantity.
- Price difference is within configured tolerance.
- Tax information is consistent.
- Overall amount is within allowed tolerance.

Fuzzy item description matching may suggest candidate lines, but ambiguous candidates require review.

## 13.4 Outcomes

- MATCHED
- MATCHED_WITH_WARNINGS
- PARTIALLY_MATCHED
- MISMATCHED
- PO_NOT_FOUND
- REVIEW_REQUIRED

## 13.5 Financial allocation model

Matching and allocation are different actions.

**Matching** is an evaluation of a candidate invoice version.

**Allocation** records actual PO balance consumption.

Recommended P0 policy:

- During reconciliation, calculate proposed allocations.
- During final approval, atomically commit allocations.
- On approval failure, commit nothing.
- On authorized approval revocation before export, release allocations transactionally.
- Do not silently release or rewrite allocations for already exported records.

This prevents competing invoices from simultaneously consuming the same PO balance.

## 13.6 Concurrent allocation

Use a short database transaction:

1. Lock affected PO lines in stable ID order.
2. Read committed allocations.
3. Compute remaining balances.
4. Validate the current invoice version.
5. Insert committed allocation rows.
6. Finalize approval decision.
7. Commit.

Use pessimistic locking where necessary.

Do not rely on application-memory locks, Redis locks, or stale frontend values as the only consistency mechanism.

## 13.7 Policy versioning

Persist with every reconciliation:

- Invoice version
- PO version
- Matching rule version
- Tolerances applied
- Candidate matches
- Findings
- Decision timestamp

---

# 14. Review and Exception Workflow

## 14.1 Review task creation

Create review tasks when:

- Mandatory extracted fields are missing.
- Validation findings are blocking.
- Extraction is uncertain.
- Potential duplicates exist.
- Supplier identity is ambiguous.
- PO matching fails.
- A processing error requires human intervention.

## 14.2 Task states

- OPEN
- CLAIMED
- IN_REVIEW
- COMPLETED
- REOPENED
- CANCELLED

## 14.3 Task assignment

Support:

- Manual assignment
- Claim from queue
- Reassignment
- Role-based access
- Optional priority and SLA metadata

## 14.4 Correction flow

1. Reviewer opens the invoice version.
2. Reviewer inspects original evidence.
3. Reviewer edits allowed fields.
4. Backend checks expected version.
5. Changes are saved.
6. New invoice version/snapshot is established.
7. Relevant validation and matching are rerun.
8. Findings are refreshed.
9. Reviewer submits for approval.

Preserve an audit record of material corrections.

## 14.5 Optimistic locking

Use a version token or ETag for edits.

If two reviewers modify the same invoice:

- First valid update succeeds.
- Second update gets a conflict.
- The system returns current state/version.
- Frontend displays a comparison or refresh option.
- No automatic silent overwrite.

---

# 15. Approval Architecture

## 15.1 Core policy

One authorized approver must approve the current valid invoice version.

Approval eligibility requires:

- Correct tenant and legal entity
- Authorized approver
- Valid invoice version
- No unresolved mandatory blocking findings
- Acceptable duplicate disposition
- Valid reconciliation outcome or authorized exception resolution
- No conflicting existing approval/export state

## 15.2 Approval transaction

Within one short transaction:

1. Load and lock/version-check invoice.
2. Recheck authorization and eligibility.
3. Confirm submitted version is current.
4. Check approved-invoice business uniqueness.
5. Commit PO allocations when applicable.
6. Record approval decision.
7. Change approval state.
8. Insert audit and outbox records.
9. Commit.

Do not invoke OCR or external accounting APIs within this transaction.

## 15.3 Approval changes

For material edits after approval:

- Require an explicit revision/reopen operation.
- Preserve prior approval decision.
- Invalidate current eligibility.
- Release applicable unexported allocations transactionally.
- Revalidate and reapprove the new version.

For already exported invoices:

- Preserve the exported approved snapshot.
- Use controlled correction/supersession procedures.

---

# 16. Accounting Export Architecture

## 16.1 P0 export adapter

Define:

- `AccountingExportPort`
- `CsvExportAdapter`
- `ExportProfile`
- `ExportBatch`
- `ExportRecord`

## 16.2 Export process

1. User requests export.
2. Backend verifies tenant and permissions.
3. Validate all selected invoice versions are export-eligible.
4. Create an idempotent export batch.
5. Snapshot exact invoice version references and export profile.
6. Create background export job.
7. Worker generates CSV.
8. Apply field escaping and CSV formula-injection protection.
9. Store export artifact in S3.
10. Record checksum and successful export status.
11. Provide authorized download.

## 16.3 Export recovery

A worker may crash after file upload but before marking the database record successful.

Use a deterministic object key linked to the export batch and version, then recover by verifying an existing artifact and checksum.

Never create duplicate successful export records on retry.

## 16.4 Export guarantees

- Only approved versions are eligible.
- Exact exported versions are recorded.
- Export files are immutable.
- Re-export is explicit.
- Export failures are visible.
- All export actions are audited.

Do not claim delivery to an external accounting system for CSV-only export.

---

# 17. REST API Specification

Prefix APIs with `/api/v1`.

## 17.1 Identity and tenant

| Method | Endpoint                          | Purpose                        |
| ------ | --------------------------------- | ------------------------------ |
| GET    | `/me`                             | Current authenticated identity |
| GET    | `/organizations`                  | Authorized organizations       |
| POST   | `/organizations`                  | Create organization            |
| GET    | `/organizations/{id}`             | Organization details           |
| PATCH  | `/organizations/{id}`             | Update organization            |
| POST   | `/organizations/{id}/invitations` | Invite user                    |
| GET    | `/organizations/{id}/members`     | Membership listing             |

## 17.2 Ingestion

| Method | Endpoint                          | Purpose                  |
| ------ | --------------------------------- | ------------------------ |
| POST   | `/invoices/uploads`               | Create upload session    |
| POST   | `/invoices/uploads/{id}/complete` | Finalize verified upload |
| POST   | `/import-batches`                 | Create bulk import       |
| GET    | `/import-batches/{id}`            | Batch progress           |
| GET    | `/invoices/{id}/processing`       | Processing state         |
| POST   | `/invoices/{id}/reprocess`        | Authorized reprocessing  |

## 17.3 Invoice and review

| Method | Endpoint                             | Purpose                    |
| ------ | ------------------------------------ | -------------------------- |
| GET    | `/invoices`                          | Search/list invoices       |
| GET    | `/invoices/{id}`                     | Invoice details            |
| PATCH  | `/invoices/{id}`                     | Update permitted draft     |
| GET    | `/invoices/{id}/document`            | Authorized document access |
| GET    | `/invoices/{id}/validations`         | Validation findings        |
| GET    | `/invoices/{id}/duplicates`          | Duplicate candidates       |
| GET    | `/review-tasks`                      | Review queue               |
| POST   | `/review-tasks/{id}/claim`           | Claim task                 |
| POST   | `/review-tasks/{id}/complete`        | Complete review            |
| POST   | `/invoices/{id}/submit-for-approval` | Submit current version     |

## 17.4 Supplier and PO

| Method    | Endpoint                            | Purpose             |
| --------- | ----------------------------------- | ------------------- |
| GET/POST  | `/suppliers`                        | List/create         |
| GET/PATCH | `/suppliers/{id}`                   | Read/update         |
| POST      | `/suppliers/imports`                | Import supplier CSV |
| GET/POST  | `/purchase-orders`                  | List/create         |
| GET/PATCH | `/purchase-orders/{id}`             | Read/update         |
| POST      | `/purchase-orders/imports`          | Import POs          |
| GET       | `/purchase-orders/{id}/allocations` | PO allocations      |

## 17.5 Reconciliation and approval

| Method | Endpoint                                | Purpose            |
| ------ | --------------------------------------- | ------------------ |
| GET    | `/invoices/{id}/reconciliation`         | Current comparison |
| POST   | `/invoices/{id}/reconciliation-runs`    | Re-run matching    |
| POST   | `/reconciliation-findings/{id}/resolve` | Resolve finding    |
| GET    | `/approvals/pending`                    | Approval queue     |
| POST   | `/invoices/{id}/approve`                | Approve version    |
| POST   | `/invoices/{id}/reject`                 | Reject version     |

## 17.6 Export and reporting

| Method | Endpoint                      | Purpose               |
| ------ | ----------------------------- | --------------------- |
| POST   | `/exports`                    | Request export        |
| GET    | `/exports`                    | Export history        |
| GET    | `/exports/{id}`               | Export details        |
| GET    | `/exports/{id}/download`      | Authorized download   |
| GET    | `/dashboard/summary`          | Dashboard metrics     |
| GET    | `/analytics/processing`       | Processing trends     |
| GET    | `/notifications`              | User notifications    |
| GET    | `/invoices/{id}/audit-events` | Invoice audit history |

## 17.7 API standards

- JSON request/response for ordinary APIs
- OpenAPI documentation
- Strongly typed DTOs
- Bean Validation
- Server-side pagination
- Explicit sort allowlists
- Request correlation IDs
- Idempotency keys for applicable commands
- ETags or version preconditions for editable records
- Problem Details error responses
- UTC timestamps with explicit offsets
- LocalDate for invoice business dates
- ISO currency codes
- Decimal amounts serialized without floating-point loss

## 17.8 Error model

Recommended HTTP mapping:

| Status      | Usage                                              |
| ----------- | -------------------------------------------------- |
| 400         | Malformed request                                  |
| 401         | Authentication required                            |
| 403         | Insufficient permission                            |
| 404         | Missing or inaccessible resource                   |
| 409         | Business-state conflict or competing command       |
| 412         | Failed version/ETag precondition                   |
| 413         | File exceeds upload limit                          |
| 415         | Unsupported document type                          |
| 422         | Semantically invalid business input                |
| 429         | Rate or quota limit                                |
| 500         | Unexpected internal failure                        |
| 502/503/504 | Relevant upstream or service availability failures |

Do not expose stack traces or sensitive document contents in API errors.

---

# 18. Security and Identity Architecture

## 18.1 Authentication

Prefer managed OIDC authentication.

Possible choices:

- Amazon Cognito
- Keycloak for local/self-managed requirements

Use standards-compliant OIDC/OAuth flows appropriate to a browser application.

## 18.2 Authorization

Model permissions explicitly:

- `invoice.read`
- `invoice.upload`
- `invoice.edit`
- `invoice.review`
- `invoice.approve`
- `invoice.export`
- `supplier.manage`
- `purchaseorder.manage`
- `tenant.manage`
- `audit.read`

Use Spring Security method/request authorization, combined with resource-level tenant checks.

## 18.3 Document access

- Private buckets
- Tenant-scoped object references
- Short-lived signed access or authorized streaming
- No permanent public URLs
- Restrict access to source and export objects
- Revalidate permissions before issuing download access

## 18.4 Secrets

- No credentials in source control
- Managed secret storage
- IAM roles instead of static cloud credentials
- Secret rotation plan
- Sensitive-field log redaction

## 18.5 Data privacy

Maintain:

- Data inventory
- Purpose and processing records
- Retention/deletion policies
- Access logging
- Data export and erasure procedures where applicable
- Provider data-sharing contracts
- Incident handling procedures

Evaluate applicable DPDP provisions and GST/accounting record requirements with legal review before declaring compliance.

---

# 19. Reliability and Failure-Mode Engineering

| Failure                                | Required behavior                                     |
| -------------------------------------- | ----------------------------------------------------- |
| API restart during upload              | Upload session remains recoverable or expires safely  |
| Duplicate upload completion            | Same document accepted once for same intake operation |
| OCR timeout                            | Retry appropriate stage with backoff                  |
| OCR permanent rejection                | Terminal state plus visible error                     |
| Worker crash                           | Job remains recoverable                               |
| Duplicate SQS delivery                 | Idempotent stage execution                            |
| Out-of-order message                   | Reject stale stage/version                            |
| Missing supplier                       | Create review finding                                 |
| Invalid GST                            | Block unauthorized approval                           |
| Two reviewers edit invoice             | Version conflict, no lost update                      |
| Two invoices consume one PO            | Transactional allocation prevents overuse             |
| Approval repeated                      | Same idempotency scope avoids duplicate decisions     |
| Export worker crashes                  | Recover from persisted batch/artifact                 |
| Unauthorized tenant access             | Deny access                                           |
| Email provider redelivery              | Deduplicate event and attachments                     |
| Object storage temporarily unavailable | Retry or record recoverable failure                   |
| DLQ growth                             | Alert and operational investigation                   |

---

# 20. Observability and Operations

## 20.1 Structured logging

Fields:

- Timestamp
- Severity
- Service/runtime role
- Tenant reference
- Correlation ID
- Trace ID
- Job ID
- Invoice ID
- Workflow stage
- Error category

Do not log full invoice documents, raw OCR text, or sensitive credentials.

## 20.2 Metrics

**Ingestion**

- Uploaded documents
- Upload failures
- Batch success rate

**Processing**

- Queue depth
- Job age
- Extraction duration
- Extraction error rate
- Retry count
- DLQ count

**Business**

- Validation failure rate
- Duplicate candidate rate
- Reconciliation match rate
- Manual review rate
- Review age
- Approval turnaround
- Export success/failure

**Infrastructure**

- CPU/memory
- JVM heap and GC
- Database pool usage
- Query latency
- HTTP latency/error rate
- Worker concurrency
- External API latency

## 20.3 Tracing

Use correlation IDs across:

- API intake
- Database/outbox
- Queue events
- Workers
- External extraction calls
- Export generation

Use OpenTelemetry-compatible tracing.

## 20.4 Operational alerts

Alert on:

- Sustained processing backlog
- DLQ growth
- Unusual OCR failure rates
- Stuck processing stages
- Export error spikes
- Critical database capacity
- Auth/security anomalies
- Backup failures

## 20.5 Initial SLO targets

Proposed, not yet measured:

- API availability: 99.9% monthly
- Ordinary indexed read API p95: under 500ms at defined load
- Typical invoice processing: under two minutes when provider permits
- Zero silent loss of admitted documents
- Zero unauthorized cross-tenant access
- Recoverable background processing

Define measurement windows, exclusions, and load profiles during performance testing.

---

# 21. AWS Deployment Architecture

## Recommended production baseline

**Networking**

- AWS VPC
- Public/private subnets
- Load balancer
- Restricted security groups
- VPC endpoints where cost-effective

**Compute**

- ECS Fargate API service
- ECS Fargate worker service
- Independent task scaling

**Database**

- Amazon RDS PostgreSQL
- Automated backups
- Appropriate Multi-AZ configuration
- Connection pooling and monitoring

**Document storage**

- Amazon S3
- Private buckets/prefixes
- Encryption and lifecycle rules

**Messaging**

- Amazon SQS
- Dead-letter queue
- Outbox dispatcher

**Inbound email**

- Amazon SES receiving
- S3 storage
- SQS-driven ingestion

**Identity**

- Cognito or compatible OIDC provider

**Security and operations**

- KMS
- Secrets Manager
- CloudWatch
- OpenTelemetry-compatible collection
- WAF
- IAM least privilege

## Environment strategy

- Local
- Development
- Staging
- Production

Use separate configuration and credentials.

Do not share production financial data with lower environments.

## Local developer stack

Docker Compose:

- PostgreSQL
- S3-compatible local object storage
- Queue emulator or deterministic local job adapter
- Mail testing adapter
- Backend API
- Backend worker
- Next.js frontend

Provide an offline, deterministic end-to-end test mode.

Clearly distinguish fixture-based extraction from a verified real provider.

---

# 22. CI/CD and Engineering Quality

## Build pipeline

1. Checkout
2. Dependency validation
3. Formatting/static analysis
4. Unit tests
5. Architecture/module tests
6. Database integration tests
7. API contract tests
8. Frontend tests
9. Security/dependency scans
10. Build images
11. Integration/E2E tests
12. Publish artifacts
13. Deploy to approved environment

## Deployment safety

- Versioned Flyway migrations
- Backward-compatible database changes
- Health/readiness checks
- Rolling or canary deployments
- Safe rollback procedures
- Explicit schema compatibility window
- No breaking event/API changes without versioning

Use expand-and-contract migrations for schema changes affecting simultaneously deployed API and worker versions.

## Quality controls

- No business logic inside controllers
- No direct JPA entity exposure through APIs
- No tenant-unscoped queries
- No raw money calculations with floating point
- No unbounded retries
- No hidden asynchronous failures
- No external I/O inside long database transactions
- No unaudited financial overrides
- No export of unapproved versions
- No frontend-only authorization enforcement

---

# 23. Testing Specification

## Unit tests

Validate:

- GSTIN rules
- Currency arithmetic
- Tax calculation
- Duplicate comparisons
- PO matching
- Approval eligibility
- State transitions
- Export field formatting

## Integration tests

Use Testcontainers with PostgreSQL to test:

- Transactions
- Flyway migrations
- Referential integrity
- Tenant isolation
- RLS behavior
- Optimistic locking
- PO allocation concurrency
- Outbox transaction behavior
- Idempotent commands

## Provider contract tests

Validate:

- OCR adapter mapping
- Timeout and throttling classification
- Missing fields
- Line-item extraction
- Confidence/evidence mapping
- Email MIME handling
- Storage error behavior

## End-to-end tests

Test:

- Sign in and organization selection
- Upload invoice
- Process and extract
- Validate
- Review/correct
- Reconcile against PO
- Approve
- Export CSV
- View audit history

## Required adversarial scenarios

- Same invoice uploaded twice
- Same email delivered twice
- Two workers process same job
- Two reviewers edit the same version
- Concurrent approval requests
- Two invoices compete for PO balance
- Export retry after partial failure
- Cross-tenant resource ID guessing
- Malicious/oversized PDF
- Prompt-injection text inside invoice
- Corrupt ZIP
- Invalid GST and tax arithmetic
- Stale invoice version during approval

Clearly report tests executed versus tests requiring external provider credentials.

---

# 24. Engineering Delivery Roadmap

## Milestone M1 — Platform foundation

Deliver:

- Backend skeleton
- Modular architecture
- Tenant/authentication
- PostgreSQL/Flyway
- Security policies
- OpenAPI foundation
- Local development environment

**Exit gate:** Tenant isolation and authentication integration tests pass.

## Milestone M2 — Document ingestion

Deliver:

- Upload sessions
- Secure storage
- Email intake
- Bulk import
- Durable processing/outbox

**Exit gate:** Documents survive worker/API restarts and duplicate events.

## Milestone M3 — AI invoice processing

Deliver:

- OCR integration
- Extraction normalization
- Invoice/line persistence
- Source evidence
- GST validation
- Duplicate detection

**Exit gate:** Real provider smoke test and deterministic local tests pass.

## Milestone M4 — Supplier and PO matching

Deliver:

- Supplier management
- PO import and management
- Matching engine
- Partial invoicing
- Reconciliation findings
- PO allocation safeguards

**Exit gate:** Mismatch and concurrency tests pass.

## Milestone M5 — Review and approval

Deliver:

- Reviewer corrections
- Task assignment
- Workflow transitions
- Approval policy
- Versioning and audit trail

**Exit gate:** Full review/approval happy path and conflicts pass.

## Milestone M6 — Export and operational reporting

Deliver:

- CSV export
- Export recovery/idempotency
- Dashboard metrics
- Notifications
- Audit history

**Exit gate:** Full invoice-to-export E2E flow passes.

## Milestone M7 — Operational hardening

Deliver:

- Load testing
- Security verification
- Observability dashboards
- Backup/restore testing
- Deployment automation
- Runbooks
- Accessibility/API integration validation

**Exit gate:** Documented readiness review and known-risk register.

---

# 25. Architecture Acceptance Matrix

| Requirement         | Backend module                 | Primary integrity control                 |
| ------------------- | ------------------------------ | ----------------------------------------- |
| Multi-tenancy       | Identity/Tenancy               | Scoped authorization and RLS              |
| Upload              | Ingestion                      | Secure admission and idempotency          |
| Email intake        | Ingestion                      | Recipient mapping and event deduplication |
| AI extraction       | Document Processing            | Versioned results and adapter contract    |
| GST validation      | Tax Validation                 | Deterministic versioned rules             |
| Duplicate detection | Duplicate Detection            | Candidate evidence and uniqueness guard   |
| Supplier management | Supplier                       | Tenant-scoped master data                 |
| PO matching         | Reconciliation                 | Explainable comparison and policy         |
| PO allocation       | Purchase Orders/Reconciliation | Transactional locking                     |
| Human review        | Review                         | Version-aware edits                       |
| Approval            | Approvals                      | Authorization and business invariants     |
| Export              | Accounting Export              | Immutable approved snapshots              |
| Dashboard           | Reporting                      | Persisted operational aggregates          |
| Notifications       | Notifications                  | Authorized tenant-scoped delivery         |
| Audit               | Audit                          | Append-only business history              |

---

# 26. Production Readiness Checklist

Before production release, verify:

**Correctness**

- Every P0 workflow is functional.
- Financial arithmetic is deterministic.
- PO allocation cannot be corrupted through concurrency.
- Duplicate approvals and exports are prevented.

**Security**

- Tenant isolation has been penetration-tested.
- Object access is authorization-controlled.
- Secret exposure is prevented.
- Document upload defenses are active.
- AI processing respects data policies.

**Reliability**

- Worker restart recovery succeeds.
- Retry policies are bounded.
- DLQ handling is documented.
- Backup restore has been tested.
- Export recovery is idempotent.

**Operations**

- Dashboards and alerts exist.
- Runbooks exist.
- Deployment/rollback processes are tested.
- On-call ownership is defined.
- Cost visibility exists.

**Verification**

- Automated tests pass.
- External integration smoke tests pass.
- Load targets have measured evidence.
- Known risks are documented.
- No unsupported production-readiness claims are made.

---

# 27. Final Architectural Direction

Build one coherent financial processing platform with strongly separated domain modules.

The most critical engineering boundaries are:

1. **Untrusted documents versus trusted canonical data**
2. **AI extraction versus deterministic financial validation**
3. **PO matching suggestions versus committed financial allocations**
4. **Editable invoice drafts versus immutable approved/exported versions**
5. **Queue message delivery versus authoritative database state**
6. **User authentication versus tenant-scoped business authorization**
7. **Transient infrastructure failures versus actionable business exceptions**

Preserve these boundaries throughout implementation.

**Final execution directive:** Implement the P0 backend end-to-end, expose stable APIs for the premium dark-theme frontend, verify database invariants and failure recovery, and deliver a system whose financial decisions are explainable, auditable, and operationally safe.
