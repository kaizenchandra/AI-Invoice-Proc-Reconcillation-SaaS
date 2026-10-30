package com.kaizen.invoice.infrastructure;

import com.fasterxml.jackson.databind.*;
import com.kaizen.invoice.domain.InvoiceData;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class InvoiceExtractionNormalizer {
  private final ObjectMapper mapper;

  public InvoiceExtractionNormalizer(ObjectMapper mapper) {
    this.mapper = mapper;
  }

  public record Normalized(InvoiceData data, Map<String, Object> evidence) {}

  public Normalized normalize(JsonNode root) {
    JsonNode docs = root.path("analyzeResult").path("documents");
    if (!docs.isArray() || docs.size() != 1)
      throw new IllegalArgumentException(
          "Expected one invoice per document; split or classify the document manually");
    JsonNode fields = docs.get(0).path("fields");
    Map<String, Object> data = new LinkedHashMap<>(), evidence = new LinkedHashMap<>();
    Map<String, String> map =
        Map.ofEntries(
            Map.entry("InvoiceId", "invoiceNumber"),
            Map.entry("InvoiceDate", "invoiceDate"),
            Map.entry("DueDate", "dueDate"),
            Map.entry("VendorName", "supplierName"),
            Map.entry("VendorTaxId", "supplierGstin"),
            Map.entry("VendorAddress", "supplierAddress"),
            Map.entry("CustomerName", "buyerName"),
            Map.entry("CustomerTaxId", "buyerGstin"),
            Map.entry("BillingAddress", "billingAddress"),
            Map.entry("ShippingAddress", "shippingAddress"),
            Map.entry("PurchaseOrder", "poNumber"),
            Map.entry("PaymentTerm", "paymentTerms"),
            Map.entry("SubTotal", "taxableAmount"),
            Map.entry("InvoiceTotal", "total"));
    map.forEach(
        (provider, canonical) -> {
          JsonNode field = fields.path(provider);
          Object value = value(field);
          if (value != null) {
            data.put(canonical, value);
            evidence.put(canonical, evidence(field));
          }
        });
    String date = Objects.toString(data.get("invoiceDate"), "");
    if (!date.isBlank()) {
      int y = LocalDate.parse(date).getYear() - (LocalDate.parse(date).getMonthValue() < 4 ? 1 : 0);
      data.put("financialYear", y + "-" + (y + 1));
    }
    JsonNode currency = fields.path("InvoiceTotal").path("valueCurrency").path("currencyCode");
    if (currency.isTextual()) data.put("currency", currency.asText());
    data.put(
        "documentType",
        "UNKNOWN"); // Provider prebuilt-invoice class; note subtype must be confirmed in review.
    // Custom Azure models can emit the full canonical field catalog. A provider can never
    // choose a trusted master-data UUID or decide e-invoice applicability.
    for (var component : InvoiceData.class.getRecordComponents()) {
      String name = component.getName();
      if (Set.of("lines", "supplierId", "einvoiceRequired").contains(name)) continue;
      JsonNode field = fields.path(name);
      if (field.isMissingNode())
        field = fields.path(Character.toUpperCase(name.charAt(0)) + name.substring(1));
      Object extracted = value(field);
      if (extracted != null) {
        data.put(name, extracted);
        evidence.put(name, evidence(field));
      }
    }
    for (String name : List.of("supplierGstin", "buyerGstin", "supplierPan"))
      if (data.get(name) instanceof String text)
        data.put(name, text.replaceAll("\\s+", "").toUpperCase(Locale.ROOT));
    List<Map<String, Object>> lines = new ArrayList<>();
    int index = 0;
    for (JsonNode item : fields.path("Items").path("valueArray")) {
      var l = new LinkedHashMap<String, Object>();
      JsonNode o = item.path("valueObject");
      for (var e :
          Map.of(
                  "ProductCode",
                  "code",
                  "Description",
                  "description",
                  "Quantity",
                  "quantity",
                  "Unit",
                  "unit",
                  "UnitPrice",
                  "price",
                  "Amount",
                  "total",
                  "TaxRate",
                  "taxRate")
              .entrySet()) {
        Object v = value(o.path(e.getKey()));
        if (v != null) {
          l.put(e.getValue(), v);
          evidence.put("lines." + index + "." + e.getValue(), evidence(o.path(e.getKey())));
        }
      }
      for (var component : InvoiceData.Line.class.getRecordComponents()) {
        String name = component.getName();
        JsonNode field = o.path(name);
        if (field.isMissingNode())
          field = o.path(Character.toUpperCase(name.charAt(0)) + name.substring(1));
        Object extracted = value(field);
        if (extracted != null) {
          l.put(name, extracted);
          evidence.put("lines." + index + "." + name, evidence(field));
        }
      }
      lines.add(l);
      index++;
    }
    data.put("lines", lines);
    // Missing GST components, HSN and transaction context stay unknown; arithmetic never fabricates
    // them.
    return new Normalized(mapper.convertValue(data, InvoiceData.class), evidence);
  }

  private Object value(JsonNode field) {
    if (field.isMissingNode()) return null;
    for (String k : List.of("valueString", "valueDate", "valueNumber"))
      if (field.hasNonNull(k)) return field.get(k).asText();
    if (field.has("valueCurrency"))
      return field.path("valueCurrency").path("amount").isNumber()
          ? new BigDecimal(field.path("valueCurrency").path("amount").asText()).toPlainString()
          : null;
    return field.hasNonNull("content") ? field.get("content").asText() : null;
  }

  private Map<String, Object> evidence(JsonNode field) {
    Map<String, Object> e = new LinkedHashMap<>();
    e.put("source", "Azure Document Intelligence");
    if (field.has("content")) e.put("original", field.path("content").asText());
    if (field.has("confidence")) e.put("confidence", field.path("confidence").decimalValue());
    if (field.has("boundingRegions")) e.put("regions", field.path("boundingRegions"));
    return e;
  }
}
