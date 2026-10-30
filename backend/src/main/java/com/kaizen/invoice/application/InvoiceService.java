package com.kaizen.invoice.application;

import static com.kaizen.invoice.domain.Problem.require;

import com.kaizen.invoice.domain.*;
import com.kaizen.invoice.infrastructure.*;
import com.kaizen.invoice.security.Access;
import java.math.BigDecimal;
import java.sql.Date;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class InvoiceService {
  private final Database db;
  private final History history;
  private final EvaluationService evaluations;

  public InvoiceService(Database db, History history, EvaluationService evaluations) {
    this.db = db;
    this.history = history;
    this.evaluations = evaluations;
  }

  public Map<String, Object> row(UUID tenant, UUID id, boolean lock) {
    return db.one(
        "select * from invoices where tenant_id=? and id=?" + (lock ? " for update" : ""),
        tenant,
        id);
  }

  public Map<String, Object> detail(UUID tenant, UUID id) {
    var r = row(tenant, id, false);
    r.put("evaluation", evaluations.evaluate(tenant, r, false));
    r.put(
        "history",
        db.list(
            "select * from audit_events where tenant_id=? and resource_id=? order by created_at desc limit 100",
            tenant,
            id));
    r.put(
        "versions",
        db.list(
            "select version,data,provenance,actor,created_at from invoice_versions where tenant_id=? and invoice_id=? order by version desc",
            tenant,
            id));
    r.put(
        "extractions",
        db.list(
            "select id,provider,model,status,normalized,evidence,error,started_at,ended_at from extraction_runs where tenant_id=? and invoice_id=? order by started_at desc",
            tenant,
            id));
    r.put(
        "decisions",
        db.list(
            "select * from approval_decisions where tenant_id=? and invoice_id=? order by created_at desc",
            tenant,
            id));
    return r;
  }

  public void checkVersion(Map<String, Object> r, long version) {
    require(
        ((Number) r.get("version")).longValue() == version,
        412,
        "This invoice changed while you were reviewing it. Your changes were not saved. Reload and compare before retrying.");
  }

  public Map<String, Object> save(
      Access.Scope scope, UUID id, long version, InvoiceData data, String note) {
    return db.transaction(
        () -> {
          var r = row(scope.tenant(), id, true);
          checkVersion(r, version);
          require(
              !Set.of("APPROVED", "EXPORTED").contains(r.get("business_state")),
              409,
              "Reopen an approved invoice before editing. Exported versions cannot be changed.");
          require(
              !Set.of("QUEUED", "EXTRACTING").contains(r.get("processing_state")),
              409,
              "Wait for processing to finish before editing");
          storeVersion(scope.tenant(), id, data, "MANUAL", scope.user());
          db.update(
              "update invoices set business_state='IN_REVIEW',preparer=?,approver=null,approved_version=null,updated_at=now() where tenant_id=? and id=?",
              scope.user(),
              scope.tenant(),
              id);
          evaluations.evaluate(scope.tenant(), row(scope.tenant(), id, false), true);
          history.audit(
              scope.tenant(),
              scope.user(),
              "INVOICE_CORRECTED",
              id,
              Map.of("previousVersion", version, "version", version + 1),
              note);
          return detail(scope.tenant(), id);
        });
  }

  public void storeVersion(UUID tenant, UUID id, InvoiceData data, String provenance, UUID actor) {
    if (data.supplierId() != null)
      db.one("select id from suppliers where tenant_id=? and id=?", tenant, data.supplierId());
    require(data.lines().size() <= 500, 400, "At most 500 lines are supported per invoice");
    long version = ((Number) row(tenant, id, true).get("version")).longValue() + 1;
    Date date = null;
    try {
      if (data.invoiceDate() != null) date = Date.valueOf(data.invoiceDate());
    } catch (Exception ignored) {
    }
    db.update(
        "insert into invoice_versions(tenant_id,invoice_id,version,data,provenance,actor) values(?,?,?,?::jsonb,?,?)",
        tenant,
        id,
        version,
        db.json(data),
        provenance,
        actor);
    db.update(
        "update invoices set version=?,data=?::jsonb,supplier_id=?,invoice_number=?,normalized_number=?,financial_year=?,document_type=?,invoice_date=?,total=?,currency=?,updated_at=now() where tenant_id=? and id=?",
        version,
        db.json(data),
        data.supplierId(),
        data.invoiceNumber(),
        EvaluationService.normalize(data.invoiceNumber()),
        data.financialYear(),
        data.documentType(),
        date,
        data.total(),
        data.currency(),
        tenant,
        id);
  }

  @SuppressWarnings("unchecked")
  public Map<String, Object> transition(
      Access.Scope s, UUID id, String action, long version, String reason, UUID assigned) {
    return db.transaction(
        () -> {
          // Stable organization lock serializes identity reservations and PO balance changes across
          // invoices.
          db.one("select id from organizations where id=? for update", s.tenant());
          var r = row(s.tenant(), id, true);
          checkVersion(r, version);
          String state = r.get("business_state").toString();
          switch (action) {
            case "revalidate", "reconcile" -> {
              require(version > 0, 409, "No structured invoice exists yet");
              evaluations.evaluate(s.tenant(), r, true);
            }
            case "claim" -> {
              require(
                  Set.of("NEEDS_REVIEW", "IN_REVIEW", "REJECTED").contains(state),
                  409,
                  "Invoice cannot be assigned in this state");
              UUID target = assigned == null ? s.user() : assigned;
              var member =
                  db.one(
                      "select role from memberships where tenant_id=? and user_id=?",
                      s.tenant(),
                      target);
              require(
                  Access.permissions(member.get("role").toString())
                      .contains(Access.Permission.EDIT),
                  400,
                  "Assignee must have review permission");
              db.update(
                  "update invoices set assignee=?,business_state='IN_REVIEW' where tenant_id=? and id=?",
                  target,
                  s.tenant(),
                  id);
              history.notify(
                  s.tenant(),
                  target,
                  id,
                  "Invoice assigned for review",
                  "review-" + id + "-" + version + "-" + target);
            }
            case "submit" -> {
              require(
                  Set.of("NEEDS_REVIEW", "IN_REVIEW", "REJECTED").contains(state),
                  409,
                  "Invoice cannot be submitted in this state");
              require(
                  "PROCESSED".equals(r.get("processing_state")),
                  409,
                  "Processing must complete before submission");
              require(
                  !evaluations.evaluate(s.tenant(), r, true).blocked(),
                  422,
                  "Resolve blocking findings before submission");
              require(assigned != null, 400, "Choose an approver");
              var member =
                  db.one(
                      "select role from memberships where tenant_id=? and user_id=?",
                      s.tenant(),
                      assigned);
              require(
                  Access.permissions(member.get("role").toString())
                      .contains(Access.Permission.APPROVE),
                  400,
                  "Assignee cannot approve invoices");
              db.update(
                  "update invoices set business_state='READY_FOR_APPROVAL',approver=?,preparer=? where tenant_id=? and id=?",
                  assigned,
                  s.user(),
                  s.tenant(),
                  id);
              history.notify(
                  s.tenant(),
                  assigned,
                  id,
                  "Invoice ready for approval",
                  "approval-" + id + "-" + version);
            }
            case "approve" -> {
              require(
                  "READY_FOR_APPROVAL".equals(state),
                  409,
                  "Only submitted invoices can be approved");
              require(
                  s.user().equals(r.get("approver")),
                  403,
                  "This invoice is assigned to another approver");
              var org =
                  db.one(
                      "select settings,policy_version from organizations where id=?", s.tenant());
              var settings = (Map<String, Object>) org.get("settings");
              require(
                  !Boolean.TRUE.equals(settings.get("separationOfDuties"))
                      || (!s.user().equals(r.get("creator"))
                          && !s.user().equals(r.get("preparer"))),
                  403,
                  "Separation of duties requires an approver who did not upload or prepare this invoice");
              var evaluation = evaluations.evaluate(s.tenant(), r, true);
              require(
                  !evaluation.blocked(),
                  422,
                  "The current invoice or PO balances have blocking findings. Reopen review and resolve them.");
              InvoiceData d = db.convert(r.get("data"), InvoiceData.class);
              String identity =
                  d.supplierId()
                      + "|"
                      + EvaluationService.normalize(d.invoiceNumber())
                      + "|"
                      + d.financialYear()
                      + "|"
                      + d.documentType();
              db.update("insert into approved_identities values(?,?,?)", s.tenant(), identity, id);
              Map<UUID, BigDecimal[]> allocations = new TreeMap<>();
              for (var m : evaluation.matches()) {
                UUID line = (UUID) m.get("poLineId");
                var sum =
                    allocations.computeIfAbsent(
                        line, k -> new BigDecimal[] {BigDecimal.ZERO, BigDecimal.ZERO});
                sum[0] = sum[0].add((BigDecimal) m.get("invoiceQuantity"));
                sum[1] = sum[1].add((BigDecimal) m.get("amount"));
              }
              for (var e : allocations.entrySet()) {
                db.one(
                    "select id from po_lines where tenant_id=? and id=? for update",
                    s.tenant(),
                    e.getKey());
                db.update(
                    "insert into po_allocations values(?,?,?,?,?,?)",
                    s.tenant(),
                    id,
                    version,
                    e.getKey(),
                    e.getValue()[0],
                    e.getValue()[1]);
              }
              db.update(
                  "insert into approval_decisions(id,tenant_id,invoice_id,version,actor,decision,reason,policy_version) values(?,?,?,?,?,'APPROVED',?,?)",
                  UUID.randomUUID(),
                  s.tenant(),
                  id,
                  version,
                  s.user(),
                  Objects.toString(reason, "Approved"),
                  org.get("policy_version"));
              db.update(
                  "update invoices set business_state='APPROVED',approved_version=? where tenant_id=? and id=?",
                  version,
                  s.tenant(),
                  id);
            }
            case "reject" -> {
              require(
                  "READY_FOR_APPROVAL".equals(state),
                  409,
                  "Only a submitted invoice can be rejected");
              require(
                  s.user().equals(r.get("approver")),
                  403,
                  "This invoice is assigned to another approver");
              require(
                  reason != null && reason.trim().length() >= 5,
                  400,
                  "Give a meaningful rejection reason");
              db.update(
                  "update invoices set business_state='REJECTED',approver=null where tenant_id=? and id=?",
                  s.tenant(),
                  id);
              db.update(
                  "insert into approval_decisions(id,tenant_id,invoice_id,version,actor,decision,reason,policy_version) select ?,?,?,?,?, 'REJECTED',?,policy_version from organizations where id=?",
                  UUID.randomUUID(),
                  s.tenant(),
                  id,
                  version,
                  s.user(),
                  reason,
                  s.tenant());
              history.notify(
                  s.tenant(),
                  (UUID) r.get("creator"),
                  id,
                  "Invoice returned for correction",
                  "rejected-" + id + "-" + version);
            }
            case "reopen" -> {
              require("APPROVED".equals(state), 409, "Only an unexported approval can be reopened");
              require(
                  reason != null && reason.trim().length() >= 5,
                  400,
                  "A reopening reason is required");
              require(
                  db.list(
                          "select 1 from export_records where tenant_id=? and invoice_id=?",
                          s.tenant(),
                          id)
                      .isEmpty(),
                  409,
                  "An export is prepared or completed; preserve its approved snapshot");
              db.update(
                  "delete from po_allocations where tenant_id=? and invoice_id=?", s.tenant(), id);
              db.update(
                  "delete from approved_identities where tenant_id=? and invoice_id=?",
                  s.tenant(),
                  id);
              db.update(
                  "update invoices set business_state='IN_REVIEW',approved_version=null,approver=null where tenant_id=? and id=?",
                  s.tenant(),
                  id);
            }
            default -> throw new Problem(400, "Unsupported workflow operation");
          }
          history.audit(
              s.tenant(),
              s.user(),
              "INVOICE_" + action.toUpperCase(Locale.ROOT),
              id,
              Map.of("version", version, "previousState", state),
              reason);
          return detail(s.tenant(), id);
        });
  }

  public void clearDuplicate(Access.Scope s, UUID id, UUID candidate, long version, String reason) {
    db.transaction(
        () -> {
          var r = row(s.tenant(), id, true);
          checkVersion(r, version);
          require(
              !Set.of("APPROVED", "EXPORTED").contains(r.get("business_state")),
              409,
              "Reopen review first");
          row(s.tenant(), candidate, false);
          require(
              reason != null && reason.trim().length() >= 10,
              400,
              "Explain why this is a false positive (at least 10 characters)");
          db.update(
              "insert into duplicate_decisions values(?,?,?,?,?,?,now()) on conflict do nothing",
              s.tenant(),
              id,
              candidate,
              version,
              reason,
              s.user());
          history.audit(
              s.tenant(),
              s.user(),
              "DUPLICATE_CLEARED",
              id,
              Map.of("candidate", candidate, "version", version),
              reason);
          evaluations.evaluate(s.tenant(), r, true);
          return null;
        });
  }
}
