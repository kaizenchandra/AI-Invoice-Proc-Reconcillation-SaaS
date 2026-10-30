package com.kaizen.invoice.infrastructure;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kaizen.invoice.domain.Problem;
import org.junit.jupiter.api.Test;

class AzureExtractionConfigurationTest {
  @Test
  void requiresBothEndpointAndKey() {
    var empty = AzureExtractionConfiguration.inspect("", "", "prebuilt-invoice");
    assertThat(empty.configured()).isFalse();
    assertThat(empty.missingVariables())
        .containsExactly("AZURE_DOCUMENT_ENDPOINT", "AZURE_DOCUMENT_KEY");
    var noKey =
        AzureExtractionConfiguration.inspect(
            "https://invoice.cognitiveservices.azure.com", "", "prebuilt-invoice");
    assertThat(noKey.configured()).isFalse();
    assertThat(noKey.missingVariables()).containsExactly("AZURE_DOCUMENT_KEY");
  }

  @Test
  void rejectsMalformedAndUnsafeEndpoints() {
    for (String endpoint :
        java.util.List.of(
            "http://invoice.example",
            "not a URI",
            "https://user:secret@invoice.example",
            "https://invoice.example/path",
            "https://invoice.example?key=secret")) {
      var result =
          AzureExtractionConfiguration.inspect(endpoint, "private-key", "prebuilt-invoice");
      assertThat(result.status()).isEqualTo("INVALID_CONFIGURATION");
      assertThat(result.configured()).isFalse();
    }
  }

  @Test
  void placeholderValuesAreNotReportedAsConfigured() {
    assertThat(
            AzureExtractionConfiguration.inspect(
                    "https://your-resource.cognitiveservices.azure.com",
                    "replace-with-key",
                    "prebuilt-invoice")
                .configured())
        .isFalse();
  }

  @Test
  void configuredStatusDoesNotExposeCredentialsOrClaimLiveVerification() throws Exception {
    var mapper = new ObjectMapper();
    var adapter =
        new AzureDocumentExtraction(
            " https://invoice.cognitiveservices.azure.com/ ",
            " private-key ",
            "",
            mapper,
            new InvoiceExtractionNormalizer(mapper));
    var status = adapter.configuration();
    assertThat(status.configured()).isTrue();
    assertThat(status.testMode()).isFalse();
    assertThat(status.model()).isEqualTo("prebuilt-invoice");
    assertThat(mapper.writeValueAsString(status)).doesNotContain("private-key", "https://invoice");
    assertThat(status.message()).contains("checked when a document is processed");
  }

  @Test
  void unconfiguredAdapterFailsBeforeSendingADocument() {
    var mapper = new ObjectMapper();
    var adapter =
        new AzureDocumentExtraction(
            "", "", "prebuilt-invoice", mapper, new InvoiceExtractionNormalizer(mapper));
    assertThatThrownBy(() -> adapter.extract(new byte[] {1}, "application/pdf"))
        .isInstanceOf(Problem.class)
        .hasMessageContaining("AZURE_DOCUMENT_ENDPOINT")
        .hasMessageContaining("AZURE_DOCUMENT_KEY");
  }
}
