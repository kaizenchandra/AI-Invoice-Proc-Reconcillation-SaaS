package com.kaizen.invoice;

import com.kaizen.invoice.application.DocumentExtractionPort;
import com.kaizen.invoice.domain.Problem;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Explicit test-classpath-only provider. Never packaged into the production application. */
public class E2EApplication {
  public static void main(String[] args) throws Exception {
    if (args.length > 0 && args[0].equals("fixtures")) {
      Path dir = Path.of("../fixtures");
      Files.createDirectories(dir);
      Files.write(dir.resolve("synthetic-scanned-invoice.pdf"), scanned("TEST-2026-001"));
      return;
    }
    SpringApplication.from(InvoiceApplication::main).with(FixtureConfiguration.class).run(args);
  }

  @TestConfiguration
  static class FixtureConfiguration {
    @Bean
    @Primary
    DocumentExtractionPort explicitTestProvider() {
      return new DocumentExtractionPort() {
        @Override
        public Configuration configuration() {
          return new Configuration(
              "TEST_DOUBLE — synthetic fixture only",
              "fixture-v1",
              true,
              true,
              "TEST_MODE",
              "Synthetic test extraction is active. Real invoices require the normal backend with Azure credentials.",
              java.util.List.of());
        }

        @Override
        public Result extract(byte[] bytes, String mime) throws Exception {
          if (!mime.equals("application/pdf"))
            throw new Problem(
                422, "TEST PROVIDER: only the synthetic scanned PDF fixture is accepted");
          try (var pdf = Loader.loadPDF(bytes)) {
            String marker = pdf.getDocumentInformation().getTitle();
            if (marker == null || !marker.startsWith("KAIZEN_SYNTHETIC_TEST:"))
              throw new Problem(
                  422, "TEST PROVIDER: document is not an explicitly marked synthetic fixture");
            String number = marker.substring("KAIZEN_SYNTHETIC_TEST:".length());
            var invoice = DomainTest.invoice(number, null, "PO-2026-001", "5");
            return new DocumentExtractionPort.Result(
                "TEST_DOUBLE — synthetic fixture only",
                "fixture-v1",
                Map.of("testFixture", true, "marker", marker),
                invoice,
                Map.of(
                    "invoiceNumber",
                    Map.of("source", "TEST FIXTURE (not live AI)", "original", number)));
          }
        }
      };
    }
  }

  static byte[] scanned(String number) throws Exception {
    BufferedImage image = new BufferedImage(1240, 1754, BufferedImage.TYPE_INT_RGB);
    Graphics2D g = image.createGraphics();
    g.setColor(Color.WHITE);
    g.fillRect(0, 0, 1240, 1754);
    g.setColor(new Color(20, 35, 45));
    g.setRenderingHint(
        RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    g.setFont(new Font("SansSerif", Font.BOLD, 40));
    g.drawString("PUNE COMPONENTS", 90, 140);
    g.setFont(new Font("SansSerif", Font.PLAIN, 22));
    g.drawString("SYNTHETIC TEST FIXTURE — NOT A REAL TAX INVOICE", 90, 195);
    g.drawString("GSTIN: 27AAPFU0939F1ZV | Pune, Maharashtra", 90, 245);
    g.drawLine(90, 285, 1150, 285);
    g.setFont(new Font("SansSerif", Font.BOLD, 28));
    g.drawString("TAX INVOICE  " + number, 90, 360);
    g.setFont(new Font("SansSerif", Font.PLAIN, 25));
    String[] text = {
      "Invoice date: 01 October 2026",
      "Purchase order: PO-2026-001",
      "Bill to: Kaizen Manufacturing, Pune",
      "Buyer GSTIN: 27AAPFU0939F1ZV",
      "Place of supply: Maharashtra (27)",
      "",
      "Item: STEEL-01 — Steel brackets",
      "HSN: 73269099",
      "Quantity: 5 PCS      Unit price: INR 100.00",
      "Taxable amount: INR 500.00",
      "CGST 9%: INR 45.00",
      "SGST 9%: INR 45.00",
      "IGST: INR 0.00",
      "",
      "GRAND TOTAL: INR 590.00",
      "",
      "Payment terms: NET30",
      "Due date: 01 November 2026"
    };
    int y = 430;
    for (String line : text) {
      g.drawString(line, 90, y);
      y += 52;
    }
    g.dispose();
    try (var pdf = new PDDocument();
        var output = new ByteArrayOutputStream()) {
      var page = new PDPage(PDRectangle.A4);
      pdf.addPage(page);
      try (var content = new PDPageContentStream(pdf, page)) {
        content.drawImage(
            LosslessFactory.createFromImage(pdf, image),
            0,
            0,
            PDRectangle.A4.getWidth(),
            PDRectangle.A4.getHeight());
      }
      pdf.getDocumentInformation().setTitle("KAIZEN_SYNTHETIC_TEST:" + number);
      pdf.save(output);
      return output.toByteArray();
    }
  }
}
