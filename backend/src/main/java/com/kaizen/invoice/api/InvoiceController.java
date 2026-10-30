package com.kaizen.invoice.api;

import static com.kaizen.invoice.domain.Problem.require;

import com.kaizen.invoice.application.*;
import com.kaizen.invoice.domain.*;
import com.kaizen.invoice.infrastructure.Database;
import com.kaizen.invoice.security.Access;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1")
public class InvoiceController {
  private final Database db;
  private final Access access;
  private final InvoiceService invoices;
  private final IntakeService intake;
  private final DocumentStorage storage;

  public InvoiceController(
      Database db,
      Access access,
      InvoiceService invoices,
      IntakeService intake,
      DocumentStorage storage) {
    this.db = db;
    this.access = access;
    this.invoices = invoices;
    this.intake = intake;
    this.storage = storage;
  }

  @GetMapping("/invoices")
  public Map<String, Object> list(
      @RequestHeader("X-Organization-ID") UUID tenant,
      @RequestParam(defaultValue = "") String q,
      @RequestParam(defaultValue = "") String status,
      @RequestParam(defaultValue = "") String source,
      @RequestParam(defaultValue = "") String from,
      @RequestParam(defaultValue = "") String to,
      @RequestParam(required = false) BigDecimal min,
      @RequestParam(required = false) BigDecimal max,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "25") int size,
      @RequestParam(defaultValue = "created_at") String sort,
      @RequestParam(defaultValue = "desc") String direction) {
    access.require(tenant, Access.Permission.READ);
    require(page >= 0 && size >= 1 && size <= 100, 400, "Invalid page size");
    String order =
        Set.of("created_at", "invoice_number", "invoice_date", "total", "business_state")
                .contains(sort)
            ? sort
            : "created_at";
    String where =
        "tenant_id=? and (?='' or concat_ws(' ',invoice_number,data->>'supplierName',data->>'supplierGstin',data->>'poNumber') ilike ?) and (?='' or business_state=? or processing_state=?) and (?='' or source=?) and (?='' or created_at>=nullif(?,'')::date) and (?='' or created_at<nullif(?,'')::date+interval '1 day') and (?::numeric is null or total>=?::numeric) and (?::numeric is null or total<=?::numeric)";
    Object[] args = {
      tenant,
      q,
      "%" + q + "%",
      status,
      status,
      status,
      source,
      source,
      from,
      from,
      to,
      to,
      min,
      min,
      max,
      max
    };
    var params = new ArrayList<>(Arrays.asList(args));
    params.add(size);
    params.add(page * size);
    var rows =
        db.list(
            "select id,invoice_number,data->>'supplierName' supplier_name,invoice_date,total,currency,processing_state,business_state,match_status,assignee,version,source,created_at,error from invoices where "
                + where
                + " order by "
                + order
                + ("asc".equals(direction) ? " asc" : " desc")
                + " nulls last,id limit ? offset ?",
            params.toArray());
    return Map.of(
        "items",
        rows,
        "total",
        db.one("select count(*) total from invoices where " + where, args).get("total"),
        "page",
        page,
        "size",
        size);
  }

  @GetMapping("/invoices/{id}")
  public Map<String, Object> detail(
      @RequestHeader("X-Organization-ID") UUID tenant, @PathVariable UUID id) {
    access.require(tenant, Access.Permission.READ);
    return invoices.detail(tenant, id);
  }

  @PostMapping("/import-batches")
  public Map<String, Object> batch(@RequestHeader("X-Organization-ID") UUID tenant) {
    var s = access.require(tenant, Access.Permission.UPLOAD);
    UUID id = UUID.randomUUID();
    db.update(
        "insert into ingestion_batches(id,tenant_id,actor) values(?,?,?)", id, tenant, s.user());
    return Map.of("id", id);
  }

  @GetMapping("/import-batches")
  public List<Map<String, Object>> batches(@RequestHeader("X-Organization-ID") UUID tenant) {
    access.require(tenant, Access.Permission.READ);
    return db.list(
        "select b.*,count(x.id) files,count(x.id) filter(where i.processing_state='PROCESSED') completed,count(x.id) filter(where i.processing_state='FAILED' or x.status='FAILED') failed from ingestion_batches b left join ingestion_items x on x.tenant_id=b.tenant_id and x.batch_id=b.id left join invoices i on i.tenant_id=x.tenant_id and i.id=x.invoice_id where b.tenant_id=? group by b.id order by b.created_at desc limit 30",
        tenant);
  }

  @GetMapping("/import-batches/{id}")
  public List<Map<String, Object>> batchDetail(
      @RequestHeader("X-Organization-ID") UUID tenant, @PathVariable UUID id) {
    access.require(tenant, Access.Permission.READ);
    return intake.items(tenant, id);
  }

  public record BatchItem(
      @jakarta.validation.constraints.NotBlank String filename,
      @jakarta.validation.constraints.Min(0) long size,
      @jakarta.validation.constraints.Size(min = 8, max = 200)
          @jakarta.validation.constraints.NotBlank
          String requestKey) {}

  @PostMapping("/import-batches/{id}/items")
  public List<Map<String, Object>> manifest(
      @RequestHeader("X-Organization-ID") UUID tenant,
      @PathVariable UUID id,
      @jakarta.validation.Valid @RequestBody List<@jakarta.validation.Valid BatchItem> items) {
    var scope = access.require(tenant, Access.Permission.UPLOAD);
    require(!items.isEmpty() && items.size() <= 100, 400, "Register 1–100 files per batch");
    return db.transaction(
        () ->
            items.stream()
                .map(
                    item ->
                        intake.register(
                            scope,
                            item.filename(),
                            item.size(),
                            item.requestKey(),
                            id,
                            "UPLOAD",
                            Map.of()))
                .toList());
  }

  @GetMapping("/import-batches/{id}/errors")
  public ResponseEntity<byte[]> errorSummary(
      @RequestHeader("X-Organization-ID") UUID tenant, @PathVariable UUID id) {
    access.require(tenant, Access.Permission.READ);
    var items = intake.items(tenant, id);
    var csv = new StringBuilder("filename,status,error,request_key\r\n");
    for (var item : items)
      if (item.get("error") != null
          || Set.of("AWAITING_UPLOAD", "INTERRUPTED").contains(item.get("processing_state"))) {
        csv.append(
                java.util.stream.Stream.of("filename", "processing_state", "error", "request_key")
                    .map(
                        k ->
                            com.kaizen.invoice.infrastructure.CsvAccountingExport.cell(
                                Objects.toString(item.get(k), "")))
                    .collect(java.util.stream.Collectors.joining(",")))
            .append("\r\n");
      }
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
        .header("Content-Disposition", "attachment; filename=\"intake-errors-" + id + ".csv\"")
        .header("Cache-Control", "private, no-store")
        .body(csv.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
  }

  @PostMapping(value = "/invoices/uploads", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public Map<String, Object> upload(
      @RequestHeader("X-Organization-ID") UUID tenant,
      @RequestHeader("Idempotency-Key") String key,
      @RequestParam MultipartFile file,
      @RequestParam(required = false) UUID batchId)
      throws Exception {
    return intake.upload(
        access.require(tenant, Access.Permission.UPLOAD),
        file.getOriginalFilename(),
        file.getBytes(),
        key,
        batchId,
        "UPLOAD",
        Map.of());
  }

  public record Edit(
      @jakarta.validation.constraints.Min(0) long version,
      @jakarta.validation.constraints.NotNull InvoiceData data,
      String note) {}

  @PatchMapping("/invoices/{id}")
  public Map<String, Object> edit(
      @RequestHeader("X-Organization-ID") UUID tenant,
      @PathVariable UUID id,
      @jakarta.validation.Valid @RequestBody Edit input) {
    return invoices.save(
        access.require(tenant, Access.Permission.EDIT),
        id,
        input.version(),
        input.data(),
        input.note());
  }

  public record Action(long version, String reason, UUID assignee) {}

  @PostMapping("/invoices/{id}/{action:revalidate|reconcile|claim|submit|approve|reject|reopen}")
  public Map<String, Object> action(
      @RequestHeader("X-Organization-ID") UUID tenant,
      @PathVariable UUID id,
      @PathVariable String action,
      @RequestBody Action input) {
    var p =
        Set.of("approve", "reject", "reopen").contains(action)
            ? Access.Permission.APPROVE
            : Access.Permission.EDIT;
    return invoices.transition(
        access.require(tenant, p), id, action, input.version(), input.reason(), input.assignee());
  }

  @PostMapping("/invoices/{id}/reprocess")
  public void retry(@RequestHeader("X-Organization-ID") UUID tenant, @PathVariable UUID id) {
    intake.retry(access.require(tenant, Access.Permission.UPLOAD), id);
  }

  @PostMapping("/invoices/{id}/duplicates/{candidate}/clear")
  public void clear(
      @RequestHeader("X-Organization-ID") UUID tenant,
      @PathVariable UUID id,
      @PathVariable UUID candidate,
      @RequestBody Action input) {
    invoices.clearDuplicate(
        access.require(tenant, Access.Permission.RESOLVE),
        id,
        candidate,
        input.version(),
        input.reason());
  }

  @GetMapping("/invoices/{id}/document")
  public ResponseEntity<byte[]> document(
      @RequestHeader("X-Organization-ID") UUID tenant, @PathVariable UUID id) {
    access.require(tenant, Access.Permission.READ);
    var r = invoices.row(tenant, id, false);
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(r.get("mime").toString()))
        .header("Cache-Control", "private, no-store")
        .header(
            "Content-Disposition",
            "inline; filename=\"invoice."
                + (r.get("mime").equals("application/pdf")
                    ? "pdf"
                    : r.get("mime").equals("image/png") ? "png" : "jpg")
                + "\"")
        .body(storage.get(r.get("storage_key").toString()));
  }
}
