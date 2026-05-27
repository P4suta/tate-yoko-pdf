package dev.sakashita.tateyokopdf.web.routes;

import static org.assertj.core.api.Assertions.assertThat;

import dev.sakashita.tateyokopdf.domain.exception.ErrorKind;
import dev.sakashita.tateyokopdf.domain.exception.SpreadException;
import dev.sakashita.tateyokopdf.testfixtures.JobWsClient;
import dev.sakashita.tateyokopdf.testfixtures.MultipartFormBody;
import dev.sakashita.tateyokopdf.testfixtures.PdfFixtures;
import dev.sakashita.tateyokopdf.testfixtures.WebTestHarness;
import dev.sakashita.tateyokopdf.web.job.Job;
import dev.sakashita.tateyokopdf.web.job.JobRegistry;
import dev.sakashita.tateyokopdf.web.job.ProgressEvent;
import io.javalin.Javalin;
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
 * Real-wire error paths that end at the WS/HTTP boundary. Pairs with {@link
 * JobFullFlowIntegrationTest} (happy path) to give every wire-visible {@link ErrorKind} a path test
 * that exercises the actual production routing.
 */
final class JobErrorPathIntegrationTest {

  private static final Pattern JOB_ID = Pattern.compile("\"id\":\"([0-9a-fA-F-]{36})\"");
  private static final Duration FRAME_TIMEOUT = Duration.ofSeconds(15);

  @Test
  void encryptedPdfFailsThroughWebSocketWithPasswordProtectedKind(@TempDir Path tmp)
      throws Exception {
    byte[] encrypted = Files.readAllBytes(PdfFixtures.passwordProtected(tmp, "in.pdf", "secret"));
    var body = new MultipartFormBody().addFile("pdf", "in.pdf", "application/pdf", encrypted);

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

          try (JobWsClient ws = JobWsClient.connect(server.port(), jobId)) {
            // The job is submitted asynchronously; the worker thread opens the PDF, hits
            // InvalidPasswordException inside PdfBoxDocumentFactory, and that gets translated
            // to SpreadException(PDF_PASSWORD_PROTECTED) which the WebProgressListener publishes
            // as a Failed event. The WS pump then closes the session.
            ProgressEvent ev = Objects.requireNonNull(ws.nextEvent(FRAME_TIMEOUT), "failed frame");
            assertThat(ev).isInstanceOf(ProgressEvent.Failed.class);
            ProgressEvent.Failed failed = (ProgressEvent.Failed) ev;
            assertThat(failed.errorKind()).isEqualTo(ErrorKind.PDF_PASSWORD_PROTECTED);
          }
        });
  }

  @Test
  void downloadAfterOutputDeletedReturns410(@TempDir Path tmp) throws Exception {
    // JOB_OUTPUT_GONE can only fire when the registry still knows about a job but its on-disk
    // output has been removed (e.g. by external cleanup). We can't drive that through the full
    // HTTP path because the registry is internal; build the DownloadHandler with a pre-populated
    // registry whose recorded outputPath does not exist on disk.
    JobRegistry registry = new JobRegistry();
    Path workDir = Files.createDirectories(tmp.resolve("work"));
    Path missing = workDir.resolve("gone.pdf"); // intentionally never written
    Job job =
        registry.register(workDir, workDir.resolve("input.pdf"), missing, "foo.pdf", "trace-gone");

    DownloadHandler handler = new DownloadHandler(registry);
    WebExceptionHandler exHandler = new WebExceptionHandler();
    Javalin app =
        Javalin.create(
            config -> {
              config.routes.get("/download", ctx -> handler.serve(ctx, job));
              config.routes.exception(SpreadException.class, exHandler::handleDomain);
            });

    JavalinTest.test(
        app,
        (server, client) -> {
          var resp = client.get("/download");
          assertThat(resp.code()).isEqualTo(410);
          assertThat(resp.body().string()).contains("JOB_OUTPUT_GONE");
        });
  }
}
