package com.kaizen.invoice.application;

import static com.kaizen.invoice.domain.Problem.require;

import com.kaizen.invoice.infrastructure.Database;
import com.kaizen.invoice.security.Access;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class ExportService {
  private final Database db;
  private final History history;
  private final AccountingExportPort exporter;

  public ExportService(Database db, History history, AccountingExportPort exporter) {
    this.db = db;
    this.history = history;
    this.exporter = exporter;
  }

  @SuppressWarnings("unchecked")
  public Map<String, Object> create(Access.Scope s, List<UUID> ids, String key, String reason) {
    require(
        ids != null && !ids.isEmpty() && ids.size() <= 100, 400, "Select 1–100 approved invoices");
    require(new HashSet<>(ids).size() == ids.size(), 400, "Duplicate invoice selection");
    require(
        key != null && key.length() >= 8 && key.length() <= 200,
        400,
        "Supply an export idempotency key");
    var batch =
        db.transaction(
            () -> {
              var org =
                  db.one("select settings from organizations where id=? for update", s.tenant());
              var existing =
                  db.optional(
                      "select id,status from export_batches where tenant_id=? and request_key=?",
                      s.tenant(),
                      key);
              if (existing.isPresent()) {
                Set<Object> previous =
                    new HashSet<>(
                        db
                            .list(
                                "select invoice_id from export_records where tenant_id=? and batch_id=?",
                                s.tenant(),
                                existing.get().get("id"))
                            .stream()
                            .map(r -> r.get("invoice_id"))
                            .toList());
                require(
                    previous.equals(new HashSet<>(ids)),
                    409,
                    "Export key belongs to another selection");
                return existing.get();
              }
              UUID id = UUID.randomUUID();
              var settings = (Map<String, Object>) org.get("settings");
              db.update(
                  "insert into export_batches(id,tenant_id,actor,request_key,profile,reason) values(?,?,?,?,?::jsonb,?)",
                  id,
                  s.tenant(),
                  s.user(),
                  key,
                  db.json(settings.get("exportFields")),
                  reason);
              for (UUID invoice : ids.stream().sorted().toList()) {
                var r =
                    db.one(
                        "select * from invoices where tenant_id=? and id=? for update",
                        s.tenant(),
                        invoice);
                require(
                    Set.of("APPROVED", "EXPORTED").contains(r.get("business_state"))
                        && r.get("approved_version") != null,
                    422,
                    "Every selected invoice must have an approved snapshot");
                var prior =
                    db.list(
                        "select b.status from export_records er join export_batches b on b.tenant_id=er.tenant_id and b.id=er.batch_id where er.tenant_id=? and er.invoice_id=?",
                        s.tenant(),
                        invoice);
                require(
                    prior.stream().noneMatch(p -> !"COMPLETED".equals(p.get("status"))),
                    409,
                    "An earlier export is pending or failed. Retry that export batch.");
                require(
                    prior.isEmpty() || (reason != null && reason.trim().length() >= 10),
                    409,
                    "Previously exported invoices require an explicit re-export reason (10 characters)");
                db.update(
                    "insert into export_records values(?,?,?,?)",
                    s.tenant(),
                    id,
                    invoice,
                    r.get("approved_version"));
              }
              history.audit(
                  s.tenant(), s.user(), "EXPORT_PREPARED", id, Map.of("invoices", ids), reason);
              return Map.<String, Object>of("id", id, "status", "PREPARING");
            });
    if (!"COMPLETED".equals(batch.get("status"))) generate(s, (UUID) batch.get("id"));
    return info(s.tenant(), (UUID) batch.get("id"));
  }

  @SuppressWarnings("unchecked")
  public void generate(Access.Scope s, UUID id) {
    var b =
        db.one(
            "select id,status,profile from export_batches where tenant_id=? and id=?",
            s.tenant(),
            id);
    if ("COMPLETED".equals(b.get("status"))) return;
    try {
      var snapshots =
          db
              .list(
                  "select v.data from export_records e join invoice_versions v on v.tenant_id=e.tenant_id and v.invoice_id=e.invoice_id and v.version=e.version where e.tenant_id=? and e.batch_id=? order by e.invoice_id",
                  s.tenant(),
                  id)
              .stream()
              .map(r -> (Map<String, Object>) r.get("data"))
              .toList();
      byte[] content = exporter.export(snapshots, (List<String>) b.get("profile"));
      db.transaction(
          () -> {
            var current =
                db.one(
                    "select status from export_batches where tenant_id=? and id=? for update",
                    s.tenant(),
                    id);
            if ("COMPLETED".equals(current.get("status"))) return null;
            db.update(
                "update export_batches set status='COMPLETED',content=?,sha256=?,error=null,completed_at=now() where tenant_id=? and id=?",
                content,
                IntakeService.hash(content),
                s.tenant(),
                id);
            db.update(
                "update invoices i set business_state='EXPORTED' where i.tenant_id=? and exists(select 1 from export_records e where e.tenant_id=i.tenant_id and e.invoice_id=i.id and e.batch_id=?)",
                s.tenant(),
                id);
            history.audit(
                s.tenant(),
                s.user(),
                "EXPORT_COMPLETED",
                id,
                Map.of("records", snapshots.size(), "sha256", IntakeService.hash(content)),
                null);
            return null;
          });
    } catch (Exception e) {
      db.transaction(
          () -> {
            db.update(
                "update export_batches set status='FAILED',error='CSV preparation failed; retry this batch' where tenant_id=? and id=? and status<>'COMPLETED'",
                s.tenant(),
                id);
            history.audit(
                s.tenant(),
                s.user(),
                "EXPORT_FAILED",
                id,
                Map.of("errorType", e.getClass().getSimpleName()),
                null);
            history.notify(
                s.tenant(), s.user(), id, "Accounting export failed", "export-failed-" + id);
            return null;
          });
    }
  }

  public Map<String, Object> info(UUID tenant, UUID id) {
    var b =
        db.one(
            "select id,status,profile,reason,error,sha256,created_at,completed_at from export_batches where tenant_id=? and id=?",
            tenant,
            id);
    b.put(
        "records",
        db.list(
            "select e.invoice_id,e.version,v.data from export_records e join invoice_versions v on v.tenant_id=e.tenant_id and v.invoice_id=e.invoice_id and v.version=e.version where e.tenant_id=? and e.batch_id=?",
            tenant,
            id));
    return b;
  }
}
