"use client";
import { useState, useEffect, createContext, useContext } from "react";
import {
  QueryClient,
  QueryClientProvider,
  useQuery,
  useQueryClient,
} from "@tanstack/react-query";
import { useSearchParams, useRouter } from "next/navigation";
import {
  LayoutDashboard,
  Files,
  UploadCloud,
  ScanLine,
  GitCompareArrows,
  CheckCheck,
  Building2,
  ShoppingBag,
  Download,
  TriangleAlert,
  History,
  Settings,
  Bell,
  Search,
  ChevronDown,
  PanelLeftClose,
  Menu,
  LogOut,
  Plus,
  ArrowRight,
  ShieldCheck,
  Users,
} from "lucide-react";
import { api, post, ApiError } from "@/lib/api";
import type { User, Organization, Notification } from "@/lib/types";
import { ErrorBox, Loading, Field, Modal, ViewLink } from "./ui";
import {
  Overview,
  Inbox,
  Activity,
  Exceptions,
  Notifications,
  Exports,
} from "./operations";
import { UploadCenter } from "./upload";
import { ReviewStudio } from "./review";
import { MasterData, OrganizationSettings } from "./management";
type Context = {
  tenant: string;
  org: Organization;
  user: User;
  can: (permission: string) => boolean;
  notify: (message: string) => void;
};
const Context = createContext<Context | null>(null);
export function useWorkspace() {
  const context = useContext(Context);
  if (!context) throw Error("Workspace missing");
  return context;
}
const permissions: Record<string, string[]> = {
  ORG_ADMIN: [
    "read",
    "upload",
    "edit",
    "master",
    "approve",
    "export",
    "admin",
    "resolve",
  ],
  PLATFORM_ADMIN: [
    "read",
    "upload",
    "edit",
    "master",
    "approve",
    "export",
    "admin",
    "resolve",
  ],
  FINANCE_MANAGER: [
    "read",
    "upload",
    "edit",
    "master",
    "approve",
    "export",
    "resolve",
  ],
  PROCESSOR: ["read", "upload", "edit", "master"],
  REVIEWER: ["read", "edit", "resolve"],
  APPROVER: ["read", "approve"],
  AUDITOR: ["read"],
};
export default function Workspace() {
  const [client] = useState(
    () =>
      new QueryClient({
        defaultOptions: {
          queries: { retry: 1, refetchOnWindowFocus: false, staleTime: 10000 },
        },
      }),
  );
  return (
    <QueryClientProvider client={client}>
      <Session />
    </QueryClientProvider>
  );
}
function Session() {
  const client = useQueryClient();
  const me = useQuery({
    queryKey: ["me"],
    queryFn: () => api<User>("/me"),
    retry: false,
  });
  const orgs = useQuery({
    queryKey: ["organizations"],
    queryFn: () => api<Organization[]>("/organizations"),
    enabled: !!me.data,
  });
  const [selected, setSelected] = useState("");
  const [creating, setCreating] = useState(false);
  const [toast, setToast] = useState("");
  const [navOpen, setNavOpen] = useState(false);
  const [collapsed, setCollapsed] = useState(false);
  const [mobile, setMobile] = useState(false);
  useEffect(() => {
    const media = window.matchMedia("(max-width: 900px)");
    const update = () => setMobile(media.matches);
    update();
    media.addEventListener("change", update);
    return () => media.removeEventListener("change", update);
  }, []);
  useEffect(() => {
    if (!navOpen) return;
    const root = document.querySelector<HTMLElement>(".sidebar");
    const controls = () =>
      Array.from(root?.querySelectorAll<HTMLElement>("a,button") || []).filter(
        (el) => el.offsetParent !== null,
      );
    controls()[0]?.focus();
    const key = (e: KeyboardEvent) => {
      if (e.key === "Escape") {
        setNavOpen(false);
        document.querySelector<HTMLButtonElement>(".mobile-menu")?.focus();
      }
      if (e.key === "Tab") {
        const all = controls();
        if (e.shiftKey && document.activeElement === all[0]) {
          e.preventDefault();
          all.at(-1)?.focus();
        } else if (!e.shiftKey && document.activeElement === all.at(-1)) {
          e.preventDefault();
          all[0]?.focus();
        }
      }
    };
    document.addEventListener("keydown", key);
    return () => document.removeEventListener("keydown", key);
  }, [navOpen]);
  const params = useSearchParams(),
    router = useRouter();
  const view = params.get("view") || "overview",
    id = params.get("id") || "";
  const org = orgs.data?.find((o) => o.id === selected) || orgs.data?.[0];
  useEffect(() => {
    if (me.data)
      setSelected(localStorage.getItem("kaizen-org-" + me.data.id) || "");
  }, [me.data]);
  useEffect(() => {
    if (!toast) return;
    const t = setTimeout(() => setToast(""), 6000);
    return () => clearTimeout(t);
  }, [toast]);
  const notifications = useQuery({
    queryKey: [org?.id, "notifications"],
    queryFn: () => api<Notification[]>("/notifications", org?.id),
    enabled: !!org,
    refetchInterval: 30000,
  });
  if (me.isPending) return <Loading />;
  if (me.error) {
    if (me.error instanceof ApiError && me.error.status === 401)
      return <Authentication onDone={() => client.invalidateQueries()} />;
    return (
      <main className="auth-page">
        <ErrorBox error={me.error} retry={() => me.refetch()} />
      </main>
    );
  }
  if (orgs.isPending) return <Loading />;
  if (orgs.error)
    return <ErrorBox error={orgs.error} retry={() => orgs.refetch()} />;
  if (!org) return <Onboarding done={() => orgs.refetch()} />;
  const can = (p: string) => (permissions[org.role] || ["read"]).includes(p);
  const navigation = [
    {
      label: "WORKSPACE",
      items: [
        ["overview", "Overview", LayoutDashboard, "read"],
        ["inbox", "Invoice inbox", Files, "read"],
        ["upload", "Upload center", UploadCloud, "upload"],
        ["review", "Review queue", ScanLine, "edit"],
        ["reconciliation", "Reconciliation", GitCompareArrows, "read"],
        ["approvals", "Approvals", CheckCheck, "approve"],
      ],
    },
    {
      label: "MANAGEMENT",
      items: [
        ["suppliers", "Suppliers", Building2, "read"],
        ["purchase-orders", "Purchase orders", ShoppingBag, "read"],
        ["exports", "Accounting exports", Download, "export"],
      ],
    },
    {
      label: "INTELLIGENCE",
      items: [
        ["exceptions", "Exceptions", TriangleAlert, "read"],
        ["activity", "Activity & audit", History, "read"],
      ],
    },
    {
      label: "ADMINISTRATION",
      items: [
        ["organization", "Organization & team", Users, "admin"],
        ["settings", "Settings", Settings, "admin"],
      ],
    },
  ] as const;
  const title =
    navigation
      .map((g) => g.items.find((x) => x[0] === view)?.[1])
      .find(Boolean) || "Invoice review";
  async function switchOrg(value: string) {
    if (
      !window.dispatchEvent(
        new Event("kaizen:before-navigate", { cancelable: true }),
      )
    )
      return;
    client.cancelQueries();
    client.removeQueries({
      predicate: (q) =>
        q.queryKey[0] !== "me" && q.queryKey[0] !== "organizations",
    });
    setSelected(value);
    localStorage.setItem("kaizen-org-" + me.data!.id, value);
    router.push("/");
  }
  return (
    <Context.Provider
      value={{ tenant: org.id, org, user: me.data!, can, notify: setToast }}
    >
      <div
        className={`workspace ${collapsed ? "collapsed" : ""} ${navOpen ? "nav-open" : ""}`}
      >
        <a className="skip" href="#main-content">
          Skip to workspace
        </a>
        {navOpen && (
          <button
            className="nav-scrim"
            aria-label="Close navigation"
            onClick={() => setNavOpen(false)}
          />
        )}
        <aside
          className="sidebar"
          inert={mobile && !navOpen}
          role={mobile && navOpen ? "dialog" : undefined}
          aria-modal={mobile && navOpen ? true : undefined}
          aria-label="Workspace navigation"
        >
          <ViewLink view="overview" className="brand">
            <span className="brand-mark">
              k<span>·</span>
            </span>
            <span className="brand-text">
              kaizen<span>INVOICE INTELLIGENCE</span>
            </span>
          </ViewLink>
          <div className="workspace-label">
            <span className="workspace-avatar">{org.name.slice(0, 1)}</span>
            <div>
              <b>{org.name}</b>
              <small>Finance workspace</small>
            </div>
            <ShieldCheck size={16} />
          </div>
          <nav aria-label="Main navigation">
            {navigation.map((group) => (
              <div className="nav-group" key={group.label}>
                <p>{group.label}</p>
                {group.items
                  .filter((x) => can(x[3]))
                  .map(([key, label, Icon]) => (
                    <ViewLink
                      key={key}
                      view={key}
                      ariaLabel={label}
                      className={`nav-item ${view === key ? "active" : ""}`}
                    >
                      <Icon size={19} />
                      <span onClick={() => setNavOpen(false)}>{label}</span>
                      {key === "approvals" && (
                        <span className="nav-indicator" />
                      )}
                    </ViewLink>
                  ))}
              </div>
            ))}
          </nav>
          <div className="sidebar-bottom">
            <div className="trust-note">
              <ShieldCheck size={18} />
              <span>
                Every decision.
                <br />
                <b>Accounted for.</b>
              </span>
            </div>
            <button
              className="collapse-button"
              onClick={() => setCollapsed(!collapsed)}
              aria-label={collapsed ? "Expand sidebar" : "Collapse sidebar"}
            >
              <PanelLeftClose size={18} />
              <span>Collapse sidebar</span>
            </button>
          </div>
        </aside>
        <div className="workspace-body">
          <header className="topbar">
            <button
              className="icon-button mobile-menu"
              aria-label="Open navigation"
              onClick={() => setNavOpen(true)}
            >
              <Menu size={21} />
            </button>
            <div className="breadcrumb">
              <span>Workspace</span>
              <span>/</span>
              <b>{title}</b>
            </div>
            <div className="topbar-actions">
              <form
                className="global-search"
                onSubmit={(e) => {
                  e.preventDefault();
                  const q = new FormData(e.currentTarget).get("q");
                  router.push(
                    "/?view=inbox&q=" + encodeURIComponent(String(q)),
                  );
                }}
              >
                <Search size={16} />
                <input
                  name="q"
                  aria-label="Search invoices"
                  placeholder="Search invoices…"
                />
                <kbd>↵</kbd>
              </form>
              <label className="org-switch">
                <Building2 size={16} />
                <select
                  aria-label="Current organization"
                  value={org.id}
                  onChange={(e) => void switchOrg(e.target.value)}
                >
                  {orgs.data?.map((o) => (
                    <option key={o.id} value={o.id}>
                      {o.name}
                    </option>
                  ))}
                </select>
                <ChevronDown size={12} />
              </label>
              <button
                className="icon-button"
                aria-label="Create organization"
                title="Create organization"
                onClick={() => setCreating(true)}
              >
                <Plus size={18} />
              </button>
              <ViewLink
                view="notifications"
                className="icon-button notification-button"
              >
                <Bell size={19} />
                <span className="sr-only">Notifications</span>
                {notifications.data?.some((n) => !n.read_at) && <i />}
              </ViewLink>
              <button
                className="avatar"
                title={`Sign out ${me.data?.name}`}
                aria-label="Sign out"
                onClick={async () => {
                  try {
                    await post("/logout", undefined);
                    client.clear();
                    router.push("/");
                    location.reload();
                  } catch (e) {
                    setToast(
                      e instanceof Error ? e.message : "Sign out failed",
                    );
                  }
                }}
              >
                {me.data?.name.slice(0, 2).toUpperCase()}
                <LogOut size={12} />
              </button>
            </div>
          </header>
          <main id="main-content" key={org.id}>
            {view === "overview" ? (
              <Overview />
            ) : view === "upload" && can("upload") ? (
              <UploadCenter />
            ) : view === "invoice" && id ? (
              <ReviewStudio key={id} id={id} />
            ) : ["inbox", "review", "approvals", "reconciliation"].includes(
                view,
              ) ? (
              <Inbox view={view} />
            ) : view === "suppliers" || view === "purchase-orders" ? (
              <MasterData kind={view} />
            ) : view === "exports" && can("export") ? (
              <Exports />
            ) : view === "activity" ? (
              <Activity />
            ) : view === "exceptions" ? (
              <Exceptions />
            ) : view === "notifications" ? (
              <Notifications />
            ) : (view === "settings" || view === "organization") &&
              can("admin") ? (
              <OrganizationSettings section={view} />
            ) : (
              <ErrorBox error="This destination is not available for your role." />
            )}
          </main>
          <footer className="app-footer">
            <span>KAIZEN WORKSPACE</span>
            <span>Private organization data · India / INR</span>
          </footer>
        </div>
        {toast && (
          <div className="toast" role="status">
            {toast}
            <button
              aria-label="Dismiss notification"
              onClick={() => setToast("")}
            >
              ×
            </button>
          </div>
        )}
        {creating && (
          <Modal
            title="Create another organization"
            onClose={() => setCreating(false)}
          >
            <Onboarding
              compact
              done={() => {
                setCreating(false);
                orgs.refetch();
              }}
            />
          </Modal>
        )}
      </div>
    </Context.Provider>
  );
}
function Authentication({ onDone }: { onDone: () => void }) {
  const [register, setRegister] = useState(false),
    [busy, setBusy] = useState(false),
    [error, setError] = useState<unknown>();
  return (
    <main className="auth-page">
      <section className="auth-story">
        <div className="brand">
          <span className="brand-mark">
            k<span>·</span>
          </span>
          <span className="brand-text">
            kaizen<span>INVOICE INTELLIGENCE</span>
          </span>
        </div>
        <div>
          <p className="eyebrow">A CLEARER WAY TO CLOSE THE BOOKS</p>
          <h1>
            From document
            <br />
            to decision.
            <br />
            <em>With confidence.</em>
          </h1>
          <p>
            One workspace for your invoices, purchase orders
            <br />
            and the decisions that connect them.
          </p>
          <div className="flow-visual">
            <span>
              <Files />
              Capture
            </span>
            <i />
            <span>
              <ScanLine />
              Review
            </span>
            <i />
            <span>
              <CheckCheck />
              Approve
            </span>
          </div>
        </div>
        <small>
          <ShieldCheck size={15} /> Tenant-isolated. Evidence-led. Auditable.
        </small>
      </section>
      <section className="auth-form">
        <div className="auth-card">
          <span className="overline">YOUR FINANCE WORKSPACE</span>
          <h2>{register ? "Make room for better work." : "Welcome back."}</h2>
          <p>
            {register
              ? "Create your account, then set up your organization."
              : "Sign in to pick up where your team left off."}
          </p>
          <form
            onSubmit={async (e) => {
              e.preventDefault();
              setBusy(true);
              setError(undefined);
              const f = new FormData(e.currentTarget);
              try {
                if (register)
                  await post("/register", undefined, {
                    name: f.get("name"),
                    email: f.get("email"),
                    password: f.get("password"),
                  });
                await api("/session", undefined, {
                  method: "POST",
                  body: new URLSearchParams({
                    email: String(f.get("email")),
                    password: String(f.get("password")),
                  }),
                });
                onDone();
              } catch (e) {
                setError(e);
              } finally {
                setBusy(false);
              }
            }}
          >
            {register && (
              <Field label="Full name">
                <input name="name" required minLength={2} autoComplete="name" />
              </Field>
            )}
            <Field label="Work email">
              <input
                name="email"
                type="email"
                placeholder="you@company.in"
                autoComplete="email"
                required
              />
            </Field>
            <Field
              label="Password"
              hint={register ? "At least 12 characters." : ""}
            >
              <input
                name="password"
                type="password"
                minLength={register ? 12 : 1}
                maxLength={72}
                autoComplete={register ? "new-password" : "current-password"}
                required
              />
            </Field>
            <ErrorBox error={error} />
            <button className="button primary wide" disabled={busy}>
              {busy
                ? "Please wait…"
                : register
                  ? "Create account"
                  : "Sign in to workspace"}
              <ArrowRight size={17} />
            </button>
          </form>
          <div className="auth-alternate">
            {register ? "Already have an account?" : "New to Kaizen?"}{" "}
            <button
              onClick={() => {
                setRegister(!register);
                setError(undefined);
              }}
            >
              {register ? "Sign in" : "Create an account"}
            </button>
          </div>
          <p className="auth-footnote">
            Access is limited to your authorized organizations.
            <br />
            Your documents stay within your workspace.
          </p>
        </div>
      </section>
    </main>
  );
}
export function Onboarding({
  done,
  compact = false,
}: {
  done: () => void;
  compact?: boolean;
}) {
  const [error, setError] = useState<unknown>(),
    [busy, setBusy] = useState(false);
  return (
    <div className={compact ? "" : "onboarding"}>
      <div className="onboarding-card">
        <p className="eyebrow">01 / YOUR ORGANIZATION</p>
        <h1>Let’s set up your workspace.</h1>
        <p>
          Start with your business details. Invite your team and import master
          data whenever you’re ready.
        </p>
        <form
          onSubmit={async (e) => {
            e.preventDefault();
            setBusy(true);
            setError(undefined);
            const f = Object.fromEntries(new FormData(e.currentTarget));
            try {
              await post("/organizations", undefined, f);
              done();
            } catch (e) {
              setError(e);
            } finally {
              setBusy(false);
            }
          }}
        >
          <Field label="Legal business name">
            <input name="name" required placeholder="Your company name" />
          </Field>
          <div className="form-grid">
            <Field label="GSTIN (if registered)">
              <input
                name="gstin"
                maxLength={15}
                placeholder="27ABCDE1234F1Z…"
              />
            </Field>
            <Field label="PAN (optional)">
              <input name="pan" maxLength={10} />
            </Field>
            <Field label="State code">
              <input
                name="state"
                pattern="[0-9]{2}"
                required
                placeholder="27"
              />
            </Field>
            <Field label="Financial year">
              <input
                name="financialYear"
                pattern="20[0-9]{2}-20[0-9]{2}"
                required
                placeholder="2026-2027"
              />
            </Field>
          </div>
          <Field label="Registered address">
            <textarea name="address" required />
          </Field>
          <ErrorBox error={error} />
          <button className="button primary" disabled={busy}>
            {busy ? "Creating…" : "Create workspace"}
            <ArrowRight size={16} />
          </button>
        </form>
        <InvitationAccept done={done} />
      </div>
    </div>
  );
}
export function InvitationAccept({ done }: { done: () => void }) {
  const [error, setError] = useState<unknown>();
  return (
    <details className="invitation-accept">
      <summary>Joining an existing organization?</summary>
      <form
        onSubmit={async (e) => {
          e.preventDefault();
          try {
            await post("/invitations/accept", undefined, {
              token: new FormData(e.currentTarget).get("token"),
            });
            done();
          } catch (e) {
            setError(e);
          }
        }}
      >
        <Field label="Invitation token">
          <input name="token" required />
        </Field>
        <button className="button">Accept invitation</button>
        <ErrorBox error={error} />
      </form>
    </details>
  );
}
