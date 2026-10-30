package com.kaizen.invoice.infrastructure;

import com.kaizen.invoice.application.AccountingExportPort;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class CsvAccountingExport implements AccountingExportPort {
  public static final Set<String> FIELDS =
      Set.of(
          "invoiceNumber",
          "invoiceDate",
          "supplierName",
          "supplierGstin",
          "buyerGstin",
          "poNumber",
          "financialYear",
          "documentType",
          "taxableAmount",
          "cgst",
          "sgst",
          "igst",
          "cess",
          "charges",
          "roundOff",
          "total",
          "currency");

  public byte[] export(List<Map<String, Object>> records, List<String> fields) {
    if (fields.isEmpty() || !FIELDS.containsAll(fields))
      throw new IllegalArgumentException("Unsupported export mapping");
    StringBuilder csv = new StringBuilder("\uFEFF");
    csv.append(String.join(",", fields.stream().map(CsvAccountingExport::cell).toList()))
        .append("\r\n");
    for (var record : records)
      csv.append(
              String.join(
                  ",",
                  fields.stream().map(f -> cell(Objects.toString(record.get(f), ""))).toList()))
          .append("\r\n");
    return csv.toString().getBytes(StandardCharsets.UTF_8);
  }

  public static String cell(String raw) {
    String value = raw;
    String trimmed = value.stripLeading();
    if (!trimmed.isEmpty()
        && ("=+-@".indexOf(trimmed.charAt(0)) >= 0
            || value.charAt(0) == '\t'
            || value.charAt(0) == '\r'
            || value.charAt(0) == '\n')) value = "'" + value;
    return "\"" + value.replace("\"", "\"\"") + "\"";
  }
}
