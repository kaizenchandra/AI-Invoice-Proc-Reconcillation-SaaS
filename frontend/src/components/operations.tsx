"use client";
import { useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { useSearchParams } from "next/navigation";
import {
  ArrowUpRight,
  ArrowRight,
  UploadCloud,
  FileCheck2,
  ScanLine,
  Clock3,
  GitCompareArrows,
  Search,
  ChevronLeft,
  ChevronRight,
  Download,
  RefreshCw,
  CheckCircle2,
  Activity as ActivityIcon,
  ShieldCheck,
  Filter,
} from "lucide-react";
import { api, post, money, human, date, download } from "@/lib/api";
import type {
  Dashboard,
  InvoiceRow,
  Paged,
  Audit,
  ExceptionRow,
  Notification,
  ExportBatch,
} from "@/lib/types";
import { useWorkspace } from "./workspace";
import {
  PageHeader,
  ViewLink,
  ErrorBox,
  Loading,
  Empty,
  Badge,
  MoreLink,
  Modal,
  Field,
} from "./ui";
export function Overview() {
  const { tenant, org, can } = useWorkspace();
  const [from, setFrom] = useState(""),
    [to, setTo] = useState("");
  const q = useQuery({
    queryKey: [tenant, "dashboard", from, to],
    queryFn: () => api<Dashboard>(`/dashboard?from=${from}&to=${to}`, tenant),
    refetchInterval: 15000,
  });
  const recent = useQuery({
    queryKey: [tenant, "recent"],
    queryFn: () => api<Paged<InvoiceRow>>("/invoices?size=5", tenant),
    refetchInterval: 15000,
  });
  const d = q.data;
  return (
    <>
      <PageHeader
        eyebrow={`${org.name.toUpperCase()} / OPERATIONS`}
        title="A clear view of what’s next."
        description="Your invoices, exceptions and approvals. Everything in focus."
      >
        <div className="date-filter">
          <input
            aria-label="From date"
            type="date"
            value={from}
            onChange={(e) => setFrom(e.target.value)}
          />
          <span>—</span>
          <input
            aria-label="To date"
            type="date"
            value={to}
            onChange={(e) => setTo(e.target.value)}
          />
        </div>
        {can("upload") && (
          <ViewLink view="upload" className="button primary">
            <UploadCloud size={17} />
            Upload invoices
          </ViewLink>
        )}
      </PageHeader>
      <ErrorBox error={q.error} retry={() => q.refetch()} />
      {q.isPending ? (
        <Loading />
      ) : (
        d && (
          <>
            <div className="section-caption">
              <span>
                <i className="live-dot" />
                WORKSPACE SNAPSHOT
              </span>
              <small>From your organization’s saved records</small>
            </div>
            <div className="kpi-grid">
              {[
                {
                  label: "Invoices processed",
                  value: d.processed,
                  sub: `${d.received} received in this period`,
                  view: "inbox&status=PROCESSED",
                  icon: FileCheck2,
                  tone: "teal",
                },
                {
                  label: "Needs your review",
                  value: d.review,
                  sub: "Ready for a human decision",
                  view: "review",
                  icon: ScanLine,
                  tone: "amber",
                },
                {
                  label: "Awaiting approval",
                  value: d.approval,
                  sub: "Prepared and submitted",
                  view: "approvals",
                  icon: Clock3,
                  tone: "violet",
                },
                {
                  label: "PO reconciled",
                  value: d.matched,
                  sub: `${d.mismatched} with discrepancies`,
                  view: "reconciliation",
                  icon: GitCompareArrows,
                  tone: "blue",
                },
              ].map((k) => (
                <ViewLink view={k.view} key={k.label} className="kpi">
                  <div>
                    <span>{k.label}</span>
                    <k.icon size={18} className={k.tone} />
                  </div>
                  <strong>{k.value.toLocaleString("en-IN")}</strong>
                  <p>
                    {k.sub}
                    <ArrowUpRight size={14} />
                  </p>
                </ViewLink>
              ))}
            </div>
            <div className="dashboard-grid">
              <section className="panel activity-chart">
                <div className="panel-heading">
                  <div>
                    <h2>Invoice activity</h2>
                    <p>Documents received and processed</p>
                  </div>
                  <span className="legend">
                    <i />
                    Received <i />
                    Processed
                  </span>
                </div>
                {d.trend.length ? (
                  <div
                    className="chart"
                    role="img"
                    aria-label="Daily invoice receipt and processing volume"
                  >
                    {[...d.trend].reverse().map((day) => {
                      const max = Math.max(
                        ...d.trend.map((x) => x.received),
                        1,
                      );
                      return (
                        <div className="chart-column" key={day.day}>
                          <div className="chart-bars">
                            <span
                              title={`${day.received} received`}
                              style={{
                                height: `${(day.received / max) * 100}%`,
                              }}
                            />
                            <span
                              title={`${day.processed} processed`}
                              style={{
                                height: `${(day.processed / max) * 100}%`,
                              }}
                            />
                          </div>
                          <small>
                            {new Date(day.day).toLocaleDateString("en-IN", {
                              day: "numeric",
                              month: "short",
                            })}
                          </small>
                        </div>
                      );
                    })}
                  </div>
                ) : (
                  <Empty title="Your activity starts here">
                    Upload your first invoice to see processing activity.
                  </Empty>
                )}
                <div className="chart-footer">
                  <span>
                    Invoice value <b>{money(d.value)}</b>
                  </span>
                  <span>
                    Exported <b>{d.exported} invoices</b>
                  </span>
                  <span>
                    Average processing{" "}
                    <b>
                      {d.processing_seconds
                        ? Math.round(Number(d.processing_seconds)) + " sec"
                        : "—"}
                    </b>
                  </span>
                </div>
              </section>
              <section className="panel action-panel">
                <div className="panel-heading">
                  <div>
                    <p className="eyebrow">KEEP WORK MOVING</p>
                    <h2>
                      Action required{" "}
                      <span className="count">{d.actions.length}</span>
                    </h2>
                  </div>
                  <span className="amber">
                    <ActivityIcon size={19} />
                  </span>
                </div>
                {d.actions.length ? (
                  <div className="action-list">
                    {d.actions.map((i) => (
                      <ViewLink
                        view="invoice"
                        id={i.id}
                        key={i.id}
                        className="action-item"
                      >
                        <span
                          className={`action-dot ${i.processing_state === "FAILED" ? "bad" : ""}`}
                        />
                        <div>
                          <b>{i.invoice_number || "New invoice"}</b>
                          <p>{i.supplier_name || "Awaiting extraction"}</p>
                          <small>
                            {i.processing_state === "FAILED"
                              ? "Processing needs attention"
                              : human(i.business_state)}
                          </small>
                        </div>
                        <ArrowUpRight size={17} />
                      </ViewLink>
                    ))}
                  </div>
                ) : (
                  <div className="clear-state">
                    <CheckCircle2 size={30} />
                    <h3>You’re all caught up.</h3>
                    <p>Invoices needing attention will appear here.</p>
                  </div>
                )}
                <MoreLink view="exceptions">Open exception center</MoreLink>
              </section>
            </div>
            <section className="panel">
              <div className="panel-heading">
                <div>
                  <h2>Recent invoices</h2>
                  <p>The latest documents in your workspace</p>
                </div>
                <MoreLink view="inbox">View all invoices</MoreLink>
              </div>
              {recent.data?.items.length ? (
                <InvoiceTable rows={recent.data.items} />
              ) : (
                <Empty
                  title="A fresh start for your finance team"
                  action={
                    can("upload") ? (
                      <ViewLink view="upload" className="button">
                        Upload your first invoice <ArrowRight size={16} />
                      </ViewLink>
                    ) : undefined
                  }
                >
                  Your uploaded documents will appear here with their processing
                  and review status.
                </Empty>
              )}
            </section>
            <div className="dashboard-bottom">
              <section className="panel">
                <div className="panel-heading">
                  <h2>Recent activity</h2>
                  <MoreLink view="activity">Audit trail</MoreLink>
                </div>
                <Timeline events={d.activity} />
              </section>
              <section className="panel">
                <div className="panel-heading">
                  <h2>Reconciliation pulse</h2>
                  <GitCompareArrows size={18} />
                </div>
                <div className="pulse-stat">
                  <strong>{d.matched}</strong>
                  <span>Invoices matched to purchase orders</span>
                </div>
                <div className="pulse-track">
                  <span
                    style={{
                      width: `${d.received ? (d.matched / d.received) * 100 : 0}%`,
                    }}
                  />
                </div>
                <div className="pulse-labels">
                  <span>{d.mismatched} discrepancies</span>
                  <span>{d.received} received</span>
                </div>
                <p className="quiet-note">
                  Matching checks supplier identity, line quantities, prices,
                  taxes and remaining PO balances.
                </p>
                <MoreLink view="reconciliation">
                  Inspect reconciliation
                </MoreLink>
              </section>
            </div>
            <details className="metric-definitions">
              <summary>How these metrics are calculated</summary>
              <p>
                Received counts accepted documents by upload date. Processed
                counts completed extraction and evaluation, including documents
                with business findings. Review includes needs-review, in-review
                and rejected invoices. PO reconciled includes matched and
                partial matches. Value sums current extracted totals; it is not
                a ledger balance. Average duration is receipt to processing
                completion, including queue and retry time. No automation rate
                is claimed without a no-touch completion policy.
              </p>
            </details>
          </>
        )
      )}
    </>
  );
}
export function InvoiceTable({
  rows,
  selected,
  onSelect,
}: {
  rows: InvoiceRow[];
  selected?: string[];
  onSelect?: (id: string) => void;
}) {
  return (
    <div className="table-wrap">
      <table>
        <thead>
          <tr>
            {onSelect && <th>Select</th>}
            <th>Invoice / Supplier</th>
            <th>Date</th>
            <th className="numeric">Amount</th>
            <th>Processing</th>
            <th>Workflow</th>
            <th>PO match</th>
            <th>
              <span className="sr-only">Open</span>
            </th>
          </tr>
        </thead>
        <tbody>
          {rows.map((i) => (
            <tr key={i.id}>
              {onSelect && (
                <td>
                  <input
                    type="checkbox"
                    aria-label={`Select ${i.invoice_number || i.id}`}
                    checked={selected?.includes(i.id)}
                    onChange={() => onSelect(i.id)}
                  />
                </td>
              )}
              <td>
                <ViewLink view="invoice" id={i.id} className="invoice-link">
                  <span className="file-mini">
                    <FileCheck2 size={16} />
                  </span>
                  <span>
                    <b>{i.invoice_number || "Awaiting extraction"}</b>
                    <small>
                      {i.supplier_name || "Supplier not yet identified"}
                    </small>
                  </span>
                </ViewLink>
              </td>
              <td className="nowrap">{date(i.invoice_date)}</td>
              <td className="numeric amount">{money(i.total)}</td>
              <td>
                <Badge value={i.processing_state} />
              </td>
              <td>
                <Badge value={i.business_state} />
              </td>
              <td>
                <Badge value={i.match_status} />
              </td>
              <td>
                <ViewLink view="invoice" id={i.id} className="icon-button">
                  <ArrowUpRight size={17} />
                  <span className="sr-only">Open invoice</span>
                </ViewLink>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
export function Inbox({ view }: { view: string }) {
  const { tenant, can } = useWorkspace();
  const params = useSearchParams();
  const [search, setSearch] = useState(params.get("q") || "");
  const [status, setStatus] = useState(
    params.get("status") ||
      (view === "approvals"
        ? "READY_FOR_APPROVAL"
        : view === "review"
          ? "NEEDS_REVIEW"
          : ""),
  );
  const [page, setPage] = useState(0),
    [source, setSource] = useState(""),
    [min, setMin] = useState(""),
    [max, setMax] = useState(""),
    [sort, setSort] = useState("created_at"),
    [from, setFrom] = useState(""),
    [to, setTo] = useState("");
  const query = new URLSearchParams({
    q: search,
    status,
    source,
    page: String(page),
    sort,
    from,
    to,
    ...(min ? { min } : {}),
    ...(max ? { max } : {}),
  });
  const q = useQuery({
    queryKey: [tenant, "invoices", query.toString()],
    queryFn: () => api<Paged<InvoiceRow>>("/invoices?" + query, tenant),
    refetchInterval: 15000,
  });
  const titles: Record<string, string> = {
    inbox: "Invoice inbox",
    review: "Review queue",
    approvals: "Approval center",
    reconciliation: "Reconciliation",
  };
  return (
    <>
      <PageHeader
        title={titles[view]}
        description={
          view === "approvals"
            ? "Review the evidence. Approve the exact version. Keep every decision traceable."
            : view === "reconciliation"
              ? "Compare invoice lines with purchase orders and inspect remaining balances."
              : "A single place for every document, from receipt to accounting."
        }
      >
        {can("upload") && (
          <ViewLink view="upload" className="button primary">
            <UploadCloud size={17} />
            Upload invoices
          </ViewLink>
        )}
      </PageHeader>
      <section className="panel">
        <div className="filter-bar">
          <div className="search-input">
            <Search size={17} />
            <input
              aria-label="Search invoice, supplier, GSTIN or PO"
              value={search}
              onChange={(e) => {
                setSearch(e.target.value);
                setPage(0);
              }}
              placeholder="Search invoice, supplier, GSTIN or PO…"
            />
          </div>
          <select
            aria-label="Invoice status"
            value={status}
            onChange={(e) => {
              setStatus(e.target.value);
              setPage(0);
            }}
          >
            <option value="">All statuses</option>
            {[
              "NEEDS_REVIEW",
              "IN_REVIEW",
              "READY_FOR_APPROVAL",
              "APPROVED",
              "EXPORTED",
              "REJECTED",
              "FAILED",
              "PROCESSED",
              "QUEUED",
            ].map((s) => (
              <option key={s} value={s}>
                {human(s)}
              </option>
            ))}
          </select>
          <select
            aria-label="Upload source"
            value={source}
            onChange={(e) => setSource(e.target.value)}
          >
            <option value="">All sources</option>
            <option>UPLOAD</option>
            <option>EMAIL</option>
          </select>
          <details className="more-filters">
            <summary>
              <Filter size={16} /> Filters
            </summary>
            <div className="filter-popover">
              <Field label="Received from">
                <input
                  type="date"
                  value={from}
                  onChange={(e) => setFrom(e.target.value)}
                />
              </Field>
              <Field label="Received to">
                <input
                  type="date"
                  value={to}
                  onChange={(e) => setTo(e.target.value)}
                />
              </Field>
              <Field label="Minimum amount">
                <input
                  inputMode="decimal"
                  value={min}
                  onChange={(e) => setMin(e.target.value)}
                />
              </Field>
              <Field label="Maximum amount">
                <input
                  inputMode="decimal"
                  value={max}
                  onChange={(e) => setMax(e.target.value)}
                />
              </Field>
              <Field label="Sort by">
                <select value={sort} onChange={(e) => setSort(e.target.value)}>
                  <option value="created_at">Newest received</option>
                  <option value="invoice_date">Invoice date</option>
                  <option value="total">Amount, highest first</option>
                  <option value="invoice_number">Invoice number</option>
                </select>
              </Field>
            </div>
          </details>
          <button
            className="icon-button"
            aria-label="Refresh invoices"
            onClick={() => q.refetch()}
          >
            <RefreshCw size={17} />
          </button>
        </div>
        <ErrorBox error={q.error} retry={() => q.refetch()} />
        {q.isPending ? (
          <Loading />
        ) : q.data?.items.length ? (
          <InvoiceTable rows={q.data.items} />
        ) : (
          <Empty title="No invoices in this view">
            Change your filters or upload an invoice to get started.
          </Empty>
        )}
        <div className="pagination">
          <span>
            {q.data?.total || 0} invoices · Page {page + 1}
          </span>
          <div>
            <button disabled={page === 0} onClick={() => setPage(page - 1)}>
              <ChevronLeft size={16} />
              Previous
            </button>
            <button
              disabled={!q.data || 25 * (page + 1) >= q.data.total}
              onClick={() => setPage(page + 1)}
            >
              Next
              <ChevronRight size={16} />
            </button>
          </div>
        </div>
      </section>
    </>
  );
}
export function Timeline({ events }: { events: Audit[] }) {
  return events.length ? (
    <ol className="timeline">
      {events.map((e) => (
        <li key={e.id}>
          <span className="timeline-dot" />
          <div>
            <b>{human(e.action)}</b>
            <p>
              {e.actor_name ||
                (e.actor === "worker"
                  ? "Processing worker"
                  : e.actor.slice(0, 8))}
              {e.reason ? " · " + e.reason : ""}
            </p>
            <time>{new Date(e.created_at).toLocaleString("en-IN")}</time>
          </div>
        </li>
      ))}
    </ol>
  ) : (
    <div className="compact-empty">Your activity history will appear here.</div>
  );
}
export function Activity() {
  const { tenant } = useWorkspace();
  const [page, setPage] = useState(0);
  const q = useQuery({
    queryKey: [tenant, "audit", page],
    queryFn: () => api<Audit[]>("/audit-events?page=" + page, tenant),
  });
  return (
    <>
      <PageHeader
        title="Activity & audit"
        description="An immutable history of the actions that move your business forward."
      >
        <span className="secure-label">
          <ShieldCheck size={17} />
          Recorded with every change
        </span>
      </PageHeader>
      <section className="panel">
        <ErrorBox error={q.error} />
        {q.isPending ? <Loading /> : <Timeline events={q.data || []} />}
        <div className="pagination">
          <button disabled={!page} onClick={() => setPage(page - 1)}>
            Previous
          </button>
          <span>Page {page + 1}</span>
          <button
            disabled={(q.data?.length || 0) < 50}
            onClick={() => setPage(page + 1)}
          >
            Next
          </button>
        </div>
      </section>
    </>
  );
}
export function Exceptions() {
  const { tenant, can, notify, user } = useWorkspace();
  const [status, setStatus] = useState("OPEN"),
    [error, setError] = useState<unknown>();
  const q = useQuery({
    queryKey: [tenant, "exceptions", status],
    queryFn: () => api<ExceptionRow[]>("/exceptions?status=" + status, tenant),
  });
  return (
    <>
      <PageHeader
        title="Exception center"
        description="Understand what needs attention, inspect the evidence, and move work forward."
      />
      <section className="panel">
        <div className="filter-bar">
          <select
            aria-label="Exception status"
            value={status}
            onChange={(e) => setStatus(e.target.value)}
          >
            {[
              "OPEN",
              "ASSIGNED",
              "IN_REVIEW",
              "RESOLVED",
              "DISMISSED_WITH_REASON",
              "ALL",
            ].map((s) => (
              <option key={s} value={s}>
                {human(s)}
              </option>
            ))}
          </select>
          <span className="muted">
            Mandatory findings resolve when the invoice is corrected and
            revalidated.
          </span>
        </div>
        <ErrorBox error={q.error || error} />
        {q.isPending ? (
          <Loading />
        ) : !q.data?.length ? (
          <Empty title="No exceptions in this view">
            New findings appear here as invoices are processed.
          </Empty>
        ) : (
          <div className="exception-list">
            {q.data.map((e) => (
              <article key={e.id} className="exception-card">
                <div>
                  <Badge value={e.severity} />
                  <small>{human(e.rule_id)}</small>
                </div>
                <h3>{e.invoice_number || "Invoice processing"}</h3>
                <p>{e.explanation}</p>
                <div className="evidence-values">
                  <span>
                    Actual <b>{e.evidence.actual || "—"}</b>
                  </span>
                  <span>
                    Expected <b>{e.evidence.expected || "—"}</b>
                  </span>
                </div>
                <footer>
                  <ViewLink view="invoice" id={e.invoice_id} className="button">
                    Review invoice
                    <ArrowUpRight size={15} />
                  </ViewLink>
                  {can("resolve") &&
                    ["OPEN", "ASSIGNED"].includes(e.status) && (
                      <button
                        className="button ghost"
                        onClick={async () => {
                          try {
                            await post("/exceptions/" + e.id, tenant, {
                              action: "review",
                              assignee: user.id,
                            });
                            notify("Exception assigned to you for review.");
                            q.refetch();
                          } catch (err) {
                            setError(err);
                          }
                        }}
                      >
                        Start review
                      </button>
                    )}
                  <Badge value={e.status} />
                </footer>
              </article>
            ))}
          </div>
        )}
      </section>
    </>
  );
}
export function Notifications() {
  const { tenant } = useWorkspace();
  const client = useQueryClient();
  const [error, setError] = useState<unknown>();
  const q = useQuery({
    queryKey: [tenant, "notifications"],
    queryFn: () => api<Notification[]>("/notifications", tenant),
  });
  return (
    <>
      <PageHeader
        title="Notifications"
        description="Assignments, decisions and processing updates for your organization."
      />
      <section className="panel">
        <ErrorBox error={q.error || error} />
        {q.isPending ? (
          <Loading />
        ) : !q.data?.length ? (
          <Empty title="You’re up to date">
            You’ll see review assignments, approval requests and important
            failures here.
          </Empty>
        ) : (
          q.data.map((n) => (
            <article
              className={`notification-row ${n.read_at ? "" : "unread"}`}
              key={n.id}
            >
              <span className="timeline-dot" />
              <div>
                <b>{n.title}</b>
                <p>{new Date(n.created_at).toLocaleString("en-IN")}</p>
              </div>
              <ViewLink
                view={n.event_key.startsWith("export") ? "exports" : "invoice"}
                id={n.resource_id}
                className="text-link"
              >
                Open
                <ArrowUpRight size={15} />
              </ViewLink>
              {!n.read_at && (
                <button
                  className="button ghost"
                  onClick={async () => {
                    try {
                      await post("/notifications/" + n.id + "/read", tenant);
                      client.invalidateQueries({
                        queryKey: [tenant, "notifications"],
                      });
                    } catch (e) {
                      setError(e);
                    }
                  }}
                >
                  Mark read
                </button>
              )}
            </article>
          ))
        )}
      </section>
    </>
  );
}
export function Exports() {
  const { tenant, notify } = useWorkspace();
  const [selected, setSelected] = useState<string[]>([]),
    [preview, setPreview] = useState(false),
    [error, setError] = useState<unknown>(),
    [busy, setBusy] = useState(false),
    [key, setKey] = useState(() => crypto.randomUUID()),
    [reason, setReason] = useState(""),
    [status, setStatus] = useState("APPROVED");
  const q = useQuery({
    queryKey: [tenant, "export-eligible", status],
    queryFn: () =>
      api<Paged<InvoiceRow>>("/invoices?size=100&status=" + status, tenant),
  });
  const history = useQuery({
    queryKey: [tenant, "exports"],
    queryFn: () => api<ExportBatch[]>("/exports", tenant),
  });
  async function perform() {
    setBusy(true);
    try {
      const b = await post<ExportBatch>(
        "/exports",
        tenant,
        { invoiceIds: selected, reason },
        { "Idempotency-Key": key },
      );
      if (b.status === "COMPLETED") {
        notify("Export prepared from approved invoice snapshots.");
        setSelected([]);
        setPreview(false);
        setKey(crypto.randomUUID());
        await download(
          "/exports/" + b.id + "/download",
          tenant,
          "kaizen-invoices.csv",
        );
      } else
        setError(b.error || "Export failed. Retry this batch from history.");
      q.refetch();
      history.refetch();
    } catch (e) {
      setError(e);
    } finally {
      setBusy(false);
    }
  }
  return (
    <>
      <PageHeader
        title="Accounting exports"
        description="Approved records, exact versions, a clear trail. Ready for your accounting workflow."
      >
        <button
          className="button primary"
          disabled={!selected.length}
          onClick={() => {
            setError(undefined);
            setPreview(true);
          }}
        >
          Preview export <ArrowRight size={16} />
        </button>
      </PageHeader>
      <ErrorBox error={error} />
      <section className="panel">
        <div className="panel-heading">
          <div>
            <h2>Export-ready invoices</h2>
            <p>{selected.length} selected · CSV accounting format</p>
          </div>
          <select
            aria-label="Export eligibility"
            value={status}
            onChange={(e) => {
              setStatus(e.target.value);
              setSelected([]);
            }}
          >
            <option value="APPROVED">Approved, not exported</option>
            <option value="EXPORTED">Previously exported</option>
          </select>
        </div>
        <ErrorBox error={q.error} />
        {q.isPending ? (
          <Loading />
        ) : q.data?.items.length ? (
          <InvoiceTable
            rows={q.data.items}
            selected={selected}
            onSelect={(id) => {
              setSelected(
                selected.includes(id)
                  ? selected.filter((x) => x !== id)
                  : [...selected, id],
              );
              setKey(crypto.randomUUID());
            }}
          />
        ) : (
          <Empty title="No approved invoices ready for export">
            Complete review and approval to make invoices available here.
          </Empty>
        )}
      </section>
      <section className="panel section-gap">
        <div className="panel-heading">
          <h2>Export history</h2>
          <span className="muted">Snapshot-backed CSV files</span>
        </div>
        <ErrorBox error={history.error} />
        {history.data?.length ? (
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>Batch</th>
                  <th>Created</th>
                  <th>Records</th>
                  <th>Status</th>
                  <th>Action</th>
                </tr>
              </thead>
              <tbody>
                {history.data.map((b) => (
                  <tr key={b.id}>
                    <td>
                      <b>EXP-{b.id.slice(0, 8).toUpperCase()}</b>
                      {b.error && <small className="bad">{b.error}</small>}
                    </td>
                    <td>{date(b.created_at)}</td>
                    <td>{b.record_count}</td>
                    <td>
                      <Badge value={b.status} />
                    </td>
                    <td>
                      {b.status === "COMPLETED" ? (
                        <button
                          className="button ghost"
                          onClick={async () => {
                            try {
                              await download(
                                "/exports/" + b.id + "/download",
                                tenant,
                                "kaizen-" + b.id + ".csv",
                              );
                            } catch (e) {
                              setError(e);
                            }
                          }}
                        >
                          <Download size={15} />
                          Download
                        </button>
                      ) : (
                        <button
                          className="button"
                          onClick={async () => {
                            try {
                              await post("/exports/" + b.id + "/retry", tenant);
                              history.refetch();
                            } catch (e) {
                              setError(e);
                            }
                          }}
                        >
                          Retry this batch
                        </button>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : (
          <Empty title="Every export will have a history">
            Download and retry batches here without losing version references.
          </Empty>
        )}
      </section>
      {preview && (
        <Modal
          title="Confirm accounting export"
          onClose={() => setPreview(false)}
        >
          <p>
            You’re exporting {selected.length} approved invoice snapshots. Their
            approved amounts and versions will be preserved.
          </p>
          <div className="preview-list">
            {q.data?.items
              .filter((i) => selected.includes(i.id))
              .map((i) => (
                <div key={i.id}>
                  <span>
                    {i.invoice_number}
                    <small>Approved version {i.version}</small>
                  </span>
                  <b>{money(i.total)}</b>
                </div>
              ))}
          </div>
          <Field
            label={
              status === "EXPORTED"
                ? "Re-export reason (required)"
                : "Business note (optional)"
            }
          >
            <textarea
              value={reason}
              onChange={(e) => setReason(e.target.value)}
              minLength={status === "EXPORTED" ? 10 : 0}
            />
          </Field>
          <ErrorBox error={error} />
          <div className="modal-actions">
            <button className="button" onClick={() => setPreview(false)}>
              Cancel
            </button>
            <button
              className="button primary"
              disabled={
                busy || (status === "EXPORTED" && reason.trim().length < 10)
              }
              onClick={perform}
            >
              {busy ? "Preparing…" : "Export & download CSV"}
            </button>
          </div>
        </Modal>
      )}
    </>
  );
}
