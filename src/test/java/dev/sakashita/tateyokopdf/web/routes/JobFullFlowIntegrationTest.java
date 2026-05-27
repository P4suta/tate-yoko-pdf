package dev.sakashita.tateyokopdf.web.routes;

import static org.assertj.core.api.Assertions.assertThat;

import dev.sakashita.tateyokopdf.testfixtures.JobWsClient;
import dev.sakashita.tateyokopdf.testfixtures.MultipartFormBody;
import dev.sakashita.tateyokopdf.testfixtures.PdfFixtures;
import dev.sakashita.tateyokopdf.testfixtures.WebTestHarness;
import dev.sakashita.tateyokopdf.web.job.ProgressEvent;
import io.javalin.testtools.JavalinTest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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

  private void runFlow(Path tmp, int pages, boolean coverSingle, int expectedSpreads)
      throws Exception {
    byte[] pdf = Files.readAllBytes(PdfFixtures.multiPageA4(tmp, "in.pdf", pages));
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

          var download = client.get("/api/jobs/" + jobId + "/download");
          assertThat(download.code()).isEqualTo(200);
          // OkHttp's testtools wrapper hides `body().bytes()`/`byteStream()`; `string()` decodes
          // with ISO-8859-1 (latin-1) which is the only single-byte charset that round-trips
          // every byte value 0..255 unchanged. Use that so a truncated PDF body is still
          // detectable by tail-searching for `%%EOF`.
          String dlBody = download.body().string();
          assertThat(dlBody).startsWith("%PDF-");
          int tailWindow = Math.min(64, dlBody.length());
          assertThat(dlBody.substring(dlBody.length() - tailWindow))
              .as("PDF body must end with %%EOF marker — truncation indicates a partial write")
              .contains("%%EOF");
        });
  }
}
