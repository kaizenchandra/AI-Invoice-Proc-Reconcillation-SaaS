package com.kaizen.invoice.api;

import static com.kaizen.invoice.domain.Problem.require;

import com.kaizen.invoice.application.*;
import com.kaizen.invoice.infrastructure.*;
import com.kaizen.invoice.security.Access;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class OperationsController {
  private final Database db;
  private final Access access;
  private final ExportService exports;
  private final History history;
  private final String domain;
  private final DocumentExtractionPort extraction;
  private final boolean scanner;

  public OperationsController(
      Database db,
      Access access,
      ExportService exports,
      History history,
      DocumentExtractionPort extraction,
      @Value("${app.email.domain}") String domain,
      @Value("${app.scanner.enabled}") boolean scanner) {
    this.db = db;
    this.access = access;
    this.exports = exports;
    this.history = history;
    this.extraction = extraction;
    this.domain = domain;
    this.scanner = scanner;
  }

  @GetMapping("/dashboard")
  public Map<String, Object> dashboard(
      @RequestHeader("X-Organization-ID") UUID t,
      @RequestParam(defaultValue = "") String from,
      @RequestParam(defaultValue = "") String to) {
    access.require(t, Access.Permission.READ);
    String filter =
        "tenant_id=? and (?='' or created_at>=nullif(?,'')::date) and (?='' or created_at<nullif(?,'')::date+interval '1 day')";
    Object[] args = {t, from, from, to, to};
    var metrics =
        db.one(
            "select count(*) received,count(*) filter(where processing_state='PROCESSED') processed,count(*) filter(where business_state in ('NEEDS_REVIEW','IN_REVIEW','REJECTED')) review,count(*) filter(where business_state='READY_FOR_APPROVAL') approval,count(*) filter(where business_state='APPROVED') approved,count(*) filter(where business_state='EXPORTED') exported,count(*) filter(where processing_state='FAILED') failed,count(*) filter(where match_status in ('MATCHED','PARTIAL')) matched,count(*) filter(where match_status='MISMATCH') mismatched,coalesce(sum(total),0) value,avg(extract(epoch from processed_at-created_at)) processing_seconds from invoices where "
                + filter,
            args);
    metrics.put(
        "trend",
        db.list(
            "select date_trunc('day',created_at)::date as day,count(*) received,count(*) filter(where processing_state='PROCESSED') processed from invoices where "
                + filter
                + " group by 1 order by 1 desc limit 14",
            args));
    metrics.put(
        "actions",
        db.list(
            "select id,invoice_number,data->>'supplierName' supplier_name,business_state,processing_state,error,created_at from invoices where "
                + filter
                + " and (processing_state='FAILED' or business_state in ('NEEDS_REVIEW','IN_REVIEW','READY_FOR_APPROVAL')) order by (processing_state='FAILED') desc,created_at limit 6",
            args));
    metrics.put(
        "activity",
        db.list(
            "select * from audit_events where " + filter + " order by created_at desc limit 8",
            args));
    metrics.put(
        "exceptions",
        db.list(
            "select rule_id,count(*) count from exceptions where tenant_id=? and status in ('OPEN','ASSIGNED','IN_REVIEW') group by rule_id order by count(*) desc limit 8",
            t));
    return metrics;
  }

  @GetMapping("/audit-events")
  public List<Map<String, Object>> audit(
      @RequestHeader("X-Organization-ID") UUID t, @RequestParam(defaultValue = "0") int page) {
    access.require(t, Access.Permission.READ);
    require(page >= 0, 400, "Invalid page");
    return db.list(
        "select a.*,u.name actor_name from audit_events a left join app_users u on u.id::text=a.actor where tenant_id=? order by created_at desc limit 50 offset ?",
        t,
        page * 50);
  }

  @GetMapping("/notifications")
  public List<Map<String, Object>> notifications(@RequestHeader("X-Organization-ID") UUID t) {
    var s = access.require(t, Access.Permission.READ);
    return db.list(
        "select * from notifications where tenant_id=? and user_id=? order by created_at desc limit 100",
        t,
        s.user());
  }

  @PostMapping("/notifications/{id}/read")
  public void read(@RequestHeader("X-Organization-ID") UUID t, @PathVariable UUID id) {
    var s = access.require(t, Access.Permission.READ);
    require(
        db.update(
                "update notifications set read_at=coalesce(read_at,now()) where tenant_id=? and user_id=? and id=?",
                t,
                s.user(),
                id)
            == 1,
        404,
        "Notification not found");
  }

  @GetMapping("/exceptions")
  public List<Map<String, Object>> exceptions(
      @RequestHeader("X-Organization-ID") UUID t,
      @RequestParam(defaultValue = "OPEN") String status,
      @RequestParam(defaultValue = "0") int page) {
    access.require(t, Access.Permission.READ);
    require(page >= 0, 400, "Invalid page");
    return db.list(
        "select e.*,i.invoice_number,i.data->>'supplierName' supplier_name,i.total from exceptions e join invoices i on i.tenant_id=e.tenant_id and i.id=e.invoice_id where e.tenant_id=? and (?='ALL' or e.status=?) order by e.created_at desc limit 50 offset ?",
        t,
        status,
        status,
        page * 50);
  }

  public record ExceptionAction(String action, UUID assignee, String reason) {}

  @PostMapping("/exceptions/{id}")
  public void exception(
      @RequestHeader("X-Organization-ID") UUID t,
      @PathVariable UUID id,
      @RequestBody ExceptionAction input) {
    var s = access.require(t, Access.Permission.RESOLVE);
    db.transaction(
        () -> {
          var e = db.one("select * from exceptions where tenant_id=? and id=? for update", t, id);
          String next =
              switch (input.action()) {
                case "assign" -> "ASSIGNED";
                case "review" -> "IN_REVIEW";
                case "dismiss" -> "DISMISSED_WITH_REASON";
                default ->
                    throw new com.kaizen.invoice.domain.Problem(
                        400, "Unsupported exception action");
              };
          require(
              !Set.of("RESOLVED", "DISMISSED_WITH_REASON").contains(e.get("status")),
              409,
              "Exception is already closed");
          if ("dismiss".equals(input.action())) {
            require(
                !"BLOCKING".equals(e.get("severity")),
                422,
                "Mandatory financial findings cannot be dismissed. Correct and revalidate the invoice.");
            require(
                input.reason() != null && input.reason().length() >= 10,
                400,
                "A dismissal reason is required");
          }
          if (input.assignee() != null) {
            var member =
                db.one(
                    "select role from memberships where tenant_id=? and user_id=?",
                    t,
                    input.assignee());
            require(
                Access.permissions(member.get("role").toString()).contains(Access.Permission.EDIT),
                400,
                "Assignee must be a reviewer");
            history.notify(
                t,
                input.assignee(),
                (UUID) e.get("invoice_id"),
                "Exception assigned for review",
                "exception-" + id + "-" + input.assignee());
          }
          db.update(
              "update exceptions set status=?,assignee=?,reason=? where tenant_id=? and id=?",
              next,
              input.assignee(),
              input.reason(),
              t,
              id);
          history.audit(
              t,
              s.user(),
              "EXCEPTION_" + next,
              (UUID) e.get("invoice_id"),
              Map.of("exception", id),
              input.reason());
          return null;
        });
  }

  @GetMapping("/settings")
  public Map<String, Object> settings(@RequestHeader("X-Organization-ID") UUID t) {
    access.require(t, Access.Permission.READ);
    var org =
        db.one(
            "select settings,policy_version,profile,intake_alias from organizations where id=?", t);
    var configuration = extraction.configuration();
    org.put("extractionConfigured", configuration.configured());
    org.put("provider", configuration.provider());
    org.put("extraction", configuration);
    org.put(
        "emailAddress",
        domain.isBlank() ? "Not configured" : org.get("intake_alias") + "@" + domain);
    org.put("malwareScanEnabled", scanner);
    return org;
  }

  public record Settings(
      long version,
      BigDecimal roundingTolerance,
      BigDecimal priceTolerance,
      boolean separationOfDuties,
      boolean requirePo,
      BigDecimal reviewThreshold,
      List<String> requiredFields,
      List<String> exportFields) {}

  @PutMapping("/settings")
  public void settings(@RequestHeader("X-Organization-ID") UUID t, @RequestBody Settings input) {
    var s = access.require(t, Access.Permission.ADMIN);
    require(
        input.reviewThreshold() != null && input.reviewThreshold().signum() >= 0,
        400,
        "Review routing threshold must be a non-negative amount");
    require(
        input.roundingTolerance() != null
            && input.roundingTolerance().signum() >= 0
            && input.roundingTolerance().compareTo(BigDecimal.ONE) <= 0,
        400,
        "Rounding tolerance must be ₹0–₹1");
    require(
        input.priceTolerance() != null
            && input.priceTolerance().signum() >= 0
            && input.priceTolerance().compareTo(new BigDecimal("100")) <= 0,
        400,
        "Price tolerance must be ₹0–₹100");
    require(
        input.exportFields() != null
            && !input.exportFields().isEmpty()
            && CsvAccountingExport.FIELDS.containsAll(input.exportFields()),
        400,
        "Unsupported export fields");
    require(
        input.requiredFields() != null
            && Set.of(
                    "invoiceNumber",
                    "invoiceDate",
                    "supplierName",
                    "buyerName",
                    "placeOfSupply",
                    "financialYear",
                    "currency",
                    "taxableAmount",
                    "total",
                    "documentType",
                    "dueDate",
                    "supplierGstin",
                    "supplierPan",
                    "buyerGstin",
                    "poNumber",
                    "irn",
                    "grossAmount")
                .containsAll(input.requiredFields()),
        400,
        "Unsupported required field");
    db.transaction(
        () -> {
          require(
              db.update(
                      "update organizations set settings=?::jsonb,policy_version=policy_version+1 where id=? and policy_version=?",
                      db.json(
                          Map.of(
                              "roundingTolerance",
                              input.roundingTolerance(),
                              "priceTolerance",
                              input.priceTolerance(),
                              "separationOfDuties",
                              input.separationOfDuties(),
                              "requirePo",
                              input.requirePo(),
                              "reviewThreshold",
                              input.reviewThreshold(),
                              "requiredFields",
                              input.requiredFields(),
                              "exportFields",
                              input.exportFields())),
                      t,
                      input.version())
                  == 1,
              412,
              "Settings changed; reload before saving");
          history.audit(t, s.user(), "SETTINGS_UPDATED", t, input, null);
          return null;
        });
  }

  public record ExportRequest(List<UUID> invoiceIds, String reason) {}

  @GetMapping("/exports")
  public List<Map<String, Object>> exports(@RequestHeader("X-Organization-ID") UUID t) {
    access.require(t, Access.Permission.READ);
    return db.list(
        "select b.id,b.status,b.profile,b.reason,b.error,b.created_at,b.completed_at,(select count(*) from export_records e where e.tenant_id=b.tenant_id and e.batch_id=b.id) record_count from export_batches b where b.tenant_id=? order by b.created_at desc limit 100",
        t);
  }

  @PostMapping("/exports")
  public Map<String, Object> export(
      @RequestHeader("X-Organization-ID") UUID t,
      @RequestHeader("Idempotency-Key") String key,
      @RequestBody ExportRequest request) {
    return exports.create(
        access.require(t, Access.Permission.EXPORT), request.invoiceIds(), key, request.reason());
  }

  @GetMapping("/exports/{id}")
  public Map<String, Object> exportInfo(
      @RequestHeader("X-Organization-ID") UUID t, @PathVariable UUID id) {
    access.require(t, Access.Permission.READ);
    return exports.info(t, id);
  }

  @PostMapping("/exports/{id}/retry")
  public Map<String, Object> retry(
      @RequestHeader("X-Organization-ID") UUID t, @PathVariable UUID id) {
    var s = access.require(t, Access.Permission.EXPORT);
    exports.generate(s, id);
    return exports.info(t, id);
  }

  @GetMapping("/exports/{id}/download")
  public ResponseEntity<byte[]> download(
      @RequestHeader("X-Organization-ID") UUID t, @PathVariable UUID id) {
    access.require(t, Access.Permission.EXPORT);
    var batch =
        db.one("select content,status from export_batches where tenant_id=? and id=?", t, id);
    require("COMPLETED".equals(batch.get("status")), 409, "Export is not ready");
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
        .header("Content-Disposition", "attachment; filename=\"invoices-" + id + ".csv\"")
        .header("Cache-Control", "private, no-store")
        .body((byte[]) batch.get("content"));
  }
}
