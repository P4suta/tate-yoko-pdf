package dev.sakashita.tateyokopdf.web.routes;

import static org.assertj.core.api.Assertions.assertThat;

import dev.sakashita.tateyokopdf.testfixtures.JobWsClient;
import dev.sakashita.tateyokopdf.testfixtures.MultipartFormBody;
import dev.sakashita.tateyokopdf.testfixtures.PdfFixtures;
import dev.sakashita.tateyokopdf.testfixtures.WebTestHarness;
import dev.sakashita.tateyokopdf.web.job.ProgressEvent;
import io.javalin.testtools.JavalinTest;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.pdfbox.Loader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Real-wire happy path for the spread conversion. Drives the full POST → WebSocket → GET pipeline
 * against a running Javalin server using JDK's built-in WebSocket client (via {@link JobWsClient}).
 *
 * <p>This is the test layer that would have caught the PR #33 {@code Content-Length} bug — the
 * download leg is part of the same flow and any future regression in that seam fails here.
 */
final class JobFullFlowIntegrationTest {

  private static final Pattern JOB_ID = Pattern.compile("\"id\":\"([0-9a-fA-F-]{36})\"");
  private static final Duration FRAME_TIMEOUT = Duration.ofSeconds(15);

  @Test
  void fourPageDefaultProducesTwoSpreadsAndDownloadCompletes(@TempDir Path tmp) throws Exception {
    runFlow(tmp, 4, false, 2);
  }

  @Test
  void fivePageCoverSingleProducesThreeSpreads(@TempDir Path tmp) throws Exception {
    // 5 pages with coverSingle=true: 1 single-page spread (cover) + 2 paired spreads = 3 total.
    runFlow(tmp, 5, true, 3);
  }

  @Test
  void fiftyPageDefaultProducesTwentyFiveSpreads(@TempDir Path tmp) throws Exception {
    // 50-page run — big enough to surface streaming/concurrency seams that a 4-page test misses.
    runFlow(tmp, 50, false, 25);
  }

  @Test
  void titleAndAuthorAreInheritedAcrossFullPipeline(@TempDir Path tmp) throws Exception {
    Path inputFile =
        PdfFixtures.withMetadata(
            tmp,
            "with-meta.pdf",
            info -> {
              info.setTitle("見開き化テスト");
              info.setAuthor("テスト著者");
            });
    byte[] pdf = Files.readAllBytes(inputFile);
    runFlowWithBytes(
        pdf,
        false,
        1,
        downloaded -> {
          Path roundTripped;
          try {
            roundTripped = Files.write(tmp.resolve("downloaded.pdf"), downloaded);
            try (var doc = Loader.loadPDF(roundTripped.toFile())) {
              var info = doc.getDocumentInformation();
              assertThat(info.getTitle()).isEqualTo("見開き化テスト");
              assertThat(info.getAuthor()).isEqualTo("テスト著者");
              assertThat(info.getProducer()).isEqualTo("tate-yoko-pdf");
            }
          } catch (Exception e) {
            throw new AssertionError("metadata round-trip failed", e);
          }
        });
  }

  private void runFlow(Path tmp, int pages, boolean coverSingle, int expectedSpreads)
      throws Exception {
    byte[] pdf = Files.readAllBytes(PdfFixtures.multiPageA4(tmp, "in.pdf", pages));
    runFlowWithBytes(pdf, coverSingle, expectedSpreads, bytes -> {});
  }

  private void runFlowWithBytes(
      byte[] pdf,
      boolean coverSingle,
      int expectedSpreads,
      java.util.function.Consumer<byte[]> onDownloaded)
      throws Exception {
    // JobController treats coverSingle as "any presence" — omit the field entirely for false.
    var body = new MultipartFormBody();
    if (coverSingle) {
      body.addField("coverSingle", "on");
    }
    body.addFile("pdf", "in.pdf", "application/pdf", pdf);

    JavalinTest.test(
        WebTestHarness.app(),
        (server, client) -> {
          var submit =
              client.request(
                  "/api/jobs",
                  rb -> rb.header("Content-Type", body.contentType()).post(body.publisher()));
          assertThat(submit.code()).isEqualTo(202);

          String json = submit.body().string();
          Matcher m = JOB_ID.matcher(json);
          assertThat(m.find()).as("submit response %s contains an id", json).isTrue();
          UUID jobId = UUID.fromString(m.group(1));

          try (JobWsClient ws = JobWsClient.connect(server.port(), jobId)) {
            ProgressEvent started = Objects.requireNonNull(ws.nextEvent(FRAME_TIMEOUT), "started");
            assertThat(started).isInstanceOf(ProgressEvent.Started.class);
            assertThat(((ProgressEvent.Started) started).total()).isEqualTo(expectedSpreads);

            for (int i = 1; i <= expectedSpreads; i++) {
              ProgressEvent ev =
                  Objects.requireNonNull(ws.nextEvent(FRAME_TIMEOUT), "progress frame");
              assertThat(ev).isInstanceOf(ProgressEvent.Progress.class);
              ProgressEvent.Progress p = (ProgressEvent.Progress) ev;
              assertThat(p.current()).as("progress current at step %d", i).isEqualTo(i);
              assertThat(p.total()).isEqualTo(expectedSpreads);
            }

            ProgressEvent terminal =
                Objects.requireNonNull(ws.nextEvent(FRAME_TIMEOUT), "terminal");
            assertThat(terminal).isInstanceOf(ProgressEvent.Completed.class);
          }

          // The testtools `client.get` wraps responses as `String`-decoded — fine for ASCII
          // checks but it mangles binary bytes (compressed object streams, UTF-16BE-encoded
          // metadata) once decoded as UTF-8. Use JDK's HttpClient directly so the body comes
          // back as `byte[]`, lossless for PDFBox parsing downstream.
          var jdkClient = java.net.http.HttpClient.newHttpClient();
          var dlRequest =
              HttpRequest.newBuilder()
                  .GET()
                  .uri(
                      URI.create(
                          "http://localhost:" + server.port() + "/api/jobs/" + jobId + "/download"))
                  .build();
          HttpResponse<byte[]> download =
              jdkClient.send(dlRequest, HttpResponse.BodyHandlers.ofByteArray());
          assertThat(download.statusCode()).isEqualTo(200);
          byte[] dlBytes = download.body();
          String dlBody = new String(dlBytes, StandardCharsets.ISO_8859_1);
          assertThat(dlBody).startsWith("%PDF-");
          int tailWindow = Math.min(64, dlBody.length());
          assertThat(dlBody.substring(dlBody.length() - tailWindow))
              .as("PDF body must end with %%EOF marker — truncation indicates a partial write")
              .contains("%%EOF");
          onDownloaded.accept(dlBytes);
        });
  }
}
