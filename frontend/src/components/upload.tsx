"use client";
import { useState, useRef } from "react";
import { useQuery } from "@tanstack/react-query";
import {
  UploadCloud,
  FileText,
  X,
  ArrowUpRight,
  ArrowRight,
  Mail,
  FolderUp,
  ShieldCheck,
  RefreshCw,
  Download,
  Check,
} from "lucide-react";
import { api, csrf, post, date, download } from "@/lib/api";
import type { ExtractionConfiguration } from "@/lib/types";
import { useWorkspace } from "./workspace";
import { PageHeader, Field, ErrorBox, Empty, Badge, ViewLink } from "./ui";
type Item = {
  key: string;
  file: File;
  progress: number;
  status: string;
  id?: string;
  error?: string;
};
type Batch = {
  id: string;
  created_at: string;
  files: number;
  completed: number;
  failed: number;
};
type IntakeItem = {
  item_id: string;
  id: string | null;
  request_key: string;
  filename: string;
  file_size: number;
  processing_state: string;
  error: string | null;
  retryable: boolean;
  source: string;
};
export function UploadCenter() {
  const { tenant, notify, can } = useWorkspace();
  const [items, setItems] = useState<Item[]>([]),
    [drag, setDrag] = useState(false),
    [busy, setBusy] = useState(false),
    [mode, setMode] = useState("files"),
    [error, setError] = useState<unknown>();
  const input = useRef<HTMLInputElement>(null);
  const recoveryInput = useRef<HTMLInputElement>(null);
  const [recovering, setRecovering] = useState<IntakeItem | null>(null);
  const batch = useRef<string | null>(null);
  const batches = useQuery({
    queryKey: [tenant, "batches"],
    queryFn: () => api<Batch[]>("/import-batches", tenant),
    refetchInterval: 5000,
  });
  const settings = useQuery({
    queryKey: [tenant, "settings"],
    queryFn: () =>
      api<{
        emailAddress: string;
        extractionConfigured: boolean;
        extraction: ExtractionConfiguration;
        malwareScanEnabled: boolean;
      }>("/settings", tenant),
    refetchInterval: 10000,
  });
  const [opened, setOpened] = useState("");
  const detail = useQuery({
    queryKey: [tenant, "batch", opened],
    queryFn: () => api<IntakeItem[]>("/import-batches/" + opened, tenant),
    enabled: !!opened,
    refetchInterval: 5000,
  });
  function add(files: FileList | File[]) {
    const added = Array.from(files).map((file) => ({
      key: crypto.randomUUID(),
      file,
      progress: 0,
      status: file.size > 20 * 1024 * 1024 ? "FAILED" : "READY",
      error: file.size > 20 * 1024 * 1024 ? "File exceeds 20 MB" : undefined,
    }));
    setItems((current) => [...current, ...added].slice(0, 100));
  }
  function update(key: string, values: Partial<Item>) {
    setItems((rows) =>
      rows.map((r) => (r.key === key ? { ...r, ...values } : r)),
    );
  }
  async function upload(item: Item, batchId: string) {
    update(item.key, { status: "UPLOADING", error: undefined, progress: 0 });
    try {
      const token = await csrf();
      await new Promise<void>((resolve, reject) => {
        const xhr = new XMLHttpRequest();
        xhr.open("POST", "/api/v1/invoices/uploads");
        xhr.setRequestHeader("X-Organization-ID", tenant);
        xhr.setRequestHeader("Idempotency-Key", item.key);
        xhr.setRequestHeader(token.header, token.token);
        xhr.upload.onprogress = (e) => {
          if (e.lengthComputable)
            update(item.key, {
              progress: Math.round((e.loaded / e.total) * 100),
            });
        };
        xhr.onload = () => {
          try {
            const data = JSON.parse(xhr.responseText);
            if (xhr.status >= 200 && xhr.status < 300) {
              update(item.key, {
                status: "ACCEPTED",
                progress: 100,
                id: data.id,
              });
              resolve();
            } else reject(Error(data.detail || "Upload failed"));
          } catch {
            reject(Error("Could not read upload response. Retry safely."));
          }
        };
        xhr.onerror = () =>
          reject(Error("Network interrupted. Retry uses the same upload key."));
        const form = new FormData();
        form.set("file", item.file);
        form.set("batchId", batchId);
        xhr.send(form);
      });
    } catch (e) {
      update(item.key, {
        status: "FAILED",
        error: e instanceof Error ? e.message : "Upload failed",
      });
    }
  }
  async function submit(target?: Item) {
    setBusy(true);
    try {
      if (!batch.current)
        batch.current = (
          await post<{ id: string }>("/import-batches", tenant)
        ).id;
      await post(
        "/import-batches/" + batch.current + "/items",
        tenant,
        (target ? [target] : items).map((i) => ({
          filename: i.file.name,
          size: i.file.size,
          requestKey: i.key,
        })),
      );
      for (const item of target
        ? [target]
        : items.filter((i) => i.status === "READY"))
        await upload(item, batch.current);
      setOpened(batch.current);
      batches.refetch();
      detail.refetch();
      notify("Upload outcomes saved. Processing continues in the background.");
    } catch (e) {
      setError(e);
    } finally {
      setBusy(false);
    }
  }
  function summary() {
    const rows = [
      "filename,status,error",
      ...items
        .filter((i) => i.status === "FAILED")
        .map((i) =>
          [i.file.name, i.status, i.error || ""]
            .map(
              (s) =>
                '"' +
                (/^[\s]*[=+@-]/.test(s) ? "'" + s : s).replaceAll('"', '""') +
                '"',
            )
            .join(","),
        ),
    ];
    const a = document.createElement("a");
    a.href = URL.createObjectURL(
      new Blob([rows.join("\r\n")], { type: "text/csv" }),
    );
    a.download = "upload-errors.csv";
    a.click();
    URL.revokeObjectURL(a.href);
  }
  return (
    <>
      <input
        ref={recoveryInput}
        type="file"
        className="sr-only"
        aria-label="Reselect interrupted upload"
        onChange={async (e) => {
          const file = e.target.files?.[0],
            item = recovering;
          if (!file || !item) return;
          setBusy(true);
          try {
            if (file.size !== item.file_size)
              throw new Error(
                "Select the original file with the same size, or upload a corrected file as a new document.",
              );
            const restored: Item = {
              key: item.request_key,
              file,
              progress: 0,
              status: "READY",
            };
            setItems((rows) => [
              ...rows.filter((r) => r.key !== restored.key),
              restored,
            ]);
            await upload(restored, opened);
            await detail.refetch();
            await batches.refetch();
          } catch (err) {
            setError(err);
          } finally {
            setBusy(false);
            setRecovering(null);
            e.target.value = "";
          }
        }}
      />
      <PageHeader
        title="Give your invoices a head start."
        eyebrow="UPLOAD CENTER"
        description="Drop in your documents. We’ll extract the details and bring exceptions into focus."
      />
      <div className="tabs">
        <button
          aria-pressed={mode === "files"}
          className={mode === "files" ? "active" : ""}
          onClick={() => setMode("files")}
        >
          <FolderUp size={17} />
          File & bulk upload
        </button>
        <button
          aria-pressed={mode === "email"}
          className={mode === "email" ? "active" : ""}
          onClick={() => setMode("email")}
        >
          <Mail size={17} />
          Email intake
        </button>
      </div>
      <ErrorBox error={error} />
      {mode === "email" ? (
        <section className="panel padded">
          <p className="eyebrow">YOUR ORGANIZATION’S INBOX</p>
          <h2>Invoices, straight from email.</h2>
          <p>
            Configure a signed Mailgun inbound route to accept attachments
            securely. Sender, subject and message reference stay attached to
            each invoice.
          </p>
          <Field label="Intake address">
            <input readOnly value={settings.data?.emailAddress || "Loading…"} />
          </Field>
          {settings.data?.emailAddress !== "Not configured" && (
            <button
              className="button"
              onClick={async () => {
                await navigator.clipboard.writeText(
                  settings.data?.emailAddress || "",
                );
                notify("Intake address copied.");
              }}
            >
              Copy address
            </button>
          )}
          <p className="quiet-note">
            An administrator must configure the intake domain and webhook
            signing key on the server. The application will reject unsigned or
            expired requests.
          </p>
        </section>
      ) : (
        <>
          <div
            className={`dropzone ${drag ? "dragging" : ""}`}
            onDragOver={(e) => {
              e.preventDefault();
              setDrag(true);
            }}
            onDragLeave={() => setDrag(false)}
            onDrop={(e) => {
              e.preventDefault();
              setDrag(false);
              add(e.dataTransfer.files);
            }}
          >
            <div className="upload-emblem">
              <UploadCloud size={34} />
            </div>
            <p className="eyebrow">LESS PAPERWORK. MORE CLARITY.</p>
            <h2>Drop your invoices here</h2>
            <p>
              PDFs, scanned invoices or photos. Up to 100 documents per batch.
            </p>
            <button
              className="button primary"
              onClick={() => input.current?.click()}
            >
              Browse files
              <ArrowUpRight size={16} />
            </button>
            <input
              ref={input}
              type="file"
              accept="application/pdf,image/png,image/jpeg"
              multiple
              className="sr-only"
              onChange={(e) => {
                if (e.target.files) add(e.target.files);
                e.target.value = "";
              }}
            />
            <small>PDF, PNG, JPG · 20 MB per file · 50 pages per PDF</small>
          </div>
          <div className="upload-assurance">
            <span>
              <ShieldCheck size={16} />
              Private document storage
            </span>
            <span>
              <Check size={16} />
              Independent file retries
            </span>
            <span>
              <FileText size={16} />
              Original documents preserved
            </span>
          </div>
          {settings.data &&
            (!settings.data.extractionConfigured ||
              settings.data.extraction?.testMode) && (
              <div className="notice">
                <div>
                  <b>
                    {settings.data.extraction?.testMode
                      ? "Synthetic test provider active"
                      : "Extraction setup required"}
                  </b>
                  <p>
                    {settings.data.extraction?.testMode
                      ? "This backend processes synthetic test fixtures only. Start the normal backend with Azure credentials for real invoices."
                      : "Your uploaded documents are stored safely and remain queued until the extraction provider is configured. No processing retries are used while setup is missing."}
                  </p>
                  {!settings.data.extractionConfigured && (
                    <p>
                      {can("admin")
                        ? settings.data.extraction?.message
                        : "Contact your organization administrator to connect the extraction provider."}
                    </p>
                  )}
                </div>
                {can("admin") && (
                  <ViewLink view="settings" className="button">
                    Open extraction setup
                    <ArrowRight size={16} />
                  </ViewLink>
                )}
              </div>
            )}
          {items.length > 0 && (
            <section className="panel">
              <div className="panel-heading">
                <div>
                  <h2>
                    Upload queue <span className="count">{items.length}</span>
                  </h2>
                  <p>
                    {items.filter((i) => i.status === "ACCEPTED").length}{" "}
                    accepted ·{" "}
                    {items.filter((i) => i.status === "FAILED").length} need
                    attention
                  </p>
                </div>
                <div className="button-row">
                  {items.some((i) => i.status === "FAILED") && (
                    <button className="button" onClick={summary}>
                      <Download size={15} />
                      Error summary
                    </button>
                  )}
                  <button
                    className="button primary"
                    disabled={busy || !items.some((i) => i.status === "READY")}
                    onClick={() => submit()}
                  >
                    {busy ? "Uploading…" : "Upload documents"}
                  </button>
                </div>
              </div>
              {items.map((item) => (
                <div className="upload-row" key={item.key}>
                  <span className="file-mini">
                    <FileText size={18} />
                  </span>
                  <div className="upload-file">
                    <b>{item.file.name}</b>
                    <small>
                      {(item.file.size / 1024 / 1024).toFixed(2)} MB
                      {item.error ? " · " + item.error : ""}
                    </small>
                    {item.status === "UPLOADING" && (
                      <progress
                        aria-label={"Uploading " + item.file.name}
                        value={item.progress}
                        max={100}
                      />
                    )}
                  </div>
                  <Badge value={item.status} />
                  {item.status === "READY" && (
                    <button
                      className="icon-button"
                      aria-label={"Remove " + item.file.name}
                      onClick={() =>
                        setItems(items.filter((i) => i.key !== item.key))
                      }
                    >
                      <X size={17} />
                    </button>
                  )}
                  {item.status === "FAILED" && (
                    <button
                      className="button"
                      disabled={busy}
                      onClick={() => submit(item)}
                    >
                      Retry
                    </button>
                  )}
                  {item.id && (
                    <ViewLink view="invoice" id={item.id} className="text-link">
                      Open
                      <ArrowUpRight size={15} />
                    </ViewLink>
                  )}
                </div>
              ))}
            </section>
          )}
        </>
      )}
      <section className="panel section-gap">
        <div className="panel-heading">
          <div>
            <h2>Processing history</h2>
            <p>Durable batches and individual document outcomes</p>
          </div>
          <RefreshCw size={17} />
        </div>
        <ErrorBox error={batches.error} />
        {batches.data?.length ? (
          <div className="batch-list">
            {batches.data.map((b) => (
              <button
                key={b.id}
                className={`batch-row ${opened === b.id ? "selected" : ""}`}
                onClick={() => setOpened(b.id)}
              >
                <span>
                  <b>Batch {b.id.slice(0, 8)}</b>
                  <small>{date(b.created_at)}</small>
                </span>
                <span>{b.files} documents</span>
                <span>{b.completed} processed</span>
                <span className={b.failed ? "bad" : "muted"}>
                  {b.failed} failed
                </span>
                <ArrowRight size={16} />
              </button>
            ))}
          </div>
        ) : (
          <Empty title="Your first batch is one upload away">
            Processing history stays here, even after you leave this page.
          </Empty>
        )}
        {opened && (
          <div className="batch-detail">
            <button
              className="button"
              onClick={async () => {
                try {
                  await download(
                    "/import-batches/" + opened + "/errors",
                    tenant,
                    "intake-errors-" + opened + ".csv",
                  );
                } catch (e) {
                  setError(e);
                }
              }}
            >
              <Download size={15} />
              Download batch error summary
            </button>
            <ErrorBox error={detail.error} />
            {detail.data?.map((i) => (
              <div className="upload-row" key={i.item_id}>
                <div className="upload-file">
                  <b>{i.filename}</b>
                  {i.error && <small className="bad">{i.error}</small>}
                </div>
                <Badge value={i.processing_state} />
                {i.id && i.processing_state === "FAILED" && (
                  <button
                    className="button"
                    onClick={async () => {
                      try {
                        await post("/invoices/" + i.id + "/reprocess", tenant);
                        detail.refetch();
                        notify("Processing retry queued.");
                      } catch (e) {
                        setError(e);
                      }
                    }}
                  >
                    Retry processing
                  </button>
                )}
                {!i.id &&
                  i.source === "UPLOAD" &&
                  i.retryable &&
                  ["AWAITING_UPLOAD", "FAILED", "INTERRUPTED"].includes(
                    i.processing_state,
                  ) && (
                    <button
                      className="button"
                      disabled={busy}
                      onClick={() => {
                        setRecovering(i);
                        recoveryInput.current?.click();
                      }}
                    >
                      Select original file to retry
                    </button>
                  )}
                {!i.id && !i.retryable && (
                  <small>
                    Rejected before acceptance. Upload a corrected document as a
                    new file.
                  </small>
                )}
                {!i.id && i.source === "EMAIL" && i.retryable && (
                  <small>
                    Awaiting provider redelivery; accepted attachments will not
                    be duplicated.
                  </small>
                )}
                {i.id && (
                  <ViewLink view="invoice" id={i.id} className="text-link">
                    Open
                    <ArrowUpRight size={15} />
                  </ViewLink>
                )}
              </div>
            ))}
          </div>
        )}
      </section>
    </>
  );
}
