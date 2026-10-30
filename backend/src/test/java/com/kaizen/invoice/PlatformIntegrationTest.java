package com.kaizen.invoice;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.kaizen.invoice.application.*;
import com.kaizen.invoice.domain.*;
import com.kaizen.invoice.infrastructure.*;
import com.kaizen.invoice.security.Access;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;

@SpringBootTest(
    properties = {
      "app.worker.enabled=false",
      "app.storage.access-key=test-key",
      "app.storage.secret-key=test-secret",
      "app.scanner.enabled=false",
      "app.email.signing-key=synthetic-signing-key",
      "app.email.domain=intake.example.test"
    })
@AutoConfigureMockMvc
@Testcontainers
class PlatformIntegrationTest {
  @Container
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17.6-alpine");

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry p) {
    p.add("spring.datasource.url", postgres::getJdbcUrl);
    p.add("spring.datasource.username", postgres::getUsername);
    p.add("spring.datasource.password", postgres::getPassword);
  }

  @Autowired Database db;
  @Autowired InvoiceService invoices;
  @Autowired EvaluationService evaluations;
  @Autowired IntakeService intake;
  @Autowired ProcessingWorker worker;
  @Autowired ExportService exports;
  @Autowired MockMvc mvc;
  @MockitoBean DocumentStorage storage;
  @MockitoBean DocumentExtractionPort extraction;
  @MockitoBean AccountingExportPort exporter;
  UUID tenant, other, processor, approver, supplier, po;
  Access.Scope prep, approve;

  @AfterEach
  void clearPendingTestJobs() {
    db.update(
        "update processing_jobs set state='FAILED' where tenant_id=? and state in ('QUEUED','RUNNING')",
        tenant);
  }

  @BeforeEach
  void setup() throws Exception {
    when(extraction.configuration())
        .thenReturn(
            new DocumentExtractionPort.Configuration(
                "TEST_DOUBLE",
                "fixture-v1",
                true,
                true,
                "TEST_MODE",
                "Explicit integration test adapter",
                List.of()));
    tenant = UUID.randomUUID();
    other = UUID.randomUUID();
    processor = UUID.randomUUID();
    approver = UUID.randomUUID();
    supplier = UUID.randomUUID();
    po = UUID.randomUUID();
    for (UUID user : List.of(processor, approver))
      db.update(
          "insert into app_users(id,email,name,password_hash) values(?,?,?,'unused')",
          user,
          user + "@example.test",
          "Test User");
    for (UUID t : List.of(tenant, other))
      db.update(
          "insert into organizations(id,name,profile,settings,intake_alias) values(?,'Test Organization',?::jsonb,?::jsonb,?)",
          t,
          db.json(Map.of("gstin", "27AAPFU0939F1ZV")),
          db.json(com.kaizen.invoice.api.IdentityController.defaults()),
          t.toString());
    db.update(
        "insert into memberships values(?,?,'PROCESSOR'),(?,?,'APPROVER')",
        tenant,
        processor,
        tenant,
        approver);
    db.update(
        "insert into suppliers(id,tenant_id,name,gstin,state,address) values(?,?,'Pune Components','27AAPFU0939F1ZV','27','Pune')",
        supplier,
        tenant);
    db.update(
        "insert into purchase_orders(id,tenant_id,supplier_id,number,date) values(?,?,?,'PO-1','2026-10-01')",
        po,
        tenant,
        supplier);
    db.update(
        "insert into po_lines(id,tenant_id,po_id,code,description,quantity,price,tax_rate) values(?,?,?,'STEEL-01','Steel brackets',10,100,18)",
        UUID.randomUUID(),
        tenant,
        po);
    prep = new Access.Scope(tenant, processor, "PROCESSOR");
    approve = new Access.Scope(tenant, approver, "APPROVER");
    doAnswer(i -> new CsvAccountingExport().export(i.getArgument(0), i.getArgument(1)))
        .when(exporter)
        .export(anyList(), anyList());
  }

  UUID create(String number, String qty) {
    UUID id = UUID.randomUUID();
    db.update(
        "insert into invoices(id,tenant_id,creator,sha256,storage_key,filename,mime,source,request_key,processing_state) values(?,?,?,?,?,'invoice.pdf','application/pdf','UPLOAD',?,'PROCESSED')",
        id,
        tenant,
        processor,
        id.toString(),
        tenant + "/" + id,
        id.toString());
    db.transaction(
        () -> {
          invoices.storeVersion(
              tenant,
              id,
              DomainTest.invoice(number, supplier, "PO-1", qty),
              "TEST_FIXTURE",
              processor);
          return null;
        });
    return id;
  }

  @Test
  void happyPathImmutableApprovalExportAndAudit() {
    UUID id = create("INV-A", "5");
    assertThat(evaluations.evaluate(tenant, invoices.row(tenant, id, false), false).blocked())
        .isFalse();
    invoices.transition(prep, id, "submit", 1, "Prepared", approver);
    invoices.transition(approve, id, "approve", 1, "Checked against source", null);
    var batch =
        exports.create(
            new Access.Scope(tenant, processor, "FINANCE_MANAGER"),
            List.of(id),
            UUID.randomUUID().toString(),
            null);
    assertThat(batch.get("status")).isEqualTo("COMPLETED");
    assertThat(invoices.row(tenant, id, false).get("business_state")).isEqualTo("EXPORTED");
    assertThatThrownBy(
            () ->
                invoices.save(
                    prep, id, 1, DomainTest.invoice("CHANGED", supplier, "PO-1", "5"), "change"))
        .isInstanceOf(Problem.class);
    assertThat(
            db.list(
                "select * from audit_events where tenant_id=? and action='EXPORT_COMPLETED'",
                tenant))
        .hasSize(1);
    assertThatThrownBy(() -> db.update("delete from audit_events where tenant_id=?", tenant))
        .isInstanceOf(Exception.class);
  }

  @Test
  void staleEditsRejectedAndOriginalPreserved() {
    UUID id = create("INV-J", "3");
    invoices.save(
        prep,
        id,
        1,
        DomainTest.invoice("INV-J-CORRECTED", supplier, "PO-1", "3"),
        "Corrected number");
    assertThatThrownBy(
            () ->
                invoices.save(
                    prep, id, 1, DomainTest.invoice("STALE", supplier, "PO-1", "3"), "stale"))
        .isInstanceOf(Problem.class)
        .hasMessageContaining("changed");
    assertThat(
            db.list(
                "select * from invoice_versions where tenant_id=? and invoice_id=?", tenant, id))
        .hasSize(2);
  }

  @Test
  void directUnauthorizedApprovalAndCrossTenantReadsDenied() throws Exception {
    UUID id = create("INV-I", "5");
    mvc.perform(
            post("/api/v1/invoices/" + id + "/approve")
                .with(user(processor + "@example.test"))
                .with(csrf())
                .header("X-Organization-ID", tenant)
                .contentType("application/json")
                .content("{\"version\":1}"))
        .andExpect(status().isForbidden());
    for (String path :
        List.of(
            "/invoices/" + id,
            "/invoices/" + id + "/document",
            "/suppliers",
            "/purchase-orders/" + po,
            "/exports"))
      mvc.perform(
              get("/api/v1" + path)
                  .with(user(processor + "@example.test"))
                  .header("X-Organization-ID", other))
          .andExpect(status().isForbidden());
  }

  @Test
  void matchingPreventsOverConsumptionUnderConcurrency() throws Exception {
    UUID a = create("INV-K1", "7"), b = create("INV-K2", "6");
    invoices.transition(prep, a, "submit", 1, "Prepared", approver);
    invoices.transition(prep, b, "submit", 1, "Prepared", approver);
    ExecutorService pool = Executors.newFixedThreadPool(2);
    var start = new CountDownLatch(1);
    AtomicInteger successes = new AtomicInteger();
    List<Future<?>> tasks = new ArrayList<>();
    for (UUID id : List.of(a, b))
      tasks.add(
          pool.submit(
              () -> {
                try {
                  start.await();
                  invoices.transition(approve, id, "approve", 1, "Checked", null);
                  successes.incrementAndGet();
                } catch (Problem expected) {
                } catch (Exception e) {
                  throw new RuntimeException(e);
                }
              }));
    start.countDown();
    for (var f : tasks) f.get(10, TimeUnit.SECONDS);
    pool.shutdown();
    assertThat(successes.get()).isEqualTo(1);
    BigDecimal used =
        (BigDecimal)
            db.one("select sum(quantity) total from po_allocations where tenant_id=?", tenant)
                .get("total");
    assertThat(used).isLessThanOrEqualTo(new BigDecimal("10"));
  }

  @Test
  void exportFailurePersistsAndRetryKeepsOneBatch() {
    UUID id = create("INV-L", "3");
    invoices.transition(prep, id, "submit", 1, "Prepared", approver);
    invoices.transition(approve, id, "approve", 1, "Checked", null);
    doThrow(new IllegalStateException("simulated failure"))
        .when(exporter)
        .export(anyList(), anyList());
    String key = UUID.randomUUID().toString();
    var b = exports.create(approve, List.of(id), key, null);
    assertThat(b.get("status")).isEqualTo("FAILED");
    doAnswer(i -> new CsvAccountingExport().export(i.getArgument(0), i.getArgument(1)))
        .when(exporter)
        .export(anyList(), anyList());
    var retried = exports.create(approve, List.of(id), key, null);
    assertThat(retried.get("id")).isEqualTo(b.get("id"));
    assertThat(retried.get("status")).isEqualTo("COMPLETED");
    assertThat(db.list("select * from export_records where tenant_id=?", tenant)).hasSize(1);
    assertThatThrownBy(
            () -> exports.create(approve, List.of(id), UUID.randomUUID().toString(), null))
        .isInstanceOf(Problem.class);
  }

  @Test
  void duplicateEvidenceBlocksAndDecisionIsVersionScoped() {
    UUID a = create("INV-C", "2"), b = create("INV-C", "2");
    var evaluation = evaluations.evaluate(tenant, invoices.row(tenant, b, false), false);
    assertThat(evaluation.findings()).anyMatch(f -> f.ruleId().equals("DUPLICATE"));
    invoices.clearDuplicate(
        approve, b, a, 1, "Different legitimate invoice, confirmed with source");
    assertThat(evaluations.evaluate(tenant, invoices.row(tenant, b, false), false).duplicates())
        .allMatch(r -> Boolean.TRUE.equals(r.get("cleared")));
    invoices.save(prep, b, 1, DomainTest.invoice("INV-C", supplier, "PO-1", "2"), "new edit");
    assertThat(evaluations.evaluate(tenant, invoices.row(tenant, b, false), false).blocked())
        .isTrue();
  }

  @Test
  void providerOutageHasBoundedRetriesThenRecoversWithoutOverwrite() throws Exception {
    UUID id = create("INV-G", "2");
    when(storage.get(anyString())).thenReturn("pdf".getBytes());
    when(extraction.extract(any(), any())).thenThrow(new Problem(504, "Provider timed out"));
    intake.retry(prep, id);
    for (int n = 0; n < 3; n++) {
      db.update("update processing_jobs set available_at=now() where tenant_id=?", tenant);
      assertThat(worker.runOne()).isTrue();
    }
    assertThat(invoices.row(tenant, id, false).get("processing_state")).isEqualTo("FAILED");
    assertThat(db.list("select * from extraction_runs where tenant_id=?", tenant)).hasSize(3);
    doReturn(
            new DocumentExtractionPort.Result(
                "TEST_DOUBLE",
                "fixture-v1",
                Map.of(),
                DomainTest.invoice("NEW_AI", supplier, "PO-1", "2"),
                Map.of()))
        .when(extraction)
        .extract(any(), any());
    intake.retry(prep, id);
    worker.runOne();
    assertThat(invoices.row(tenant, id, false).get("invoice_number")).isEqualTo("INV-G");
    assertThat(invoices.row(tenant, id, false).get("processing_state")).isEqualTo("PROCESSED");
  }

  @Test
  void organizationWritesRequireCsrf() throws Exception {
    mvc.perform(
            post("/api/v1/organizations")
                .with(user(processor + "@example.test"))
                .contentType("application/json")
                .content("{}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void failedAdmissionIsDurableAndBatchErrorsAreTenantSafe() throws Exception {
    UUID batch = UUID.randomUUID();
    db.update(
        "insert into ingestion_batches(id,tenant_id,actor) values(?,?,?)",
        batch,
        tenant,
        processor);
    intake.register(prep, "=bad.pdf", 3, "bad-document-key", batch, "UPLOAD", Map.of());
    assertThatThrownBy(
            () ->
                intake.upload(
                    prep,
                    "=bad.pdf",
                    new byte[] {1, 2, 3},
                    "bad-document-key",
                    batch,
                    "UPLOAD",
                    Map.of()))
        .isInstanceOf(Problem.class);
    var item = intake.items(tenant, batch).get(0);
    assertThat(item)
        .containsEntry("processing_state", "FAILED")
        .containsEntry("id", null)
        .containsEntry("retryable", false);
    assertThat(db.list("select id from invoices where tenant_id=?", tenant)).isEmpty();
    mvc.perform(
            get("/api/v1/import-batches/" + batch + "/errors")
                .with(user(processor + "@example.test"))
                .header("X-Organization-ID", tenant))
        .andExpect(status().isOk())
        .andExpect(content().string(org.hamcrest.Matchers.containsString("\"'=bad.pdf\"")));
    mvc.perform(
            get("/api/v1/import-batches/" + batch)
                .with(user(processor + "@example.test"))
                .header("X-Organization-ID", other))
        .andExpect(status().isForbidden());
  }

  @Test
  void transientStorageFailureRetriesSameIntakeItemWithoutDuplicateInvoice() throws Exception {
    UUID batch = UUID.randomUUID();
    db.update(
        "insert into ingestion_batches(id,tenant_id,actor) values(?,?,?)",
        batch,
        tenant,
        processor);
    byte[] pdf = E2EApplication.scanned("STORAGE-RETRY");
    doThrow(new IllegalStateException("simulated storage outage"))
        .when(storage)
        .put(anyString(), any(), anyString());
    assertThatThrownBy(
            () ->
                intake.upload(
                    prep, "invoice.pdf", pdf, "storage-retry-key", batch, "UPLOAD", Map.of()))
        .isInstanceOf(IllegalStateException.class);
    var failed = intake.items(tenant, batch).get(0);
    assertThat(failed.get("retryable")).isEqualTo(true);
    doNothing().when(storage).put(anyString(), any(), anyString());
    var result =
        intake.upload(prep, "invoice.pdf", pdf, "storage-retry-key", batch, "UPLOAD", Map.of());
    assertThat(result.get("id")).isEqualTo(failed.get("item_id"));
    assertThat(intake.items(tenant, batch)).hasSize(1);
    assertThat(intake.items(tenant, batch).get(0).get("attempts")).isEqualTo(2);
    assertThat(
            intake
                .upload(prep, "invoice.pdf", pdf, "storage-retry-key", batch, "UPLOAD", Map.of())
                .get("id"))
        .isEqualTo(result.get("id"));
    assertThat(db.list("select id from invoices where tenant_id=?", tenant)).hasSize(1);
  }

  @Test
  void signedEmailContinuesPastInvalidAttachmentAndReplayIsIdempotent() throws Exception {
    String timestamp = String.valueOf(java.time.Instant.now().getEpochSecond()),
        token = "synthetic-token";
    var mac = javax.crypto.Mac.getInstance("HmacSHA256");
    mac.init(
        new javax.crypto.spec.SecretKeySpec(
            "synthetic-signing-key".getBytes(java.nio.charset.StandardCharsets.UTF_8),
            "HmacSHA256"));
    String signature =
        HexFormat.of()
            .formatHex(
                mac.doFinal((timestamp + token).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    byte[] pdf = E2EApplication.scanned("EMAIL-PARTIAL");
    for (int attempt = 0; attempt < 2; attempt++) {
      mvc.perform(
              multipart("/api/v1/email/mailgun")
                  .file(
                      new org.springframework.mock.web.MockMultipartFile(
                          "attachment-1", "bad.txt", "text/plain", new byte[] {1, 2, 3}))
                  .file(
                      new org.springframework.mock.web.MockMultipartFile(
                          "attachment-2", "good.pdf", "application/pdf", pdf))
                  .param("timestamp", timestamp)
                  .param("token", token)
                  .param("signature", signature)
                  .param("recipient", tenant + "@intake.example.test")
                  .param("Message-Id", "message-" + tenant)
                  .param("sender", "sender@example.test"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.attachments[0].status").value("FAILED"))
          .andExpect(jsonPath("$.attachments[1].status").value("ACCEPTED"));
    }
    assertThat(db.list("select id from invoices where tenant_id=?", tenant)).hasSize(1);
    assertThat(db.list("select id from ingestion_items where tenant_id=?", tenant)).hasSize(2);
    assertThat(db.list("select id from ingestion_batches where tenant_id=?", tenant)).hasSize(1);
  }

  @Test
  void invalidEmailSignatureCreatesNoIntakeRecords() throws Exception {
    mvc.perform(
            multipart("/api/v1/email/mailgun")
                .file(
                    new org.springframework.mock.web.MockMultipartFile(
                        "attachment-1", "x.pdf", "application/pdf", new byte[] {1}))
                .param("timestamp", String.valueOf(java.time.Instant.now().getEpochSecond()))
                .param("token", "x")
                .param("signature", "0".repeat(64)))
        .andExpect(status().isUnauthorized());
    assertThat(db.list("select id from ingestion_batches where tenant_id=?", tenant)).isEmpty();
  }

  @Test
  void missingProviderConfigurationPreservesQueuedWorkUntilConfigured() throws Exception {
    UUID id = create("CONFIGURATION-WAIT", "2");
    intake.retry(prep, id);
    when(extraction.configuration())
        .thenReturn(
            new DocumentExtractionPort.Configuration(
                "Azure Document Intelligence",
                "prebuilt-invoice",
                false,
                false,
                "NOT_CONFIGURED",
                "Set AZURE_DOCUMENT_KEY",
                List.of("AZURE_DOCUMENT_KEY")));
    assertThat(worker.runOne()).isFalse();
    assertThat(
            db.one(
                "select state,attempts from processing_jobs where tenant_id=? and invoice_id=?",
                tenant,
                id))
        .containsEntry("state", "QUEUED")
        .containsEntry("attempts", 0);
    assertThat(invoices.row(tenant, id, false).get("processing_state")).isEqualTo("QUEUED");
    verify(extraction, never()).extract(any(), any());
    verify(storage, never()).get(anyString());
    mvc.perform(
            get("/api/v1/settings")
                .with(user(processor + "@example.test"))
                .header("X-Organization-ID", tenant))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.extractionConfigured").value(false))
        .andExpect(jsonPath("$.extraction.missingVariables[0]").value("AZURE_DOCUMENT_KEY"));
    when(extraction.configuration())
        .thenReturn(
            new DocumentExtractionPort.Configuration(
                "TEST_DOUBLE", "fixture-v1", true, true, "TEST_MODE", "Test", List.of()));
    when(storage.get(anyString())).thenReturn(new byte[] {1});
    when(extraction.extract(any(), any()))
        .thenReturn(
            new DocumentExtractionPort.Result(
                "TEST_DOUBLE",
                "fixture-v1",
                Map.of(),
                DomainTest.invoice("CONFIGURATION-WAIT", supplier, "PO-1", "2"),
                Map.of()));
    assertThat(worker.runOne()).isTrue();
    assertThat(invoices.row(tenant, id, false).get("processing_state")).isEqualTo("PROCESSED");
  }

  @Test
  void dashboardAggregatesAreExecutableAndTenantScoped() throws Exception {
    create("DASHBOARD-1", "2");
    mvc.perform(
            get("/api/v1/dashboard")
                .with(user(processor + "@example.test"))
                .header("X-Organization-ID", tenant))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.received").value(1))
        .andExpect(jsonPath("$.value").value("236.0000"))
        .andExpect(jsonPath("$.trend[0].received").value(1));
  }

  @Test
  void bulkHundredDocumentsHaveIndependentJobsAndSafeRetries() throws Exception {
    byte[] pdf = E2EApplication.scanned("BULK-SYNTHETIC");
    when(storage.get(anyString())).thenReturn(pdf);
    when(extraction.extract(any(), any()))
        .thenReturn(
            new DocumentExtractionPort.Result(
                "TEST_DOUBLE",
                "bulk-fixture",
                Map.of(),
                DomainTest.invoice("BULK-1", null, "PO-1", "1"),
                Map.of()));
    UUID batch = UUID.randomUUID();
    db.update(
        "insert into ingestion_batches(id,tenant_id,actor) values(?,?,?)",
        batch,
        tenant,
        processor);
    List<UUID> ids = new ArrayList<>();
    for (int n = 0; n < 100; n++)
      ids.add(
          (UUID)
              intake
                  .upload(
                      prep,
                      "synthetic-" + n + ".pdf",
                      pdf,
                      "bulk-" + batch + "-" + n,
                      batch,
                      "UPLOAD",
                      Map.of("test", true))
                  .get("id"));
    for (int n = 0; n < 100; n++) assertThat(worker.runOne()).isTrue();
    assertThat(
            db.one(
                    "select count(*) n from invoices where tenant_id=? and batch_id=? and processing_state='PROCESSED'",
                    tenant,
                    batch)
                .get("n"))
        .isEqualTo(100L);
    intake.upload(prep, "synthetic-0.pdf", pdf, "bulk-" + batch + "-0", batch, "UPLOAD", Map.of());
    assertThat(
            db.one(
                    "select count(*) n from invoices where tenant_id=? and batch_id=?",
                    tenant,
                    batch)
                .get("n"))
        .isEqualTo(100L);
    intake.retry(prep, ids.get(0));
    worker.runOne();
    assertThat(db.one("select count(*) n from extraction_runs where tenant_id=?", tenant).get("n"))
        .isEqualTo(101L);
  }

  @Test
  void blockingGstErrorCanBeCorrectedAndRevalidated() {
    UUID id = create("INV-B", "4");
    var original = DomainTest.invoice("INV-B", supplier, "PO-1", "4");
    Map<String, Object> changed =
        db.mapper()
            .convertValue(original, new com.fasterxml.jackson.core.type.TypeReference<>() {});
    changed.put("cgst", "99.99");
    invoices.save(
        prep,
        id,
        1,
        db.convert(changed, InvoiceData.class),
        "Introduce GST discrepancy in synthetic test");
    assertThatThrownBy(() -> invoices.transition(prep, id, "submit", 2, "Prepared", approver))
        .isInstanceOf(Problem.class);
    invoices.save(prep, id, 2, original, "Correct GST against source");
    invoices.transition(prep, id, "submit", 3, "Prepared", approver);
    assertThat(invoices.row(tenant, id, false).get("business_state"))
        .isEqualTo("READY_FOR_APPROVAL");
  }

  @Test
  void decimalOpenApiContractMatchesWireSerialization() throws Exception {
    String schema =
        mvc.perform(get("/v3/api-docs"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    var node = db.mapper().readTree(schema);
    assertThat(node.at("/components/schemas/InvoiceData/properties/total/type").asText())
        .isEqualTo("string");
    assertThat(
            db.mapper()
                .readTree(db.json(DomainTest.invoice("SCHEMA", supplier, "PO-1", "1")))
                .get("total")
                .isTextual())
        .isTrue();
  }
}
