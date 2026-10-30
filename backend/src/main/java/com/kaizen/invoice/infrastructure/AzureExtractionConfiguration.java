package com.kaizen.invoice.infrastructure;

import com.kaizen.invoice.application.DocumentExtractionPort.Configuration;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/** Local configuration validation only; never returns credentials or claims live connectivity. */
final class AzureExtractionConfiguration {
  private AzureExtractionConfiguration() {}

  static Configuration inspect(String endpoint, String key, String model) {
    var missing = new ArrayList<String>();
    if (unset(endpoint)) missing.add("AZURE_DOCUMENT_ENDPOINT");
    if (unset(key)) missing.add("AZURE_DOCUMENT_KEY");
    if (!missing.isEmpty())
      return new Configuration(
          "Azure Document Intelligence",
          model,
          false,
          false,
          "NOT_CONFIGURED",
          "Set "
              + String.join(" and ", missing)
              + " in the backend environment, then restart the backend. Accepted documents remain queued.",
          List.copyOf(missing));
    try {
      URI uri = URI.create(endpoint);
      if (!"https".equalsIgnoreCase(uri.getScheme())
          || uri.getHost() == null
          || uri.getUserInfo() != null
          || uri.getQuery() != null
          || uri.getFragment() != null
          || !(uri.getPath().isEmpty() || uri.getPath().equals("/")))
        throw new IllegalArgumentException();
    } catch (IllegalArgumentException e) {
      return new Configuration(
          "Azure Document Intelligence",
          model,
          false,
          false,
          "INVALID_CONFIGURATION",
          "AZURE_DOCUMENT_ENDPOINT must be the HTTPS resource endpoint from Azure Keys and Endpoint, without an API path, query or credentials.",
          List.of());
    }
    return new Configuration(
        "Azure Document Intelligence",
        model,
        true,
        false,
        "CONFIGURED",
        "Provider credentials are configured. Live authentication and extraction are checked when a document is processed.",
        List.of());
  }

  private static boolean unset(String value) {
    if (value == null || value.isBlank()) return true;
    String lower = value.trim().toLowerCase(java.util.Locale.ROOT);
    return lower.contains("replace-with")
        || lower.contains("your-resource")
        || lower.contains("your-key")
        || lower.startsWith("<");
  }
}
