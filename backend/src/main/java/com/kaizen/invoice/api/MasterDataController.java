package com.kaizen.invoice.api;

import static com.kaizen.invoice.domain.Problem.require;

import com.kaizen.invoice.application.History;
import com.kaizen.invoice.domain.*;
import com.kaizen.invoice.infrastructure.Database;
import com.kaizen.invoice.security.Access;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.apache.commons.csv.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1")
public class MasterDataController {
  private final Database db;
  private final Access access;
  private final History history;

  public MasterDataController(Database db, Access access, History history) {
    this.db = db;
    this.access = access;
    this.history = history;
  }

  public record Supplier(
      @NotBlank String name,
      String gstin,
      String pan,
      String state,
      String address,
      String contact,
      long version) {}

  @GetMapping("/suppliers")
  public Map<String, Object> suppliers(
      @RequestHeader("X-Organization-ID") UUID t,
      @RequestParam(defaultValue = "") String q,
      @RequestParam(defaultValue = "0") int page) {
    access.require(t, Access.Permission.READ);
    require(page >= 0, 400, "Invalid page");
    return Map.of(
        "items",
        db.list(
            "select s.*,(select count(*) from invoices i where i.tenant_id=s.tenant_id and i.supplier_id=s.id) invoice_count from suppliers s where tenant_id=? and concat_ws(' ',name,gstin) ilike ? order by name limit 50 offset ?",
            t,
            "%" + q + "%",
            page * 50),
        "total",
        db.one(
                "select count(*) n from suppliers where tenant_id=? and concat_ws(' ',name,gstin) ilike ?",
                t,
                "%" + q + "%")
            .get("n"));
  }

  @PostMapping("/suppliers")
  public Map<String, Object> supplier(
      @RequestHeader("X-Organization-ID") UUID t, @Valid @RequestBody Supplier input) {
    return saveSupplier(access.require(t, Access.Permission.MASTER), null, input);
  }

  @PatchMapping("/suppliers/{id}")
  public Map<String, Object> supplierEdit(
      @RequestHeader("X-Organization-ID") UUID t,
      @PathVariable UUID id,
      @Valid @RequestBody Supplier input) {
    return saveSupplier(access.require(t, Access.Permission.MASTER), id, input);
  }

  private Map<String, Object> saveSupplier(Access.Scope s, UUID existing, Supplier v) {
    require(v.name() != null && !v.name().isBlank(), 400, "Supplier name is required");
    require(
        v.gstin() == null || v.gstin().isBlank() || Gstin.valid(v.gstin()),
        400,
        "Invalid GSTIN checksum");
    require(
        v.pan() == null || v.pan().isBlank() || v.pan().matches("[A-Z]{5}[0-9]{4}[A-Z]"),
        400,
        "Invalid PAN");
    UUID id = existing == null ? UUID.randomUUID() : existing;
    return db.transaction(
        () -> {
          if (existing == null)
            db.update(
                "insert into suppliers(id,tenant_id,name,gstin,pan,state,address,contact) values(?,?,?,?,?,?,?,?)",
                id,
                s.tenant(),
                v.name(),
                v.gstin(),
                v.pan(),
                v.state(),
                v.address(),
                v.contact());
          else
            require(
                db.update(
                        "update suppliers set name=?,gstin=?,pan=?,state=?,address=?,contact=?,version=version+1 where tenant_id=? and id=? and version=?",
                        v.name(),
                        v.gstin(),
                        v.pan(),
                        v.state(),
                        v.address(),
                        v.contact(),
                        s.tenant(),
                        id,
                        v.version())
                    == 1,
                412,
                "Supplier changed; reload before saving");
          history.audit(
              s.tenant(),
              s.user(),
              existing == null ? "SUPPLIER_CREATED" : "SUPPLIER_UPDATED",
              id,
              Map.of("name", v.name()),
              null);
          return db.one("select * from suppliers where tenant_id=? and id=?", s.tenant(), id);
        });
  }

  public record PoLine(
      @NotBlank String code,
      @NotBlank String description,
      @NotNull @DecimalMin("0.000001") BigDecimal quantity,
      @NotNull @DecimalMin("0") BigDecimal price,
      @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal taxRate) {}

  public record Po(
      @NotNull UUID supplierId,
      @NotBlank String number,
      @NotBlank String date,
      @NotBlank String currency,
      @NotEmpty List<@Valid PoLine> lines,
      long version,
      String status) {}

  @GetMapping("/purchase-orders")
  public Map<String, Object> pos(
      @RequestHeader("X-Organization-ID") UUID t,
      @RequestParam(defaultValue = "") String q,
      @RequestParam(defaultValue = "0") int page) {
    access.require(t, Access.Permission.READ);
    require(page >= 0, 400, "Invalid page");
    return Map.of(
        "items",
        db.list(
            "select p.*,s.name supplier_name,(select coalesce(sum(quantity*price*(1+tax_rate/100)),0) from po_lines l where l.tenant_id=p.tenant_id and l.po_id=p.id) total from purchase_orders p join suppliers s on s.tenant_id=p.tenant_id and s.id=p.supplier_id where p.tenant_id=? and concat_ws(' ',p.number,s.name) ilike ? order by p.created_at desc limit 50 offset ?",
            t,
            "%" + q + "%",
            page * 50),
        "total",
        db.one(
                "select count(*) n from purchase_orders where tenant_id=? and number ilike ?",
                t,
                "%" + q + "%")
            .get("n"));
  }

  @GetMapping("/purchase-orders/{id}")
  public Map<String, Object> po(@RequestHeader("X-Organization-ID") UUID t, @PathVariable UUID id) {
    access.require(t, Access.Permission.READ);
    var p = db.one("select * from purchase_orders where tenant_id=? and id=?", t, id);
    p.put(
        "lines",
        db.list(
            "select l.*,coalesce((select sum(a.quantity) from po_allocations a where a.tenant_id=l.tenant_id and a.po_line_id=l.id),0) allocated from po_lines l where l.tenant_id=? and l.po_id=? order by l.code",
            t,
            id));
    p.put(
        "invoices",
        db.list(
            "select distinct a.invoice_id,a.version from po_allocations a join po_lines l on l.tenant_id=a.tenant_id and l.id=a.po_line_id where l.tenant_id=? and l.po_id=?",
            t,
            id));
    return p;
  }

  @PostMapping("/purchase-orders")
  public Map<String, Object> createPo(
      @RequestHeader("X-Organization-ID") UUID t, @Valid @RequestBody Po p) {
    return savePo(access.require(t, Access.Permission.MASTER), null, p);
  }

  @PatchMapping("/purchase-orders/{id}")
  public Map<String, Object> updatePo(
      @RequestHeader("X-Organization-ID") UUID t, @PathVariable UUID id, @Valid @RequestBody Po p) {
    return savePo(access.require(t, Access.Permission.MASTER), id, p);
  }

  private Map<String, Object> savePo(Access.Scope s, UUID existing, Po p) {
    UUID id = existing == null ? UUID.randomUUID() : existing;
    require(
        "INR".equals(p.currency()) && p.lines().size() <= 500,
        400,
        "PO currency must be INR and contain at most 500 lines");
    return db.transaction(
        () -> {
          db.one("select id from organizations where id=? for update", s.tenant());
          db.one("select id from suppliers where tenant_id=? and id=?", s.tenant(), p.supplierId());
          if (existing != null) {
            var old =
                db.one(
                    "select * from purchase_orders where tenant_id=? and id=? for update",
                    s.tenant(),
                    id);
            require(
                ((Number) old.get("version")).longValue() == p.version(),
                412,
                "PO changed; reload it");
            require(
                db.list(
                        "select 1 from po_allocations a join po_lines l on l.tenant_id=a.tenant_id and l.id=a.po_line_id where l.tenant_id=? and l.po_id=?",
                        s.tenant(),
                        id)
                    .isEmpty(),
                409,
                "Allocated purchase orders cannot be amended; create a supplemental order");
            history.audit(s.tenant(), s.user(), "PO_AMENDED", id, old, null);
            db.update("delete from po_lines where tenant_id=? and po_id=?", s.tenant(), id);
            db.update(
                "update purchase_orders set supplier_id=?,number=?,date=?::date,version=version+1,status=? where tenant_id=? and id=?",
                p.supplierId(),
                p.number(),
                p.date(),
                "CLOSED".equals(p.status()) ? "CLOSED" : "OPEN",
                s.tenant(),
                id);
          } else
            db.update(
                "insert into purchase_orders(id,tenant_id,supplier_id,number,date,currency) values(?,?,?,?,?::date,?)",
                id,
                s.tenant(),
                p.supplierId(),
                p.number(),
                p.date(),
                p.currency());
          for (var l : p.lines())
            db.update(
                "insert into po_lines(id,tenant_id,po_id,code,description,quantity,price,tax_rate) values(?,?,?,?,?,?,?,?)",
                UUID.randomUUID(),
                s.tenant(),
                id,
                l.code(),
                l.description(),
                l.quantity(),
                l.price(),
                l.taxRate());
          history.audit(s.tenant(), s.user(), "PO_SAVED", id, p, null);
          return db.one("select * from purchase_orders where tenant_id=? and id=?", s.tenant(), id);
        });
  }

  @PostMapping("/suppliers/import")
  public List<Map<String, Object>> supplierImport(
      @RequestHeader("X-Organization-ID") UUID t, @RequestParam MultipartFile file)
      throws Exception {
    var s = access.require(t, Access.Permission.MASTER);
    List<Map<String, Object>> results = new ArrayList<>();
    try (var csv =
        CSVFormat.DEFAULT
            .builder()
            .setHeader()
            .setSkipHeaderRecord(true)
            .get()
            .parse(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
      for (var r : csv) {
        require(results.size() < 1000, 400, "Import is limited to 1000 rows");
        try {
          var saved =
              saveSupplier(
                  s,
                  null,
                  new Supplier(
                      r.get("name"),
                      r.get("gstin"),
                      r.get("pan"),
                      r.get("state"),
                      r.get("address"),
                      r.get("contact"),
                      0));
          results.add(
              Map.of("row", r.getRecordNumber(), "status", "IMPORTED", "id", saved.get("id")));
        } catch (Exception e) {
          results.add(
              Map.of(
                  "row",
                  r.getRecordNumber(),
                  "status",
                  "FAILED",
                  "error",
                  e instanceof Problem ? e.getMessage() : "Invalid row or duplicate identifier"));
        }
      }
    }
    return results;
  }

  @PostMapping("/purchase-orders/import")
  public List<Map<String, Object>> poImport(
      @RequestHeader("X-Organization-ID") UUID t, @RequestParam MultipartFile file)
      throws Exception {
    var s = access.require(t, Access.Permission.MASTER);
    Map<String, List<CSVRecord>> groups = new LinkedHashMap<>();
    try (var csv =
        CSVFormat.DEFAULT
            .builder()
            .setHeader()
            .setSkipHeaderRecord(true)
            .get()
            .parse(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
      int count = 0;
      for (var r : csv) {
        require(++count <= 1000, 400, "Import is limited to 1000 rows");
        groups.computeIfAbsent(r.get("number"), k -> new ArrayList<>()).add(r);
      }
    }
    List<Map<String, Object>> result = new ArrayList<>();
    groups.forEach(
        (number, rows) -> {
          try {
            var first = rows.get(0);
            var lines =
                rows.stream()
                    .map(
                        r ->
                            new PoLine(
                                r.get("code"),
                                r.get("description"),
                                new BigDecimal(r.get("quantity")),
                                new BigDecimal(r.get("price")),
                                new BigDecimal(r.get("taxRate"))))
                    .toList();
            require(
                lines.stream()
                    .allMatch(
                        l ->
                            l.quantity().signum() > 0
                                && l.price().signum() >= 0
                                && l.taxRate().signum() >= 0
                                && l.taxRate().compareTo(new BigDecimal("100")) <= 0),
                400,
                "Invalid line amount");
            var saved =
                savePo(
                    s,
                    null,
                    new Po(
                        UUID.fromString(first.get("supplierId")),
                        number,
                        first.get("date"),
                        "INR",
                        lines,
                        0,
                        "OPEN"));
            result.add(Map.of("number", number, "status", "IMPORTED", "id", saved.get("id")));
          } catch (Exception e) {
            result.add(
                Map.of(
                    "number",
                    number,
                    "status",
                    "FAILED",
                    "error",
                    e instanceof Problem
                        ? e.getMessage()
                        : "Invalid rows, supplier reference or duplicate number"));
          }
        });
    return result;
  }
}
