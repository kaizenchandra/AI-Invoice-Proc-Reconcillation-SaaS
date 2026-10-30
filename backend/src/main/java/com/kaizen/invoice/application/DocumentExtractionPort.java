package com.kaizen.invoice.application;

import com.kaizen.invoice.domain.InvoiceData;
import java.util.List;
import java.util.Map;

public interface DocumentExtractionPort {
  record Configuration(
      String provider,
      String model,
      boolean configured,
      boolean testMode,
      String status,
      String message,
      List<String> missingVariables) {}

  default Configuration configuration() {
    return new Configuration(
        "Unconfigured extraction adapter",
        "",
        false,
        false,
        "NOT_CONFIGURED",
        "The active extraction adapter has not declared its configuration.",
        List.of());
  }

  record Result(
      String provider, String model, Object raw, InvoiceData data, Map<String, Object> evidence) {}

  Result extract(byte[] bytes, String mime) throws Exception;
}
