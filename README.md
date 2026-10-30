# Kaizen — Invoice Processing & Reconciliation

A Java 21 / Spring Boot and Next.js finance workspace for Indian businesses. The repository now contains a working persisted invoice-to-export path, a dark document-review interface, PostgreSQL migrations, private S3 document storage, real provider adapters, and automated tests.

**Delivery status:** the integrated workflow is verified using explicitly labelled synthetic extraction. This is not yet a production-certified or fully accepted P0 release. Live Azure extraction, Mailgun delivery and enterprise OIDC require configuration and separate verification. See [traceability](docs/TRACEABILITY.md) for remaining feature limitations and [verification](docs/VERIFICATION.md) for executed checks.

## Start locally

Requirements: Java 21, Docker with Compose, Node.js 24, and outbound access for initial dependency/image downloads. Ports 3107, 8187, 5432, 9000 and 3310 must be free.

1. Copy [.env.example](.env.example) to `.env`. Replace database and storage placeholders with random local secrets. Never commit `.env`.
2. Start the full stack:

   ```sh
   docker compose up --build -d
   ```

3. Open http://127.0.0.1:3107, create an account (minimum 12-character password), and create your organization. No default credentials or seeded business metrics exist.
4. Configure Azure credentials in `.env` for live extraction. The provider sends missing or unsupported values to review; it does not fabricate GST amounts or confidence.
5. Add suppliers and a PO, upload a PDF/PNG/JPEG, correct findings, submit to an invited approver, approve and export CSV.

ClamAV may need time to download signatures on first startup. Intake fails closed until the scanner is available. S3 is emulated by LocalStack **only in local Compose**; production should use private AWS S3. MinIO's tested registry locations denied access, so they are not used in the delivered Compose file.

## Develop without rebuilding containers

```sh
docker compose up -d db storage clamav
./scripts/run-backend.sh
```

In a second terminal:

```sh
cd frontend
npm ci
npm run dev
```

The helper reads `.env` and uses `JAVA_HOME`; on this Mac it detects the installed JDK 21 location if `JAVA_HOME` is unset. The frontend proxies `/api`, `/oauth2` and the OIDC callback to the backend. Cookies remain HTTP-only; mutations require CSRF tokens.

## Verify

```sh
cd backend
./mvnw verify
```

Backend tests use a disposable real PostgreSQL container. Storage/extraction/export fault injection is explicit in the test configuration.

```sh
cd frontend
npm ci
npm run typecheck
npm run lint
npm run build
npx playwright install chromium
```

For the repeatable browser workflow, start infrastructure, then run the **test-classpath-only** backend instead of the normal backend:

```sh
./scripts/run-backend.sh test-fixtures
```

Run `npm run dev` and `npm test` from `frontend`. The test creates synthetic users/organizations, uploads the scanned [fixture](fixtures/synthetic-scanned-invoice.pdf), processes it through a clearly labelled test adapter, reviews it, approves it as a second user, downloads CSV and inspects audit history. Production JARs do **not** contain this adapter. Do not start this mode for real documents.

Generate API declarations from a running backend with `npm run contracts`. The financial editor's types derive from the generated schema; a backend test asserts decimal-string wire/schema compatibility. OpenAPI is available at http://127.0.0.1:8187/v3/api-docs. Disable public OpenAPI in a hardened deployment if required.

## What's included

- Organization onboarding, multiple memberships, invitations tied to recipient email, role checks, business registrations, CSRF-protected JDBC sessions and optional OIDC.
- Multi-file upload with independent retry keys, content/page/size validation, ClamAV admission, private S3 originals, durable leased PostgreSQL jobs and bounded retries.
- Azure Document Intelligence REST adapter, raw extraction history, canonical normalization, original field evidence and preserved manual versions.
- Exact-decimal GST/arithmetic validation; supplier resolution; document/business/suspected duplicate findings; two-way PO comparison; atomic approval allocations.
- Review studio with PDF navigation/zoom/rotation, field and line editing, unsaved-change protection, stale-version conflicts, notes and version history.
- Assigned approvals, configurable separation of duties, explicit reopening, immutable approved snapshots, recoverable CSV export, preview/history/download and audited re-export reasons.
- Persisted dashboard, server-side invoice filters, supplier/PO CSV imports, exceptions, notifications, audit and policy settings.

## Documentation

- [Architecture and security boundaries](docs/ARCHITECTURE.md)
- [Configuration, provider setup and operations](docs/OPERATIONS.md)
- [Requirement traceability and remaining work](docs/TRACEABILITY.md)
- [Executed verification and limitations](docs/VERIFICATION.md)
- [AWS deployment and Well-Architected review](docs/DEPLOYMENT.md)
- [Regulatory and provider assumptions](docs/ASSUMPTIONS.md)
- [Implementation decisions](docs/IMPLEMENTATION.md)

The original [product requirements](prompt.md), [backend specification](backend-engineering-specification.md) and [UI specification](ui-ux-desing-specification.md) remain unchanged.
