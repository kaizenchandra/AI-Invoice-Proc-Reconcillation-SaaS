"use client";
import { useState, useEffect, useRef } from "react";
import { useQuery } from "@tanstack/react-query";
import {
  ArrowLeft,
  Save,
  RefreshCw,
  Send,
  Plus,
  Trash2,
  FileText,
  PanelLeftClose,
  ShieldCheck,
  Check,
  GitCompareArrows,
  ScanLine,
  History as HistoryIcon,
  AlertTriangle,
} from "lucide-react";
import { api, post, money, human, date } from "@/lib/api";
import type {
  Invoice,
  InvoiceData,
  Line,
  Paged,
  Supplier,
  Member,
} from "@/lib/types";
import { useWorkspace } from "./workspace";
import { ErrorBox, Loading, Badge, Field, ViewLink, Modal, Empty } from "./ui";
import { DocumentViewer } from "./document-viewer";
import { Timeline } from "./operations";
const headers: [keyof InvoiceData, string, string?][] = [
  ["invoiceNumber", "Invoice number"],
  ["invoiceDate", "Invoice date", "date"],
  ["dueDate", "Due date", "date"],
  ["financialYear", "Financial year"],
  ["poNumber", "Purchase order"],
  ["paymentTerms", "Payment terms"],
  ["currency", "Currency"],
  ["originalInvoiceNumber", "Original invoice reference"],
  ["irn", "IRN"],
];
const supplierFields: [keyof InvoiceData, string][] = [
  ["supplierName", "Supplier name"],
  ["supplierGstin", "Supplier GSTIN"],
  ["supplierPan", "PAN"],
  ["supplierState", "Supplier state"],
  ["supplierAddress", "Supplier address"],
  ["supplierContact", "Supplier contact"],
];
const buyerFields: [keyof InvoiceData, string][] = [
  ["buyerName", "Buyer name"],
  ["buyerGstin", "Buyer GSTIN"],
  ["billingAddress", "Billing address"],
  ["shippingAddress", "Shipping address"],
  ["placeOfSupply", "Place of supply (state code)"],
];
const totals: [keyof InvoiceData, string][] = [
  ["grossAmount", "Gross amount"],
  ["discount", "Discount"],
  ["taxableAmount", "Taxable value"],
  ["cgst", "CGST"],
  ["sgst", "SGST / UTGST"],
  ["igst", "IGST"],
  ["cess", "Cess"],
  ["charges", "Other charges"],
  ["roundOff", "Round-off"],
  ["total", "Grand total"],
];
const lineFields: [keyof Line, string][] = [
  ["code", "Product code"],
  ["description", "Description"],
  ["hsn", "HSN / SAC"],
  ["quantity", "Quantity"],
  ["unit", "Unit"],
  ["price", "Unit price"],
  ["discount", "Discount"],
  ["taxableAmount", "Taxable value"],
  ["taxRate", "Tax rate %"],
  ["cgst", "CGST"],
  ["sgst", "SGST"],
  ["igst", "IGST"],
  ["cess", "Cess"],
  ["total", "Line total"],
];
export function ReviewStudio({ id }: { id: string }) {
  const { tenant, can, notify, user } = useWorkspace();
  const [supplierSearch, setSupplierSearch] = useState("");
  const q = useQuery({
    queryKey: [tenant, "invoice", id],
    queryFn: () => api<Invoice>("/invoices/" + id, tenant),
    refetchInterval: 15000,
  });
  const suppliers = useQuery({
    queryKey: [tenant, "suppliers", supplierSearch],
    queryFn: () =>
      api<Paged<Supplier>>(
        "/suppliers?q=" + encodeURIComponent(supplierSearch),
        tenant,
      ),
  });
  const members = useQuery({
    queryKey: [tenant, "members"],
    queryFn: () =>
      api<Member[]>("/organizations/" + tenant + "/members", tenant),
  });
  const [draft, setDraft] = useState<InvoiceData | null>(null),
    [base, setBase] = useState(0),
    [dirty, setDirty] = useState(false),
    [note, setNote] = useState(""),
    [busy, setBusy] = useState(false),
    [error, setError] = useState<unknown>(),
    [tab, setTab] = useState("fields"),
    [showDoc, setShowDoc] = useState(true),
    [selected, setSelected] = useState("invoiceNumber"),
    [confirm, setConfirm] = useState(""),
    [reason, setReason] = useState(""),
    [assignee, setAssignee] = useState(""),
    [candidate, setCandidate] = useState("");
  const dirtyRef = useRef(dirty);
  useEffect(() => {
    dirtyRef.current = dirty;
  }, [dirty]);
  useEffect(() => {
    if (q.data && !dirtyRef.current) {
      setDraft({ ...q.data.data, lines: q.data.data.lines || [] });
      setBase(q.data.version);
    }
  }, [q.data]);
  useEffect(() => {
    function before(e: BeforeUnloadEvent) {
      if (dirtyRef.current) {
        e.preventDefault();
        e.returnValue = "";
      }
    }
    function link(e: MouseEvent) {
      const anchor = (e.target as HTMLElement).closest("a");
      if (
        dirtyRef.current &&
        anchor?.href &&
        !window.confirm(
          "You have unsaved changes. Leave this invoice without saving?",
        )
      ) {
        e.preventDefault();
        e.stopPropagation();
      }
    }
    window.addEventListener("beforeunload", before);
    const navigate = (event: Event) => {
      if (
        dirtyRef.current &&
        !window.confirm(
          "You have unsaved changes. Switch organization without saving?",
        )
      )
        event.preventDefault();
    };
    window.addEventListener("kaizen:before-navigate", navigate);
    document.addEventListener("click", link, true);
    return () => {
      window.removeEventListener("beforeunload", before);
      window.removeEventListener("kaizen:before-navigate", navigate);
      document.removeEventListener("click", link, true);
    };
  }, []);
  if (q.isPending) return <Loading />;
  if (!q.data) return <ErrorBox error={q.error} retry={() => q.refetch()} />;
  const invoice = q.data;
  if (!draft) return <Loading />;
  const editable =
    can("edit") &&
    !["APPROVED", "EXPORTED"].includes(invoice.business_state) &&
    !["QUEUED", "EXTRACTING"].includes(invoice.processing_state);
  const findings = invoice.evaluation.findings;
  const activeEvidence = invoice.extractions.find((e) => e.evidence)
    ?.evidence?.[selected];
  function setField(key: keyof InvoiceData, value: string) {
    setDraft((d) => (d ? { ...d, [key]: value || null } : d));
    setDirty(true);
  }
  function focus(field: string) {
    setTab("fields");
    setSelected(field);
    setTimeout(() => {
      document
        .getElementById("field-" + field)
        ?.scrollIntoView({ behavior: "smooth", block: "center" });
      document.getElementById("field-" + field)?.focus();
    }, 60);
  }
  async function save() {
    setBusy(true);
    setError(undefined);
    try {
      const result = await api<Invoice>("/invoices/" + id, tenant, {
        method: "PATCH",
        body: JSON.stringify({ version: base, data: draft, note }),
      });
      setDirty(false);
      setDraft(result.data);
      setBase(result.version);
      notify("Draft saved. Validation and reconciliation updated.");
      await q.refetch();
    } catch (e) {
      setError(e);
    } finally {
      setBusy(false);
    }
  }
  async function action(name: string) {
    setBusy(true);
    setError(undefined);
    try {
      if (name === "duplicate") {
        await post(`/invoices/${id}/duplicates/${candidate}/clear`, tenant, {
          version: base,
          reason,
        });
      } else if (name === "reprocess") {
        await post("/invoices/" + id + "/reprocess", tenant);
      } else
        await post("/invoices/" + id + "/" + name, tenant, {
          version: base,
          reason,
          assignee: assignee || null,
        });
      notify(
        name === "submit"
          ? "Invoice submitted to the assigned approver."
          : name === "approve"
            ? "This invoice version is now approved."
            : "Invoice updated.",
      );
      setConfirm("");
      setReason("");
      await q.refetch();
    } catch (e) {
      setError(e);
    } finally {
      setBusy(false);
    }
  }
  function field([key, label, type]: [keyof InvoiceData, string, string?]) {
    const value = draft?.[key];
    return (
      <Field label={label} key={key}>
        <input
          id={"field-" + key}
          type={type || "text"}
          value={typeof value === "string" ? value : ""}
          disabled={!editable}
          onFocus={() => setSelected(key)}
          onChange={(e) => setField(key, e.target.value)}
          aria-invalid={findings.some((f) => f.field === key && f.blocking)}
        />
      </Field>
    );
  }
  return (
    <div className="review-workspace">
      <div className="review-heading">
        <ViewLink view="review" className="text-link">
          <ArrowLeft size={16} />
          Back to queue
        </ViewLink>
        <div className="review-title">
          <div>
            <p className="eyebrow">
              DOCUMENT REVIEW / VERSION {invoice.version}
            </p>
            <h1>{invoice.invoice_number || invoice.filename}</h1>
            <p>
              {invoice.data.supplierName || "Supplier awaiting review"}
              <span>·</span>
              {money(invoice.data.total)}
            </p>
          </div>
          <div className="button-row">
            <Badge value={invoice.processing_state} />
            <Badge value={invoice.business_state} />
            <button className="button" onClick={() => setShowDoc(!showDoc)}>
              <PanelLeftClose size={16} />
              {showDoc ? "Hide document" : "Show document"}
            </button>
          </div>
        </div>
      </div>
      <ErrorBox error={error || q.error} />
      {invoice.error && (
        <div className="notice">
          <AlertTriangle size={18} />
          <span>{invoice.error}</span>
          {can("upload") && invoice.processing_state === "FAILED" && (
            <button
              className="button"
              disabled={busy}
              onClick={() => action("reprocess")}
            >
              Retry processing
            </button>
          )}
        </div>
      )}
      {invoice.version !== base && dirty && (
        <div className="error-box" role="alert">
          Another reviewer saved a new version. Your unsaved draft is preserved;
          copy your edits and reload before saving.
        </div>
      )}
      <div className={`review-layout ${showDoc ? "" : "document-hidden"}`}>
        {showDoc && (
          <div className="source-pane">
            <div className="pane-label">
              <FileText size={16} />
              Source document<span>Original</span>
            </div>
            <DocumentViewer tenant={tenant} id={id} mime={invoice.mime} />
          </div>
        )}
        <section className="editor-pane">
          <div className="tabs">
            <button
              className={tab === "fields" ? "active" : ""}
              onClick={() => setTab("fields")}
            >
              <ScanLine size={16} />
              Extracted data
            </button>
            <button
              className={tab === "match" ? "active" : ""}
              onClick={() => setTab("match")}
            >
              <GitCompareArrows size={16} />
              PO comparison
            </button>
            <button
              className={tab === "history" ? "active" : ""}
              onClick={() => setTab("history")}
            >
              <HistoryIcon size={16} />
              History
            </button>
          </div>
          {tab === "fields" ? (
            <div className="invoice-form">
              <div className="form-section-heading">
                <h2>Invoice details</h2>
                <span className={dirty ? "amber" : "muted"}>
                  {dirty ? "Unsaved changes" : "Saved version " + base}
                </span>
              </div>
              <div className="form-grid">
                {headers.map(field)}
                <Field label="Document type">
                  <select
                    id="field-documentType"
                    disabled={!editable}
                    value={draft.documentType || ""}
                    onChange={(e) => setField("documentType", e.target.value)}
                  >
                    <option value="">Confirm document type</option>
                    {[
                      "TAX_INVOICE",
                      "PURCHASE_INVOICE",
                      "CREDIT_NOTE",
                      "DEBIT_NOTE",
                    ].map((v) => (
                      <option key={v} value={v}>
                        {human(v)}
                      </option>
                    ))}
                  </select>
                </Field>
                <Field label="Tax context">
                  <select
                    id="field-taxContext"
                    disabled={!editable}
                    value={draft.taxContext || ""}
                    onChange={(e) => setField("taxContext", e.target.value)}
                  >
                    <option value="">Confirm tax context</option>
                    {[
                      "REGISTERED",
                      "UNREGISTERED",
                      "EXEMPT",
                      "EXPORT",
                      "SEZ",
                    ].map((v) => (
                      <option key={v} value={v}>
                        {human(v)}
                      </option>
                    ))}
                  </select>
                </Field>
                <label className="checkbox-label">
                  <input
                    type="checkbox"
                    checked={!!draft.einvoiceRequired}
                    disabled={!editable}
                    onChange={(e) => {
                      setDraft({
                        ...draft,
                        einvoiceRequired: e.target.checked,
                      });
                      setDirty(true);
                    }}
                  />
                  E-invoice required for this transaction
                </label>
              </div>
              <h2 className="form-section-title">Supplier identity</h2>
              {editable && (
                <Field label="Find supplier by name or GSTIN">
                  <input
                    value={supplierSearch}
                    onChange={(e) => setSupplierSearch(e.target.value)}
                    placeholder="Search all suppliers…"
                  />
                </Field>
              )}
              <ErrorBox error={suppliers.error} />
              <Field label="Resolved supplier master">
                <select
                  id="field-supplierId"
                  disabled={!editable}
                  value={draft.supplierId || ""}
                  onChange={(e) => {
                    setField("supplierId", e.target.value);
                  }}
                >
                  <option value="">Select a supplier</option>
                  {draft.supplierId &&
                    !suppliers.data?.items.some(
                      (s) => s.id === draft.supplierId,
                    ) && (
                      <option value={draft.supplierId}>
                        {draft.supplierName || "Current supplier"} · selected
                        record
                      </option>
                    )}
                  {suppliers.data?.items.map((s) => (
                    <option key={s.id} value={s.id}>
                      {s.name} · {s.gstin || "Unregistered"}
                    </option>
                  ))}
                </select>
              </Field>
              {editable && (
                <small>
                  {suppliers.data?.total || 0} suppliers match. Refine the
                  search to find records beyond the first 50 results.
                </small>
              )}
              <div className="form-grid">{supplierFields.map(field)}</div>
              <h2 className="form-section-title">Buyer & place of supply</h2>
              <div className="form-grid">{buyerFields.map(field)}</div>
              <h2 className="form-section-title">
                Line items <span className="count">{draft.lines.length}</span>
              </h2>
              {draft.lines.map((line, index) => (
                <details className="line-editor" key={index} open>
                  <summary>
                    <b>
                      {index + 1}. {line.description || "Untitled line"}
                    </b>
                    <span>{money(line.total)}</span>
                  </summary>
                  <div className="form-grid">
                    {lineFields.map(([key, label]) => (
                      <Field key={key} label={label}>
                        <input
                          id={`field-lines.${index}.${key}`}
                          value={line[key] || ""}
                          disabled={!editable}
                          onFocus={() => setSelected(`lines.${index}.${key}`)}
                          onChange={(e) => {
                            setDraft({
                              ...draft,
                              lines: draft.lines.map((l, n) =>
                                n === index
                                  ? { ...l, [key]: e.target.value || null }
                                  : l,
                              ),
                            });
                            setDirty(true);
                          }}
                        />
                      </Field>
                    ))}
                  </div>
                  {editable && (
                    <button
                      className="button danger ghost"
                      onClick={() => {
                        setDraft({
                          ...draft,
                          lines: draft.lines.filter((_, n) => n !== index),
                        });
                        setDirty(true);
                      }}
                    >
                      <Trash2 size={15} />
                      Remove line
                    </button>
                  )}
                </details>
              ))}
              {editable && (
                <button
                  className="button"
                  onClick={() => {
                    const line = Object.fromEntries(
                      lineFields.map(([key]) => [key, null]),
                    ) as Line;
                    setDraft({ ...draft, lines: [...draft.lines, line] });
                    setDirty(true);
                  }}
                >
                  <Plus size={16} />
                  Add line
                </button>
              )}
              <h2 className="form-section-title">Tax & totals</h2>
              <div className="form-grid">{totals.map(field)}</div>
              <div className="grand-total">
                <span>Invoice grand total</span>
                <strong>{money(draft.total)}</strong>
              </div>
              <Field label="Review note / correction reason">
                <textarea
                  disabled={!editable}
                  value={note}
                  onChange={(e) => {
                    setNote(e.target.value);
                    setDirty(true);
                  }}
                  placeholder="Explain the correction or leave context for your approver…"
                />
              </Field>
            </div>
          ) : tab === "match" ? (
            <div className="comparison">
              <div className="form-section-heading">
                <h2>Invoice ↔ Purchase order</h2>
                <Badge value={invoice.evaluation.status} />
              </div>
              <p className="muted">
                {invoice.data.poNumber || "No purchase order reference"} ·
                Balances are checked again at approval.
              </p>
              {invoice.evaluation.matches.length ? (
                <div className="table-wrap">
                  <table>
                    <thead>
                      <tr>
                        <th>Item</th>
                        <th>Invoice</th>
                        <th>PO remaining</th>
                        <th>Difference</th>
                        <th>Status</th>
                      </tr>
                    </thead>
                    <tbody>
                      {invoice.evaluation.matches.map((m, n) => (
                        <tr key={n}>
                          <td>
                            <b>{m.description}</b>
                            <small>
                              Ordered {m.ordered} · Used {m.allocated}
                            </small>
                          </td>
                          <td>
                            {m.invoiceQuantity} units
                            <small>{money(m.invoicePrice)} / unit</small>
                          </td>
                          <td>
                            {m.remaining} units
                            <small>{money(m.poPrice)} / unit</small>
                          </td>
                          <td>
                            {m.quantityDifference} units
                            <small>{money(m.priceDifference)} / unit</small>
                          </td>
                          <td>
                            <Badge value={m.status} />
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              ) : (
                <Empty title="No eligible PO lines matched">
                  Confirm the PO reference, supplier and product codes, then
                  re-run reconciliation.
                </Empty>
              )}
              <p className="quiet-note">
                Negative quantity difference indicates a partial invoice.
                Remaining balances reflect approved allocations. Over-allocation
                cannot be overridden.
              </p>
              <ViewLink view="purchase-orders" className="button">
                Inspect purchase orders
                <ArrowLeft size={15} />
              </ViewLink>
            </div>
          ) : (
            <div className="padded">
              <h2>Approval decisions</h2>
              {invoice.decisions.map((d, n) => (
                <div key={n} className="decision">
                  <Badge value={d.decision} />
                  <b>Version {d.version}</b>
                  <p>{d.reason}</p>
                  <small>
                    {date(d.created_at)} · {d.actor}
                  </small>
                </div>
              ))}
              <h2 className="form-section-title">Saved versions</h2>
              {invoice.versions.map((v) => (
                <details className="version-history" key={v.version}>
                  <summary>
                    Version {v.version} · {human(v.provenance)} ·{" "}
                    {date(v.created_at)}
                  </summary>
                  <div className="evidence-values">
                    <span>
                      Invoice <b>{v.data.invoiceNumber || "Unknown"}</b>
                    </span>
                    <span>
                      Grand total <b>{money(v.data.total)}</b>
                    </span>
                  </div>
                  <pre>{JSON.stringify(v.data, null, 2)}</pre>
                </details>
              ))}
              <h2 className="form-section-title">Activity timeline</h2>
              <Timeline events={invoice.history} />
            </div>
          )}
        </section>
        <aside className="insight-pane">
          <div className="pane-label">
            <ShieldCheck size={16} />
            Review insights<span>{findings.length}</span>
          </div>
          <div className="insight-summary">
            <span className="eyebrow">DETERMINISTIC CHECKS</span>
            <h3>
              {findings.length
                ? `${findings.length} findings to review`
                : "Validation checks passed"}
            </h3>
            <p>
              {findings.length
                ? "Resolve required values and financial discrepancies before submitting."
                : "Review the original document before submitting for approval."}
            </p>
          </div>
          <div className="findings">
            {findings.map((f, n) => (
              <button
                className="finding"
                key={n}
                onClick={() => focus(f.field)}
              >
                <div>
                  <Badge value={f.severity} />
                  <small>
                    {f.ruleId} / {f.ruleVersion}
                  </small>
                </div>
                <b>{f.explanation}</b>
                <small>{f.field}</small>
                <p>
                  Actual: {f.actual}
                  <br />
                  Expected: {f.expected}
                </p>
              </button>
            ))}
          </div>
          <section className="evidence-panel">
            <p className="eyebrow violet">SOURCE EVIDENCE</p>
            <h3>{human(selected.replaceAll(".", " "))}</h3>
            {activeEvidence ? (
              <>
                <blockquote>
                  {activeEvidence.original || "Source text unavailable"}
                </blockquote>
                <p>{activeEvidence.source}</p>
                {activeEvidence.confidence != null && (
                  <span className="badge ai">
                    Provider confidence:{" "}
                    {Math.round(Number(activeEvidence.confidence) * 100)}%
                  </span>
                )}
                {activeEvidence.regions?.map((r, n) => (
                  <small key={n}>Source page {r.pageNumber}</small>
                ))}
              </>
            ) : (
              <p>
                No provider evidence is available for this field. Verify against
                the original document.
              </p>
            )}
            <small>
              Confidence describes extraction certainty, not financial validity.
              Manual edits are retained in version history.
            </small>
          </section>
          {invoice.evaluation.duplicates.map((d) => (
            <section className="duplicate-card" key={d.id}>
              <h3>Duplicate candidate</h3>
              <Badge value={d.kind} />
              <p>{d.evidence}</p>
              <b>
                {d.invoice_number || d.id.slice(0, 8)} · {money(d.total)}
              </b>
              <p>
                Current {money(invoice.data.total)} / Candidate{" "}
                {money(d.data.total)}
                <br />
                Dates {date(invoice.data.invoiceDate)} /{" "}
                {date(d.data.invoiceDate)}
              </p>
              <ViewLink view="invoice" id={d.id} className="text-link">
                Open comparison record
              </ViewLink>
              {can("resolve") && !d.cleared && (
                <button
                  className="button"
                  onClick={() => {
                    setCandidate(d.id);
                    setConfirm("duplicate");
                    setReason("");
                  }}
                >
                  Mark false positive
                </button>
              )}
              {d.cleared && <Badge value="RESOLVED" />}
            </section>
          ))}
          {invoice.extractions.length > 0 && (
            <section className="evidence-panel">
              <h3>Extraction history</h3>
              {invoice.extractions.map((e) => (
                <details key={e.id}>
                  <summary>
                    {human(e.status)} · {date(e.started_at)}
                  </summary>
                  <p>
                    {e.provider}
                    <br />
                    {e.model}
                  </p>
                  {e.error && <p className="bad">{e.error}</p>}
                  {e.normalized && editable && (
                    <button
                      className="button"
                      onClick={() => {
                        if (
                          window.confirm(
                            "Use this extraction as a new draft? Your saved versions remain intact.",
                          )
                        ) {
                          setDraft(e.normalized);
                          setDirty(true);
                        }
                      }}
                    >
                      Review this extraction as draft
                    </button>
                  )}
                </details>
              ))}
            </section>
          )}
        </aside>
      </div>
      <div className="review-actionbar">
        <span className={dirty ? "amber" : "muted"}>
          {dirty ? (
            "● Unsaved changes"
          ) : (
            <>
              <Check size={15} />
              All changes saved
            </>
          )}
        </span>
        <div className="button-row">
          {editable && (
            <>
              <button
                className="button primary"
                disabled={busy || !dirty}
                onClick={save}
              >
                <Save size={16} />
                Save draft
              </button>
              <button
                className="button"
                disabled={busy || dirty || !invoice.version}
                onClick={() => action("revalidate")}
              >
                <RefreshCw size={16} />
                Revalidate
              </button>
              <button
                className="button"
                disabled={busy || dirty || !invoice.version}
                onClick={() => action("reconcile")}
              >
                <GitCompareArrows size={16} />
                Reconcile
              </button>
              {["NEEDS_REVIEW", "IN_REVIEW", "REJECTED"].includes(
                invoice.business_state,
              ) && (
                <button
                  className="button"
                  disabled={
                    busy ||
                    dirty ||
                    findings.some((f) => f.blocking) ||
                    invoice.processing_state !== "PROCESSED"
                  }
                  onClick={() => {
                    setConfirm("submit");
                    setReason("");
                  }}
                >
                  <Send size={16} />
                  Submit for approval
                </button>
              )}
            </>
          )}
          {can("approve") &&
            invoice.business_state === "READY_FOR_APPROVAL" &&
            invoice.approver === user.id && (
              <>
                <button
                  className="button danger"
                  onClick={() => {
                    setConfirm("reject");
                    setReason("");
                  }}
                >
                  Return for correction
                </button>
                <button
                  className="button primary"
                  onClick={() => {
                    setConfirm("approve");
                    setReason("");
                  }}
                >
                  Approve invoice
                </button>
              </>
            )}
          {can("approve") && invoice.business_state === "APPROVED" && (
            <button
              className="button"
              onClick={() => {
                setConfirm("reopen");
                setReason("");
              }}
            >
              Reopen for correction
            </button>
          )}
          {can("export") && invoice.business_state === "APPROVED" && (
            <ViewLink view="exports" className="button primary">
              Open accounting exports
            </ViewLink>
          )}
        </div>
      </div>
      {confirm && (
        <Modal
          title={
            confirm === "duplicate"
              ? "Resolve duplicate candidate"
              : human(confirm) + " invoice"
          }
          onClose={() => setConfirm("")}
        >
          <p>
            {invoice.invoice_number || invoice.filename} ·{" "}
            {money(invoice.data.total)} · Version {base}
          </p>
          {confirm === "submit" && (
            <Field label="Assign approver">
              <select
                value={assignee}
                onChange={(e) => setAssignee(e.target.value)}
              >
                <option value="">Choose an approver</option>
                {members.data
                  ?.filter((m) =>
                    [
                      "ORG_ADMIN",
                      "FINANCE_MANAGER",
                      "APPROVER",
                      "PLATFORM_ADMIN",
                    ].includes(m.role),
                  )
                  .map((m) => (
                    <option key={m.id} value={m.id}>
                      {m.name} · {m.email}
                    </option>
                  ))}
              </select>
            </Field>
          )}
          <Field
            label={
              ["reject", "reopen", "duplicate"].includes(confirm)
                ? "Business reason (required)"
                : "Decision note"
            }
          >
            <textarea
              value={reason}
              onChange={(e) => setReason(e.target.value)}
            />
          </Field>
          <ErrorBox error={error} />
          <div className="modal-actions">
            <button className="button" onClick={() => setConfirm("")}>
              Cancel
            </button>
            <button
              className="button primary"
              disabled={
                busy ||
                (confirm === "submit" && !assignee) ||
                (["reject", "reopen", "duplicate"].includes(confirm) &&
                  reason.trim().length < (confirm === "duplicate" ? 10 : 5))
              }
              onClick={() => action(confirm)}
            >
              {busy
                ? "Saving decision…"
                : "Confirm " + human(confirm).toLowerCase()}
            </button>
          </div>
        </Modal>
      )}
    </div>
  );
}
