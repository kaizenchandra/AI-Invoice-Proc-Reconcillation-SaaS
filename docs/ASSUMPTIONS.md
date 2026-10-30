# Regulatory and provider assumptions

This application performs deterministic document checks. It does not establish legal GST registration, confirm IRNs with a government registry, file GST returns, or certify tax/privacy compliance.

## Indian invoice rules

- Financial years are April–March, derived from the invoice date and represented as `YYYY-YYYY`.
- GSTIN validation checks structure, state-code range and base-36 checksum. It is not an active-registration lookup.
- Registered, unregistered, exempt, export and SEZ contexts are explicit reviewed values. Provider omission is not evidence of exemption.
- Intra-state comparisons expect CGST and SGST/UTGST with matching amounts; inter-state/SEZ/export comparisons do not accept CGST/SGST mixed into IGST. Unusual tax contexts need professional review.
- HSN/SAC checks validate supported 4/6/8-digit formatting only. Correct classification, mandatory digit length by turnover and effective-date exemptions are not certified.
- IRN format is checked only when applicability is explicitly confirmed. Turnover thresholds and legal applicability are not inferred from a PDF.
- Credit notes use negative quantity/tax/amount convention and a required original invoice reference. Positive-face-value credit notes require explicit normalization by a reviewer. Automated reversal of an exported PO allocation is not implemented; credit notes referencing POs are blocked pending a controlled adjustment workflow.
- Money uses BigDecimal; tax calculations use HALF_UP to two decimals and versioned organization tolerance. Generic provider totals are not split into invented GST components. CSV protects formula-leading values, including signed numbers, as spreadsheet-safe text.

Authoritative legal sources to validate with the organization's advisor before rollout:

- CBIC GST Acts and Rules: https://cbic-gst.gov.in/gst-acts.html and https://cbic-gst.gov.in/gst-rules.html
- GST e-invoice portal: https://einvoice.gst.gov.in/
- India Code: https://www.indiacode.nic.in/

These links identify the authoritative sources; no current legal applicability review or regulatory certification was completed in this implementation session. Do not hardcode a universal e-invoice mandate based on an old threshold.

## Privacy and records

Confirm applicable DPDP commencement dates/rules, purposes, notices, processors, region transfers, breach handling, erasure exceptions and grievance processes using current MeitY publications: https://www.meity.gov.in/data-protection-framework

Confirm GST/accounting statutory retention and audit/legal holds against the current law, including the organization's filing periods. No automatic deletion policy is enabled. Store provider contracts and retention choices with the tenant's operational governance before using real invoices.

## Provider disclosure

Azure receives original uploaded document bytes at the configured endpoint. Configure region, service permissions, retention and contract settings outside this application. Raw extraction results and normalized values remain in PostgreSQL, with original bytes in private S3. Keys never reach the frontend. The application does not send unrelated tenant records or execute instructions found in documents.

Mailgun handles inbound email and attachments under its configured regional service contract. DNS, domain ownership, signing credentials and receiving routes must be verified separately. Invitation token creation does not claim email delivery.

## Dependency/license assumptions

PDF.js is served locally from its pinned npm package with upstream license headers retained. ClamAV signatures and image must be kept current. The UI uses a DM Sans CSS font request with local system-font fallback; deployments requiring no external font requests should self-host the font or remove the import.
