package com.kaizen.invoice;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kaizen.invoice.domain.*;
import com.kaizen.invoice.infrastructure.CsvAccountingExport;
import com.kaizen.invoice.security.Access;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;

public class DomainTest {
  public static InvoiceData invoice(String number, UUID supplier, String po, String quantity) {
    BigDecimal qty = new BigDecimal(quantity),
        taxable = qty.multiply(new BigDecimal("100.00")),
        tax = taxable.multiply(new BigDecimal("0.09")),
        total = taxable.add(tax.multiply(new BigDecimal("2")));
    var line =
        new InvoiceData.Line(
            "STEEL-01",
            "Steel brackets",
            "73269099",
            qty,
            "PCS",
            new BigDecimal("100.00"),
            BigDecimal.ZERO,
            taxable,
            new BigDecimal("18"),
            tax,
            tax,
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            total);
    return new InvoiceData(
        number,
        "2026-10-01",
        "2026-11-01",
        "2026-2027",
        "TAX_INVOICE",
        "INR",
        supplier,
        "Pune Components",
        "27AAPFU0939F1ZV",
        "AAPFU0939F",
        "Pune",
        "27",
        null,
        "Kaizen Manufacturing",
        "27AAPFU0939F1ZV",
        "Pune",
        "Pune",
        "27",
        po,
        "NET30",
        null,
        null,
        "REGISTERED",
        false,
        taxable,
        BigDecimal.ZERO,
        taxable,
        tax,
        tax,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        total,
        List.of(line));
  }

  @Test
  void validIndianInvoiceHasNoFindings() {
    assertThat(
            new InvoiceValidator()
                .validate(
                    invoice("INV-1", UUID.randomUUID(), "PO-1", "5"),
                    new BigDecimal("0.50"),
                    Set.of()))
        .isEmpty();
  }

  @Test
  void gstChecksumRejectsMutation() {
    assertThat(Gstin.valid("27AAPFU0939F1ZV")).isTrue();
    assertThat(Gstin.valid("27AAPFU0939F1ZA")).isFalse();
    assertThat(Gstin.valid("00AAPFU0939F1ZV")).isFalse();
  }

  @Test
  void incorrectTotalsBlockApproval() {
    ObjectMapper mapper = new ObjectMapper();
    Map<String, Object> data =
        mapper.convertValue(
            invoice("INV-2", UUID.randomUUID(), "PO-1", "5"),
            new com.fasterxml.jackson.core.type.TypeReference<>() {});
    data.put("total", "700.00");
    var findings =
        new InvoiceValidator()
            .validate(
                mapper.convertValue(data, InvoiceData.class), new BigDecimal("0.50"), Set.of());
    assertThat(findings).anyMatch(f -> f.ruleId().equals("GRAND_TOTAL") && f.blocking());
  }

  @Test
  void csvFormulaPayloadsAreNeutralized() {
    for (String s : List.of("=HYPERLINK(\"evil\")", " +1", "-1", "@SUM(A1)", "\tcmd"))
      assertThat(CsvAccountingExport.cell(s)).startsWith("\"'");
    assertThat(CsvAccountingExport.cell("Pune, \"Steel\"")).isEqualTo("\"Pune, \"\"Steel\"\"\"");
  }

  @Test
  void processorCannotApproveAndAuditorCannotWrite() {
    assertThat(Access.permissions("PROCESSOR"))
        .doesNotContain(Access.Permission.APPROVE, Access.Permission.ADMIN);
    assertThat(Access.permissions("AUDITOR")).containsExactly(Access.Permission.READ);
  }
}
