package com.kaizen.invoice.application;

import static com.kaizen.invoice.domain.Problem.require;

import com.kaizen.invoice.domain.Problem;
import com.kaizen.invoice.infrastructure.*;
import com.kaizen.invoice.security.Access;
import java.security.*;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class IntakeService {
  private final Database db;
  private final DocumentStorage storage;
  private final DocumentAdmission admission;
  private final History history;

  public IntakeService(
      Database db, DocumentStorage storage, DocumentAdmission admission, History history) {
    this.db = db;
    this.storage = storage;
    this.admission = admission;
    this.history = history;
  }

  public Map<String, Object> upload(
      Access.Scope s,
      String filename,
      byte[] bytes,
      String requestKey,
      UUID batch,
      String source,
      Object metadata) {
    var item = register(s, filename, bytes.length, requestKey, batch, source, metadata);
    UUID id = (UUID) item.get("id");
    String digest = hash(bytes);
    UUID lease = UUID.randomUUID();
    var previous =
        db.optional(
            "select * from invoices where tenant_id=? and request_key=?", s.tenant(), requestKey);
    if (previous.isPresent()) {
      require(
          previous.get().get("sha256").equals(hash(bytes)),
          409,
          "This upload key belongs to different document bytes");
      return previous.get();
    }
    boolean claimed =
        db.transaction(
            () -> {
              var current =
                  db.one(
                      "select *,lease_until>now() lease_active from ingestion_items where tenant_id=? and id=? for update",
                      s.tenant(),
                      id);
              require(
                  current.get("sha256") == null || digest.equals(current.get("sha256")),
                  409,
                  "This upload key belongs to different document bytes");
              if ("ACCEPTED".equals(current.get("status"))) return false;
              require(
                  !"UPLOADING".equals(current.get("status"))
                      || !Boolean.TRUE.equals(current.get("lease_active")),
                  409,
                  "This document is already being accepted. Wait and retry with the same key.");
              require(
                  current.get("sha256") == null || digest.equals(current.get("sha256")),
                  409,
                  "This upload key belongs to different document bytes");
              db.update(
                  "update ingestion_items set status='UPLOADING',sha256=?,attempts=attempts+1,error=null,lease_token=?,lease_until=now()+interval '10 minutes',updated_at=now() where tenant_id=? and id=?",
                  digest,
                  lease,
                  s.tenant(),
                  id);
              return true;
            });
    if (!claimed)
      return db.one("select * from invoices where tenant_id=? and id=?", s.tenant(), id);
    String key = s.tenant() + "/documents/" + id;
    String name = item.get("filename").toString();
    try {
      String mime = admission.validate(bytes);
      storage.put(
          key, bytes,
          mime); // Accepted only after durable object write. Orphan objects are reconciled by the
      // retention runbook.
      String finalName = name;
      return db.transaction(
          () -> {
            var current =
                db.one(
                    "select status,lease_token from ingestion_items where tenant_id=? and id=? for update",
                    s.tenant(),
                    id);
            require(
                lease.equals(current.get("lease_token")),
                409,
                "Upload lease changed. Refresh the batch before retrying.");
            db.update(
                "insert into invoices(id,tenant_id,batch_id,creator,sha256,storage_key,filename,mime,source,source_metadata,request_key) values(?,?,?,?,?,?,?,?,?,?::jsonb,?)",
                id,
                s.tenant(),
                batch,
                s.user(),
                hash(bytes),
                key,
                finalName,
                mime,
                source,
                db.json(metadata),
                requestKey);
            db.update(
                "insert into processing_jobs(id,tenant_id,invoice_id) values(?,?,?)",
                UUID.randomUUID(),
                s.tenant(),
                id);
            history.audit(
                s.tenant(),
                s.user(),
                "DOCUMENT_RECEIVED",
                id,
                Map.of(
                    "source", source, "bytes", bytes.length, "batch", Objects.toString(batch, "")),
                null);
            db.update(
                "update ingestion_items set status='ACCEPTED',invoice_id=?,error=null,lease_until=null,updated_at=now() where tenant_id=? and id=?",
                id,
                s.tenant(),
                id);
            return db.one("select * from invoices where tenant_id=? and id=?", s.tenant(), id);
          });
    } catch (RuntimeException failure) {
      String message =
          failure instanceof Problem
              ? failure.getMessage()
              : "Document acceptance failed. Retry this file with the same upload key.";
      boolean retryable = !(failure instanceof Problem p) || p.status >= 500 || p.status == 409;
      db.transaction(
          () -> {
            int changed =
                db.update(
                    "update ingestion_items set status='FAILED',error=?,retryable=?,lease_until=null,updated_at=now() where tenant_id=? and id=? and lease_token=? and status<>'ACCEPTED'",
                    message,
                    retryable,
                    s.tenant(),
                    id,
                    lease);
            if (changed > 0)
              history.audit(
                  s.tenant(),
                  s.user(),
                  "DOCUMENT_ADMISSION_FAILED",
                  id,
                  Map.of("requestKey", requestKey, "retryable", retryable, "error", message),
                  null);
            return null;
          });
      throw failure;
    }
  }

  public Map<String, Object> register(
      Access.Scope s,
      String filename,
      long size,
      String requestKey,
      UUID batch,
      String source,
      Object metadata) {
    require(
        requestKey != null && requestKey.length() >= 8 && requestKey.length() <= 200,
        400,
        "Provide a stable upload idempotency key");
    require(size >= 0, 400, "File size must be non-negative");
    String name = Objects.toString(filename, "document").replaceAll("[\\p{Cntrl}/\\\\]", "_");
    require(!name.isBlank() && name.length() <= 240, 400, "Filename must contain 1–240 characters");
    return db.transaction(
        () -> {
          if (batch != null) {
            db.one(
                "select id from ingestion_batches where tenant_id=? and id=? for update",
                s.tenant(),
                batch);
          }
          var existing =
              db.optional(
                  "select * from ingestion_items where tenant_id=? and request_key=?",
                  s.tenant(),
                  requestKey);
          if (existing.isPresent()) {
            require(
                Objects.equals(existing.get().get("batch_id"), batch),
                409,
                "Upload key belongs to another batch");
            return existing.get();
          }
          if (batch != null)
            require(
                ((Number)
                            db.one(
                                    "select count(*) n from ingestion_items where tenant_id=? and batch_id=?",
                                    s.tenant(),
                                    batch)
                                .get("n"))
                        .intValue()
                    < 100,
                400,
                "A batch may contain at most 100 files");
          boolean valid = size > 0 && size <= 20 * 1024 * 1024;
          UUID id = UUID.randomUUID();
          db.update(
              "insert into ingestion_items(id,tenant_id,batch_id,actor,request_key,filename,file_size,source,source_metadata,status,error,retryable) values(?,?,?,?,?,?,?,?,?::jsonb,?,?,?) on conflict(tenant_id,request_key) do nothing",
              id,
              s.tenant(),
              batch,
              s.user(),
              requestKey,
              name,
              size,
              source,
              db.json(metadata),
              valid ? "AWAITING_UPLOAD" : "FAILED",
              valid ? null : "Each document must be between 1 byte and 20 MB",
              valid);
          return db.one(
              "select * from ingestion_items where tenant_id=? and request_key=?",
              s.tenant(),
              requestKey);
        });
  }

  public List<Map<String, Object>> items(UUID tenant, UUID batch) {
    db.one("select id from ingestion_batches where tenant_id=? and id=?", tenant, batch);
    return db.list(
        "select x.id item_id,x.invoice_id id,x.request_key,x.filename,x.file_size,x.status intake_status,x.retryable,coalesce(i.processing_state,case when x.status='UPLOADING' and x.lease_until<now() then 'INTERRUPTED' else x.status end) processing_state,coalesce(i.error,x.error) error,x.attempts,x.source from ingestion_items x left join invoices i on i.tenant_id=x.tenant_id and i.id=x.invoice_id where x.tenant_id=? and x.batch_id=? order by x.created_at,x.id",
        tenant,
        batch);
  }

  public void retry(Access.Scope s, UUID id) {
    db.transaction(
        () -> {
          var row =
              db.one(
                  "select * from invoices where tenant_id=? and id=? for update", s.tenant(), id);
          require(
              Set.of("FAILED", "PROCESSED").contains(row.get("processing_state")),
              409,
              "Document already has active processing");
          require(
              !Set.of("APPROVED", "EXPORTED", "READY_FOR_APPROVAL")
                  .contains(row.get("business_state")),
              409,
              "Reopen this invoice before reprocessing");
          db.update(
              "insert into processing_jobs(id,tenant_id,invoice_id) values(?,?,?)",
              UUID.randomUUID(),
              s.tenant(),
              id);
          db.update(
              "update invoices set processing_state='QUEUED',error=null where tenant_id=? and id=?",
              s.tenant(),
              id);
          history.audit(
              s.tenant(),
              s.user(),
              "REPROCESS_REQUESTED",
              id,
              Map.of("preserveVersion", row.get("version")),
              null);
          return null;
        });
  }

  public static String hash(byte[] bytes) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }
}
