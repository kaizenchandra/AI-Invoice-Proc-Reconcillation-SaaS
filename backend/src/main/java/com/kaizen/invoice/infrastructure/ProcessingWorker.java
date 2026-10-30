package com.kaizen.invoice.infrastructure;

import com.kaizen.invoice.application.*;
import com.kaizen.invoice.domain.*;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ProcessingWorker {
  private final Database db;
  private final DocumentStorage storage;
  private final DocumentExtractionPort extraction;
  private final InvoiceService invoices;
  private final EvaluationService evaluations;
  private final History history;
  private final boolean enabled;

  public ProcessingWorker(
      Database db,
      DocumentStorage storage,
      DocumentExtractionPort extraction,
      InvoiceService invoices,
      EvaluationService evaluations,
      History history,
      @Value("${app.worker.enabled}") boolean enabled) {
    this.db = db;
    this.storage = storage;
    this.extraction = extraction;
    this.invoices = invoices;
    this.evaluations = evaluations;
    this.history = history;
    this.enabled = enabled;
  }

  @Scheduled(fixedDelay = 2000)
  public void tick() {
    if (enabled) runOne();
  }

  public boolean runOne() {
    // Configuration is an operator prerequisite, not a document failure. Do not consume
    // attempts or fetch private source bytes until the active adapter is configured.
    if (!extraction.configuration().configured()) return false;
    Map<String, Object> job =
        db.transaction(
            () -> {
              var pending =
                  db.optional(
                      "select * from processing_jobs where (state='QUEUED' and available_at<=now()) or (state='RUNNING' and lease_until<now()) order by available_at for update skip locked limit 1");
              if (pending.isEmpty()) return null;
              var j = pending.get();
              UUID token = UUID.randomUUID();
              db.update(
                  "update processing_jobs set state='RUNNING',attempts=attempts+1,lease_until=now()+interval '10 minutes',lease_token=? where id=?",
                  token,
                  j.get("id"));
              j.put("lease_token", token);
              db.update(
                  "update invoices set processing_state='EXTRACTING',error=null where tenant_id=? and id=?",
                  j.get("tenant_id"),
                  j.get("invoice_id"));
              UUID run = UUID.randomUUID();
              j.put("run", run);
              db.update(
                  "insert into extraction_runs(id,tenant_id,invoice_id,provider,model,status) values(?,?,?,'azure-document-intelligence','configured','RUNNING')",
                  run,
                  j.get("tenant_id"),
                  j.get("invoice_id"));
              return j;
            });
    if (job == null) return false;
    UUID tenant = (UUID) job.get("tenant_id"), id = (UUID) job.get("invoice_id");
    try {
      var row = invoices.row(tenant, id, false);
      var result =
          extraction.extract(
              storage.get(row.get("storage_key").toString()), row.get("mime").toString());
      db.transaction(
          () -> {
            var lease =
                db.one("select * from processing_jobs where id=? for update", job.get("id"));
            if (!job.get("lease_token").equals(lease.get("lease_token"))) return null;
            var current = invoices.row(tenant, id, true);
            db.update(
                "update extraction_runs set status='SUCCEEDED',provider=?,model=?,raw=?::jsonb,normalized=?::jsonb,evidence=?::jsonb,ended_at=now() where id=?",
                result.provider(),
                result.model(),
                db.json(result.raw()),
                db.json(result.data()),
                db.json(result.evidence()),
                job.get("run"));
            if (((Number) current.get("version")).longValue() == 0) {
              Map<String, Object> normalized =
                  db.mapper()
                      .convertValue(
                          result.data(),
                          new com.fasterxml.jackson.core.type.TypeReference<
                              Map<String, Object>>() {});
              var suppliers =
                  db.list(
                      "select id from suppliers where tenant_id=? and ((gstin=? and gstin<>'') or (lower(name)=lower(?) and (?::text is null or ?=''))) limit 2",
                      tenant,
                      result.data().supplierGstin(),
                      result.data().supplierName(),
                      result.data().supplierGstin(),
                      result.data().supplierGstin());
              if (suppliers.size() == 1) normalized.put("supplierId", suppliers.get(0).get("id"));
              invoices.storeVersion(
                  tenant, id, db.convert(normalized, InvoiceData.class), "AI_EXTRACTION", null);
            }
            db.update(
                "update invoices set processing_state='VALIDATING' where tenant_id=? and id=?",
                tenant,
                id);
            var evaluation = evaluations.evaluate(tenant, invoices.row(tenant, id, false), true);
            var routed = invoices.row(tenant, id, false);
            var threshold =
                new java.math.BigDecimal(
                    db.one(
                            "select coalesce(settings->>'reviewThreshold','100000') threshold from organizations where id=?",
                            tenant)
                        .get("threshold")
                        .toString());
            boolean high =
                routed.get("total") instanceof java.math.BigDecimal total
                    && total.abs().compareTo(threshold) >= 0;
            if (routed.get("assignee") == null) {
              var reviewer =
                  db.optional(
                      "select user_id from memberships where tenant_id=? and role in "
                          + (high ? "('FINANCE_MANAGER','ORG_ADMIN')" : "('REVIEWER')")
                          + " order by case when role='FINANCE_MANAGER' then 0 else 1 end,user_id limit 1",
                      tenant);
              if (reviewer.isPresent()) {
                UUID assignee = (UUID) reviewer.get().get("user_id");
                db.update(
                    "update invoices set assignee=? where tenant_id=? and id=?",
                    assignee,
                    tenant,
                    id);
                db.update(
                    "update exceptions set assignee=?,status='ASSIGNED' where tenant_id=? and invoice_id=? and status='OPEN'",
                    assignee,
                    tenant,
                    id);
                history.audit(
                    tenant,
                    "worker",
                    "REVIEW_ROUTED",
                    id,
                    Map.of("assignee", assignee, "threshold", threshold),
                    null);
                history.notify(
                    tenant,
                    assignee,
                    id,
                    "Invoice assigned for review",
                    "routing-" + job.get("run"));
              }
            }
            db.update(
                "update invoices set processing_state='PROCESSED',processed_at=now(),error=null where tenant_id=? and id=?",
                tenant,
                id);
            db.update(
                "update processing_jobs set state='DONE',lease_until=null where id=?",
                job.get("id"));
            history.audit(
                tenant,
                "worker",
                "EXTRACTION_COMPLETED",
                id,
                Map.of(
                    "run",
                    job.get("run"),
                    "preservedExistingVersion",
                    ((Number) current.get("version")).longValue() > 0,
                    "blockingFindings",
                    evaluation.findings().size()),
                null);
            history.notifyTeam(
                tenant,
                id,
                "Invoice processing completed — review available",
                "processed-" + job.get("run"));
            return null;
          });
    } catch (Exception e) {
      String message =
          e instanceof Problem
              ? e.getMessage()
              : "Extraction failed ("
                  + e.getClass().getSimpleName()
                  + "). Check provider configuration and document quality.";
      db.transaction(
          () -> {
            var lease =
                db.one("select * from processing_jobs where id=? for update", job.get("id"));
            if (!job.get("lease_token").equals(lease.get("lease_token"))) return null;
            int attempts = ((Number) lease.get("attempts")).intValue();
            boolean terminal = attempts >= 3 || (e instanceof Problem p && p.status == 422);
            db.update(
                "update extraction_runs set status='FAILED',error=?,ended_at=now() where id=?",
                message,
                job.get("run"));
            db.update(
                "update processing_jobs set state=?,error=?,lease_until=null,available_at=now()+(? * interval '1 second') where id=?",
                terminal ? "FAILED" : "QUEUED",
                message,
                (1 << attempts) * 10 + ThreadLocalRandom.current().nextInt(10),
                job.get("id"));
            db.update(
                "update invoices set processing_state=?,error=? where tenant_id=? and id=?",
                terminal ? "FAILED" : "QUEUED",
                message,
                tenant,
                id);
            history.audit(
                tenant,
                "worker",
                terminal ? "PROCESSING_FAILED" : "PROCESSING_RETRY",
                id,
                Map.of("attempt", attempts, "error", message),
                null);
            if (terminal) {
              history.notifyTeam(
                  tenant, id, "Invoice processing failed", "failed-" + job.get("id"));
              db.update(
                  "insert into exceptions(id,tenant_id,invoice_id,version,rule_id,field,severity,explanation,evidence) select ?,tenant_id,id,version,'PROCESSING_FAILURE','document','BLOCKING',?,'{}' from invoices where tenant_id=? and id=?",
                  UUID.randomUUID(),
                  message,
                  tenant,
                  id);
            }
            return null;
          });
    }
    return true;
  }
}
