"use client";
import { useState, useRef } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import {
  Plus,
  Upload,
  Search,
  ArrowUpRight,
  Save,
  ShieldCheck,
  Copy,
  Trash2,
} from "lucide-react";
import { api, post, money, date, human } from "@/lib/api";
import type {
  Supplier,
  PurchaseOrder,
  Paged,
  Member,
  Settings,
  ExtractionConfiguration,
} from "@/lib/types";
import { useWorkspace, InvitationAccept } from "./workspace";
import {
  PageHeader,
  Field,
  Modal,
  ErrorBox,
  Loading,
  Empty,
  Badge,
  ViewLink,
} from "./ui";
type PoDraftLine = {
  code: string;
  description: string;
  quantity: string;
  price: string;
  taxRate: string;
};
export function MasterData({
  kind,
}: {
  kind: "suppliers" | "purchase-orders";
}) {
  const { tenant, can, notify } = useWorkspace();
  const supplier = kind === "suppliers";
  const [supplierSearch, setSupplierSearch] = useState("");
  const [search, setSearch] = useState(""),
    [page, setPage] = useState(0),
    [modal, setModal] = useState(false),
    [edit, setEdit] = useState<Supplier | null>(null),
    [editPo, setEditPo] = useState<PurchaseOrder | null>(null),
    [selected, setSelected] = useState(""),
    [error, setError] = useState<unknown>(),
    [busy, setBusy] = useState(false),
    [lines, setLines] = useState<PoDraftLine[]>([
      { code: "", description: "", quantity: "", price: "", taxRate: "" },
    ]),
    [importResult, setImportResult] = useState<
      { row?: number; number?: string; status: string; error?: string }[] | null
    >(null);
  const input = useRef<HTMLInputElement>(null);
  const q = useQuery({
    queryKey: [tenant, kind, search, page],
    queryFn: () =>
      api<Paged<Supplier | PurchaseOrder>>(
        `/${kind}?q=${encodeURIComponent(search)}&page=${page}`,
        tenant,
      ),
  });
  const suppliers = useQuery({
    queryKey: [tenant, "supplier-options", supplierSearch],
    queryFn: () =>
      api<Paged<Supplier>>(
        "/suppliers?q=" + encodeURIComponent(supplierSearch),
        tenant,
      ),
    enabled: !supplier,
  });
  const detail = useQuery({
    queryKey: [tenant, "po", selected],
    queryFn: () => api<PurchaseOrder>("/purchase-orders/" + selected, tenant),
    enabled: !!selected,
  });
  async function submit(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    setBusy(true);
    setError(undefined);
    const f = Object.fromEntries(new FormData(e.currentTarget));
    try {
      await api(
        "/" + kind + (edit ? "/" + edit.id : editPo ? "/" + editPo.id : ""),
        tenant,
        {
          method: edit || editPo ? "PATCH" : "POST",
          body: JSON.stringify(
            supplier
              ? { ...f, version: edit?.version || 0 }
              : { ...f, lines, currency: "INR", version: editPo?.version || 0 },
          ),
        },
      );
      notify(supplier ? "Supplier saved." : "Purchase order saved.");
      setModal(false);
      setEdit(null);
      setEditPo(null);
      q.refetch();
    } catch (e) {
      setError(e);
    } finally {
      setBusy(false);
    }
  }
  return (
    <>
      <PageHeader
        title={supplier ? "Supplier directory" : "Purchase orders"}
        description={
          supplier
            ? "Trusted identities for every invoice. Keep your supplier master accurate."
            : "The other half of reconciliation. Track ordered and invoiced quantities."
        }
      >
        {can("master") && (
          <>
            <button className="button" onClick={() => input.current?.click()}>
              <Upload size={16} />
              Import CSV
            </button>
            <button
              className="button primary"
              onClick={() => {
                setEdit(null);
                setEditPo(null);
                setLines([
                  {
                    code: "",
                    description: "",
                    quantity: "",
                    price: "",
                    taxRate: "",
                  },
                ]);
                setError(undefined);
                setModal(true);
              }}
            >
              <Plus size={17} />
              {supplier ? "Add supplier" : "Create purchase order"}
            </button>
          </>
        )}
      </PageHeader>
      <input
        type="file"
        accept=".csv,text/csv"
        ref={input}
        className="sr-only"
        onChange={async (e) => {
          const file = e.target.files?.[0];
          if (!file) return;
          const form = new FormData();
          form.set("file", file);
          try {
            setImportResult(
              await api("/" + kind + "/import", tenant, {
                method: "POST",
                body: form,
              }),
            );
            q.refetch();
          } catch (e) {
            setError(e);
          }
          e.target.value = "";
        }}
      />
      <ErrorBox error={error} />
      <section className="panel">
        <div className="filter-bar">
          <div className="search-input">
            <Search size={17} />
            <input
              aria-label={`Search ${kind}`}
              value={search}
              onChange={(e) => {
                setSearch(e.target.value);
                setPage(0);
              }}
              placeholder={
                supplier
                  ? "Search supplier name or GSTIN…"
                  : "Search PO number or supplier…"
              }
            />
          </div>
          <span className="muted">{q.data?.total || 0} records</span>
        </div>
        <ErrorBox error={q.error} />
        {q.isPending ? (
          <Loading />
        ) : !q.data?.items.length ? (
          <Empty
            title={
              supplier
                ? "Build a reliable supplier directory"
                : "Make every purchase traceable"
            }
          >
            {supplier
              ? "Add a supplier or import a CSV to resolve invoice identities."
              : "Create a purchase order with product codes, quantities and tax rates."}
          </Empty>
        ) : (
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  {(supplier
                    ? ["Supplier", "GSTIN", "State", "Invoices", "Action"]
                    : [
                        "Purchase order",
                        "Supplier",
                        "Date",
                        "Total",
                        "Status",
                        "Action",
                      ]
                  ).map((s) => (
                    <th key={s}>{s}</th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {q.data.items.map((row) => {
                  if (supplier) {
                    const s = row as Supplier;
                    return (
                      <tr key={s.id}>
                        <td>
                          <b>{s.name}</b>
                          <small>{s.address || "No address recorded"}</small>
                        </td>
                        <td className="mono">{s.gstin || "Unregistered"}</td>
                        <td>{s.state || "—"}</td>
                        <td>{s.invoice_count}</td>
                        <td>
                          {can("master") && (
                            <button
                              className="button ghost"
                              onClick={() => {
                                setEdit(s);
                                setModal(true);
                              }}
                            >
                              Edit supplier
                            </button>
                          )}
                        </td>
                      </tr>
                    );
                  }
                  const p = row as PurchaseOrder;
                  return (
                    <tr key={p.id}>
                      <td>
                        <button
                          className="text-link"
                          onClick={() => setSelected(p.id)}
                        >
                          {p.number}
                          <ArrowUpRight size={14} />
                        </button>
                      </td>
                      <td>{p.supplier_name}</td>
                      <td>{date(p.date)}</td>
                      <td className="amount">{money(p.total)}</td>
                      <td>
                        <Badge value={p.status} />
                      </td>
                      <td>
                        <button
                          className="button ghost"
                          onClick={() => setSelected(p.id)}
                        >
                          Inspect balances
                        </button>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
        <div className="pagination">
          <span>Page {page + 1}</span>
          <div>
            <button disabled={!page} onClick={() => setPage(page - 1)}>
              Previous
            </button>
            <button
              disabled={(page + 1) * 50 >= (q.data?.total || 0)}
              onClick={() => setPage(page + 1)}
            >
              Next
            </button>
          </div>
        </div>
      </section>
      <details className="import-help">
        <summary>CSV import format & rules</summary>
        <p>
          {supplier
            ? "Required columns: name,gstin,pan,state,address,contact. Leave GSTIN blank for an unregistered supplier."
            : "Required columns: number,supplierId,date,code,description,quantity,price,taxRate. Repeat the PO number for multiple lines. Dates use YYYY-MM-DD. supplierId is the supplier record UUID."}
        </p>
        <p>
          Imports validate each supplier or purchase order independently.
          Successful records persist even if another record fails. Maximum 1,000
          rows per import.
        </p>
      </details>
      {modal && (
        <Modal
          title={
            supplier
              ? edit
                ? "Edit supplier"
                : "Add supplier"
              : "Create purchase order"
          }
          onClose={() => setModal(false)}
        >
          <form onSubmit={submit}>
            {supplier ? (
              <>
                <Field label="Legal / trade name">
                  <input name="name" defaultValue={edit?.name} required />
                </Field>
                <div className="form-grid">
                  {(["gstin", "pan", "state", "contact"] as const).map((f) => (
                    <Field
                      key={f}
                      label={
                        f === "gstin"
                          ? "GSTIN (blank if unregistered)"
                          : human(f)
                      }
                    >
                      <input name={f} defaultValue={edit?.[f]} />
                    </Field>
                  ))}
                </div>
                <Field label="Address">
                  <textarea name="address" defaultValue={edit?.address} />
                </Field>
              </>
            ) : (
              <>
                <div className="form-grid">
                  <Field label="PO number">
                    <input
                      name="number"
                      defaultValue={editPo?.number}
                      required
                    />
                  </Field>
                  <Field label="PO date">
                    <input
                      name="date"
                      type="date"
                      defaultValue={editPo?.date}
                      required
                    />
                  </Field>
                </div>
                <Field label="Find supplier by name or GSTIN">
                  <input
                    value={supplierSearch}
                    onChange={(e) => setSupplierSearch(e.target.value)}
                    placeholder="Search all suppliers…"
                  />
                </Field>
                <ErrorBox error={suppliers.error} />
                <Field label="Supplier">
                  <select
                    name="supplierId"
                    defaultValue={editPo?.supplier_id}
                    required
                  >
                    <option value="">Choose a supplier</option>
                    {editPo?.supplier_id &&
                      !suppliers.data?.items.some(
                        (s) => s.id === editPo.supplier_id,
                      ) && (
                        <option value={editPo.supplier_id}>
                          {editPo.supplier_name || "Current supplier"}
                        </option>
                      )}
                    {suppliers.data?.items.map((s) => (
                      <option key={s.id} value={s.id}>
                        {s.name}
                      </option>
                    ))}
                  </select>
                </Field>
                {editPo && (
                  <Field label="Purchase order status">
                    <select name="status" defaultValue={editPo.status}>
                      <option>OPEN</option>
                      <option>CLOSED</option>
                    </select>
                  </Field>
                )}
                <h3>Ordered line items</h3>
                {lines.map((l, n) => (
                  <div className="po-line-form" key={n}>
                    <div className="form-grid">
                      {(Object.keys(l) as (keyof PoDraftLine)[]).map((k) => (
                        <Field
                          key={k}
                          label={human(k.replace("taxRate", "Tax rate %"))}
                        >
                          <input
                            value={l[k]}
                            required
                            onChange={(e) =>
                              setLines(
                                lines.map((old, i) =>
                                  i === n
                                    ? { ...old, [k]: e.target.value }
                                    : old,
                                ),
                              )
                            }
                          />
                        </Field>
                      ))}
                    </div>
                    {lines.length > 1 && (
                      <button
                        type="button"
                        className="button ghost danger"
                        onClick={() =>
                          setLines(lines.filter((_, i) => i !== n))
                        }
                      >
                        <Trash2 size={14} />
                        Remove line
                      </button>
                    )}
                  </div>
                ))}
                <button
                  type="button"
                  className="button"
                  onClick={() =>
                    setLines([
                      ...lines,
                      {
                        code: "",
                        description: "",
                        quantity: "",
                        price: "",
                        taxRate: "",
                      },
                    ])
                  }
                >
                  <Plus size={16} />
                  Add line
                </button>
              </>
            )}
            <ErrorBox error={error} />
            <div className="modal-actions">
              <button
                type="button"
                className="button"
                onClick={() => setModal(false)}
              >
                Cancel
              </button>
              <button className="button primary" disabled={busy}>
                {busy
                  ? "Saving…"
                  : "Save " + (supplier ? "supplier" : "purchase order")}
              </button>
            </div>
          </form>
        </Modal>
      )}
      {selected && (
        <Modal
          title={detail.data?.number || "Purchase order details"}
          onClose={() => setSelected("")}
        >
          <ErrorBox error={detail.error} />
          {detail.isPending ? (
            <Loading />
          ) : (
            <>
              <p>Ordered quantities and committed invoice allocations.</p>
              {can("master") &&
                detail.data &&
                !detail.data.invoices?.length && (
                  <button
                    className="button"
                    onClick={() => {
                      const p = detail.data!;
                      setEditPo(p);
                      setLines(
                        (p.lines || []).map((l) => ({
                          code: l.code,
                          description: l.description,
                          quantity: l.quantity,
                          price: l.price,
                          taxRate: l.tax_rate,
                        })),
                      );
                      setSelected("");
                      setModal(true);
                    }}
                  >
                    Amend purchase order
                  </button>
                )}
              <div className="table-wrap">
                <table>
                  <thead>
                    <tr>
                      <th>Product</th>
                      <th>Ordered</th>
                      <th>Allocated</th>
                      <th>Price</th>
                      <th>Tax</th>
                    </tr>
                  </thead>
                  <tbody>
                    {detail.data?.lines?.map((l) => (
                      <tr key={l.id}>
                        <td>
                          <b>{l.code}</b>
                          <small>{l.description}</small>
                        </td>
                        <td>{l.quantity}</td>
                        <td>{l.allocated}</td>
                        <td>{money(l.price)}</td>
                        <td>{l.tax_rate}%</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
              <h3>Linked approved invoices</h3>
              {detail.data?.invoices?.length ? (
                detail.data.invoices.map((i) => (
                  <p key={i.invoice_id}>
                    <ViewLink
                      view="invoice"
                      id={i.invoice_id}
                      className="text-link"
                    >
                      Invoice {i.invoice_id.slice(0, 8)} · version {i.version}
                      <ArrowUpRight size={15} />
                    </ViewLink>
                  </p>
                ))
              ) : (
                <p className="muted">
                  No allocations yet. Quantities are committed at approval.
                </p>
              )}
            </>
          )}
        </Modal>
      )}
      {importResult && (
        <Modal title="Import results" onClose={() => setImportResult(null)}>
          <p>
            {importResult.filter((r) => r.status === "IMPORTED").length}{" "}
            imported ·{" "}
            {importResult.filter((r) => r.status === "FAILED").length} failed
          </p>
          {importResult.map((r, n) => (
            <div className="upload-row" key={n}>
              <b>{r.number || "Row " + r.row}</b>
              <Badge value={r.status} />
              {r.error && <p>{r.error}</p>}
            </div>
          ))}
        </Modal>
      )}
    </>
  );
}
export function OrganizationSettings({ section }: { section: string }) {
  const { tenant, org, notify, user } = useWorkspace();
  const client = useQueryClient();
  const q = useQuery({
    queryKey: [tenant, "settings-full"],
    queryFn: () =>
      api<{
        settings: Settings;
        policy_version: number;
        profile: Record<string, string>;
        provider: string;
        extractionConfigured: boolean;
        extraction: ExtractionConfiguration;
        emailAddress: string;
        malwareScanEnabled: boolean;
      }>("/settings", tenant),
  });
  const members = useQuery({
    queryKey: [tenant, "members"],
    queryFn: () =>
      api<Member[]>("/organizations/" + tenant + "/members", tenant),
  });
  const units = useQuery({
    queryKey: [tenant, "business-units"],
    queryFn: () =>
      api<
        {
          id: string;
          name: string;
          gstin: string;
          state: string;
          address: string;
        }[]
      >("/organizations/" + tenant + "/business-units", tenant),
  });
  const [error, setError] = useState<unknown>(),
    [busy, setBusy] = useState(false),
    [invite, setInvite] = useState(""),
    [unitModal, setUnitModal] = useState(false);
  const roles = [
    "ORG_ADMIN",
    "FINANCE_MANAGER",
    "PROCESSOR",
    "REVIEWER",
    "APPROVER",
    "AUDITOR",
  ];
  if (q.isPending) return <Loading />;
  if (!q.data) return <ErrorBox error={q.error} />;
  const data = q.data;
  async function saveSettings(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    setBusy(true);
    setError(undefined);
    const f = new FormData(e.currentTarget);
    try {
      await api("/settings", tenant, {
        method: "PUT",
        body: JSON.stringify({
          version: data.policy_version,
          roundingTolerance: f.get("roundingTolerance"),
          priceTolerance: f.get("priceTolerance"),
          separationOfDuties: f.has("separationOfDuties"),
          requirePo: f.has("requirePo"),
          reviewThreshold: f.get("reviewThreshold"),
          requiredFields: String(f.get("requiredFields") || "")
            .split(",")
            .map((s) => s.trim())
            .filter(Boolean),
          exportFields: String(f.get("exportFields"))
            .split(",")
            .map((s) => s.trim())
            .filter(Boolean),
        }),
      });
      notify("Policy saved. Historical decisions remain unchanged.");
      q.refetch();
    } catch (e) {
      setError(e);
    } finally {
      setBusy(false);
    }
  }
  return (
    <>
      <PageHeader
        title={
          section === "organization"
            ? "Organization & team"
            : "Workspace settings"
        }
        description="Configure your workspace without losing the history behind your decisions."
      />
      <ErrorBox error={error} />
      {section === "organization" ? (
        <>
          <section className="panel padded">
            <h2>Business profile</h2>
            <form
              key={data.policy_version}
              onSubmit={async (e) => {
                e.preventDefault();
                try {
                  await api("/organizations/" + tenant, undefined, {
                    method: "PATCH",
                    body: JSON.stringify(
                      Object.fromEntries(new FormData(e.currentTarget)),
                    ),
                  });
                  notify("Organization profile saved.");
                  client.invalidateQueries({ queryKey: ["organizations"] });
                  q.refetch();
                } catch (e) {
                  setError(e);
                }
              }}
            >
              <div className="form-grid">
                {[
                  "name",
                  "gstin",
                  "pan",
                  "state",
                  "financialYear",
                  "address",
                ].map((key) => (
                  <Field
                    key={key}
                    label={human(
                      key.replace("financialYear", "Financial year"),
                    )}
                  >
                    <input
                      name={key}
                      defaultValue={data.profile[key]}
                      required={!["gstin", "pan"].includes(key)}
                    />
                  </Field>
                ))}
              </div>
              <button className="button primary">
                <Save size={16} />
                Save profile
              </button>
            </form>
          </section>
          <section className="panel section-gap">
            <div className="panel-heading">
              <div>
                <h2>Team & permissions</h2>
                <p>Permissions are enforced for every backend operation.</p>
              </div>
              <ShieldCheck size={20} />
            </div>
            <div className="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>Member</th>
                    <th>Email</th>
                    <th>Role</th>
                  </tr>
                </thead>
                <tbody>
                  {members.data?.map((m) => (
                    <tr key={m.id}>
                      <td>
                        {m.name}
                        {m.id === user.id ? " (you)" : ""}
                      </td>
                      <td>{m.email}</td>
                      <td>
                        <select
                          aria-label={"Role for " + m.name}
                          disabled={m.id === user.id}
                          value={m.role}
                          onChange={async (e) => {
                            try {
                              await api(
                                `/organizations/${tenant}/members/${m.id}`,
                                undefined,
                                {
                                  method: "PATCH",
                                  body: JSON.stringify({
                                    email: m.email,
                                    role: e.target.value,
                                  }),
                                },
                              );
                              members.refetch();
                              notify("Membership role updated.");
                            } catch (err) {
                              setError(err);
                            }
                          }}
                        >
                          {roles.map((r) => (
                            <option key={r} value={r}>
                              {human(r)}
                            </option>
                          ))}
                        </select>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <form
              className="invite-form"
              onSubmit={async (e) => {
                e.preventDefault();
                try {
                  const result = await post<{ token: string }>(
                    `/organizations/${tenant}/invitations`,
                    undefined,
                    Object.fromEntries(new FormData(e.currentTarget)),
                  );
                  setInvite(result.token);
                  notify(
                    "Invitation created. Share the token securely with the invited colleague.",
                  );
                } catch (e) {
                  setError(e);
                }
              }}
            >
              <Field label="Invite by email">
                <input
                  type="email"
                  name="email"
                  required
                  placeholder="colleague@company.in"
                />
              </Field>
              <Field label="Role">
                <select name="role">
                  {roles.map((r) => (
                    <option key={r} value={r}>
                      {human(r)}
                    </option>
                  ))}
                </select>
              </Field>
              <button className="button primary">Create invitation</button>
            </form>
            {invite && (
              <div className="padded">
                <Field label="Single-use invitation token (expires in 7 days)">
                  <input readOnly value={invite} />
                </Field>
                <button
                  className="button"
                  onClick={async () => {
                    await navigator.clipboard.writeText(invite);
                    notify("Invitation token copied.");
                  }}
                >
                  <Copy size={16} />
                  Copy token
                </button>
                <p className="quiet-note">
                  Share this with the invited email address. Automatic
                  invitation email delivery is not configured.
                </p>
              </div>
            )}
          </section>
          <section className="panel section-gap padded">
            <div className="panel-heading">
              <h2>GST registrations & business units</h2>
              <button className="button" onClick={() => setUnitModal(true)}>
                <Plus size={16} />
                Add registration
              </button>
            </div>
            {units.data?.map((u) => (
              <div key={u.id} className="upload-row">
                <b>{u.name}</b>
                <span>{u.gstin || "Unregistered"}</span>
                <span>State {u.state}</span>
              </div>
            ))}
            {!units.data?.length && (
              <p className="muted">
                Your primary profile is active. Add other authorized
                registrations here.
              </p>
            )}
          </section>
          <section className="panel padded section-gap">
            <InvitationAccept
              done={() =>
                client.invalidateQueries({ queryKey: ["organizations"] })
              }
            />
          </section>
        </>
      ) : (
        <>
          <section className="settings-grid">
            <form
              className="panel padded"
              key={data.policy_version}
              onSubmit={saveSettings}
            >
              <p className="eyebrow">POLICY VERSION {data.policy_version}</p>
              <h2>Financial controls</h2>
              <p className="muted">
                Deterministic rules protect every approval and export.
              </p>
              <div className="form-grid">
                <Field
                  label="Tax rounding tolerance (₹)"
                  hint="Maximum ₹1. Applied to arithmetic checks."
                >
                  <input
                    name="roundingTolerance"
                    defaultValue={data.settings.roundingTolerance}
                    inputMode="decimal"
                    required
                  />
                </Field>
                <Field
                  label="PO unit-price tolerance (₹)"
                  hint="Quantity over-allocation is always blocked."
                >
                  <input
                    name="priceTolerance"
                    defaultValue={data.settings.priceTolerance}
                    inputMode="decimal"
                    required
                  />
                </Field>
              </div>
              <label className="setting-toggle">
                <span>
                  <b>Separation of duties</b>
                  <small>
                    Uploader and preparer cannot approve their own invoices.
                  </small>
                </span>
                <input
                  type="checkbox"
                  name="separationOfDuties"
                  defaultChecked={data.settings.separationOfDuties}
                />
              </label>
              <label className="setting-toggle">
                <span>
                  <b>Require purchase order</b>
                  <small>
                    Block approval until a PO is resolved and reconciled.
                  </small>
                </span>
                <input
                  type="checkbox"
                  name="requirePo"
                  defaultChecked={data.settings.requirePo}
                />
              </label>
              <h3>Accounting export mapping</h3>
              <Field
                label="Finance-manager review threshold (₹)"
                hint="Invoices at or above this value route to a finance manager or organization administrator. Other invoices route to an available reviewer."
              >
                <input
                  name="reviewThreshold"
                  inputMode="decimal"
                  defaultValue={data.settings.reviewThreshold || "100000.00"}
                  required
                />
              </Field>
              <Field
                label="Additional required fields"
                hint="Optional comma-separated fields: dueDate, supplierGstin, supplierPan, buyerGstin, poNumber, irn, grossAmount."
              >
                <input
                  name="requiredFields"
                  defaultValue={data.settings.requiredFields.join(", ")}
                />
              </Field>
              <Field
                label="Ordered CSV fields"
                hint="Comma-separated canonical field names."
              >
                <textarea
                  name="exportFields"
                  defaultValue={data.settings.exportFields.join(", ")}
                  required
                />
              </Field>
              <p className="quiet-note">
                Supported: invoiceNumber, invoiceDate, supplierName,
                supplierGstin, buyerGstin, poNumber, financialYear,
                documentType, taxableAmount, cgst, sgst, igst, cess, charges,
                roundOff, total, currency.
              </p>
              <button className="button primary" disabled={busy}>
                <Save size={16} />
                {busy ? "Saving…" : "Save policy"}
              </button>
            </form>
            <div>
              <section className="panel padded">
                <p className="eyebrow violet">DOCUMENT INTELLIGENCE</p>
                <h2>Extraction provider</h2>
                <p>{data.provider}</p>
                <Badge
                  value={
                    data.extraction?.status ||
                    (data.extractionConfigured
                      ? "CONFIGURED"
                      : "NOT_CONFIGURED")
                  }
                />
                <p className="quiet-note">{data.extraction?.message}</p>
                {!data.extractionConfigured && (
                  <div>
                    <h3>Connect Azure Document Intelligence</h3>
                    <ol>
                      <li>
                        Open your Azure Document Intelligence resource → Keys
                        and Endpoint.
                      </li>
                      <li>
                        Set <code>AZURE_DOCUMENT_ENDPOINT</code> and{" "}
                        <code>AZURE_DOCUMENT_KEY</code> in the backend
                        environment or local <code>.env</code>. Keep the key
                        server-side.
                      </li>
                      <li>
                        Restart the backend to load the new environment. For
                        Compose, recreate the backend container; restarting
                        alone does not reload <code>.env</code>.
                      </li>
                    </ol>
                    <p className="quiet-note">
                      Queued invoices resume automatically after configuration.
                      For invoices that already failed, choose Retry processing
                      in Upload Center. You do not need to upload them again.
                    </p>
                  </div>
                )}
                {data.extraction?.testMode && (
                  <p className="quiet-note">
                    Restart with the normal backend command for real invoices.
                    Test-fixture mode is only for synthetic documents.
                  </p>
                )}
                <button
                  className="button"
                  disabled={q.isFetching}
                  onClick={() => q.refetch()}
                >
                  {q.isFetching ? "Checking…" : "Refresh provider status"}
                </button>
                <p className="quiet-note">
                  Configured means the server has locally valid settings;
                  provider authentication is verified during processing.
                  Documents are sent to your configured Azure region.
                  Credentials are never returned to the browser.
                </p>
                <ViewLink view="upload" className="text-link">
                  Open upload center
                  <ArrowUpRight size={15} />
                </ViewLink>
              </section>
              <section className="panel padded section-gap">
                <p className="eyebrow">DOCUMENT ADMISSION</p>
                <h2>Malware scanning</h2>
                <Badge
                  value={data.malwareScanEnabled ? "ENABLED" : "DISABLED"}
                />
                <p className="quiet-note">
                  When enabled, an unavailable scanner blocks document
                  acceptance. Storage remains private, and downloads require
                  current tenant membership.
                </p>
              </section>
              <section className="panel padded section-gap">
                <h2>Invoice numbering</h2>
                <p className="quiet-note">
                  Duplicate identity uses the resolved supplier, normalized
                  alphanumeric invoice number, financial year and document type.
                  Original invoice formatting is preserved for review and
                  export.
                </p>
              </section>
            </div>
          </section>
        </>
      )}
      {unitModal && (
        <Modal title="Add GST registration" onClose={() => setUnitModal(false)}>
          <form
            onSubmit={async (e) => {
              e.preventDefault();
              try {
                await post(
                  "/organizations/" + tenant + "/business-units",
                  undefined,
                  {
                    ...Object.fromEntries(new FormData(e.currentTarget)),
                    financialYear: org.profile.financialYear,
                  },
                );
                setUnitModal(false);
                units.refetch();
                notify("Business registration added.");
              } catch (e) {
                setError(e);
              }
            }}
          >
            {["name", "gstin", "state", "address"].map((k) => (
              <Field key={k} label={human(k)}>
                <input name={k} required={k !== "gstin"} />
              </Field>
            ))}
            <ErrorBox error={error} />
            <button className="button primary">Save registration</button>
          </form>
        </Modal>
      )}
    </>
  );
}
