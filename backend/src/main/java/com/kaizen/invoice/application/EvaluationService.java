package com.kaizen.invoice.application;

import static com.kaizen.invoice.domain.Finding.block;
import static com.kaizen.invoice.domain.InvoiceValidator.*;

import com.kaizen.invoice.domain.*;
import com.kaizen.invoice.infrastructure.Database;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class EvaluationService {
  private final Database db;
  private final History history;

  public EvaluationService(Database db, History history) {
    this.db = db;
    this.history = history;
  }

  public record Evaluation(
      List<Finding> findings,
      List<Map<String, Object>> matches,
      List<Map<String, Object>> duplicates,
      String status) {
    public boolean blocked() {
      return findings.stream().anyMatch(Finding::blocking);
    }
  }

  @SuppressWarnings("unchecked")
  public Evaluation evaluate(UUID tenant, Map<String, Object> row, boolean persist) {
    UUID id = (UUID) row.get("id");
    long version = ((Number) row.get("version")).longValue();
    InvoiceData data = db.convert(row.get("data"), InvoiceData.class);
    var org =
        db.one("select settings,policy_version,profile from organizations where id=?", tenant);
    Map<String, Object> settings = (Map<String, Object>) org.get("settings");
    BigDecimal tolerance = new BigDecimal(settings.get("roundingTolerance").toString());
    BigDecimal priceTolerance = new BigDecimal(settings.get("priceTolerance").toString());
    var findings =
        new ArrayList<>(
            new InvoiceValidator()
                .validate(
                    data,
                    tolerance,
                    new HashSet<>(
                        (List<String>) settings.getOrDefault("requiredFields", List.of()))));
    if (data.supplierId() == null)
      findings.add(
          block(
              "SUPPLIER_RESOLUTION",
              "supplierId",
              null,
              "Confirmed supplier",
              "Select a supplier master record; ambiguous identity cannot be auto-approved."));
    else {
      var supplier =
          db.one("select * from suppliers where tenant_id=? and id=?", tenant, data.supplierId());
      if (!Objects.equals(
          Objects.toString(supplier.get("gstin"), ""), Objects.toString(data.supplierGstin(), "")))
        findings.add(
            block(
                "SUPPLIER_IDENTITY",
                "supplierGstin",
                data.supplierGstin(),
                supplier.get("gstin"),
                "Extracted identity does not agree with the selected supplier."));
    }
    var profile = (Map<String, Object>) org.get("profile");
    String buyer = Objects.toString(data.buyerGstin(), "");
    if (!Objects.toString(profile.get("gstin"), "").isBlank() && buyer.isBlank())
      findings.add(
          block(
              "BUYER_GST_REQUIRED",
              "buyerGstin",
              null,
              "Organization GST registration",
              "The registered buyer's GSTIN is required for this organization."));
    if (!buyer.isBlank()
        && !buyer.equals(Objects.toString(profile.get("gstin"), ""))
        && db.list("select id from business_units where tenant_id=? and gstin=?", tenant, buyer)
            .isEmpty())
      findings.add(
          block(
              "BUYER_OWNERSHIP",
              "buyerGstin",
              buyer,
              "An organization GST registration",
              "Invoice buyer is not an authorized GST registration for this organization."));
    var duplicates =
        db.list(
            "select id,invoice_number,data,total,sha256,business_state from invoices where tenant_id=? and id<>? and (sha256=? or (supplier_id=? and normalized_number=? and financial_year=? and document_type=?) or (total=? and invoice_date between ?::date-interval '7 days' and ?::date+interval '7 days' and (supplier_id=? or lower(data->>'supplierName')=lower(?)))) order by created_at limit 30",
            tenant,
            id,
            row.get("sha256"),
            data.supplierId(),
            normalize(data.invoiceNumber()),
            data.financialYear(),
            data.documentType(),
            data.total(),
            row.get("invoice_date"),
            row.get("invoice_date"),
            data.supplierId(),
            data.supplierName());
    for (var candidate : duplicates) {
      boolean exact = Objects.equals(row.get("sha256"), candidate.get("sha256"));
      candidate.put("kind", exact ? "EXACT_FILE" : "BUSINESS_OR_SUSPECTED");
      candidate.put(
          "evidence",
          exact
              ? "Identical SHA-256 document bytes"
              : "Supplier/number context or equal amount within seven days");
      boolean cleared =
          !db.list(
                  "select 1 from duplicate_decisions where tenant_id=? and invoice_id=? and candidate_id=? and version=?",
                  tenant,
                  id,
                  candidate.get("id"),
                  version)
              .isEmpty();
      candidate.put("cleared", cleared);
      if (!cleared)
        findings.add(
            block(
                "DUPLICATE",
                "invoiceNumber",
                candidate.get("id"),
                "Review duplicate candidate",
                candidate.get("evidence").toString()));
    }
    List<Map<String, Object>> matches = new ArrayList<>();
    String status = "NOT_REQUIRED";
    if (blank(data.poNumber())) {
      if (Boolean.TRUE.equals(settings.get("requirePo"))) {
        findings.add(
            block(
                "MISSING_PO",
                "poNumber",
                null,
                "PO reference",
                "This organization requires purchase order reconciliation."));
        status = "MISSING_PO";
      }
    } else {
      var po =
          db.optional(
              "select * from purchase_orders where tenant_id=? and number=?",
              tenant,
              data.poNumber());
      if (po.isEmpty()) {
        findings.add(
            block(
                "MISSING_PO",
                "poNumber",
                data.poNumber(),
                "Existing PO",
                "Purchase order not found in this organization."));
        status = "MISSING_PO";
      } else {
        var order = po.get();
        status = "MATCHED";
        if (!Objects.equals(order.get("supplier_id"), data.supplierId()))
          findings.add(
              block(
                  "PO_SUPPLIER",
                  "supplierId",
                  data.supplierId(),
                  order.get("supplier_id"),
                  "Invoice and PO suppliers differ."));
        if (!"OPEN".equals(order.get("status")))
          findings.add(
              block(
                  "PO_CLOSED",
                  "poNumber",
                  data.poNumber(),
                  "Open PO",
                  "This purchase order is closed."));
        if (!Objects.equals(order.get("currency"), data.currency()))
          findings.add(
              block(
                  "PO_CURRENCY",
                  "currency",
                  data.currency(),
                  order.get("currency"),
                  "Currencies differ."));
        var poLines =
            db.list(
                "select p.*,coalesce((select sum(a.quantity) from po_allocations a where a.tenant_id=p.tenant_id and a.po_line_id=p.id and a.invoice_id<>?),0) allocated from po_lines p where p.tenant_id=? and p.po_id=? order by p.id",
                id,
                tenant,
                order.get("id"));
        Map<UUID, BigDecimal> proposed = new HashMap<>();
        int n = 0;
        for (var line : data.lines()) {
          final var current = line;
          var candidates =
              poLines.stream()
                  .filter(
                      p ->
                          !blank(current.code())
                              ? current.code().equalsIgnoreCase(p.get("code").toString())
                              : Objects.toString(current.description(), "")
                                  .trim()
                                  .equalsIgnoreCase(p.get("description").toString().trim()))
                  .toList();
          String field = "lines." + n + ".";
          n++;
          if (candidates.size() != 1) {
            findings.add(
                block(
                    "UNMATCHED_LINE",
                    field + "code",
                    line.code(),
                    "One unambiguous PO line",
                    "Select an exact product code or description; fuzzy matches cannot allocate balances."));
            continue;
          }
          var p = candidates.get(0);
          UUID lineId = (UUID) p.get("id");
          BigDecimal qty = zero(line.quantity()),
              price = zero(line.price()),
              remaining =
                  ((BigDecimal) p.get("quantity")).subtract((BigDecimal) p.get("allocated"));
          BigDecimal proposedTotal = proposed.merge(lineId, qty, BigDecimal::add);
          BigDecimal priceDifference = price.subtract((BigDecimal) p.get("price"));
          boolean over = proposedTotal.compareTo(remaining) > 0;
          if (qty.signum() <= 0)
            findings.add(
                block(
                    "PO_QUANTITY_SIGN",
                    field + "quantity",
                    qty,
                    "Positive quantity",
                    "Credit-note allocation reversal requires a separate controlled adjustment; it cannot consume a purchase order."));
          if (over)
            findings.add(
                block(
                    "OVER_ALLOCATION",
                    field + "quantity",
                    proposedTotal,
                    remaining,
                    "Proposed quantity exceeds the remaining PO balance."));
          if (priceDifference.abs().compareTo(priceTolerance) > 0)
            findings.add(
                block(
                    "PO_PRICE",
                    field + "price",
                    price,
                    p.get("price"),
                    "Unit price difference exceeds the configured tolerance."));
          if (line.taxRate() == null
              || line.taxRate().compareTo((BigDecimal) p.get("tax_rate")) != 0)
            findings.add(
                block(
                    "PO_TAX",
                    field + "taxRate",
                    line.taxRate(),
                    p.get("tax_rate"),
                    "Invoice and purchase order tax rates differ."));
          Map<String, Object> m = new LinkedHashMap<>();
          m.put("poLineId", lineId);
          m.put("description", p.get("description"));
          m.put("invoiceQuantity", qty);
          m.put("ordered", p.get("quantity"));
          m.put("allocated", p.get("allocated"));
          m.put("remaining", remaining);
          m.put("quantityDifference", qty.subtract(remaining));
          m.put("invoicePrice", price);
          m.put("poPrice", p.get("price"));
          m.put("priceDifference", priceDifference);
          m.put("amount", qty.multiply(price));
          m.put(
              "status",
              over || priceDifference.abs().compareTo(priceTolerance) > 0
                  ? "MISMATCH"
                  : qty.compareTo(remaining) < 0 ? "PARTIAL" : "MATCHED");
          matches.add(m);
        }
        if (findings.stream()
            .anyMatch(
                f ->
                    f.ruleId().startsWith("PO_")
                        || Set.of("OVER_ALLOCATION", "UNMATCHED_LINE").contains(f.ruleId())))
          status = "MISMATCH";
        else if (matches.stream().anyMatch(m -> "PARTIAL".equals(m.get("status"))))
          status = "PARTIAL";
      }
    }
    if (persist && version > 0) {
      db.update(
          "insert into evaluation_runs(id,tenant_id,invoice_id,version,policy_version,policy,findings,matches) values(?,?,?,?,?,?::jsonb,?::jsonb,?::jsonb)",
          UUID.randomUUID(),
          tenant,
          id,
          version,
          org.get("policy_version"),
          db.json(settings),
          db.json(findings),
          db.json(matches));
      db.update(
          "update exceptions set status='RESOLVED',resolved_at=now(),reason='Re-evaluated against current invoice and policy' where tenant_id=? and invoice_id=? and status in ('OPEN','ASSIGNED','IN_REVIEW')",
          tenant,
          id);
      for (var f : findings)
        db.update(
            "insert into exceptions(id,tenant_id,invoice_id,version,rule_id,field,severity,explanation,evidence) values(?,?,?,?,?,?,?,?,?::jsonb)",
            UUID.randomUUID(),
            tenant,
            id,
            version,
            f.ruleId(),
            f.field(),
            f.severity(),
            f.explanation(),
            db.json(f));
      db.update(
          "update invoices set match_status=? where tenant_id=? and id=?", status, tenant, id);
    }
    return new Evaluation(findings, matches, duplicates, status);
  }

  public static String normalize(String value) {
    return value == null ? null : value.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
  }
}
