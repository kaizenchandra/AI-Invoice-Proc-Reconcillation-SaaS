CREATE TABLE ingestion_items (
  id uuid PRIMARY KEY,
  tenant_id uuid NOT NULL REFERENCES organizations(id),
  batch_id uuid,
  actor uuid NOT NULL REFERENCES app_users(id),
  request_key text NOT NULL,
  filename text NOT NULL,
  file_size bigint NOT NULL CHECK(file_size >= 0),
  source text NOT NULL,
  source_metadata jsonb NOT NULL DEFAULT '{}',
  sha256 text,
  status text NOT NULL DEFAULT 'AWAITING_UPLOAD' CHECK(status IN ('AWAITING_UPLOAD','UPLOADING','ACCEPTED','FAILED')),
  invoice_id uuid,
  attempts integer NOT NULL DEFAULT 0,
  error text,
  retryable boolean NOT NULL DEFAULT true,
  lease_token uuid,
  lease_until timestamptz,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE(tenant_id,id),
  UNIQUE(tenant_id,request_key),
  FOREIGN KEY(tenant_id,batch_id) REFERENCES ingestion_batches(tenant_id,id),
  FOREIGN KEY(tenant_id,invoice_id) REFERENCES invoices(tenant_id,id)
);
CREATE INDEX intake_batch_items ON ingestion_items(tenant_id,batch_id,created_at);
INSERT INTO ingestion_items(id,tenant_id,batch_id,actor,request_key,filename,file_size,source,source_metadata,sha256,status,invoice_id,attempts,created_at)
  SELECT id,tenant_id,batch_id,creator,request_key,filename,0,source,source_metadata,sha256,'ACCEPTED',id,1,created_at FROM invoices;
ALTER TABLE email_events ADD COLUMN batch_id uuid;
ALTER TABLE email_events ADD CONSTRAINT email_batch_tenant FOREIGN KEY(tenant_id,batch_id) REFERENCES ingestion_batches(tenant_id,id);
