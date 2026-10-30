export class ApiError extends Error {
  constructor(
    public status: number,
    message: string,
  ) {
    super(message);
  }
}
export async function csrf() {
  const r = await fetch("/api/v1/csrf", {
    credentials: "same-origin",
    cache: "no-store",
  });
  if (!r.ok)
    throw new ApiError(
      r.status,
      "Cannot establish a secure session. Please retry.",
    );
  return r.json() as Promise<{ header: string; token: string }>;
}
export async function api<T>(
  path: string,
  tenant?: string,
  options: RequestInit = {},
): Promise<T> {
  const headers = new Headers(options.headers);
  if (tenant) headers.set("X-Organization-ID", tenant);
  if (
    options.body &&
    !(options.body instanceof FormData) &&
    !(options.body instanceof URLSearchParams)
  )
    headers.set("Content-Type", "application/json");
  if (options.method && options.method !== "GET") {
    const token = await csrf();
    headers.set(token.header, token.token);
  }
  const r = await fetch("/api/v1" + path, {
    ...options,
    headers,
    credentials: "same-origin",
    cache: "no-store",
  });
  if (!r.ok) {
    let detail = "The service could not complete this request. Please retry.";
    try {
      const problem = await r.json();
      detail = problem.detail || detail;
    } catch {}
    throw new ApiError(r.status, detail);
  }
  if (r.status === 204 || r.headers.get("content-length") === "0")
    return undefined as T;
  const text = await r.text();
  return text ? JSON.parse(text) : (undefined as T);
}
export function post<T>(
  path: string,
  tenant: string | undefined,
  data: unknown = {},
  headers?: HeadersInit,
) {
  return api<T>(path, tenant, {
    method: "POST",
    body: JSON.stringify(data),
    headers,
  });
}
export async function download(
  path: string,
  tenant: string,
  filename?: string,
) {
  const r = await fetch("/api/v1" + path, {
    headers: { "X-Organization-ID": tenant },
    credentials: "same-origin",
  });
  if (!r.ok)
    throw new ApiError(
      r.status,
      "Download unavailable. Check permissions and processing status.",
    );
  const url = URL.createObjectURL(await r.blob());
  if (filename) {
    const a = document.createElement("a");
    a.href = url;
    a.download = filename;
    a.click();
    setTimeout(() => URL.revokeObjectURL(url), 5000);
  }
  return url;
}
// Exact Indian grouping without converting monetary strings through IEEE-754 numbers.
export function money(value: string | number | null | undefined) {
  if (value == null) return "—";
  const raw = String(value);
  if (!/^-?\d+(\.\d+)?$/.test(raw)) return "—";
  const negative = raw.startsWith("-");
  const [whole, part = ""] = raw.replace("-", "").split(".");
  const last = whole.slice(-3),
    lead = whole.slice(0, -3).replace(/\B(?=(\d{2})+(?!\d))/g, ",");
  return `${negative ? "−" : ""}₹${lead ? lead + "," : ""}${last}.${part.replace(/0+$/, "").padEnd(2, "0")}`;
}
export function human(value: string | undefined | null) {
  return (value || "Unknown")
    .toLowerCase()
    .replaceAll("_", " ")
    .replace(/^./, (c) => c.toUpperCase());
}
export function date(value: string | null | undefined) {
  return value
    ? new Date(value).toLocaleDateString("en-IN", {
        day: "numeric",
        month: "short",
        year: "numeric",
      })
    : "—";
}
