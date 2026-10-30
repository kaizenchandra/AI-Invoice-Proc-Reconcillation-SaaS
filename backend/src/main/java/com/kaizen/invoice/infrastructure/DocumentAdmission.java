package com.kaizen.invoice.infrastructure;

import static com.kaizen.invoice.domain.Problem.require;

import com.kaizen.invoice.domain.Problem;
import java.io.*;
import java.net.*;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class DocumentAdmission {
  private final String host;
  private final int port;
  private final boolean enabled;

  public DocumentAdmission(
      @Value("${app.scanner.host}") String host,
      @Value("${app.scanner.port}") int port,
      @Value("${app.scanner.enabled}") boolean enabled) {
    this.host = host;
    this.port = port;
    this.enabled = enabled;
  }

  public String validate(byte[] bytes) {
    require(
        bytes.length > 0 && bytes.length <= 20 * 1024 * 1024,
        400,
        "Each document must be between 1 byte and 20 MB");
    String mime;
    try {
      if (bytes.length > 5
          && new String(bytes, 0, 5, java.nio.charset.StandardCharsets.US_ASCII).equals("%PDF-")) {
        mime = "application/pdf";
        try (var pdf = Loader.loadPDF(bytes)) {
          require(
              !pdf.isEncrypted() && pdf.getNumberOfPages() > 0 && pdf.getNumberOfPages() <= 50,
              400,
              "PDFs must be unencrypted and contain 1–50 pages");
          require(
              pdf.getDocumentCatalog().getOpenAction() == null
                  && pdf.getDocumentCatalog().getNames() == null,
              400,
              "PDF actions and embedded content are not accepted");
        }
      } else {
        mime =
            bytes.length > 8 && (bytes[0] & 255) == 137 && bytes[1] == 80
                ? "image/png"
                : bytes.length > 3 && (bytes[0] & 255) == 255 && (bytes[1] & 255) == 216
                    ? "image/jpeg"
                    : "";
        require(!mime.isEmpty(), 400, "Only PDF, JPEG and PNG content is supported");
        try (var stream = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
          var readers = ImageIO.getImageReaders(stream);
          require(readers.hasNext(), 400, "Unreadable image");
          var reader = readers.next();
          try {
            reader.setInput(stream);
            require(
                (long) reader.getWidth(0) * reader.getHeight(0) <= 40_000_000,
                400,
                "Image exceeds the 40 megapixel limit");
          } finally {
            reader.dispose();
          }
        }
      }
    } catch (Problem e) {
      throw e;
    } catch (Exception e) {
      throw new Problem(400, "The document is malformed or cannot be read");
    }
    if (enabled) scan(bytes);
    return mime;
  }

  private void scan(byte[] bytes) {
    try (Socket socket = new Socket()) {
      socket.connect(new InetSocketAddress(host, port), 3000);
      socket.setSoTimeout(15000);
      var out = new DataOutputStream(socket.getOutputStream());
      out.write("zINSTREAM\0".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
      for (int pos = 0; pos < bytes.length; pos += 8192) {
        int n = Math.min(8192, bytes.length - pos);
        out.writeInt(n);
        out.write(bytes, pos, n);
      }
      out.writeInt(0);
      out.flush();
      String result =
          new String(
              socket.getInputStream().readNBytes(1024), java.nio.charset.StandardCharsets.UTF_8);
      require(
          result.contains("stream: OK"), 422, "Document quarantined: malware scan did not pass");
    } catch (Problem e) {
      throw e;
    } catch (Exception e) {
      throw new Problem(
          503, "Document scanner unavailable. No document was accepted; retry shortly.");
    }
  }
}
