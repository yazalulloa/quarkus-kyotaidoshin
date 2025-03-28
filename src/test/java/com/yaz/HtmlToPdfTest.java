package com.yaz;

import com.openhtmltopdf.pdfboxout.PdfBoxRenderer;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import io.vertx.core.json.Json;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

@Slf4j
public class HtmlToPdfTest {

  final String dirPath = "/home/yaz/Downloads/ANTONIETA_FEBRERO_2025-03-14";

  @Test
  void test() throws IOException {
    Paths.get(dirPath, "PDF").toFile().mkdirs();
    final var dir = new File(dirPath);
    final var builder = new PdfRendererBuilder();
    builder.withProducer("kyotaidoshin");

    for (var file : dir.listFiles()) {
      if (file.getName().endsWith(".html")) {
        log.info("{}", file);
        final var str = Files.readString(file.toPath());


        final var baseURI = "";
        builder.withHtmlContent(str, baseURI);
        final var out = new FileOutputStream(Paths.get(dirPath, "PDF", file.getName() + ".pdf").toFile());
        builder.toStream(out);
        try (PdfBoxRenderer renderer = builder.buildPdfRenderer()) {
          renderer.createPDF();
        }

      }

    }


  }

  public record HtmlToPdfRequest(
      String objectKey,
      String html
  ) {

  }


  @Test
  void toBase64() throws IOException {

    final var requests = new ArrayList<HtmlToPdfRequest>();

    final var dir = new File(dirPath);
    for (var file : dir.listFiles()) {

      if (file.getName().endsWith(".html")) {
        log.info("{}", file);
        final var str = Files.readString(file.toPath());
        final var bytes = str.getBytes();
        final var encoded = java.util.Base64.getUrlEncoder().encodeToString(bytes);
        requests.add(new HtmlToPdfRequest(file.getName(), encoded));
      }

    }

    log.info("{}", Json.encode(requests));
  }


}
