# Configuration and runbook

## Secrets and identity

See [.env.example](../.env.example). The local `.env` is ignored and should have owner-only permissions. Never place provider or storage keys in a `NEXT_PUBLIC_*` variable. Production secrets belong in a secret manager with short-lived workload credentials for S3.

The default sign-in flow uses BCrypt, HTTP-only JDBC sessions, SameSite=Lax cookies, session fixation protection and CSRF tokens. Set `SECURE_COOKIES=true` behind HTTPS. Authentication endpoints have a per-instance per-address 30/minute limit; production requires gateway enforcement and abuse monitoring across replicas. Password recovery and email verification delivery are not implemented. Prefer enterprise OIDC before onboarding real customers.

Optional enterprise OIDC: activate `SPRING_PROFILES_ACTIVE=oidc` and configure `OIDC_CLIENT_ID`, `OIDC_CLIENT_SECRET`, `OIDC_ISSUER_URI`, `PUBLIC_URL`. Start at `/oauth2/authorization/enterprise`. Email must be verified by the provider; identity authority is `(issuer, subject)`, not a mutable email. Existing password accounts are intentionally not linked automatically; explicit administrator-assisted linkage is required. Live OIDC has not been verified.

Create an organization, then create invitations under Organization & Team. Invitations expire after seven days, are stored hashed, and can only be accepted while signed in with the specified email. Share the returned token securely; automated invitation email delivery is not implemented. A user with no organization can accept it on onboarding; existing members can accept another invitation in Organization & Team.

## Live Azure extraction

### Fix “Extraction setup required” after upload

Both `AZURE_DOCUMENT_ENDPOINT` and `AZURE_DOCUMENT_KEY` must be non-empty real values. The endpoint alone is insufficient. Copy them from your Azure Document Intelligence resource's **Keys and Endpoint** page into the repository-root `.env` (or production secret manager). Never paste the key into chat, commit it, or put it in frontend variables. `AZURE_DOCUMENT_MODEL` is optional and defaults to `prebuilt-invoice`.

The endpoint must be the HTTPS resource root, such as `https://<resource-name>.cognitiveservices.azure.com`, not an Azure Portal URL or the `/documentintelligence/...` API route.

For local Maven development, stop the existing backend and run `./scripts/run-backend.sh` again. For Docker Compose, load the changed environment by recreating the backend:

```sh
docker compose up -d --build --force-recreate backend
```

`docker compose restart backend` does not reload `.env`. Open Settings → Extraction provider → Refresh provider status after restart. This reports local configuration readiness, not a successful live authentication test. Upload a real document to verify the configured resource.

Accepted documents remain **QUEUED** while configuration is missing or invalid; the worker does not fetch originals, call a provider or consume retry attempts. They resume when a configured backend starts. Documents that failed under the previous behavior retain their originals: select **Retry processing** in Upload Center after configuration, rather than uploading again.

If Settings says **TEST_MODE**, the backend was started with `test-fixtures`. Stop that test process and start the normal backend for real invoices; synthetic fixture extraction is never a substitute for a configured live provider.

1. Provision Azure AI Document Intelligence in an approved region.
2. Configure `AZURE_DOCUMENT_ENDPOINT` (HTTPS), `AZURE_DOCUMENT_KEY` and optionally `AZURE_DOCUMENT_MODEL` (default `prebuilt-invoice`).
3. Restart the backend. Upload a representative scanned multi-page invoice.
4. Inspect extraction run status, raw provider persistence, normalized values and field evidence. Record provider/model version and duration. Validate against a manually labelled ground truth before approving.

REST contract: `POST /documentintelligence/documentModels/{model}:analyze?api-version=2024-11-30`, followed by the same-host HTTPS `operation-location`. Polling and request timeouts are bounded. Official reference: https://learn.microsoft.com/en-us/azure/ai-services/document-intelligence/prebuilt/invoice

Prebuilt invoice extraction does not establish Indian transaction applicability or guarantee individual GST/HSN fields. Generic total tax is never divided into invented CGST/SGST. Unknown classification and missing fields block submission. A configured custom model may emit the full canonical field names from `InvoiceData`; evidence is retained. The adapter never accepts a provider-selected supplier UUID or lets provider content approve/export records. Missing confidence stays absent.

**Do not use test-fixture mode for live invoices.** That mode is a separate test-classpath application and will only accept PDFs marked with the synthetic test metadata. Its returned provider name explicitly says `TEST_DOUBLE`.

## Mailgun inbound email

Configure `MAILGUN_SIGNING_KEY` and `INTAKE_DOMAIN`. Create a Mailgun receiving route forwarding multipart attachments to the HTTPS `/api/v1/email/mailgun` endpoint. Route the exact organization alias shown in Upload Center; organization selection is never taken from the sender. Official contract: https://documentation.mailgun.com/docs/mailgun/user-manual/receive-forward-store/receive-http

The adapter validates HMAC-SHA256(timestamp + token), checks five-minute freshness, validates the recipient domain/alias and retains sender/subject/message ID/receipt time. Attachment request keys deduplicate repeated deliveries. Signed webhook verification is required even though the route is exempt from browser CSRF. No live Mailgun route was available for verification.

Known limitation: an unsupported attachment stops that webhook attempt; already accepted attachments remain durable and deduplicated on redelivery. Persisted per-attachment failure summaries and continuing past every email attachment failure need further work.

## Failure recovery

- **Scanner unavailable:** no document is accepted. Wait for ClamAV health/signatures and retry the same queued file. Never disable scanning in a customer environment to bypass an outage.
- **Upload interrupted:** retry using the same per-file idempotency key. Different bytes with the same key are rejected. Browser refresh currently loses files that were never accepted; accepted batches remain in PostgreSQL.
- **Provider not configured:** accepted documents stay queued without using attempts. Configure both Azure variables and restart/recreate the backend. Already-failed documents require Retry processing after setup.
- **Configured provider unavailable:** the worker records each attempted run, retries up to three attempts, then exposes FAILED with explanation. Fix credentials/connectivity and select Retry processing. Existing corrections remain unchanged.
- **Worker crash:** expired leases become claimable; fencing tokens prevent stale completions from overwriting a later attempt. Do not manually delete jobs to replay them.
- **Concurrent edits:** HTTP 412 retains the local unsaved draft. Copy/compare edits against the latest version before saving again.
- **PO contention:** failed approval commits neither decision nor allocation. Reconcile against current balances; amend an unallocated PO or correct the invoice. Do not override over-allocation.
- **Export failure:** retry the existing batch in Export History. Do not create another batch with a new key. CSV references remain fixed to approved versions.
- **Re-export:** select Previously exported and supply a meaningful business reason. The original batch and bytes remain preserved.

## Health and troubleshooting

`/actuator/health`, `/actuator/health/liveness`, `/actuator/health/readiness` expose basic application/database readiness. Storage/provider/scanner readiness is reported by actual operations; health does not certify provider connectivity. `X-Correlation-ID` is generated server-side and attached to request audit events. Job/run identifiers and persisted errors support worker troubleshooting. Logs omit document bytes, provider response bodies and secrets.

Inspect Compose with `docker compose ps` and `docker compose logs --tail=100 backend storage clamav`. The initial scanner image runs under amd64 emulation on this ARM Mac; use native production architecture and measured memory limits.

## Backup, retention and restore

- PostgreSQL: daily encrypted snapshots plus WAL/PITR. Include session, job, audit, version and export tables. Restrict restore credentials.
- S3: private bucket, TLS, SSE-KMS, versioning, lifecycle and access logging. Retention must preserve originals corresponding to retained invoice versions.
- Restore into an isolated environment. Restore database and bucket versions from a consistent recovery point, check every invoice object key exists, verify export checksums and reconcile pending/expired jobs before enabling workers.
- Do not restore a database while production workers continue consuming its jobs.
- No automatic invoice/audit deletion is implemented. Define statutory/contractual retention, legal holds and authorized erasure procedures before production.
- Orphan reconciliation: inventory objects against `invoices.storage_key`; investigate unreferenced objects older than the intake grace window. Never delete referenced documents automatically. Obtain reviewed approval for a destructive cleanup.

A restore drill and measured RPO/RTO remain pending.
