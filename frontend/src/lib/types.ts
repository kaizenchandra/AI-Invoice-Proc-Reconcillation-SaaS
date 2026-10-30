import type { components } from "./api-schema";
export type User = { id: string; email: string; name: string };
export type ExtractionConfiguration = {
  provider: string;
  model: string;
  configured: boolean;
  testMode: boolean;
  status: string;
  message: string;
  missingVariables: string[];
};
export type Organization = {
  id: string;
  name: string;
  role: string;
  profile: Record<string, string>;
  settings: Settings;
  policy_version: number;
  intake_alias: string;
};
export type Settings = {
  roundingTolerance: string;
  priceTolerance: string;
  separationOfDuties: boolean;
  requirePo: boolean;
  reviewThreshold: string;
  requiredFields: string[];
  exportFields: string[];
};
export type Member = User & { role: string };
type NullableFields<T> = { [K in keyof T]: T[K] | null };
export type Line = NullableFields<Required<components["schemas"]["Line"]>>;
export type InvoiceData = Omit<
  NullableFields<components["schemas"]["InvoiceData"]>,
  "lines"
> & { lines: Line[] };
export type Finding = {
  ruleId: string;
  ruleVersion: string;
  severity: string;
  field: string;
  actual: string;
  expected: string;
  explanation: string;
  resolution: string;
  blocking: boolean;
};
export type Audit = {
  id: string;
  actor: string;
  actor_name?: string;
  action: string;
  resource_id: string;
  created_at: string;
  reason?: string;
  summary: Record<string, unknown>;
};
export type InvoiceRow = {
  id: string;
  invoice_number: string | null;
  supplier_name: string | null;
  invoice_date: string | null;
  total: string | null;
  currency: string | null;
  processing_state: string;
  business_state: string;
  match_status: string;
  assignee: string | null;
  version: number;
  source: string;
  created_at: string;
  error: string | null;
};
export type Match = {
  poLineId: string;
  description: string;
  invoiceQuantity: string;
  ordered: string;
  allocated: string;
  remaining: string;
  quantityDifference: string;
  invoicePrice: string;
  poPrice: string;
  priceDifference: string;
  amount: string;
  status: string;
};
export type Evidence = {
  source: string;
  original?: string;
  confidence?: string | number;
  regions?: { pageNumber: number; polygon: number[] }[];
};
export type Extraction = {
  id: string;
  provider: string;
  model: string;
  status: string;
  normalized: InvoiceData | null;
  evidence: Record<string, Evidence> | null;
  error: string | null;
  started_at: string;
  ended_at: string | null;
};
export type Invoice = InvoiceRow & {
  data: InvoiceData;
  filename: string;
  mime: string;
  creator: string;
  preparer: string | null;
  approver: string | null;
  approved_version: number | null;
  evaluation: {
    findings: Finding[];
    matches: Match[];
    duplicates: (InvoiceRow & {
      data: InvoiceData;
      kind: string;
      evidence: string;
      cleared: boolean;
    })[];
    status: string;
  };
  history: Audit[];
  versions: {
    version: number;
    data: InvoiceData;
    provenance: string;
    actor: string;
    created_at: string;
  }[];
  extractions: Extraction[];
  decisions: {
    decision: string;
    reason: string;
    actor: string;
    version: number;
    created_at: string;
  }[];
};
export type Paged<T> = {
  items: T[];
  total: number;
  page?: number;
  size?: number;
};
export type Supplier = {
  id: string;
  name: string;
  gstin: string;
  pan: string;
  state: string;
  address: string;
  contact: string;
  version: number;
  invoice_count: number;
};
export type PurchaseOrder = {
  id: string;
  supplier_id: string;
  supplier_name: string;
  number: string;
  date: string;
  currency: string;
  status: string;
  version: number;
  total: string;
  lines?: {
    id: string;
    code: string;
    description: string;
    quantity: string;
    price: string;
    tax_rate: string;
    allocated: string;
  }[];
  invoices?: { invoice_id: string; version: number }[];
};
export type Notification = {
  id: string;
  title: string;
  resource_id: string;
  created_at: string;
  read_at: string | null;
  event_key: string;
};
export type ExceptionRow = {
  id: string;
  invoice_id: string;
  invoice_number: string;
  supplier_name: string;
  total: string;
  rule_id: string;
  field: string;
  severity: string;
  explanation: string;
  status: string;
  assignee: string | null;
  created_at: string;
  evidence: Finding;
};
export type ExportBatch = {
  id: string;
  status: string;
  profile: string[];
  reason: string | null;
  error: string | null;
  created_at: string;
  completed_at: string | null;
  record_count: number;
  records?: { invoice_id: string; version: number; data: InvoiceData }[];
};
export type Dashboard = {
  received: number;
  processed: number;
  review: number;
  approval: number;
  approved: number;
  exported: number;
  failed: number;
  matched: number;
  mismatched: number;
  value: string;
  processing_seconds: string | null;
  trend: { day: string; received: number; processed: number }[];
  actions: InvoiceRow[];
  activity: Audit[];
  exceptions: { rule_id: string; count: number }[];
};
