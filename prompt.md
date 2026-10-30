

Act as an elite cross-functional product engineering team combining the expertise of:

- Principal Software Architect
- Fintech Product Manager
- Enterprise Business Analyst
- Senior Java 21 / Spring Boot Engineer
- Senior Next.js / React / TypeScript Engineer
- Principal UI/UX Product Designer
- AI/Intelligent Document Processing Engineer
- PostgreSQL Data Architect
- Cloud and DevSecOps Architect
- QA Automation Engineer
- Security and Reliability Engineer

## Primary mission

**Design, develop, integrate, test, and deliver a complete AI-powered Invoice Processing & Reconciliation SaaS platform for Indian businesses.**

Build a genuinely useful commercial SaaS product—not a prototype consisting of attractive dashboards, disconnected API endpoints, or static mock data.

The final system must be:

1. Functionally complete within the defined MVP.
2. Visually exceptional, with an immersive, elegant, dark-theme interface.
3. AI-native, document-centric, and optimized for human review.
4. Secure, reliable, auditable, and multi-tenant.
5. Built using pragmatic Clean/Hexagonal Architecture.
6. Fully integrated across frontend, backend, AI services, database, and storage.
7. Tested using realistic end-to-end business scenarios.
8. Extensible without introducing unnecessary infrastructure complexity.

### Non-negotiable execution rules

- Inspect the repository before implementation.
- Respect existing architecture, dependencies, and coding conventions.
- If no application exists, initialize the recommended stack.
- Implement complete vertical slices, not isolated frontend mockups.
- Make all visible business actions functional.
- Use persisted backend data throughout the application.
- Do not fabricate operational metrics or AI confidence.
- Do not implement fake AI responses and label them as real extraction.
- Do not leave core MVP functionality behind TODOs.
- Prioritize correctness, usability, auditability, and data integrity.
- Execute relevant verification commands.
- Disclose anything not verified or requiring external credentials.
- Continue implementation beyond planning and scaffolding.

---

# 2. PRODUCT VISION

## Product positioning

Create an **AI-native finance operations platform** for Indian businesses that automates the invoice lifecycle from receipt to accounting export.

### Target customers

- Distributors and wholesalers
- Manufacturing companies
- Procurement and accounts payable teams
- CA and accounting firms
- Finance shared-service teams
- SMEs handling 500–10,000+ invoices monthly
- Businesses with multiple GST registrations or branches

### Core customer problems

Organizations currently struggle with:

- Manual invoice data entry
- Large numbers of inconsistent supplier invoice layouts
- Scanned invoices and poor-quality documents
- Inaccurate tax extraction
- Duplicate invoice recording
- Supplier identity inconsistencies
- GST calculation errors
- PO and invoice mismatches
- Slow manual approvals
- Missing or untraceable documents
- Difficult accounting exports
- Poor visibility into processing bottlenecks
- Lack of reliable audit trails
- Repetitive activities across multiple clients

### Product value proposition

**Convert incoming invoices into validated, reconciled, approval-ready accounting records with minimal manual work.**

The product should make users feel that AI has already completed the repetitive work and they only need to handle meaningful exceptions.

### Core workflow

Invoice Intake → Intelligent Classification → OCR/AI Extraction → Field Normalization → Supplier Resolution → GST Validation → Duplicate Detection → PO Matching → Exception Evaluation → Human Review → Approval → Accounting Export → Audit and Analytics

### Product differentiation

Develop around five principles:

**1. Exception-first processing**

Users should spend their time reviewing important discrepancies instead of verifying every correctly extracted field.

**2. Explainable AI**

Every AI recommendation must display the underlying evidence, confidence when available, and relevant validation or matching rule.

**3. Finance-specific intelligence**

Prioritize Indian GST, invoice arithmetic, supplier identity, tax breakdowns, and purchase order reconciliation.

**4. Operational simplicity**

Complex backend processes should appear as clear, intuitive actions.

**5. Trustworthy automation**

AI can assist with extraction, suggestions, and triage, but financial approval and export are governed by deterministic policies and explicit authorization.

---

# 3. PRODUCT SCOPE AND FEATURE PRIORITIZATION

Classify features into three delivery levels.

## P0 — Mandatory working MVP

All P0 requirements must work end-to-end:

- Tenant onboarding and authentication
- Organization and GST profile
- Multi-company access for authorized accounting firms
- Role-based authorization
- PDF, email, and bulk invoice ingestion
- Real AI document extraction
- Supplier and invoice field extraction
- Line-item extraction
- GST and arithmetic validation
- Supplier master-data management
- Exact and suspected duplicate detection
- Purchase order creation/import
- Two-way invoice-to-PO matching
- Exception detection and routing
- Human review workstation
- Approval and rejection workflow
- CSV accounting export
- Search and filtering
- Dashboard and processing metrics
- Audit trail
- Notifications within the application
- Secure document storage
- Resilient background processing
- Elegant, consistent dark-theme UI
- Automated testing and local deployment

## P1 — Enhanced product experience

Implement after P0 functionality is stable:

- AI-assisted exception explanations
- Suggested field corrections with evidence
- Supplier alias learning
- Smart review prioritization
- Batch review and bulk operations
- Configurable approval matrices
- Saved queue views
- Keyboard command palette
- Real accounting integration, prioritizing one validated target
- Advanced operational analytics
- Custom CSV export templates
- Advanced supplier analytics
- Reusable exception-resolution recommendations
- Workflow SLA management
- Customizable dashboard layouts
- Document splitting/merging review
- Configurable automation policies

## P2 — Future roadmap

Design for future extensibility, but do not prematurely implement:

- Three-way matching using goods receipts
- GSTR-2B reconciliation
- GST return filing integrations
- Multi-ERP integrations
- Automated accounting ledger coding
- Payment processing
- Bank reconciliation
- Contract/price-agreement matching
- Vendor self-service portal
- Forecasting and predictive analytics
- Natural-language finance analytics
- Advanced agentic workflows
- Mobile-native applications
- Multi-level complex workflow builders
- Usage-based subscriptions and billing
- Document model fine-tuning pipelines

**Important:** Never allow P1 or P2 features to delay the completion and verification of the entire P0 invoice-to-export workflow.

---

# 4. DETAILED FUNCTIONAL REQUIREMENTS

## FR-01 — Multi-Tenant SaaS and Organization Management [P0]

### Functional capabilities

- Create and configure organizations.
- Capture legal business name, trade name, GSTIN, PAN when appropriate, registered address, business state, and financial year.
- Support organizations with multiple authorized business units or GST registrations.
- Configure tenant-specific invoice settings.
- Invite users by email.
- Assign roles and control permissions.
- Support authorized accounting professionals managing multiple client organizations.
- Provide a secure organization switcher.
- Keep invoice, supplier, PO, review, export, and audit information tenant-isolated.
- Record organization creation and membership changes.

### Roles

- Platform Administrator
- Organization Administrator
- Finance Manager
- Accountant / Processor
- Reviewer
- Approver
- Read-Only Auditor

Implement an explicit role/permission matrix and enforce authorization on every backend operation.

### Acceptance criteria

An authenticated user can only access documents and business records belonging to organizations where they have valid membership and the required permissions.

---

## FR-02 — Intelligent Invoice Intake [P0]

### Intake channels

**Manual upload**

- Drag-and-drop file upload.
- Multiple-document selection.
- PDF and supported image files.
- File preview before submission.
- Upload progress.
- Batch upload status.
- Cancel before submission and safely retry failures.

**Email ingestion**

- Per-organization intake email or configured mailbox.
- Process invoice attachments.
- Preserve sender, subject, received timestamp, and message reference.
- Associate attachments with the correct tenant.
- Support multiple attachments.
- Deduplicate repeated provider events.
- Validate inbound webhook signatures and tenant routing.

**Bulk ingestion**

- Multi-file and optional ZIP import.
- Per-file validation.
- Batch progress summary.
- Partial success handling.
- Retry failed items independently.
- Export batch error summaries.

### Intelligent intake enhancements

- Detect unsupported/corrupt documents.
- Identify likely invoices versus unrelated attachments.
- Recognize credit notes and debit notes where supported.
- Detect possible multi-invoice PDFs and flag for manual separation.
- Identify rotated or low-quality pages.
- Indicate documents requiring extraction retry or review.

### UI requirements

Build a dedicated Upload Center featuring:

- Large, elegant drag-and-drop target
- Clearly labeled intake methods
- Upload queue with individual progress
- Processing activity timeline
- Visual stage indicators
- Success, warning, and failure states
- Retry actions
- Batch-level summary

### Acceptance criteria

A document uploaded through a supported channel is durably stored, assigned a unique reference, and processed asynchronously.

---

## FR-03 — AI Document Classification and Extraction [P0]

### Supported document types

- Tax invoices
- Standard purchase invoices
- Credit notes
- Debit notes
- Other supported invoice-related documents, with uncertain classifications routed to review

### Supplier extraction

- Legal/trade name
- GSTIN
- PAN when available
- Address
- State
- Contact details when available

### Buyer extraction

- Buyer name
- Buyer GSTIN
- Billing address
- Shipping address
- Place of supply

### Invoice extraction

- Invoice number
- Invoice date
- Due date
- Financial year
- PO reference
- Payment terms
- Invoice/document type
- Currency
- IRN when present
- E-invoice identifiers when present

### Financial extraction

- Gross amount
- Discounts
- Taxable value
- CGST rate and amount
- SGST/UTGST rate and amount
- IGST rate and amount
- Cess
- Additional charges
- Round-off
- Invoice grand total

### Line-item extraction

- Product/service description
- HSN/SAC
- Quantity
- Unit of measurement
- Unit price
- Discount
- Taxable amount
- Tax rate and type
- Tax amount
- Line total

### Extraction behavior

- Process scanned and searchable PDFs.
- Handle multi-page invoices.
- Support multiple line items.
- Normalize dates and financial amounts.
- Preserve raw and normalized extraction results.
- Preserve field source positions where supported.
- Report field confidence only if genuinely available.
- Track provider/model version.
- Capture processing duration and errors.
- Maintain extraction run history.
- Support explicit reprocessing.

### AI safeguards

- Never invent missing invoice fields.
- Mark unsupported extraction as unknown.
- Never silently replace human-corrected fields during reprocessing.
- Treat document content as untrusted input.
- Validate structured output against an explicit schema.
- Separate provider confidence from business-rule validity.
- Preserve source provenance.

### Architecture

Create:

- `DocumentExtractionPort`
- `DocumentClassificationPort` where justified
- Provider-specific infrastructure adapters
- `InvoiceExtractionNormalizer`
- Extraction result versioning
- Deterministic test fixtures

### Acceptance criteria

A real configured extraction provider can transform a scanned invoice into persisted structured invoice data visible in the frontend.

---

## FR-04 — AI Confidence and Source Evidence [P0/P1]

### Core features [P0]

- Show per-field extraction evidence where supported.
- Flag missing required values.
- Show uncertainty indicators where confidence is available.
- Display whether a value came from AI extraction or manual correction.
- Surface validation findings independently of confidence.

### Enhanced features [P1]

- Clicking an extracted field highlights its source area on the document.
- Selecting a source region focuses the corresponding field.
- Suggest likely corrections.
- Allow accept, reject, or manually edit suggestions.
- Display original and suggested values.
- Track correction history and field provenance.

### UX principle

**Make AI decisions inspectable, not mysterious.**

A confidence percentage is not proof that a financial value is valid.

---

## FR-05 — Indian GST and Financial Validation [P0]

Build a deterministic, explainable, configurable validation engine.

### GST rules

- GSTIN structure and checksum validation
- Supplier/buyer GSTIN requirements by document context
- State code consistency
- Intra-state versus inter-state tax context
- CGST/SGST versus IGST consistency
- Taxable value checks
- GST rate and tax amount calculations
- HSN/SAC format checks
- Round-off tolerance
- Credit/debit note sign and reference consistency
- Conditional e-invoice/IRN checks when applicable

Support exceptional legitimate cases such as unregistered suppliers, exempt supplies, and other relevant invoice classifications.

Do not hardcode regulatory assumptions that vary by transaction type, taxpayer status, or effective date.

### Arithmetic validation

Validate:

- Quantity × unit price
- Line discounts
- Taxable amount
- Per-line tax
- Total tax
- Header totals versus line totals
- Additional charges
- Final invoice amount

### Rule engine behavior

Each finding must contain:

- Rule ID and version
- Severity
- Field reference
- Actual value
- Expected value when determinable
- Explanation
- Resolution options
- Whether approval/export is blocked

### UI

Show validation outcomes in a contextual panel, grouped by severity and location.

Allow users to navigate directly from an error to the affected field.

### Acceptance criteria

Incorrect tax calculations or invalid required GST information prevent unreviewed approval/export and can be corrected through the review interface.

---

## FR-06 — Supplier Intelligence and Master Data [P0/P1]

### Core supplier capabilities [P0]

- Add, edit, search, and filter suppliers.
- Import suppliers through CSV.
- Validate relevant supplier identifiers.
- Store addresses and GST details.
- Link invoices and purchase orders to suppliers.
- Flag probable duplicate supplier records.
- Resolve extracted suppliers against master records.
- Require review when supplier identity is ambiguous.

### Enhanced capabilities [P1]

- Supplier aliases and common name variations
- Vendor matching suggestions
- Preferred invoice terminology
- Supplier-level processing trends
- Supplier correction history
- Supplier exception frequency
- Duplicate supplier merge workflow
- Configurable supplier-specific matching tolerances

### Supplier insights

Show:

- Invoice count
- Total processed value
- Exception rate
- Duplicate occurrence rate
- PO mismatch frequency
- Average processing time

Do not characterize calculated operational indicators as fraud probabilities.

---

## FR-07 — Duplicate and Anomaly Detection [P0/P1]

### Exact duplicate detection [P0]

- File SHA-256 checksum
- Supplier identity plus normalized invoice number
- Financial-year and document-type context
- Invoice amount and date verification

### Potential duplicate detection [P0]

- Similar supplier names
- Similar invoice numbers
- Same totals and nearby dates
- Matching PO reference
- Equivalent documents with different formatting

### Business behavior

- Distinguish exact duplicates from suspected duplicates.
- Show evidence for every match.
- Provide side-by-side comparison.
- Allow authorized false-positive resolution.
- Require a reason for overriding a blocking finding.
- Prevent silent duplicate export.
- Preserve original records and history.

### Enhanced anomaly detection [P1]

- Unusual invoice value compared with supplier history
- Repeated invoice-number patterns
- Unexpected tax variation
- Unusually frequent invoice submissions
- Changes in supplier identity data
- Repeated exception patterns

Present anomalies as review suggestions, not accusations of fraud.

---

## FR-08 — Purchase Order Management [P0]

### Functionality

- Create purchase orders.
- Import POs and line items from CSV.
- Maintain supplier references.
- Support PO amendments with version history where necessary.
- Search and filter POs.
- Display open and closed POs.
- Store ordered quantity, price, and tax.
- Track previously approved/invoiced allocations.
- Show remaining invoiceable balances.
- Record which invoices consume PO balances.

### PO screen

Provide:

- Searchable PO table
- Supplier details
- PO status
- Total and remaining value
- Ordered versus invoiced quantity
- Related invoice links
- Line-item details
- Discrepancy overview

---

## FR-09 — Invoice-to-PO Reconciliation [P0]

Implement two-way invoice-to-PO matching.

### Matching dimensions

- Supplier
- PO number
- Product identifier
- Item description
- Quantity
- Unit price
- Tax rate
- Line amount
- Invoice total
- Previously allocated quantities

### Matching categories

- Exact match
- Match within tolerance
- Partial match
- Missing PO
- Supplier mismatch
- Quantity mismatch
- Unit-price mismatch
- Tax mismatch
- Unmatched line item
- Over-invoicing
- Manual verification required

### Matching algorithm

1. Resolve the supplier.
2. Resolve explicit PO references.
3. Compare eligible PO lines.
4. Calculate differences using exact decimal arithmetic.
5. Apply versioned tolerance policies.
6. Check previously consumed quantities.
7. Classify results.
8. Generate explainable findings.
9. Route unresolved discrepancies to review.

Never use a free-form AI recommendation as the sole basis for financial allocation.

### Reconciliation UI

Create an interactive comparison workspace:

**Invoice values | PO values | Difference | Status | Resolution**

Include:

- Header matching summary
- Line-by-line comparison
- Visually emphasized discrepancies
- Expandable rule explanations
- Suggested corrections where reliable
- Manual resolution controls
- Linked original invoice and PO
- Totals comparison
- Remaining PO balance

### Acceptance criteria

A reviewer can identify a quantity/price mismatch, understand why matching failed, resolve it through an authorized action, and re-run reconciliation.

Concurrent processing must not over-allocate PO balances.

---

## FR-10 — AI-Powered Exception Management [P0/P1]

This is a central product differentiator.

### Exception categories [P0]

- Extraction uncertainty
- Missing field
- Invalid GST data
- Arithmetic discrepancy
- Potential duplicate
- Supplier mismatch
- Missing PO
- PO quantity mismatch
- PO price mismatch
- Export failure
- Processing failure

### Exception lifecycle

`OPEN → ASSIGNED → IN_REVIEW → RESOLVED / DISMISSED_WITH_REASON`

### Features [P0]

- Central exception queue
- Exception type and severity
- Linked invoice
- Assigned reviewer
- Creation date
- Status
- Evidence and recommended next action
- Resolution history

### Smart triage [P1]

Prioritize review based on:

- Blocking severity
- Invoice amount
- Processing age
- Due date
- SLA
- Number of unresolved issues
- Explicit business priority

### Intelligent assistance [P1]

For each exception, display:

**What happened?**

A concise explanation.

**Why was this flagged?**

The relevant rule, extracted source data, or matching discrepancy.

**What can I do?**

Safe, actionable resolution choices.

**What happens next?**

The downstream workflow outcome.

AI-generated explanations must be marked as suggestions and must not override deterministic rule findings.

---

## FR-11 — Human Review Workstation [P0]

Build an exceptional, document-centric reviewer experience.

### Workspace layout

Use a resizable, multi-panel layout:

**Document Viewer | Extracted Data Editor | Validation/AI Insights**

Allow users to resize or collapse contextual panels.

### Document viewer

- Multi-page PDF rendering
- Zoom
- Page navigation
- Fit-to-width
- Rotate view
- Full-screen view
- Source highlights where available
- Stable scrolling
- Keyboard navigation

### Data editing

- Invoice summary
- Supplier and buyer fields
- Financial/tax fields
- Editable line-item table
- Required-field markers
- Validation messages
- Unsaved-change indicators
- Save changes
- Revalidate
- Reconcile again

### Intelligent reviewer assistance

- Prioritize unresolved fields.
- Highlight required actions.
- Allow direct navigation to an error.
- Show source evidence.
- Show original versus corrected values.
- Preserve reviewer notes.
- Display audit activity.

### Productivity features

- Next unresolved field
- Previous unresolved field
- Save
- Save and continue
- Submit for approval
- Keyboard shortcuts with documented help
- Confirmation of critical actions

### Critical UX behavior

Preserve unsaved edits, detect concurrent modifications, and avoid silently overwriting another reviewer’s changes.

### Acceptance criteria

A reviewer completes the entire correction and submission workflow from one screen without navigating between unrelated pages.

---

## FR-12 — Approval Workflow [P0/P1]

### Core approval [P0]

- Submit invoice for approval.
- Assign an authorized approver.
- Approve or reject with reason.
- View approval history.
- Block approval on unresolved mandatory validations.
- Prevent unauthorized status transitions.
- Prevent export before required approval.
- Invalidate approval after material data changes.

### Configurable policies [P1]

Examples:

- Approval by amount threshold
- Supplier-specific approver
- Department-based approval
- Sequential approvers
- Delegation
- Escalation when overdue

### Separation of duties

Support a configurable policy preventing the preparer from approving the same invoice when required.

### Approval UX

Show:

- Invoice summary
- Validation status
- PO match status
- Outstanding issues
- Prior reviewers
- Approve/reject actions
- Comments
- Decision history

---

## FR-13 — Invoice Lifecycle and State Management [P0]

Model lifecycle transitions explicitly.

Recommended processing states:

`RECEIVED → QUEUED → EXTRACTING → VALIDATING → MATCHING → PROCESSED`

Separate processing lifecycle from business workflow:

`NEEDS_REVIEW → IN_REVIEW → READY_FOR_APPROVAL → APPROVED → EXPORTED`

Include appropriate failed, rejected, duplicate, archived, and superseded outcomes.

Avoid forcing extraction, approval, and export concerns into one ambiguous status field.

Requirements:

- Valid state transitions only
- Transactional guards
- Idempotent retries
- Version-aware edits
- State-transition audit events
- Explicit failure explanations

---

## FR-14 — Accounting Export [P0/P1]

### Core export [P0]

- Approved invoice export to CSV
- Single and bulk export
- Configurable field mapping profile
- Export preview
- Export history
- Download
- Failure tracking
- Safe retry
- Explicit re-export with audit reason

### Financial integrity

- Export only approved document versions.
- Preserve record-to-export references.
- Prevent accidental duplicate export.
- Record precise export timestamps.
- Ensure exported totals match the approved snapshot.
- Protect against CSV formula injection.

### Enhanced integration [P1]

Implement one supported accounting platform adapter, prioritizing TallyPrime or Zoho Books after confirming viable official integration contracts.

Use `AccountingExportPort` and an explicit integration adapter.

Do not call a generic CSV download a completed direct ERP integration.

---

## FR-15 — Finance Operations Dashboard [P0]

Build an operational command center, not just a gallery of KPI cards.

### Primary KPIs

- Total invoices received
- Processed invoices
- Pending review
- Pending approval
- Approved invoices
- Exported invoices
- Failed invoices
- Duplicate candidates
- PO mismatches
- Invoice financial value
- Average processing duration
- Automation rate

### Dashboard sections

**A. Operational overview**

- KPI cards
- Processing trend
- Invoice volume
- Current queue health

**B. Action center**

- High-priority exceptions
- Pending approvals
- Failed processing
- Oldest unresolved documents

**C. Reconciliation intelligence**

- PO matching success
- Most common mismatch types
- Supplier-related issues

**D. Recent activity**

- Latest uploaded invoices
- Recent approvals
- Recent exports
- Processing failures

### Dashboard behavior

- Date filtering
- Supplier filtering
- Organization selection
- Drill-down from KPI to filtered list
- Appropriate loading/empty/error states
- No invented financial figures
- Clear metric calculation definitions

### Enhanced analytics [P1]

- Trends by supplier
- Reviewer productivity
- Processing bottlenecks
- SLA compliance
- Extraction provider costs
- Manual intervention rate
- Finance operation efficiency

---

## FR-16 — Search and Advanced Filtering [P0/P1]

### Core search [P0]

Search by:

- Invoice number
- Supplier name
- GSTIN
- PO number
- Date
- Status
- Amount range
- Upload source

Implement server-side filtering, pagination, and sorting.

### Enhanced search [P1]

- Saved filter presets
- Personal queue views
- Multi-condition filters
- Filter chips
- Search history where appropriate
- Export filtered results
- Command-palette navigation

Do not expose cross-tenant results through search endpoints.

---

## FR-17 — Notifications and Activity Center [P0/P1]

### Core [P0]

Notify users of:

- Invoice assigned for review
- Approval requested
- Invoice rejected
- Processing failure
- Export failure
- Important unresolved exceptions

Provide:

- In-app notification center
- Read/unread state
- Direct navigation to related records
- Tenant-safe notification delivery

### Enhanced [P1]

- Email notifications
- Notification preferences
- Daily summary
- SLA reminders
- Digest frequency controls

Avoid notification overload and duplicate events.

---

## FR-18 — Audit Trail and Governance [P0]

Record:

- Document upload
- Extraction/reprocessing
- Data corrections
- Supplier resolution
- Duplicate decisions
- PO reconciliation
- Exception overrides
- Review assignment
- Approval/rejection
- Export
- Settings changes
- User permission changes

For important events capture:

- Actor
- Tenant
- Timestamp
- Action
- Target resource
- Before/after summary when appropriate
- Business justification
- Correlation ID

Audit records must be immutable through normal application operations.

Provide a readable activity timeline on invoice details.

---

## FR-19 — Settings and Automation Policies [P0/P1]

### Core settings [P0]

- Organization profile
- GST profile
- Financial year
- User management
- Invoice number handling
- Extraction provider configuration
- Required fields
- Tax rounding tolerance
- PO matching tolerance
- Review routing thresholds
- Export profile

### Enhanced policies [P1]

- Supplier-specific rules
- Approval matrices
- SLA thresholds
- Automated routing conditions
- Saved workflow templates
- Policy change history
- Policy testing against sample invoices

Policy changes must not silently rewrite historical approval/export decisions.

---

## FR-20 — AI Review Assistant [P1]

Provide a contextual AI assistant inside the invoice review and exception workspaces.

### Supported questions

Examples:

- Why was this invoice flagged?
- Which fields require review?
- Why did PO matching fail?
- What is the difference between the invoice and PO total?
- Which GST values are inconsistent?
- Show the evidence for the extracted invoice number.

### Requirements

- Ground answers in current invoice data and available source documents.
- Clearly distinguish observations from suggestions.
- Show source references when available.
- Do not fabricate missing information.
- Do not approve/export invoices autonomously.
- Do not permit AI to bypass authorization.
- Apply tenant-aware context isolation.
- Prevent prompt injection from invoice content.
- Keep external model data exposure configurable and documented.

The assistant must augment existing workflows, not become a prerequisite for processing an invoice.

---

# 5. IMMERSIVE DARK THEME — DESIGN SYSTEM

**This is a mandatory, first-class implementation requirement.**

The platform must feel like a premium enterprise fintech application with an AI-native identity.

## 5.1 Design vision

Create a visual experience described by:

- Immersive
- Sophisticated
- Minimal
- Elegant
- Intelligent
- Contemporary
- Clean
- Precise
- Professional
- Effortless to navigate
- Premium without unnecessary decoration

The design should evoke a modern financial operations command center.

The interface must prioritize readability, user control, and task completion.

**Do not create a generic admin template with a dark background.**

## 5.2 Theme specification

Use a carefully controlled dark palette.

Suggested design tokens:

| Token              | Suggested value |
| ------------------ | --------------- |
| App background     | `#0B101B`       |
| Sidebar background | `#0E1523`       |
| Primary surface    | `#141D2C`       |
| Elevated surface   | `#1A2637`       |
| Hover surface      | `#243246`       |
| Primary border     | `#29374B`       |
| Primary text       | `#F1F5F9`       |
| Secondary text     | `#B1BED0`       |
| Muted text         | `#94A3B8`       |
| Primary accent     | `#5EEAD4`       |
| Accent hover       | `#2DD4BF`       |
| Secondary accent   | `#A78BFA`       |
| Success            | `#4ADE80`       |
| Warning            | `#FBBF24`       |
| Error              | `#FB7185`       |
| Information        | `#60A5FA`       |

Treat values as starting tokens. Validate contrast for their actual foreground/background combinations.

Use semantic design tokens rather than hardcoding colors throughout components.

### Color discipline

- Use dark graphite and navy for most surfaces.
- Use cyan/teal sparingly for primary actions and active states.
- Use violet for AI-related identity or insights.
- Use green for success.
- Use amber for attention.
- Use red/coral for errors and destructive operations.
- Do not apply the same bright accent to every dashboard element.
- Never communicate status with color alone.

## 5.3 Layering and depth

Create visual depth through:

- Subtle surface elevation
- Thin borders
- Consistent radius
- Soft shadows where useful
- Limited background gradients
- Restrained highlights
- Carefully managed whitespace

Avoid:

- Excessive glassmorphism
- Strong neon glow
- Oversaturated gradients
- Heavy drop shadows
- Distracting animated backgrounds
- Unnecessary 3D effects
- Large empty decorative panels

The application should feel immersive because it is coherent and responsive, not because every element animates.

## 5.4 Typography

Use a modern, readable sans-serif typeface such as Geist or Inter, with an appropriate fallback stack.

Typography principles:

- Strong but restrained headings
- Clear hierarchy
- Readable body text
- Tabular numeric formatting for money and counts
- Consistent invoice identifier formatting
- Distinct labels and editable values
- No tiny unreadable secondary text
- Use `₹` and Indian number formatting appropriately

Example financial formatting:

`₹12,45,678.90`

Do not use giant typography for routine financial tables.

## 5.5 Spacing and layout

Use a consistent spacing scale based on 4px or 8px increments.

Recommended:

- Compact form controls for finance workflows
- Comfortable dashboard card padding
- Consistent content gutters
- Standardized dialog widths
- Predictable section spacing
- Responsive grid layouts
- Configurable table density

The visual language must remain consistent across all modules.

## 5.6 Component system

Build reusable components for:

- Application shell
- Sidebar navigation
- Breadcrumbs
- Page headers
- KPI cards
- Data tables
- Status badges
- Financial amount cells
- Validation findings
- Confidence/evidence indicators
- Tabs
- Accordions
- Dialogs
- Drawers
- Toast notifications
- Search fields
- Date/amount filters
- Form controls
- File upload zones
- Document preview panels
- Resizable split panels
- Empty states
- Loading skeletons
- Processing progress
- Activity timelines
- Confirmation actions

Use shared tokens and variants consistently.

---

# 6. APPLICATION NAVIGATION AND INFORMATION ARCHITECTURE

Create a highly navigable application shell.

## Sidebar navigation

Recommended structure:

**Workspace**

- Overview
- Invoice Inbox
- Upload Center
- Review Queue
- Reconciliation
- Approvals

**Management**

- Suppliers
- Purchase Orders
- Exports

**Intelligence**

- Analytics
- Exceptions
- Activity/Audit

**Administration**

- Organization
- Team and Permissions
- Automation Rules
- Settings

Visibility must depend on permissions.

### Sidebar UX

- Persistent desktop sidebar
- Collapsible compact mode
- Clear active navigation state
- Contextual unread/pending counts
- Tooltips in collapsed mode
- Accessible keyboard navigation
- Mobile/tablet drawer behavior

## Top navigation

Include:

- Current organization switcher
- Breadcrumb/context
- Global search entry point
- Notifications
- Help
- User profile menu

## Global command palette [P1]

Use a keyboard shortcut such as `Cmd/Ctrl + K`.

Capabilities:

- Navigate application screens
- Search invoices and suppliers
- Open review queue
- Start upload
- Locate purchase orders
- Trigger authorized contextual actions

Keyboard shortcuts must not conflict with normal data entry and assistive technologies.

---

# 7. PAGE-BY-PAGE UX REQUIREMENTS

## Screen 1 — Sign In and Onboarding

Visual direction:

- Clean centered authentication panel
- Elegant gradient or subtle illustration on a limited surface
- Clear product identity
- Minimal distractions
- Accessible forms
- Strong error handling

Onboarding should guide users through:

1. Create organization
2. Configure GST/business details
3. Invite users
4. Import suppliers/POs where relevant
5. Upload first invoice

Display progress without making optional onboarding steps mandatory.

## Screen 2 — Overview Dashboard

Layout:

- Page title and contextual date filter
- Primary action: Upload Invoices
- Compact KPI cards
- Processing activity trend
- Review and approval queues
- Reconciliation summary
- Recent invoices
- Important exceptions

Every KPI should link to a meaningful filtered view.

Avoid excessive charts without actionable purpose.

## Screen 3 — Invoice Inbox

Build a data-dense but visually calm table.

Columns:

- Selection
- Invoice number
- Supplier
- Invoice date
- Amount
- Processing status
- Validation status
- PO match
- Review assignee
- Actions

Include:

- Search
- Filters
- Sorting
- Pagination
- Bulk selection
- Row detail access
- Saved views [P1]
- Table density control [P1]

## Screen 4 — Upload Center

Features:

- Large drag-and-drop region
- Multi-file queue
- Upload method switcher
- Per-file progress
- Clear error messages
- Document processing timeline
- Batch history
- Retry
- Navigation to completed invoices

Use subtle visual transitions to communicate progress.

## Screen 5 — Invoice Detail

Tabs or structured sections:

- Overview
- Extracted Data
- Validation
- Reconciliation
- Document
- Activity

Show a persistent summary:

- Supplier
- Invoice number
- Amount
- Date
- Business status
- Processing status

## Screen 6 — AI Review Studio

This should be the most refined and productivity-optimized screen.

Recommended desktop layout:

**Left: Document Canvas**

- PDF rendering
- Page navigation
- Zoom
- Source highlights

**Center: Data Editor**

- Supplier details
- Invoice header
- Tax information
- Editable line items
- Totals

**Right: Intelligence and Exceptions**

- Validation findings
- Confidence/evidence
- PO mismatches
- Duplicate candidates
- Review notes
- AI assistance [P1]

Allow panels to resize and collapse.

Add a sticky action bar:

- Save Draft
- Revalidate
- Reconcile
- Submit for Approval

Avoid losing the document context during editing.

## Screen 7 — Reconciliation Studio

Use a highly legible comparison interface.

Show:

- Linked invoice and PO
- Match summary
- Amount/quantity differences
- Line-item matching
- Remaining PO balance
- Exception details
- Resolution actions

Use aligned columns and clear numerical differences.

## Screen 8 — Approval Center

Features:

- Pending approval list
- Priority indicators
- Invoice summaries
- Validation and PO status
- Approval history
- Approve/reject with confirmation
- Comments
- Permission-aware controls

## Screen 9 — Suppliers and POs

Build clean master-data management interfaces:

- Searchable tables
- Details drawer/page
- Create/edit forms
- Import dialog
- Import validation
- Related records
- Activity history

## Screen 10 — Analytics

Design an operational intelligence dashboard:

- Processing trend
- Automation metrics
- Exception distribution
- Supplier performance
- Review turnaround
- Reconciliation success
- Estimated processing costs

All chart values must derive from backend data.

## Screen 11 — Settings

Use organized sections:

- Organization
- Members and Roles
- Invoice Rules
- GST Configuration
- Matching Tolerances
- Extraction Provider
- Export Profiles
- Notification Preferences
- Audit and Retention

Show explicit save feedback and validation.

---

# 8. MICROINTERACTIONS AND UX QUALITY

Use animations to explain state changes and preserve orientation.

Examples:

- Subtle button hover and press feedback
- Sidebar active-state transitions
- Smooth drawer and dialog transitions
- Upload progress transitions
- Skeleton-to-content appearance
- Table loading indicators
- Highlighting the field associated with an error
- Brief success feedback after saving
- Smooth panel resizing where performant

Recommended motion:

- Fast hover feedback
- Short transitions, generally around 120–250 ms
- Ease-out motion for panel entry
- Respect `prefers-reduced-motion`

Avoid:

- Constant animations
- Excessive bounces
- Persistent pulsing elements
- Large moving gradients
- Slow page transitions
- Motion that disrupts document review

## Accessibility

Target WCAG 2.2 AA.

Implement:

- Keyboard access to all functionality
- Proper semantic controls
- Screen-reader labels
- Visible focus states
- Appropriate text contrast
- Non-color status indicators
- Accessible dialogs and drawers
- Predictable focus management
- Meaningful form error messages
- Support for zoom and text enlargement
- Accessible table controls
- Reduced-motion support
- Appropriate contrast for focus, borders, and meaningful icons

Do not sacrifice usability for visual minimalism.

## UX state completeness

Every screen and component must account for:

- Initial loading
- Loading more
- Empty data
- No search results
- Validation errors
- Authorization errors
- Network failures
- Partial success
- Success
- Retry
- Stale or conflicting data
- Expired session
- No permission to perform an action

No business action may become a dead-end interaction.

---

# 9. RECOMMENDED TECHNOLOGY STACK

## Frontend

- Next.js
- React
- TypeScript in strict mode
- Tailwind CSS
- shadcn/ui-style accessible components
- Radix-based primitives where appropriate
- TanStack Query
- TanStack Table
- React Hook Form
- Zod
- PDF.js
- A lightweight maintained icon library
- Playwright for E2E testing

Use one coherent design and state-management strategy.

## Backend

- Java 21
- Current compatible Spring Boot version
- Spring Security
- Spring Data JPA / Hibernate
- PostgreSQL
- Flyway
- Jakarta Bean Validation
- Spring Actuator
- OpenAPI
- JUnit 5
- Mockito
- Testcontainers
- Micrometer / OpenTelemetry integration

## Storage and processing

- S3-compatible object storage
- Durable job/queue processing
- PostgreSQL-backed transactionally durable state
- One real Document AI/OCR integration
- One real inbound email integration
- Optional Redis when justified
- Docker Compose
- AWS deployment reference

Verify dependency compatibility against official documentation rather than choosing arbitrary latest versions.

---

# 10. CLEAN / HEXAGONAL ARCHITECTURE

Use a **modular monolith with well-defined domain modules**.

Recommended modules:

- Identity and Tenancy
- Invoice Ingestion
- Document Processing
- Supplier Management
- GST and Financial Validation
- Duplicate Detection
- Purchase Order Management
- Reconciliation
- Review and Exceptions
- Approvals
- Accounting Export
- Notifications
- Reporting and Audit

## Layering

**Domain**

- Entities
- Value objects
- Domain policies
- Invariants
- Domain events

**Application**

- Use cases
- Commands and queries
- Ports
- Transaction orchestration
- Authorization-aware application services

**Infrastructure**

- Persistence adapters
- Storage adapters
- OCR adapters
- Email adapters
- Queue/job adapters
- Export adapters

**API**

- Controllers
- DTOs
- Request validation
- Authentication/authorization
- Error translation

Business logic must not depend on frontend components, persistence frameworks, or external OCR SDKs.

Use dependency inversion without unnecessary abstractions.

## Data model

At minimum consider:

- Tenant
- OrganizationProfile
- UserMembership
- Supplier
- SupplierAlias
- Invoice
- InvoiceLine
- InvoiceDocument
- InvoiceVersion
- ExtractionRun
- ValidationFinding
- DuplicateCandidate
- PurchaseOrder
- PurchaseOrderLine
- POAllocation
- ReconciliationRun
- ReconciliationFinding
- ReviewTask
- ExceptionCase
- ApprovalDecision
- ExportBatch
- ExportRecord
- IngestionBatch
- ProcessingJob
- Notification
- AuditEvent
- AutomationPolicy

Add entities only when needed to implement the associated requirements.

## Transactional integrity

- Explicit transaction boundaries
- Optimistic locking for invoice edits
- Idempotency for ingestion, reprocessing, and export
- Transaction-safe PO allocations
- Database uniqueness constraints
- Tenant-scoped indexes
- Safe retries
- No external OCR calls inside open DB transactions
- Durable scheduling after document acceptance

Use `BigDecimal` for money and intentional rounding rules.

---

# 11. ASYNCHRONOUS DOCUMENT PROCESSING

Implement a durable processing pipeline.

1. Receive document.
2. Validate input and security constraints.
3. Store document.
4. Persist ingestion record and work item.
5. Extract and classify.
6. Normalize structured data.
7. Resolve supplier.
8. Validate financial/GST fields.
9. Detect duplicates.
10. Match PO.
11. Compute exception/review requirements.
12. Persist processing outcome.
13. Notify the frontend and assigned users where relevant.

### Failure handling

- Idempotent worker execution
- Timeouts
- Bounded retries
- Backoff and jitter
- Terminal failure state
- Safe manual reprocessing
- Durable event/work state
- Correlation identifiers
- Observable processing history

Use at-least-once delivery assumptions and protect side effects against duplicates.

Do not claim exactly-once delivery across external systems.

---

# 12. FRONTEND AND BACKEND CONTRACTS

Create versioned APIs and an OpenAPI definition.

Core endpoint groups:

- `/api/v1/organizations`
- `/api/v1/users`
- `/api/v1/invoices`
- `/api/v1/invoices/uploads`
- `/api/v1/import-batches`
- `/api/v1/invoices/{id}/processing`
- `/api/v1/invoices/{id}/validations`
- `/api/v1/invoices/{id}/duplicates`
- `/api/v1/invoices/{id}/reconciliation`
- `/api/v1/review-tasks`
- `/api/v1/exceptions`
- `/api/v1/approvals`
- `/api/v1/suppliers`
- `/api/v1/purchase-orders`
- `/api/v1/exports`
- `/api/v1/dashboard`
- `/api/v1/notifications`
- `/api/v1/audit-events`
- `/api/v1/settings`

Define operation-specific routes and HTTP methods as appropriate.

## Contract requirements

- Typed request/response models
- Consistent error envelope / Problem Details
- Validation errors mapped to fields
- Pagination, filters, and sort contracts
- Tenant authorization
- Permission requirements
- Idempotency behavior
- Resource-version preconditions
- Async status tracking
- Upload/download mechanisms
- Explicit state-transition endpoints

Generate or validate frontend TypeScript types from OpenAPI.

Every UI action must correspond to an implemented backend use case.

---

# 13. SECURITY, PRIVACY, AND RELIABILITY

## Security

- Standards-based identity integration
- Secure sessions or token management
- Server-side authorization
- Per-tenant access enforcement
- Secure file permissions
- Restricted object storage
- Encryption in transit and at rest
- Secret management
- CSRF protection where applicable
- XSS/SQL injection prevention
- Signed email webhook verification
- Rate limiting
- Malware scanning/quarantine strategy
- PDF and ZIP safety controls
- Audit logging
- Secure data retention/deletion
- Prompt-injection defenses
- Explicit AI-provider data handling policies

Evaluate current applicable Indian privacy and financial record requirements using authoritative sources and document compliance assumptions.

## Reliability

- Safe processing recovery
- No silent document loss
- Idempotent operations
- Concurrency-safe updates
- Retryable background tasks
- Health and readiness checks
- Database backup/restore procedures
- Provider failure handling
- Audit-safe state changes
- Export consistency

## Performance targets

Initial engineering targets:

- Support 1,000–10,000 invoices monthly per target tenant.
- Process bulk batches containing at least 100 documents.
- Use bounded worker concurrency.
- Keep ordinary indexed read API p95 latency under 500 ms at a documented test load.
- Target typical small-invoice processing within two minutes when provider performance permits.
- Keep tables paginated and query-efficient.
- Measure frontend interaction responsiveness on representative hardware.

Treat these as targets until load tests establish results.

---

# 14. SIX WELL-ARCHITECTED PILLARS

Follow the AWS Well-Architected Framework.

## Operational Excellence

- CI/CD
- Health checks
- Observability
- Runbooks
- Traceability
- Automated migration and deployment

## Security

- Authentication
- Authorization
- Tenant isolation
- Encryption
- Secrets
- Auditability
- Secure AI and document handling

## Reliability

- Durable processing
- Failure recovery
- Idempotency
- Backups
- Transaction integrity
- Retry policies

## Performance Efficiency

- Async workers
- Efficient queries
- Indexes
- Connection pooling
- Bounded concurrency
- Efficient frontend rendering

## Cost Optimization

- Modular-monolith deployment
- Cost-effective OCR usage
- Avoid redundant extraction
- Storage lifecycle controls
- Track provider consumption
- Right-sized infrastructure

## Sustainability

- Avoid duplicate processing
- Efficient batch execution
- Controlled resource use
- Storage optimization
- Demand-based scaling

Provide a brief implementation review for each pillar, including risks and trade-offs.

---

# 15. END-TO-END BUSINESS ACCEPTANCE SCENARIOS

All P0 workflows must be verified.

### Scenario A — Happy path

Upload invoice → extract → validate → resolve supplier → match PO → approve → export CSV → inspect audit history.

### Scenario B — GST validation error

Upload invoice with incorrect GST calculation → show blocking finding → correct values → revalidate → approve.

### Scenario C — Duplicate invoice

Upload the same invoice twice → identify duplicate → compare evidence → prevent accidental duplicate export.

### Scenario D — PO mismatch

Upload invoice exceeding ordered quantity → show line-level difference → create exception → authorized resolution.

### Scenario E — Human review

Extract uncertain supplier/amount → route to review → display source → correct → save → revalidate.

### Scenario F — Bulk processing

Upload 100 invoices → independently process → show completed and failed documents → retry failed items.

### Scenario G — Extraction outage

Simulate provider timeout → bounded retry → persisted error → safe recovery → no duplicate side effects.

### Scenario H — Multi-tenant security

Attempt cross-tenant invoice, PDF, supplier, PO, and export access → deny every unauthorized request.

### Scenario I — Approval authorization

Unprivileged user attempts direct API approval → reject.

### Scenario J — Concurrent updates

Two reviewers edit the same invoice → stale update detected → no silent overwrite.

### Scenario K — PO allocation concurrency

Two invoices concurrently consume remaining PO quantity → prevent over-allocation.

### Scenario L — Export failure

Simulate failure while preparing export → record failure → safely retry → avoid duplicate successful export.

### Scenario M — Dark-theme usability

Verify contrast, focus visibility, keyboard operation, responsive layouts, and meaningful errors on every critical workflow.

### Scenario N — AI provenance

Select an extracted field → display available extraction source evidence → save correction → preserve original and corrected values.

### Scenario O — Audit traceability

Inspect invoice history → identify who uploaded, edited, reviewed, approved, and exported the final document version.

---

# 16. TESTING STRATEGY

## Backend testing

- Domain unit tests
- GST validation tests
- Amount arithmetic tests
- Duplicate detection tests
- Reconciliation tests
- State-transition tests
- RBAC tests
- Tenant isolation tests
- Database integration tests
- Concurrency tests
- Retry and idempotency tests
- Export consistency tests

## Frontend testing

- Component rendering
- Form validation
- Table interactions
- Upload progress and errors
- Document review
- Approval controls
- Async state updates
- Keyboard accessibility
- Dark-theme visual consistency
- Responsive behavior

## E2E testing

Use Playwright or an equivalent maintained framework.

Test actual integrated workflows with real backend persistence.

Use deterministic synthetic data for local repeatability.

Include scanned invoices and a configured real extraction-provider smoke test when credentials are available.

## UI visual quality verification

Capture and inspect representative application screenshots.

Verify:

- Consistent layout
- No overflow or clipping
- Appropriate spacing
- Readable contrast
- Valid financial formatting
- Smooth responsive behavior
- Clear action hierarchy
- Correct dark-mode states
- Accessible form interactions

Avoid marking UI work complete based solely on successful compilation.

---

# 17. IMPLEMENTATION PLAN

## Phase 1 — Foundation and Design System

- Inspect repository.
- Choose compatible versions.
- Configure frontend/backend.
- Create modular architecture.
- Configure PostgreSQL and migrations.
- Establish authentication and tenants.
- Implement core dark-theme tokens and components.
- Build responsive application shell.

## Phase 2 — Ingestion and Processing

- Upload Center
- Secure file storage
- Durable processing
- Batch ingestion
- Email ingestion
- Real extraction integration
- Status reporting

## Phase 3 — Invoice Intelligence

- Extraction normalization
- Structured persistence
- Supplier resolution
- GST validation
- Duplicate detection
- Source evidence
- Invoice Inbox and detail views

## Phase 4 — Purchase Orders and Reconciliation

- Supplier and PO management
- PO import
- Two-way matching
- Partial allocation
- Exceptions
- Reconciliation UI

## Phase 5 — Human Review and Approval

- Review workstation
- Correction workflow
- Revalidation
- Review tasks
- Approval/rejection
- Audit history

## Phase 6 — Export and Dashboard

- CSV export
- Export history
- Dashboard aggregates
- Operational reporting
- Notifications
- Search and filters

## Phase 7 — Quality and Deployment

- Integration and E2E tests
- Accessibility evaluation
- Security checks
- UI visual review
- Performance tests
- Docker Compose
- Deployment configuration
- Documentation

## Phase 8 — Enhanced Features [P1]

After P0 passes:

- AI review assistance
- Smart triage
- Saved views
- Command palette
- Advanced analytics
- Configurable approval routing
- Accounting platform integration

Do not implement P1 enhancements at the expense of unfinished P0 workflows.

---

# 18. REQUIREMENT TRACEABILITY

Maintain a traceability matrix:

`Feature ID → Business Requirement → User Role → UI Screen → Backend Use Case → API Endpoint → Database Entity → Authorization Rule → Test Case → Status`

For each P0 requirement, specify:

- Completion status
- Implementation location
- Test evidence
- Known limitations

Do not mark a feature as completed when only its UI or API skeleton exists.

---

# 19. DEFINITION OF DONE

A feature is complete only if:

1. Business behavior is implemented.
2. Frontend interaction works.
3. Backend API works.
4. Real state is persisted.
5. Validation and authorization are enforced.
6. Error paths are handled.
7. Relevant automated tests pass.
8. UI is responsive and accessible.
9. Business actions have clear feedback.
10. Auditability is maintained where needed.

The P0 platform is complete only when a user can:

**Sign in → Select organization → Upload real invoice → Process with configured AI → Review extracted information → Validate GST → Detect duplicates → Match against PO → Resolve exceptions → Approve → Export CSV → View audit and dashboard history.**

All of this must work within one coherent dark-theme application.

---

# 20. REQUIRED DELIVERABLES

Deliver:

- Integrated Next.js frontend
- Java 21 / Spring Boot backend
- PostgreSQL database
- Flyway migrations
- Secure multi-tenant authentication
- Document ingestion
- Real AI extraction adapter
- Validation engine
- Duplicate detection
- Supplier and PO management
- Reconciliation engine
- Human review studio
- Approval workflows
- Exception management
- Accounting CSV export
- Operational dashboard
- Notifications
- Audit logging
- Elegant dark-theme design system
- Typed REST API integration
- OpenAPI specification
- Docker Compose environment
- Automated tests
- Synthetic India-specific invoice fixtures
- Deployment documentation
- Architecture diagrams
- README and operating instructions

Deliver a working application—not only a high-level specification.

---

# 21. FINAL EXECUTION INSTRUCTIONS

Begin by inspecting the repository and identifying its current state.

Then:

1. Establish architecture boundaries and implementation priorities.
2. Define the business domain and API contracts.
3. Build the application shell and design system.
4. Implement complete backend and frontend vertical slices.
5. Integrate AI and external services.
6. Complete all P0 business workflows.
7. Run tests and repair failures.
8. Inspect the rendered UI and correct visual defects.
9. Verify tenant isolation and financial integrity.
10. Produce final documentation and verification evidence.

### Final engineering principles

- Elegant and immersive, but never cluttered.
- Innovative, but never unpredictable.
- AI-native, but always explainable.
- Automated, but financially controlled.
- Scalable, but not overengineered.
- Secure, reliable, and auditable.
- Visually refined and operationally efficient.

### FINAL DIRECTIVE

**Implement the entire P0 AI Invoice Processing & Reconciliation SaaS platform end-to-end, including the premium dark-theme frontend, fully integrated Java backend, AI extraction, GST validation, PO reconciliation, human review, approvals, accounting export, audit trail, and automated verification.**

**The result must look and behave like a professionally engineered enterprise SaaS product—not an AI-generated admin dashboard or a disconnected proof of concept.**

## What improved from the original

| Area             | Enhancement                                                                           |
| ---------------- | ------------------------------------------------------------------------------------- |
| Functional scope | Expanded from 12 core requirement groups to 20 feature groups                         |
| AI capabilities  | Source evidence, field provenance, guided corrections, contextual assistance          |
| Reconciliation   | Dedicated comparison studio and detailed exception-resolution workflows               |
| UI/UX            | Explicit dark-theme design system, page architecture, interactions, and accessibility |
| Productivity     | Review queues, keyboard workflows, smart triage, contextual actions                   |
| Engineering      | Clearer transactional boundaries, processing states, idempotency, and tenant security |
| Delivery         | P0/P1/P2 prioritization, complete vertical slices, traceable acceptance criteria      |

