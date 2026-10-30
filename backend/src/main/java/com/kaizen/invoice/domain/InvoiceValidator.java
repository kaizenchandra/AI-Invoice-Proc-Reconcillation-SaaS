package com.kaizen.invoice.domain;

import static com.kaizen.invoice.domain.Finding.block;

import java.math.*;
import java.time.LocalDate;
import java.util.*;

public final class InvoiceValidator {
  public static BigDecimal zero(BigDecimal n) {
    return n == null ? BigDecimal.ZERO : n;
  }

  public static boolean blank(String s) {
    return s == null || s.isBlank();
  }

  public List<Finding> validate(InvoiceData d, BigDecimal tolerance, Set<String> required) {
    List<Finding> f = new ArrayList<>();
    Map<String, Object> fields = new LinkedHashMap<>();
    fields.put("invoiceNumber", d.invoiceNumber());
    fields.put("invoiceDate", d.invoiceDate());
    fields.put("supplierName", d.supplierName());
    fields.put("buyerName", d.buyerName());
    fields.put("placeOfSupply", d.placeOfSupply());
    fields.put("financialYear", d.financialYear());
    fields.put("currency", d.currency());
    fields.put("taxableAmount", d.taxableAmount());
    fields.put("total", d.total());
    fields.put("documentType", d.documentType());
    for (var e : fields.entrySet())
      if (e.getValue() == null || e.getValue().toString().isBlank())
        f.add(
            block(
                "REQUIRED", e.getKey(), null, "Present", "A mandatory invoice value is missing."));
    fields.put("dueDate", d.dueDate());
    fields.put("supplierGstin", d.supplierGstin());
    fields.put("supplierPan", d.supplierPan());
    fields.put("buyerGstin", d.buyerGstin());
    fields.put("poNumber", d.poNumber());
    fields.put("irn", d.irn());
    fields.put("grossAmount", d.grossAmount());
    for (String r : required) {
      Object v = fields.get(r);
      if (v == null || v.toString().isBlank())
        f.add(
            block(
                "POLICY_REQUIRED",
                r,
                v,
                "Present",
                "This organization requires the field under its current policy."));
    }
    boolean credit = "CREDIT_NOTE".equals(d.documentType());
    if (!Set.of("TAX_INVOICE", "PURCHASE_INVOICE", "CREDIT_NOTE", "DEBIT_NOTE")
        .contains(Objects.toString(d.documentType(), "")))
      f.add(
          block(
              "CLASSIFICATION",
              "documentType",
              d.documentType(),
              "Supported invoice type",
              "Unknown documents require classification by a reviewer."));
    if (!"INR".equals(d.currency()))
      f.add(
          block(
              "CURRENCY",
              "currency",
              d.currency(),
              "INR",
              "This accounting profile supports INR only."));
    if (!blank(d.invoiceDate()))
      try {
        LocalDate date = LocalDate.parse(d.invoiceDate());
        int year = date.getMonthValue() < 4 ? date.getYear() - 1 : date.getYear();
        String fy = year + "-" + (year + 1);
        if (!fy.equals(d.financialYear()))
          f.add(
              block(
                  "FINANCIAL_YEAR",
                  "financialYear",
                  d.financialYear(),
                  fy,
                  "Financial year must follow the invoice date (April–March)."));
        if (!blank(d.dueDate()) && LocalDate.parse(d.dueDate()).isBefore(date))
          f.add(
              block(
                  "DUE_DATE",
                  "dueDate",
                  d.dueDate(),
                  "On or after invoice date",
                  "Due date precedes the invoice date."));
      } catch (Exception e) {
        f.add(
            block(
                "DATE",
                "invoiceDate",
                d.invoiceDate(),
                "YYYY-MM-DD",
                "Invoice and due dates must be valid calendar dates."));
      }
    if (!Set.of("REGISTERED", "UNREGISTERED", "EXEMPT", "EXPORT", "SEZ")
        .contains(Objects.toString(d.taxContext(), "")))
      f.add(
          block(
              "TAX_CONTEXT",
              "taxContext",
              d.taxContext(),
              "Explicit transaction context",
              "Confirm registration and tax applicability; do not infer it from missing GSTIN."));
    if ("REGISTERED".equals(d.taxContext()) && blank(d.supplierGstin()))
      f.add(
          block(
              "SUPPLIER_GST_REQUIRED",
              "supplierGstin",
              null,
              "GSTIN",
              "Registered supplier requires a GSTIN."));
    for (var e :
        Map.of(
                "supplierGstin",
                Objects.toString(d.supplierGstin(), ""),
                "buyerGstin",
                Objects.toString(d.buyerGstin(), ""))
            .entrySet())
      if (!blank(e.getValue()) && !Gstin.valid(e.getValue()))
        f.add(
            block(
                "GSTIN_CHECKSUM",
                e.getKey(),
                e.getValue(),
                "Valid GSTIN",
                "GSTIN structure or checksum is invalid; this does not query the GST registry."));
    if (!blank(d.supplierGstin())
        && d.supplierGstin().length() >= 2
        && !blank(d.supplierState())
        && !d.supplierGstin().startsWith(d.supplierState()))
      f.add(
          block(
              "SUPPLIER_STATE",
              "supplierState",
              d.supplierState(),
              d.supplierGstin().substring(0, 2),
              "Supplier state does not match GSTIN state."));
    if (!blank(d.placeOfSupply()) && !d.placeOfSupply().matches("[0-9]{2}"))
      f.add(
          block(
              "PLACE_OF_SUPPLY",
              "placeOfSupply",
              d.placeOfSupply(),
              "Two-digit state code",
              "Confirm the place of supply."));
    BigDecimal c = zero(d.cgst()), s = zero(d.sgst()), i = zero(d.igst());
    if (d.cgst() == null || d.sgst() == null || d.igst() == null)
      f.add(
          block(
              "TAX_BREAKDOWN",
              "cgst",
              null,
              "Explicit components (zero when inapplicable)",
              "Generic provider tax is not a verified GST breakdown."));
    if ("EXEMPT".equals(d.taxContext()) && c.add(s).add(i).signum() != 0)
      f.add(
          block(
              "EXEMPT_TAX",
              "taxContext",
              c.add(s).add(i),
              0,
              "Exempt supplies cannot carry GST under this classification."));
    if (!blank(d.supplierState()) && !blank(d.placeOfSupply())) {
      boolean intra =
          d.supplierState().equals(d.placeOfSupply())
              && !Set.of("EXPORT", "SEZ").contains(Objects.toString(d.taxContext(), ""));
      if (intra && i.signum() != 0)
        f.add(block("INTRA_STATE", "igst", i, 0, "Intra-state supplies use CGST and SGST/UTGST."));
      if (!intra && (c.signum() != 0 || s.signum() != 0))
        f.add(
            block(
                "INTER_STATE",
                "cgst",
                c.add(s),
                0,
                "Inter-state/SEZ/export treatment cannot combine central/state tax with IGST."));
      if (intra) compare(f, "SPLIT_TAX", "sgst", s, c, tolerance);
    }
    if ((credit || "DEBIT_NOTE".equals(d.documentType())) && blank(d.originalInvoiceNumber()))
      f.add(
          block(
              "NOTE_REFERENCE",
              "originalInvoiceNumber",
              null,
              "Original invoice reference",
              "Credit/debit notes need an explicit original invoice reference."));
    if (d.total() != null && (credit ? d.total().signum() >= 0 : d.total().signum() < 0))
      f.add(
          block(
              "DOCUMENT_SIGN",
              "total",
              d.total(),
              credit ? "Negative" : "Non-negative",
              "The accounting convention uses signed credit note amounts."));
    if (Boolean.TRUE.equals(d.einvoiceRequired())
        && (blank(d.irn()) || !d.irn().matches("[a-fA-F0-9]{64}")))
      f.add(
          block(
              "IRN_REQUIRED",
              "irn",
              d.irn(),
              "64 hex characters",
              "Configured e-invoice applicability requires an IRN; format is not government verification."));
    if (d.lines().isEmpty())
      f.add(
          block(
              "LINES",
              "lines",
              0,
              "At least one line",
              "Invoice lines are required for arithmetic and matching."));
    BigDecimal taxable = BigDecimal.ZERO,
        lc = BigDecimal.ZERO,
        ls = BigDecimal.ZERO,
        li = BigDecimal.ZERO,
        cess = BigDecimal.ZERO,
        gross = BigDecimal.ZERO,
        discount = BigDecimal.ZERO;
    for (int n = 0; n < d.lines().size(); n++) {
      var l = d.lines().get(n);
      String p = "lines." + n + ".";
      if (l.quantity() == null
          || l.price() == null
          || l.taxableAmount() == null
          || l.taxRate() == null
          || l.total() == null
          || l.cgst() == null
          || l.sgst() == null
          || l.igst() == null) {
        f.add(
            block(
                "LINE_REQUIRED",
                p + "quantity",
                null,
                "Complete line amounts",
                "Quantity, price, taxable amount, tax rate/components and line total are required."));
        continue;
      }
      if (blank(l.description()))
        f.add(
            block(
                "LINE_DESCRIPTION",
                p + "description",
                null,
                "Description",
                "Line description is required."));
      if (blank(l.hsn()) || !l.hsn().matches("[0-9]{4}|[0-9]{6}|[0-9]{8}"))
        f.add(
            block(
                "HSN_FORMAT",
                p + "hsn",
                l.hsn(),
                "4, 6 or 8 digits",
                "Confirm the applicable HSN/SAC code."));
      if (l.price().signum() < 0
          || l.quantity().signum() == 0
          || (credit ? l.quantity().signum() > 0 : l.quantity().signum() < 0)
          || l.taxRate().signum() < 0
          || l.taxRate().compareTo(new BigDecimal("100")) > 0)
        f.add(
            block(
                "LINE_RANGE",
                p + "quantity",
                l.quantity(),
                "Valid signed quantity, positive price and 0–100 rate",
                "Line values are outside supported ranges."));
      compare(
          f,
          "LINE_TAXABLE",
          p + "taxableAmount",
          l.taxableAmount(),
          l.quantity().multiply(l.price()).subtract(zero(l.discount())),
          tolerance);
      boolean intra =
          Objects.equals(d.supplierState(), d.placeOfSupply())
              && !Set.of("EXPORT", "SEZ").contains(Objects.toString(d.taxContext(), ""));
      if (intra) {
        compare(f, "LINE_SPLIT_TAX", p + "sgst", l.sgst(), l.cgst(), tolerance);
        if (l.igst().signum() != 0)
          f.add(
              block(
                  "LINE_TAX_CONTEXT",
                  p + "igst",
                  l.igst(),
                  0,
                  "Intra-state lines cannot carry IGST."));
      } else if (l.cgst().signum() != 0 || l.sgst().signum() != 0)
        f.add(
            block(
                "LINE_TAX_CONTEXT",
                p + "cgst",
                l.cgst(),
                0,
                "Inter-state lines cannot carry CGST or SGST."));
      for (var amount : List.of(l.taxableAmount(), l.cgst(), l.sgst(), l.igst(), l.total()))
        if (credit ? amount.signum() > 0 : amount.signum() < 0)
          f.add(
              block(
                  "LINE_AMOUNT_SIGN",
                  p + "total",
                  amount,
                  credit ? "Non-positive" : "Non-negative",
                  "Amounts must follow the document's signed accounting convention."));
      compare(
          f,
          "LINE_TAX",
          p + "taxRate",
          zero(l.cgst()).add(zero(l.sgst())).add(zero(l.igst())),
          l.taxableAmount()
              .multiply(l.taxRate())
              .divide(new BigDecimal("100"))
              .setScale(2, RoundingMode.HALF_UP),
          tolerance);
      compare(
          f,
          "LINE_TOTAL",
          p + "total",
          l.total(),
          l.taxableAmount()
              .add(zero(l.cgst()))
              .add(zero(l.sgst()))
              .add(zero(l.igst()))
              .add(zero(l.cess())),
          tolerance);
      taxable = taxable.add(l.taxableAmount());
      lc = lc.add(zero(l.cgst()));
      ls = ls.add(zero(l.sgst()));
      li = li.add(zero(l.igst()));
      cess = cess.add(zero(l.cess()));
      gross = gross.add(l.quantity().multiply(l.price()));
      discount = discount.add(zero(l.discount()));
    }
    compare(f, "HEADER_TAXABLE", "taxableAmount", d.taxableAmount(), taxable, tolerance);
    compare(f, "HEADER_GROSS", "grossAmount", d.grossAmount(), gross, tolerance);
    compare(f, "HEADER_DISCOUNT", "discount", d.discount(), discount, tolerance);
    compare(f, "HEADER_CGST", "cgst", d.cgst(), lc, tolerance);
    compare(f, "HEADER_SGST", "sgst", d.sgst(), ls, tolerance);
    compare(f, "HEADER_IGST", "igst", d.igst(), li, tolerance);
    compare(f, "HEADER_CESS", "cess", zero(d.cess()), cess, tolerance);
    compare(
        f,
        "GRAND_TOTAL",
        "total",
        d.total(),
        zero(d.taxableAmount())
            .add(c)
            .add(s)
            .add(i)
            .add(zero(d.cess()))
            .add(zero(d.charges()))
            .add(zero(d.roundOff())),
        tolerance);
    if (zero(d.roundOff()).abs().compareTo(new BigDecimal("1")) > 0)
      f.add(
          block(
              "ROUND_OFF",
              "roundOff",
              d.roundOff(),
              "Within ₹1",
              "Larger adjustments must be represented as charges, not rounding."));
    return f;
  }

  private void compare(
      List<Finding> f,
      String rule,
      String field,
      BigDecimal actual,
      BigDecimal expected,
      BigDecimal tolerance) {
    if (actual != null && actual.subtract(expected).abs().compareTo(tolerance) > 0)
      f.add(
          block(
              rule,
              field,
              actual,
              expected,
              "Amount differs beyond the configured rounding tolerance of ₹" + tolerance + "."));
  }
}
