package com.kaizen.invoice.api;

import static com.kaizen.invoice.domain.Problem.require;

import com.kaizen.invoice.application.IntakeService;
import com.kaizen.invoice.domain.Problem;
import com.kaizen.invoice.infrastructure.Database;
import com.kaizen.invoice.security.Access;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartHttpServletRequest;

@RestController
@RequestMapping("/api/v1/email")
public class EmailController {
  private final Database db;
  private final IntakeService intake;
  private final String signingKey, domain;

  public EmailController(
      Database db,
      IntakeService intake,
      @Value("${app.email.signing-key}") String signingKey,
      @Value("${app.email.domain}") String domain) {
    this.db = db;
    this.intake = intake;
    this.signingKey = signingKey;
    this.domain = domain;
  }

  @PostMapping("/mailgun")
  public ResponseEntity<Map<String, Object>> receive(MultipartHttpServletRequest request)
      throws Exception {
    require(!signingKey.isBlank() && !domain.isBlank(), 503, "Email intake is not configured");
    String timestamp = request.getParameter("timestamp"),
        token = request.getParameter("token"),
        signature = request.getParameter("signature");
    require(timestamp != null && token != null && signature != null, 401, "Missing signature");
    require(
        timestamp.matches("[0-9]{1,12}")
            && signature.matches("[a-fA-F0-9]{64}")
            && token.length() <= 256,
        401,
        "Malformed webhook signature");
    require(
        Math.abs(Instant.now().getEpochSecond() - Long.parseLong(timestamp)) <= 300,
        401,
        "Expired webhook signature");
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(signingKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
    require(
        MessageDigest.isEqual(
            mac.doFinal((timestamp + token).getBytes(StandardCharsets.UTF_8)),
            HexFormat.of().parseHex(signature)),
        401,
        "Invalid webhook signature");
    String recipient =
        Objects.toString(request.getParameter("recipient"), "").toLowerCase(Locale.ROOT);
    require(
        recipient.indexOf('@') == recipient.lastIndexOf('@')
            && recipient.endsWith("@" + domain.toLowerCase(Locale.ROOT)),
        403,
        "Unknown intake domain");
    var org =
        db.one(
            "select id from organizations where intake_alias=?",
            recipient.substring(0, recipient.indexOf('@')));
    UUID tenant = (UUID) org.get("id");
    UUID actor =
        (UUID)
            db.one(
                    "select user_id from memberships where tenant_id=? and role='ORG_ADMIN' order by user_id limit 1",
                    tenant)
                .get("user_id");
    String message = Objects.toString(request.getParameter("Message-Id"), "");
    require(
        !message.isBlank() && message.length() <= 2000,
        400,
        "Message-Id is required and must be at most 2000 characters");
    int fileCount = request.getMultiFileMap().values().stream().mapToInt(List::size).sum();
    require(fileCount > 0 && fileCount <= 100, 400, "Email must contain 1–100 attachments");
    var metadata =
        Map.of(
            "sender",
            Objects.toString(request.getParameter("sender"), ""),
            "subject",
            Objects.toString(request.getParameter("subject"), ""),
            "messageId",
            message,
            "receivedAt",
            Instant.ofEpochSecond(Long.parseLong(timestamp)).toString());
    UUID batch =
        db.transaction(
            () -> {
              db.one("select id from organizations where id=? for update", tenant);
              db.update(
                  "insert into email_events(provider_id,tenant_id,metadata) values(?,?,?::jsonb) on conflict do nothing",
                  tenant + ":" + message,
                  tenant,
                  db.json(metadata));
              var event =
                  db.one(
                      "select batch_id from email_events where provider_id=? and tenant_id=? for update",
                      tenant + ":" + message,
                      tenant);
              if (event.get("batch_id") != null) return (UUID) event.get("batch_id");
              UUID created = UUID.randomUUID();
              db.update(
                  "insert into ingestion_batches(id,tenant_id,actor) values(?,?,?)",
                  created,
                  tenant,
                  actor);
              db.update(
                  "update email_events set batch_id=? where provider_id=? and tenant_id=?",
                  created,
                  tenant + ":" + message,
                  tenant);
              return created;
            });
    List<Map<String, Object>> outcomes = new ArrayList<>();
    boolean retryableFailure = false;
    for (var entry : request.getMultiFileMap().entrySet())
      for (int part = 0; part < entry.getValue().size(); part++) {
        var file = entry.getValue().get(part);
        String key =
            "email:" + IdentityController.hash(message + ":" + entry.getKey() + ":" + part);
        try {
          var row =
              intake.upload(
                  new Access.Scope(tenant, actor, "ORG_ADMIN"),
                  file.getOriginalFilename(),
                  file.getBytes(),
                  key,
                  batch,
                  "EMAIL",
                  metadata);
          outcomes.add(
              Map.of("id", row.get("id"), "filename", row.get("filename"), "status", "ACCEPTED"));
        } catch (RuntimeException error) {
          boolean retryable = !(error instanceof Problem p) || p.status >= 500 || p.status == 409;
          retryableFailure |= retryable;
          outcomes.add(
              Map.of(
                  "requestKey",
                  key,
                  "filename",
                  Objects.toString(file.getOriginalFilename(), "attachment"),
                  "status",
                  "FAILED",
                  "retryable",
                  retryable,
                  "error",
                  error instanceof Problem
                      ? error.getMessage()
                      : "Attachment acceptance failed; provider redelivery will retry it."));
        }
      }
    // A transient failure requests provider redelivery only after all attachments have been tried.
    // Accepted parts return their original invoice on replay; malformed parts remain visible.
    return ResponseEntity.status(retryableFailure ? 503 : 200)
        .body(Map.of("batchId", batch, "attachments", outcomes));
  }
}
