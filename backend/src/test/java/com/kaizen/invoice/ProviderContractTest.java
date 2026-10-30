package com.kaizen.invoice;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kaizen.invoice.infrastructure.InvoiceExtractionNormalizer;
import org.junit.jupiter.api.Test;

class ProviderContractTest {
  final ObjectMapper mapper = new ObjectMapper();

  @Test
  void absentIndianTaxDetailsRemainUnknownAndEvidenceIsPreserved() throws Exception {
    var raw =
        mapper.readTree(
            """
      {"analyzeResult":{"documents":[{"fields":{
        "InvoiceId":{"valueString":"INV-1","content":"INV-1","confidence":0.92,"boundingRegions":[{"pageNumber":2,"polygon":[1,1,2,1,2,2,1,2]}]},
        "InvoiceDate":{"valueDate":"2026-10-01"},
        "InvoiceTotal":{"valueCurrency":{"amount":590.00,"currencyCode":"INR"}},
        "TotalTax":{"valueCurrency":{"amount":90.00}},
        "Items":{"valueArray":[{"valueObject":{"Description":{"valueString":"Brackets"},"Quantity":{"valueNumber":5},"UnitPrice":{"valueCurrency":{"amount":100}}}}]}
      }}]}}
      """);
    var result = new InvoiceExtractionNormalizer(mapper).normalize(raw);
    assertThat(result.data().invoiceNumber()).isEqualTo("INV-1");
    assertThat(result.data().financialYear()).isEqualTo("2026-2027");
    assertThat(result.data().cgst()).isNull();
    assertThat(result.data().sgst()).isNull();
    assertThat(result.data().taxContext()).isNull();
    assertThat(result.data().documentType()).isEqualTo("UNKNOWN");
    assertThat(result.evidence()).containsKey("invoiceNumber");
    assertThat(result.data().lines().get(0).taxableAmount()).isNull();
  }

  @Test
  void multipleInvoiceDocumentsCannotBeSilentlyMerged() throws Exception {
    var raw = mapper.readTree("{\"analyzeResult\":{\"documents\":[{},{}]}}");
    assertThatThrownBy(() -> new InvoiceExtractionNormalizer(mapper).normalize(raw))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
