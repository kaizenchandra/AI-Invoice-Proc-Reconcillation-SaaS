package com.kaizen.invoice.infrastructure;

import com.fasterxml.jackson.databind.*;
import com.kaizen.invoice.application.DocumentExtractionPort;
import com.kaizen.invoice.domain.Problem;
import java.net.*;
import java.net.http.*;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class AzureDocumentExtraction implements DocumentExtractionPort {
  private final String endpoint, key, model;
  private final ObjectMapper mapper;
  private final InvoiceExtractionNormalizer normalizer;
  private final HttpClient http =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

  public AzureDocumentExtraction(
      @Value("${app.extraction.endpoint}") String endpoint,
      @Value("${app.extraction.key}") String key,
      @Value("${app.extraction.model}") String model,
      ObjectMapper mapper,
      InvoiceExtractionNormalizer normalizer) {
    this.endpoint = endpoint.trim().replaceAll("/+$", "");
    this.key = key.trim();
    this.model = model.isBlank() ? "prebuilt-invoice" : model.trim();
    this.mapper = mapper;
    this.normalizer = normalizer;
  }

  @Override
  public Configuration configuration() {
    return AzureExtractionConfiguration.inspect(endpoint, key, model);
  }

  public Result extract(byte[] bytes, String mime) throws Exception {
    Configuration configuration = configuration();
    if (!configuration.configured()) throw new Problem(503, configuration.message());
    URI base = URI.create(endpoint);
    if (!"https".equals(base.getScheme()))
      throw new Problem(503, "Extraction endpoint must use HTTPS");
    var response =
        http.send(
            HttpRequest.newBuilder(
                    URI.create(
                        endpoint
                            + "/documentintelligence/documentModels/"
                            + URLEncoder.encode(model, java.nio.charset.StandardCharsets.UTF_8)
                            + ":analyze?api-version=2024-11-30"))
                .timeout(Duration.ofSeconds(60))
                .header("Ocp-Apim-Subscription-Key", key)
                .header("Content-Type", mime)
                .POST(HttpRequest.BodyPublishers.ofByteArray(bytes))
                .build(),
            HttpResponse.BodyHandlers.ofString());
    if (response.statusCode() != 202)
      throw new Problem(
          502, "Extraction provider rejected the request (HTTP " + response.statusCode() + ")");
    URI poll = URI.create(response.headers().firstValue("operation-location").orElseThrow());
    if (!base.getHost().equals(poll.getHost()) || !"https".equals(poll.getScheme()))
      throw new Problem(502, "Invalid provider operation location");
    for (int n = 0; n < 75; n++) {
      Thread.sleep(2000);
      var r =
          http.send(
              HttpRequest.newBuilder(poll)
                  .timeout(Duration.ofSeconds(20))
                  .header("Ocp-Apim-Subscription-Key", key)
                  .GET()
                  .build(),
              HttpResponse.BodyHandlers.ofString());
      if (r.statusCode() != 200)
        throw new Problem(502, "Extraction polling failed (HTTP " + r.statusCode() + ")");
      JsonNode raw = mapper.readTree(r.body());
      String state = raw.path("status").asText();
      if ("succeeded".equals(state)) {
        var normalized = normalizer.normalize(raw);
        return new Result(
            "azure-document-intelligence",
            model + "/2024-11-30",
            raw,
            normalized.data(),
            normalized.evidence());
      }
      if ("failed".equals(state))
        throw new Problem(
            422,
            "Provider could not extract this document. Review its quality and supported type.");
    }
    throw new Problem(504, "Extraction timed out; a bounded retry will be scheduled");
  }
}
