package dev.sakashita.tateyokopdf.web.routes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import dev.sakashita.tateyokopdf.testfixtures.JobWsClient;
import dev.sakashita.tateyokopdf.testfixtures.MultipartFormBody;
import dev.sakashita.tateyokopdf.testfixtures.PdfFixtures;
import dev.sakashita.tateyokopdf.testfixtures.WebTestHarness;
import dev.sakashita.tateyokopdf.web.job.ProgressEvent;
import io.javalin.testtools.JavalinTest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Exercises the disconnect side of the WS pump in {@link JobController#pump}. Catches regressions
 * that would leak the per-subscriber queue or strand the pump thread when the client closes without
 * going through the normal Completed/Failed handshake.
 *
 * <p>Verification strategy: after the first client aborts, connect a second client to the same job
 * ID. Because the first connection's pump unsubscribed on its way out, the second client must still
 * receive the history backfill and any subsequent terminal frame — proving that the listener stayed
 * functional and no shared state was corrupted by the abrupt close.
 */
final class WsPumpDisconnectTest {

  private static final Pattern JOB_ID = Pattern.compile("\"id\":\"([0-9a-fA-F-]{36})\"");
  private static final Duration FRAME_TIMEOUT = Duration.ofSeconds(15);

  @Test
  void abruptClientCloseLeavesListenerFunctionalForNewSubscribers(@TempDir Path tmp)
      throws Exception {
    byte[] pdf = Files.readAllBytes(PdfFixtures.multiPageA4(tmp, "in.pdf", 8));
    var body = new MultipartFormBody().addFile("pdf", "in.pdf", "application/pdf", pdf);

    JavalinTest.test(
        WebTestHarness.app(),
        (server, client) -> {
          var submit =
              client.request(
                  "/api/jobs",
                  rb -> rb.header("Content-Type", body.contentType()).post(body.publisher()));
          assertThat(submit.code()).isEqualTo(202);
          Matcher m = JOB_ID.matcher(submit.body().string());
          assertThat(m.find()).isTrue();
          UUID jobId = UUID.fromString(m.group(1));

          // First client: connect, see at least the Started frame, then yank the socket out.
          JobWsClient first = JobWsClient.connect(server.port(), jobId);
          try {
            ProgressEvent started =
                java.util.Objects.requireNonNull(first.nextEvent(FRAME_TIMEOUT), "started");
            assertThat(started).isInstanceOf(ProgressEvent.Started.class);
          } finally {
            first.abort();
          }

          // Second client: must still receive history backfill plus the eventual Completed frame.
          // If the pump leaked the first subscription or left the listener half-broken, this
          // either hangs (no frames received) or sees a corrupted state.
          try (JobWsClient second = JobWsClient.connect(server.port(), jobId)) {
            await()
                .atMost(FRAME_TIMEOUT)
                .pollDelay(Duration.ofMillis(50))
                .untilAsserted(
                    () -> {
                      ProgressEvent ev = second.nextEvent(Duration.ofMillis(500));
                      assertThat(ev).isNotNull();
                      if (ev instanceof ProgressEvent.Completed) {
                        return; // done
                      }
                      // Otherwise it's Started/Progress; loop until we see Completed.
                      throw new AssertionError("waiting for Completed, got " + ev);
                    });
          }
        });
  }
}
